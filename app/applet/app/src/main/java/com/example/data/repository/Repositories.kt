package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.dao.ProductDao
import com.example.data.dao.ScanTransactionDao
import com.example.data.dao.StaffDao
import com.example.data.dao.WarehouseSettingDao
import com.example.data.entities.ProductEntity
import com.example.data.entities.ScanTransactionEntity
import com.example.data.entities.StaffEntity
import com.example.data.entities.WarehouseSettingEntity
import com.example.util.SyncUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

object Catalog {
    val defaultProducts = listOf(
        ProductEntity("PROD-001", "Brodan Insecticide 100ml", "BRD-100", "Crop Protection", 0, "bottles", 5, 12),
        ProductEntity("PROD-002", "Complete Fertilizer 14-14-14", "CF-1414", "Fertilizers", 0, "bags", 10, 1),
        ProductEntity("PROD-003", "Hybrid Corn Seeds Y88", "HCS-Y88", "Seeds", 0, "packs", 8, 10),
        ProductEntity("PROD-004", "Knapsack Sprayer 16L", "KS-16L", "Equipment", 0, "units", 3, 1),
        ProductEntity("PROD-005", "Urea Fertilizer 46-0-0", "UF-4600", "Fertilizers", 0, "bags", 10, 1)
    )

    fun findProduct(productId: String): ProductEntity? {
        return defaultProducts.find { it.productId == productId || it.sku == productId }
    }
}

object RtdbClient {
    val rtdb: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    private const val RTDB_URL = "https://agri-stock-ims-default-rtdb.firebaseio.com"

    suspend fun get(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
            val token = suspendCancellableCoroutine<String?> { cont ->
                user.getIdToken(false).addOnSuccessListener { result ->
                    cont.resume(result.token)
                }.addOnFailureListener {
                    cont.resume(null)
                }
            } ?: return@withContext null

            val url = URL("$RTDB_URL/$path.json?auth=$token")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                response.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("RtdbClient", "GET $path failed: ${e.message}")
            null
        }
    }

    suspend fun updateChildren(updates: Map<String, Any?>): Boolean = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            rtdb.reference.updateChildren(updates) { error, _ ->
                if (error == null) {
                    cont.resume(true)
                } else {
                    Log.e("RtdbClient", "updateChildren failed: ${error.message}")
                    cont.resume(false)
                }
            }
        }
    }
}

class AuthRepository(
    private val staffDao: StaffDao,
    private val context: Context
) {
    private val _currentStaff = MutableStateFlow<StaffEntity?>(null)
    val currentStaff: StateFlow<StaffEntity?> = _currentStaff.asStateFlow()

    private fun isBlockedStatus(status: String?): Boolean =
        status?.trim()?.uppercase() in setOf("INACTIVE", "REVOKED", "DISABLED", "SUSPENDED")

    suspend fun login(employeeId: String, password: String): Result<StaffEntity> = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val formattedId = employeeId.trim().uppercase()
        val email = "$formattedId@agristock.internal"

        try {
            val authResult = suspendCancellableCoroutine<Boolean> { cont ->
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener { cont.resume(true) }
                    .addOnFailureListener { cont.resume(false) }
            }

            if (authResult && auth.currentUser != null) {
                val uid = auth.currentUser!!.uid
                val profileJsonStr = RtdbClient.get("userProfiles/$uid")
                
                if (profileJsonStr != null && profileJsonStr != "null") {
                    val json = JSONObject(profileJsonStr)
                    val status = json.optString("status")
                    if (isBlockedStatus(status)) {
                        auth.signOut()
                        val localStaff = staffDao.getStaffByEmployeeId(formattedId)
                        if (localStaff != null) {
                            staffDao.insertStaff(localStaff.copy(status = "INACTIVE"))
                        }
                        _currentStaff.value = null
                        return@withContext Result.failure(SecurityException("Access has been revoked. Please contact your warehouse supervisor."))
                    }

                    val name = json.optString("fullName", json.optString("name", "Warehouse Staff"))
                    val role = json.optString("role", "STAFF")
                    val staff = StaffEntity(
                        staffId = formattedId,
                        employeeId = formattedId,
                        fullName = name,
                        role = role,
                        assignedWarehouse = "UniFam Store",
                        status = "ACTIVE"
                    )
                    staffDao.insertStaff(staff)
                    _currentStaff.value = staff

                    // Safety net trigger for product sync after login
                    ProductRepository.syncProductsFromRtdb()
                    ProductRepository.startLiveProductSync()

                    return@withContext Result.success(staff)
                }
            }
        } catch (e: Exception) {
            Log.w("AuthRepository", "Online auth failed, falling back to local: ${e.message}")
        }

        // Offline login path
        val localStaff = staffDao.getStaffByEmployeeId(formattedId)
        if (localStaff != null) {
            if (isBlockedStatus(localStaff.status)) {
                _currentStaff.value = null
                return@withContext Result.failure(SecurityException("Access has been revoked. Please contact your warehouse supervisor."))
            }
            _currentStaff.value = localStaff

            // Safety net trigger
            ProductRepository.syncProductsFromRtdb()
            ProductRepository.startLiveProductSync()

            return@withContext Result.success(localStaff)
        }

        _currentStaff.value = null
        Result.failure(IllegalArgumentException("Invalid Employee ID or password."))
    }

    fun logout() {
        FirebaseAuth.getInstance().signOut()
        _currentStaff.value = null
        ProductRepository.stopLiveProductSync()
    }
}

object ProductRepository {
    private const val TAG = "ProductRepository"
    private var productDao: ProductDao? = null
    private var appContext: Context? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var productsListener: ValueEventListener? = null

    private val _lastSyncTime = MutableStateFlow<Long>(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    fun init(dao: ProductDao, context: Context) {
        productDao = dao
        appContext = context.applicationContext
        _lastSyncTime.value = SyncUtils.getLastProductSyncAt(context)

        coroutineScope.launch {
            seedLocalDatabase()
        }

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    syncProductsFromRtdb()
                    startLiveProductSync()
                }
            } else {
                stopLiveProductSync()
            }
        }
    }

    suspend fun seedLocalDatabase() {
        val dao = productDao ?: return
        if (dao.getProductCountDirect() == 0) {
            dao.insertAllIfAbsent(Catalog.defaultProducts)
        }
    }

    fun getAllProductsFlow(): Flow<List<ProductEntity>>? {
        return productDao?.getAllProductsFlow()
    }

    fun startLiveProductSync() {
        if (productsListener != null) return
        if (FirebaseAuth.getInstance().currentUser == null) return

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                coroutineScope.launch(Dispatchers.IO) {
                    processProductSnapshot(snapshot)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Product live sync cancelled: ${error.message}")
                try {
                    if (productsListener != null) {
                        RtdbClient.rtdb.reference.child("products").removeEventListener(this)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing products listener: ${e.message}")
                }
                productsListener = null
            }
        }

        productsListener = listener
        RtdbClient.rtdb.reference.child("products").addValueEventListener(listener)
    }

    fun stopLiveProductSync() {
        productsListener?.let { listener ->
            try {
                RtdbClient.rtdb.reference.child("products").removeEventListener(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping live product sync: ${e.message}")
            }
        }
        productsListener = null
    }

    suspend fun syncProductsFromRtdb() = withContext(Dispatchers.IO) {
        val dao = productDao ?: return@withContext
        val jsonStr = RtdbClient.get("products") ?: return@withContext

        try {
            val jsonObject = JSONObject(jsonStr)
            val keys = jsonObject.keys()
            var updatedCount = 0
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith("legacy_") || key == "delete") continue

                val itemJson = jsonObject.optJSONObject(key) ?: continue
                val existingProduct = dao.getProductById(key)
                val catalogItem = Catalog.findProduct(key)
                val defaultThreshold = catalogItem?.lowStockThreshold ?: 10

                val qty = parseQuantityFromJson(itemJson, existingProduct?.quantity ?: 0)
                val threshold = parseThresholdFromJson(itemJson, existingProduct?.lowStockThreshold ?: defaultThreshold)
                val piecesPerBox = if (itemJson.has("piecesPerBox") && !itemJson.isNull("piecesPerBox")) {
                    itemJson.optInt("piecesPerBox", existingProduct?.piecesPerBox ?: catalogItem?.piecesPerBox ?: 12)
                } else {
                    existingProduct?.piecesPerBox ?: catalogItem?.piecesPerBox ?: 12
                }

                val name = itemJson.optString("productName").ifEmpty { existingProduct?.productName ?: catalogItem?.productName ?: key }
                val sku = itemJson.optString("sku").ifEmpty { existingProduct?.sku ?: catalogItem?.sku ?: key }
                val category = itemJson.optString("category").ifEmpty { existingProduct?.category ?: catalogItem?.category ?: "General" }
                val unit = itemJson.optString("unit").ifEmpty { existingProduct?.unit ?: catalogItem?.unit ?: "units" }

                val product = ProductEntity(
                    productId = key,
                    productName = name,
                    sku = sku,
                    category = category,
                    quantity = qty,
                    unit = unit,
                    lowStockThreshold = threshold,
                    piecesPerBox = piecesPerBox
                )
                dao.insertProduct(product)
                updatedCount++
            }

            if (updatedCount > 0) {
                val now = System.currentTimeMillis()
                appContext?.let { SyncUtils.setLastProductSyncAt(it, now) }
                _lastSyncTime.value = now
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing products from RTDB: ${e.message}")
        }
    }

    private suspend fun processProductSnapshot(snapshot: DataSnapshot) = withContext(Dispatchers.IO) {
        val dao = productDao ?: return@withContext
        if (!snapshot.exists()) return@withContext

        var updatedCount = 0
        for (child in snapshot.children) {
            val key = child.key ?: continue
            if (key.startsWith("legacy_") || key == "delete") continue

            val valueMap = child.value as? Map<String, Any?> ?: continue
            val existingProduct = dao.getProductById(key)
            val catalogItem = Catalog.findProduct(key)
            val defaultThreshold = catalogItem?.lowStockThreshold ?: 10

            val qty = parseQuantityFromMap(valueMap, existingProduct?.quantity ?: 0)
            val threshold = parseThresholdFromMap(valueMap, existingProduct?.lowStockThreshold ?: defaultThreshold)
            val piecesPerBox = (valueMap["piecesPerBox"] as? Number)?.toInt()
                ?: (valueMap["piecesPerBox"] as? String)?.toIntOrNull()
                ?: existingProduct?.piecesPerBox ?: catalogItem?.piecesPerBox ?: 12

            val name = valueMap["productName"] as? String ?: existingProduct?.productName ?: catalogItem?.productName ?: key
            val sku = valueMap["sku"] as? String ?: existingProduct?.sku ?: catalogItem?.sku ?: key
            val category = valueMap["category"] as? String ?: existingProduct?.category ?: catalogItem?.category ?: "General"
            val unit = valueMap["unit"] as? String ?: existingProduct?.unit ?: catalogItem?.unit ?: "units"

            val product = ProductEntity(
                productId = key,
                productName = name,
                sku = sku,
                category = category,
                quantity = qty,
                unit = unit,
                lowStockThreshold = threshold,
                piecesPerBox = piecesPerBox
            )
            dao.insertProduct(product)
            updatedCount++
        }

        if (updatedCount > 0) {
            val now = System.currentTimeMillis()
            appContext?.let { SyncUtils.setLastProductSyncAt(it, now) }
            _lastSyncTime.value = now
        }
    }

    private fun parseQuantityFromMap(map: Map<String, Any?>?, defaultVal: Int = 0): Int {
        if (map == null) return defaultVal
        val keys = listOf("quantityOnHand", "quantity", "qty", "stock")
        for (key in keys) {
            if (map.containsKey(key) && map[key] != null) {
                val num = parseNum(map[key])
                if (num != null) return num
            }
        }
        return defaultVal
    }

    private fun parseThresholdFromMap(map: Map<String, Any?>?, catalogDefault: Int): Int {
        if (map == null) return catalogDefault
        val keys = listOf("reorderPoint", "minThreshold", "min_threshold", "reorderThreshold", "lowStockThreshold")
        for (key in keys) {
            if (map.containsKey(key) && map[key] != null) {
                val num = parseNum(map[key])
                if (num != null) return num
            }
        }
        return catalogDefault
    }

    private fun parseQuantityFromJson(json: JSONObject?, defaultVal: Int = 0): Int {
        if (json == null) return defaultVal
        val keys = listOf("quantityOnHand", "quantity", "qty", "stock")
        for (key in keys) {
            if (json.has(key) && !json.isNull(key)) {
                val num = parseNum(json.opt(key))
                if (num != null) return num
            }
        }
        return defaultVal
    }

    private fun parseThresholdFromJson(json: JSONObject?, catalogDefault: Int): Int {
        if (json == null) return catalogDefault
        val keys = listOf("reorderPoint", "minThreshold", "min_threshold", "reorderThreshold", "lowStockThreshold")
        for (key in keys) {
            if (json.has(key) && !json.isNull(key)) {
                val num = parseNum(json.opt(key))
                if (num != null) return num
            }
        }
        return catalogDefault
    }

    private fun parseNum(v: Any?): Int? {
        if (v == null) return null
        return when (v) {
            is Number -> v.toInt()
            is String -> v.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
    }

    fun getPrioritySortedAlertProducts(products: List<ProductEntity>): List<ProductEntity> {
        val outOfStock = products.filter { it.quantity <= 0 }
            .sortedBy { it.productName.lowercase() }
        val lowStock = products.filter { it.quantity > 0 && it.quantity <= it.lowStockThreshold }
            .sortedBy { it.productName.lowercase() }
        return outOfStock + lowStock
    }
}

class ScanRepository(
    private val transactionDao: ScanTransactionDao
) {
    val allTransactions: Flow<List<ScanTransactionEntity>> = transactionDao.getAllTransactionsFlow()

    fun getTransactionsByStaff(staffId: String): Flow<List<ScanTransactionEntity>> {
        return transactionDao.getTransactionsByStaffFlow(staffId)
    }

    suspend fun saveScanBatch(
        staffId: String,
        productId: String,
        canonicalProductId: String,
        boxCount: Int,
        pieceCount: Int,
        piecesPerBox: Int,
        mode: String,
        notes: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val totalPieces = (boxCount * piecesPerBox) + pieceCount
        val pieceQuantity = if (mode.uppercase() == "OUT") -totalPieces else totalPieces
        val timestamp = System.currentTimeMillis()
        val itemScanId = "SCN-${timestamp}-${(100..999).random()}"

        val entity = ScanTransactionEntity(
            transactionId = itemScanId,
            staffId = staffId,
            productId = canonicalProductId,
            scanType = mode.uppercase(),
            boxesScanned = boxCount,
            loosePiecesScanned = pieceCount,
            totalPiecesChange = pieceQuantity,
            timestamp = timestamp,
            notes = notes,
            syncStatus = "PENDING"
        )
        transactionDao.insertTransaction(entity)

        try {
            val scanMap = mapOf(
                "itemScanId" to itemScanId,
                "staffId" to staffId,
                "productId" to canonicalProductId,
                "scanType" to mode.uppercase(),
                "boxesScanned" to boxCount,
                "loosePiecesScanned" to pieceCount,
                "totalPiecesChange" to pieceQuantity,
                "timestamp" to timestamp,
                "notes" to notes,
                "stockApplied" to true
            )

            val updates = mapOf(
                "scans/$itemScanId" to scanMap,
                "products/$canonicalProductId/quantityOnHand" to ServerValue.increment(pieceQuantity.toLong())
            )

            val success = RtdbClient.updateChildren(updates)
            if (success) {
                transactionDao.updateSyncStatusAndError(itemScanId, "SYNCED", null)
                Result.success(Unit)
            } else {
                transactionDao.updateSyncStatusAndError(itemScanId, "PENDING", "Multi-path update failed")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            transactionDao.updateSyncStatusAndError(itemScanId, "PENDING", e.message)
            Result.success(Unit)
        }
    }
}

class WarehouseRepository(
    private val settingDao: WarehouseSettingDao
) {
    val warehouseName: Flow<WarehouseSettingEntity?> = settingDao.getSettingFlow()

    suspend fun saveWarehouseName(name: String) {
        settingDao.insertSetting(WarehouseSettingEntity(1, name.trim()))
    }
}
