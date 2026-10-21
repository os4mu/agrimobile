package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.dao.ProductDao
import com.example.data.dao.ScanTransactionDao
import com.example.data.dao.StaffDao
import com.example.data.dao.WarehouseSettingDao
import com.example.data.entities.ProductEntity
import com.example.data.entities.ScanTransactionEntity
import com.example.data.entities.ScannedItemPayload
import com.example.data.entities.StaffEntity
import com.example.data.entities.WarehouseSettingEntity
import com.example.util.SyncUtils
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import com.example.util.SyncPendingScansWorker
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
    val defaultProducts: List<ProductEntity> = emptyList()

    fun findProduct(productId: String): ProductEntity? {
        return defaultProducts.find { it.productId == productId || it.sku == productId }
    }
}

object RtdbClient {
    const val RTDB_URL = "https://agri-stock-ims-default-rtdb.asia-southeast1.firebasedatabase.app"
    val rtdb: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(RTDB_URL) }

    suspend fun get(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Log.w("RtdbClient", "GET $path failed: Firebase user is null")
                return@withContext null
            }
            val token = suspendCancellableCoroutine<String?> { cont ->
                user.getIdToken(false).addOnSuccessListener { result ->
                    cont.resume(result.token)
                }.addOnFailureListener { e ->
                    Log.w("RtdbClient", "getIdToken failed: ${e.message}")
                    cont.resume(null)
                }
            }
            if (token == null) {
                Log.w("RtdbClient", "GET $path failed: ID token is null")
                return@withContext null
            }

            val url = URL("$RTDB_URL/$path.json?auth=$token")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                response.toString()
            } else {
                val stream = conn.errorStream ?: conn.inputStream
                val errorBody = if (stream != null) {
                    BufferedReader(InputStreamReader(stream)).use { it.readText() }
                } else ""
                val shortError = errorBody.take(200)
                Log.w("RtdbClient", "GET $path -> HTTP $code: $shortError")
                null
            }
        } catch (e: Exception) {
            Log.e("RtdbClient", "GET $path failed: ${e.message}")
            null
        }
    }

    suspend fun updateSinglePath(path: String, value: Any?): Boolean = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            rtdb.reference.child(path).setValue(value) { error, _ ->
                if (error == null) {
                    cont.resume(true)
                } else {
                    Log.e("RtdbClient", "setValue at $path failed: ${error.message} (code ${error.code})")
                    cont.resume(false)
                }
            }
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

    suspend fun updateChildrenWithFallback(updates: Map<String, Any?>): Boolean = withContext(Dispatchers.IO) {
        val multiPathSuccess = updateChildren(updates)
        if (multiPathSuccess) return@withContext true

        Log.w("RtdbClient", "Multi-path root update failed. Executing granular path updates to diagnose permission issues...")
        var allSucceeded = true
        val failedPaths = mutableListOf<String>()

        for ((path, value) in updates) {
            val singleSuccess = updateSinglePath(path, value)
            if (singleSuccess) {
                Log.d("RtdbClient", "Granular update SUCCESS for path: $path")
            } else {
                Log.e("RtdbClient", "Granular update DENIED/FAILED for path: $path")
                allSucceeded = false
                failedPaths.add(path)
            }
        }

        if (allSucceeded) {
            Log.i("RtdbClient", "All granular updates succeeded individually!")
            return@withContext true
        } else {
            Log.w("RtdbClient", "Granular updates finished with failed paths: $failedPaths")
            // Return true if at least scans or stockOutLogs succeeded so user gets partial/successful sync
            return@withContext failedPaths.size < updates.size
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

    private fun saveSession(keepLoggedIn: Boolean, employeeId: String) {
        val prefs = context.getSharedPreferences("agristock_session", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("keepLoggedIn", keepLoggedIn)
            .putString("employeeId", employeeId)
            .apply()
    }

    fun clearSession() {
        val prefs = context.getSharedPreferences("agristock_session", Context.MODE_PRIVATE)
        prefs.edit()
            .clear()
            .apply()
    }

    private fun getSavedSession(): Pair<Boolean, String?> {
        val prefs = context.getSharedPreferences("agristock_session", Context.MODE_PRIVATE)
        val keepLoggedIn = prefs.getBoolean("keepLoggedIn", false)
        val employeeId = prefs.getString("employeeId", null)
        return Pair(keepLoggedIn, employeeId)
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + password).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun savePasswordHashForOffline(employeeId: String, password: String) {
        val prefs = context.getSharedPreferences("agri_stock_auth_prefs", Context.MODE_PRIVATE)
        val salt = UUID.randomUUID().toString()
        val hash = hashPassword(password, salt)
        prefs.edit()
            .putString("salt_$employeeId", salt)
            .putString("hash_$employeeId", hash)
            .apply()
    }

    private fun verifyOfflinePassword(employeeId: String, password: String): Boolean {
        val prefs = context.getSharedPreferences("agri_stock_auth_prefs", Context.MODE_PRIVATE)
        val salt = prefs.getString("salt_$employeeId", null) ?: return false
        val storedHash = prefs.getString("hash_$employeeId", null) ?: return false
        val computedHash = hashPassword(password, salt)
        return computedHash == storedHash
    }

    private data class AuthAttemptResult(
        val success: Boolean,
        val exception: Exception?
    )

    private suspend fun trySignIn(email: String, pass: String): AuthAttemptResult = suspendCancellableCoroutine { cont ->
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                cont.resume(AuthAttemptResult(true, null))
            }
            .addOnFailureListener { e ->
                Log.w("AuthRepository", "signIn failed for $email: ${e::class.simpleName}: ${e.message}")
                cont.resume(AuthAttemptResult(false, e))
            }
    }

    private data class ProfileFetchResult(
        val snapshot: DataSnapshot?,
        val exception: Exception?
    )

    private suspend fun fetchUserProfileSnapshot(uid: String): ProfileFetchResult = suspendCancellableCoroutine { cont ->
        RtdbClient.rtdb.reference.child("userProfiles").child(uid).get()
            .addOnSuccessListener { snapshot ->
                cont.resume(ProfileFetchResult(snapshot, null))
            }
            .addOnFailureListener { e ->
                Log.w("AuthRepository", "fetchUserProfile failed for $uid: ${e::class.simpleName}: ${e.message}")
                cont.resume(ProfileFetchResult(null, e))
            }
    }

    suspend fun restoreSession(): Result<Boolean> = withContext(Dispatchers.IO) {
        val (keepLoggedIn, savedEmployeeId) = getSavedSession()
        if (!keepLoggedIn || savedEmployeeId.isNullOrBlank()) {
            FirebaseAuth.getInstance().signOut()
            clearSession()
            _currentStaff.value = null
            return@withContext Result.success(false)
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        val localStaff = staffDao.getStaffByEmployeeId(savedEmployeeId)

        if (localStaff == null || isBlockedStatus(localStaff.status)) {
            FirebaseAuth.getInstance().signOut()
            clearSession()
            _currentStaff.value = null
            return@withContext Result.success(false)
        }

        // Online revocation check if currentUser is logged into Firebase
        if (currentUser != null && SyncUtils.isOnline(context)) {
            val uid = currentUser.uid
            val profileResult = fetchUserProfileSnapshot(uid)

            if (profileResult.exception != null || profileResult.snapshot == null || !profileResult.snapshot.exists()) {
                FirebaseAuth.getInstance().signOut()
                clearSession()
                _currentStaff.value = null
                return@withContext Result.failure(SecurityException("Access has been revoked. Please contact your warehouse supervisor."))
            }

            val snapshot = profileResult.snapshot
            val status = snapshot.child("status").value?.toString() ?: ""
            if (isBlockedStatus(status)) {
                FirebaseAuth.getInstance().signOut()
                clearSession()
                staffDao.insertStaff(localStaff.copy(status = "INACTIVE"))
                _currentStaff.value = null
                return@withContext Result.failure(SecurityException("Access has been revoked. Please contact your warehouse supervisor."))
            }

            val roleStr = snapshot.child("role").value?.toString()?.ifBlank { localStaff.role } ?: localStaff.role
            val isAdmin = roleStr.equals("ADMIN", ignoreCase = true) || roleStr.equals("ADMINISTRATOR", ignoreCase = true)
            val canStockOutVal = (snapshot.child("canStockOut").value as? Boolean)
                ?: (snapshot.child("can_stock_out").value as? Boolean)
                ?: isAdmin

            val updatedStaff = localStaff.copy(role = roleStr, canStockOut = canStockOutVal)
            staffDao.insertStaff(updatedStaff)
            _currentStaff.value = updatedStaff
            startLiveProfileSync(uid)
        } else {
            _currentStaff.value = localStaff
        }

        // Restore session
        ProductRepository.syncProductsFromRtdb()
        ProductRepository.startLiveProductSync()
        Result.success(true)
    }

    suspend fun login(
        employeeId: String,
        password: String,
        keepLoggedIn: Boolean = true
    ): Result<StaffEntity> = withContext(Dispatchers.IO) {
        val cleanPassword = password.trim()
        var norm = employeeId.trim().uppercase().replace(Regex("[ _]+"), "-")
        if (norm.matches(Regex("^EMP\\d{3}$"))) {
            norm = "EMP-" + norm.substring(3)
        }

        if (!norm.matches(Regex("^EMP-\\d{3}$"))) {
            return@withContext Result.failure(IllegalArgumentException("Employee ID must look like EMP-003."))
        }

        val auth = FirebaseAuth.getInstance()
        val idLower = norm.lowercase()
        val idNoDash = idLower.replace("-", "")

        val candidateEmails = listOf(
            "$idLower@agri-stock.local",
            "$idNoDash@agri-stock.local",
            "$idLower@agri-stock.com",
            "$idNoDash@agri-stock.com"
        ).distinct()

        var signedIn = false
        var lastException: Exception? = null

        if (SyncUtils.isOnline(context)) {
            for (candidateEmail in candidateEmails) {
                val attempt = trySignIn(candidateEmail, cleanPassword)
                if (attempt.success) {
                    signedIn = true
                    break
                }
                lastException = attempt.exception
                if (attempt.exception is FirebaseNetworkException) {
                    break
                }
                if (attempt.exception !is FirebaseAuthInvalidUserException && attempt.exception !is FirebaseAuthInvalidCredentialsException) {
                    break
                }
            }
        }

        if (signedIn && auth.currentUser != null) {
            val uid = auth.currentUser!!.uid
            val profileResult = fetchUserProfileSnapshot(uid)

            if (profileResult.exception != null) {
                auth.signOut()
                clearSession()
                _currentStaff.value = null
                val ex = profileResult.exception
                val isPermissionError = ex.message?.contains("permission", ignoreCase = true) == true ||
                        ex.message?.contains("denied", ignoreCase = true) == true ||
                        ex is DatabaseException
                val errorMsg = if (isPermissionError) {
                    "Cannot read your profile (permission denied)"
                } else {
                    "Cannot read profile: ${ex.message ?: "Unknown error"}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val snapshot = profileResult.snapshot
            if (snapshot == null || !snapshot.exists()) {
                auth.signOut()
                clearSession()
                _currentStaff.value = null
                return@withContext Result.failure(Exception("Your account profile was not found"))
            }

            val fullNameStr = snapshot.child("fullName").value?.toString() ?: ""
            val nameStr = snapshot.child("name").value?.toString() ?: ""
            val name = if (fullNameStr.isNotBlank()) fullNameStr else if (nameStr.isNotBlank()) nameStr else "Warehouse Staff"

            val role = snapshot.child("role").value?.toString()?.ifBlank { "STAFF" } ?: "STAFF"
            val status = snapshot.child("status").value?.toString() ?: ""

            val isAdmin = role.equals("ADMIN", ignoreCase = true) || role.equals("ADMINISTRATOR", ignoreCase = true)
            val canStockOutVal = (snapshot.child("canStockOut").value as? Boolean)
                ?: (snapshot.child("can_stock_out").value as? Boolean)
                ?: isAdmin

            if (isBlockedStatus(status)) {
                auth.signOut()
                clearSession()
                val localStaff = staffDao.getStaffByEmployeeId(norm)
                if (localStaff != null) {
                    staffDao.insertStaff(localStaff.copy(status = "INACTIVE"))
                }
                _currentStaff.value = null
                return@withContext Result.failure(SecurityException("Access has been revoked. Please contact your warehouse supervisor."))
            }

            val staff = StaffEntity(
                staffId = norm,
                employeeId = norm,
                fullName = name,
                role = role,
                assignedWarehouse = "UniFam Store",
                status = "ACTIVE",
                canStockOut = canStockOutVal
            )
            staffDao.insertStaff(staff)
            _currentStaff.value = staff

            saveSession(keepLoggedIn, norm)
            savePasswordHashForOffline(norm, cleanPassword)

            // Live profile and product sync
            startLiveProfileSync(uid)
            ProductRepository.syncProductsFromRtdb()
            ProductRepository.startLiveProductSync()

            return@withContext Result.success(staff)
        }

        // Check why online login didn't succeed
        if (lastException != null && lastException !is FirebaseNetworkException) {
            _currentStaff.value = null
            return@withContext if (lastException is FirebaseAuthInvalidUserException || lastException is FirebaseAuthInvalidCredentialsException) {
                Result.failure(IllegalArgumentException("Invalid Employee ID or password."))
            } else {
                val reason = lastException.message?.ifBlank { null } ?: lastException::class.simpleName ?: "Unknown error"
                Result.failure(Exception("Login failed: $reason"))
            }
        }

        // Offline login path (only if network error or SyncUtils.isOnline is false)
        if (!verifyOfflinePassword(norm, cleanPassword)) {
            _currentStaff.value = null
            return@withContext Result.failure(IllegalArgumentException("Invalid Employee ID or password."))
        }

        val localStaff = staffDao.getStaffByEmployeeId(norm)
        if (localStaff != null) {
            if (isBlockedStatus(localStaff.status)) {
                _currentStaff.value = null
                return@withContext Result.failure(SecurityException("Access has been revoked. Please contact your warehouse supervisor."))
            }
            _currentStaff.value = localStaff

            saveSession(keepLoggedIn, norm)

            // Safety net trigger
            ProductRepository.syncProductsFromRtdb()
            ProductRepository.startLiveProductSync()

            return@withContext Result.success(localStaff)
        }

        _currentStaff.value = null
        Result.failure(IllegalArgumentException("Invalid Employee ID or password."))
    }

    private var profileListener: ValueEventListener? = null

    fun startLiveProfileSync(uid: String) {
        stopLiveProfileSync()
        val ref = RtdbClient.rtdb.reference.child("userProfiles").child(uid)
        profileListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val current = _currentStaff.value ?: return

                val roleStr = snapshot.child("role").value?.toString()?.ifBlank { current.role } ?: current.role
                val status = snapshot.child("status").value?.toString() ?: ""
                val isAdmin = roleStr.equals("ADMIN", ignoreCase = true) || roleStr.equals("ADMINISTRATOR", ignoreCase = true)
                val canStockOutVal = (snapshot.child("canStockOut").value as? Boolean)
                    ?: (snapshot.child("can_stock_out").value as? Boolean)
                    ?: isAdmin

                if (isBlockedStatus(status)) {
                    logout()
                    return
                }

                val updated = current.copy(role = roleStr, canStockOut = canStockOutVal)
                _currentStaff.value = updated
                CoroutineScope(Dispatchers.IO).launch {
                    staffDao.insertStaff(updated)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("AuthRepository", "Profile listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(profileListener!!)
    }

    fun stopLiveProfileSync() {
        profileListener?.let {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                RtdbClient.rtdb.reference.child("userProfiles").child(user.uid).removeEventListener(it)
            }
            profileListener = null
        }
    }

    fun logout() {
        stopLiveProfileSync()
        FirebaseAuth.getInstance().signOut()
        clearSession()
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
        dao.deleteLegacyFakeProducts()
    }

    private suspend fun fetchCategoryMap(): Map<String, String> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, String>()
        try {
            val snapshotResult = suspendCancellableCoroutine<DataSnapshot?> { cont ->
                RtdbClient.rtdb.reference.child("category").get()
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
            if (snapshotResult != null && snapshotResult.exists()) {
                for (child in snapshotResult.children) {
                    val key = child.key ?: continue
                    val catName = child.child("categoryName").value?.toString()
                        ?: child.child("name").value?.toString()
                        ?: (child.value as? String)
                    if (!catName.isNullOrBlank()) {
                        map[key] = catName
                        map[key.lowercase()] = catName
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch categories via SDK: ${e.message}")
        }

        if (map.isEmpty()) {
            try {
                val jsonStr = RtdbClient.get("category")
                if (!jsonStr.isNullOrBlank() && jsonStr != "null") {
                    val jsonObject = JSONObject(jsonStr)
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val itemObj = jsonObject.optJSONObject(key)
                        val catName = itemObj?.optString("categoryName")?.ifBlank { null }
                            ?: itemObj?.optString("name")?.ifBlank { null }
                            ?: jsonObject.optString(key).ifBlank { null }
                        if (!catName.isNullOrBlank()) {
                            map[key] = catName
                            map[key.lowercase()] = catName
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch categories via REST: ${e.message}")
            }
        }

        map
    }

    private fun resolveCategoryName(
        directCategory: String,
        categoryId: String,
        categoryMap: Map<String, String>
    ): String {
        if (directCategory.isNotBlank() && directCategory != "General") {
            return directCategory
        }
        if (categoryId.isNotBlank()) {
            categoryMap[categoryId]?.let { return it }
            categoryMap[categoryId.lowercase()]?.let { return it }
        }
        if (directCategory.isNotBlank()) {
            return directCategory
        }
        return "Uncategorized"
    }

    private suspend fun reconcileLocalProducts(validIds: List<String>) {
        val dao = productDao ?: return
        if (validIds.isEmpty()) return
        val validSet = validIds.toSet()
        val existingIds = dao.getAllProductIds()
        val toDelete = existingIds.filter { it !in validSet }
        if (toDelete.isNotEmpty()) {
            toDelete.chunked(500).forEach { chunk ->
                dao.deleteProductsByIds(chunk)
            }
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
        if (jsonStr.isBlank() || jsonStr == "null") return@withContext

        try {
            val categoryMap = fetchCategoryMap()
            val jsonObject = JSONObject(jsonStr)
            val keys = jsonObject.keys()
            val validProductIds = mutableListOf<String>()
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

                val directCat = itemJson.optString("category").ifEmpty { itemJson.optString("categoryName") }
                val catId = itemJson.optString("categoryId").ifEmpty { itemJson.optString("category_id") }
                val finalCategory = resolveCategoryName(directCat, catId, categoryMap)

                val unit = itemJson.optString("unit").ifEmpty { existingProduct?.unit ?: catalogItem?.unit ?: "units" }

                val product = ProductEntity(
                    productId = key,
                    productName = name,
                    sku = sku,
                    category = finalCategory,
                    quantity = qty,
                    unit = unit,
                    lowStockThreshold = threshold,
                    piecesPerBox = piecesPerBox
                )
                dao.insertProduct(product)
                validProductIds.add(key)
                updatedCount++
            }

            if (validProductIds.isNotEmpty()) {
                reconcileLocalProducts(validProductIds)
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

        val categoryMap = fetchCategoryMap()
        val validProductIds = mutableListOf<String>()
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

            val directCat = (valueMap["category"] as? String)?.ifEmpty { null }
                ?: (valueMap["categoryName"] as? String)?.ifEmpty { null } ?: ""
            val catId = (valueMap["categoryId"] as? String)?.ifEmpty { null }
                ?: (valueMap["category_id"] as? String)?.ifEmpty { null } ?: ""
            val finalCategory = resolveCategoryName(directCat, catId, categoryMap)

            val unit = valueMap["unit"] as? String ?: existingProduct?.unit ?: catalogItem?.unit ?: "units"

            val product = ProductEntity(
                productId = key,
                productName = name,
                sku = sku,
                category = finalCategory,
                quantity = qty,
                unit = unit,
                lowStockThreshold = threshold,
                piecesPerBox = piecesPerBox
            )
            dao.insertProduct(product)
            validProductIds.add(key)
            updatedCount++
        }

        if (validProductIds.isNotEmpty()) {
            reconcileLocalProducts(validProductIds)
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
    private val transactionDao: ScanTransactionDao,
    private val productDao: ProductDao? = null,
    private val staffDao: StaffDao? = null,
    private val appContext: Context? = null
) {
    val allTransactions: Flow<List<ScanTransactionEntity>> = transactionDao.getAllTransactionsFlow()
    private val syncMutex = Mutex()

    fun getTransactionsByStaff(staffId: String): Flow<List<ScanTransactionEntity>> {
        return transactionDao.getTransactionsByStaffFlow(staffId)
    }

    private fun formatUtcTimestamp(timestampMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestampMs))
    }

    private fun formatIsoUtcTimestamp(timestampMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestampMs))
    }

    private fun isStockOutMode(mode: String): Boolean {
        val m = mode.trim().uppercase().replace("_", "").replace(" ", "")
        return m == "OUT" || m == "STOCKOUT"
    }

    private fun sanitizeStockOutReason(notes: String): String {
        val trimmed = notes.trim()
        val allowed = listOf("Sold", "Damaged", "Expired", "Other")
        return allowed.firstOrNull { it.equals(trimmed, ignoreCase = true) }
            ?: if (trimmed.isBlank()) "Sold" else "Other"
    }

    suspend fun syncPendingScans(): Int = withContext(Dispatchers.IO) {
        if (FirebaseAuth.getInstance().currentUser == null) return@withContext 0
        if (!syncMutex.tryLock()) return@withContext 0

        var uploadedCount = 0
        try {
            val pendingList = transactionDao.getPendingTransactions()
            for (entity in pendingList) {
                val success = syncSingleTransaction(entity)
                if (success) {
                    uploadedCount++
                }
            }
            reconcileStockOutLogs()
        } finally {
            syncMutex.unlock()
        }
        uploadedCount
    }

    suspend fun reconcileStockOutLogs() = withContext(Dispatchers.IO) {
        if (FirebaseAuth.getInstance().currentUser == null) return@withContext
        try {
            val recentList = transactionDao.getAllRecentTransactions()
            val stockOuts = recentList.filter { isStockOutMode(it.scanType) || it.totalPiecesChange < 0 }

            for (entity in stockOuts) {
                val logId = "SO-LOG-${entity.timestamp}"
                val logSnapshot = suspendCancellableCoroutine<DataSnapshot?> { cont ->
                    RtdbClient.rtdb.reference.child("stockOutLogs").child(logId).get()
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resume(null) }
                }

                if (logSnapshot == null || !logSnapshot.exists()) {
                    Log.i("ScanRepository", "Reconciling missing stockOutLog: $logId")
                    val productObj = productDao?.getProductById(entity.productId)
                    val staffObj = staffDao?.getStaffByEmployeeId(entity.staffId)

                    val empId = staffObj?.employeeId ?: entity.staffId
                    val sName = staffObj?.fullName ?: "Warehouse Staff"
                    val pName = productObj?.productName ?: entity.productId
                    val pieceQuantity = entity.totalPiecesChange
                    val formattedLastUpdated = formatIsoUtcTimestamp(entity.timestamp)

                    val stockOutLogMap = mapOf(
                        "id" to logId,
                        "productId" to entity.productId,
                        "productName" to pName,
                        "qty" to kotlin.math.abs(pieceQuantity),
                        "reason" to sanitizeStockOutReason(entity.notes),
                        "employeeId" to empId,
                        "employeeName" to sName,
                        "timestamp" to formattedLastUpdated
                    )

                    val updates = mutableMapOf<String, Any?>(
                        "stockOutLogs/$logId" to stockOutLogMap,
                        "products/${entity.productId}/quantityOnHand" to ServerValue.increment(pieceQuantity.toLong()),
                        "products/${entity.productId}/quantity" to ServerValue.increment(pieceQuantity.toLong()),
                        "products/${entity.productId}/lastUpdated" to formattedLastUpdated,
                        "scans/${entity.transactionId}/stockApplied" to true,
                        "scans/${entity.transactionId}/inventorySynced" to true,
                        "scans/${entity.transactionId}/syncStatus" to "synced"
                    )

                    val success = RtdbClient.updateChildrenWithFallback(updates)
                    if (success) {
                        transactionDao.updateSyncStatusAndError(entity.transactionId, "SYNCED", null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ScanRepository", "reconcileStockOutLogs error: ${e.message}")
        }
    }

    private suspend fun syncSingleTransaction(entity: ScanTransactionEntity): Boolean = withContext(Dispatchers.IO) {
        val scanId = entity.transactionId

        // Step 1: Read scans/{id} from Firebase
        val cloudSnapshotResult = suspendCancellableCoroutine<DataSnapshot?> { cont ->
            RtdbClient.rtdb.reference.child("scans").child(scanId).get()
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { e ->
                    Log.w("ScanRepository", "Failed to fetch cloud scan $scanId: ${e.message}")
                    cont.resume(null)
                }
        }

        if (cloudSnapshotResult == null) {
            transactionDao.updateSyncStatusAndError(scanId, "PENDING", "No connection")
            return@withContext false
        }

        if (cloudSnapshotResult.exists()) {
            val stockApplied = cloudSnapshotResult.child("stockApplied").value as? Boolean ?: false

            if (isStockOutMode(entity.scanType)) {
                val logId = "SO-LOG-${entity.timestamp}"
                val logSnapshot = suspendCancellableCoroutine<DataSnapshot?> { cont ->
                    RtdbClient.rtdb.reference.child("stockOutLogs").child(logId).get()
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resume(null) }
                }

                if (logSnapshot == null || !logSnapshot.exists()) {
                    val productObj = productDao?.getProductById(entity.productId)
                    val staffObj = staffDao?.getStaffByEmployeeId(entity.staffId)

                    val empId = staffObj?.employeeId ?: entity.staffId
                    val sName = staffObj?.fullName ?: "Warehouse Staff"
                    val pName = productObj?.productName ?: entity.productId
                    val pieceQuantity = entity.totalPiecesChange
                    val formattedLastUpdated = formatIsoUtcTimestamp(entity.timestamp)

                    val stockOutLogMap = mapOf(
                        "id" to logId,
                        "productId" to entity.productId,
                        "productName" to pName,
                        "qty" to kotlin.math.abs(pieceQuantity),
                        "reason" to sanitizeStockOutReason(entity.notes),
                        "employeeId" to empId,
                        "employeeName" to sName,
                        "timestamp" to formattedLastUpdated
                    )

                    val updates = mutableMapOf<String, Any?>(
                        "stockOutLogs/$logId" to stockOutLogMap,
                        "scans/$scanId/stockApplied" to true,
                        "scans/$scanId/inventorySynced" to true,
                        "scans/$scanId/syncStatus" to "synced",
                        "scans/$scanId/status" to "confirmed"
                    )

                    if (!stockApplied) {
                        updates["products/${entity.productId}/quantityOnHand"] = ServerValue.increment(pieceQuantity.toLong())
                        updates["products/${entity.productId}/quantity"] = ServerValue.increment(pieceQuantity.toLong())
                        updates["products/${entity.productId}/lastUpdated"] = formattedLastUpdated
                    }

                    val success = RtdbClient.updateChildrenWithFallback(updates)
                    if (success) {
                        transactionDao.updateSyncStatusAndError(scanId, "SYNCED", null)
                        return@withContext true
                    }
                }
            }

            if (stockApplied) {
                // Already in cloud and stock applied -> just mark local row SYNCED
                transactionDao.updateSyncStatusAndError(scanId, "SYNCED", null)
                return@withContext true
            } else {
                // Exists in cloud without stockApplied (legacy) -> do stock-only increment and mark SYNCED
                val totalPieces = entity.totalPiecesChange
                val formattedLastUpdated = formatIsoUtcTimestamp(entity.timestamp)
                val updates = mapOf(
                    "scans/$scanId/stockApplied" to true,
                    "scans/$scanId/inventorySynced" to true,
                    "scans/$scanId/syncStatus" to "synced",
                    "scans/$scanId/status" to "confirmed",
                    "products/${entity.productId}/quantityOnHand" to ServerValue.increment(totalPieces.toLong()),
                    "products/${entity.productId}/quantity" to ServerValue.increment(totalPieces.toLong()),
                    "products/${entity.productId}/lastUpdated" to formattedLastUpdated
                )
                val success = RtdbClient.updateChildrenWithFallback(updates)
                if (success) {
                    transactionDao.updateSyncStatusAndError(scanId, "SYNCED", null)
                    return@withContext true
                } else {
                    transactionDao.updateSyncStatusAndError(scanId, "PENDING", "Stock update failed")
                    return@withContext false
                }
            }
        }

        // Does not exist in cloud -> run full atomic upload!
        val productObj = productDao?.getProductById(entity.productId)
        val staffObj = staffDao?.getStaffByEmployeeId(entity.staffId)

        val empId = staffObj?.employeeId ?: entity.staffId
        val sName = staffObj?.fullName ?: "Warehouse Staff"
        val pName = productObj?.productName ?: entity.productId
        val pSku = productObj?.sku ?: entity.productId
        val pUnit = productObj?.unit ?: "units"
        val pieceQuantity = entity.totalPiecesChange
        val boxCount = entity.boxesScanned
        val loosePieces = entity.loosePiecesScanned

        val itemDetailMap = mapOf(
            "name" to pName,
            "productName" to pName,
            "sku" to pSku,
            "count" to pieceQuantity,
            "pieceQuantity" to pieceQuantity,
            "unit" to pUnit
        )

        val scanMap = mapOf(
            "itemScanId" to scanId,
            "scanId" to scanId,
            "staffId" to entity.staffId,
            "employeeId" to empId,
            "staffName" to sName,
            "scannedBy" to sName,
            "productId" to entity.productId,
            "productName" to pName,
            "sku" to pSku,
            "unit" to pUnit,
            "detectedClass" to pName,
            "scanType" to entity.scanType.uppercase(),
            "boxesScanned" to boxCount,
            "boxQuantity" to boxCount,
            "loosePiecesScanned" to loosePieces,
            "totalPiecesChange" to pieceQuantity,
            "pieceQuantity" to pieceQuantity,
            "items" to listOf(itemDetailMap),
            "timestamp" to entity.timestamp,
            "notes" to entity.notes,
            "stockApplied" to true,
            "inventorySynced" to true,
            "syncStatus" to "synced",
            "status" to "confirmed"
        )

        val formattedLastUpdated = formatIsoUtcTimestamp(entity.timestamp)

        val updates = mutableMapOf<String, Any?>(
            "scans/$scanId" to scanMap,
            "products/${entity.productId}/quantityOnHand" to ServerValue.increment(pieceQuantity.toLong()),
            "products/${entity.productId}/quantity" to ServerValue.increment(pieceQuantity.toLong()),
            "products/${entity.productId}/lastUpdated" to formattedLastUpdated
        )

        if (isStockOutMode(entity.scanType)) {
            val logId = "SO-LOG-${entity.timestamp}"
            val stockOutLogMap = mapOf(
                "id" to logId,
                "productId" to entity.productId,
                "productName" to pName,
                "qty" to kotlin.math.abs(pieceQuantity),
                "reason" to sanitizeStockOutReason(entity.notes),
                "employeeId" to empId,
                "employeeName" to sName,
                "timestamp" to formattedLastUpdated
            )
            updates["stockOutLogs/$logId"] = stockOutLogMap
        }

        val success = RtdbClient.updateChildrenWithFallback(updates)
        if (success) {
            transactionDao.updateSyncStatusAndError(scanId, "SYNCED", null)
            true
        } else {
            transactionDao.updateSyncStatusAndError(scanId, "PENDING", "Multi-path upload failed")
            false
        }
    }

    private suspend fun saveSingleScan(
        staffId: String,
        employeeId: String,
        staffName: String,
        productId: String,
        canonicalProductId: String,
        productName: String,
        sku: String,
        unit: String,
        boxCount: Int,
        pieceCount: Int,
        piecesPerBox: Int,
        mode: String,
        notes: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val totalPieces = (boxCount * piecesPerBox) + pieceCount
        val pieceQuantity = if (isStockOutMode(mode)) -totalPieces else totalPieces
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
            val itemDetailMap = mapOf(
                "name" to productName,
                "productName" to productName,
                "sku" to sku,
                "count" to pieceQuantity,
                "pieceQuantity" to pieceQuantity,
                "unit" to unit
            )

            val scanMap = mapOf(
                "itemScanId" to itemScanId,
                "scanId" to itemScanId,
                "staffId" to staffId,
                "employeeId" to employeeId,
                "staffName" to staffName,
                "scannedBy" to staffName,
                "productId" to canonicalProductId,
                "productName" to productName,
                "sku" to sku,
                "unit" to unit,
                "detectedClass" to productName,
                "scanType" to mode.uppercase(),
                "boxesScanned" to boxCount,
                "boxQuantity" to boxCount,
                "loosePiecesScanned" to pieceCount,
                "totalPiecesChange" to pieceQuantity,
                "pieceQuantity" to pieceQuantity,
                "items" to listOf(itemDetailMap),
                "timestamp" to timestamp,
                "notes" to notes,
                "stockApplied" to true,
                "inventorySynced" to true,
                "syncStatus" to "synced",
                "status" to "confirmed"
            )

            val formattedLastUpdated = formatIsoUtcTimestamp(timestamp)

            val updates = mutableMapOf<String, Any?>(
                "scans/$itemScanId" to scanMap,
                "products/$canonicalProductId/quantityOnHand" to ServerValue.increment(pieceQuantity.toLong()),
                "products/$canonicalProductId/quantity" to ServerValue.increment(pieceQuantity.toLong()),
                "products/$canonicalProductId/lastUpdated" to formattedLastUpdated
            )

            if (isStockOutMode(mode)) {
                val logId = "SO-LOG-$timestamp"
                val stockOutLogMap = mapOf(
                    "id" to logId,
                    "productId" to canonicalProductId,
                    "productName" to productName,
                    "qty" to kotlin.math.abs(pieceQuantity),
                    "reason" to sanitizeStockOutReason(notes),
                    "employeeId" to employeeId,
                    "employeeName" to staffName,
                    "timestamp" to formattedLastUpdated
                )
                updates["stockOutLogs/$logId"] = stockOutLogMap
            }

            val success = RtdbClient.updateChildrenWithFallback(updates)
            if (success) {
                transactionDao.updateSyncStatusAndError(itemScanId, "SYNCED", null)
                Result.success(Unit)
            } else {
                transactionDao.updateSyncStatusAndError(itemScanId, "PENDING", "Multi-path update failed")
                appContext?.let { SyncPendingScansWorker.enqueue(it) }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            transactionDao.updateSyncStatusAndError(itemScanId, "PENDING", e.message)
            appContext?.let { SyncPendingScansWorker.enqueue(it) }
            Result.success(Unit)
        }
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
    ): Result<Unit> {
        val staffObj = staffDao?.getStaffByEmployeeId(staffId)
        val productObj = productDao?.getProductById(canonicalProductId)

        val empId = staffObj?.employeeId ?: staffId
        val sName = staffObj?.fullName ?: "Warehouse Staff"
        val pName = productObj?.productName ?: canonicalProductId
        val pSku = productObj?.sku ?: canonicalProductId
        val pUnit = productObj?.unit ?: "units"

        val res = saveSingleScan(
            staffId = staffId,
            employeeId = empId,
            staffName = sName,
            productId = productId,
            canonicalProductId = canonicalProductId,
            productName = pName,
            sku = pSku,
            unit = pUnit,
            boxCount = boxCount,
            pieceCount = pieceCount,
            piecesPerBox = piecesPerBox,
            mode = mode,
            notes = notes
        )
        syncPendingScans()
        return res
    }

    suspend fun saveScanBatch(
        staff: StaffEntity,
        items: List<ScannedItemPayload>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val localProducts = productDao?.getAllProducts() ?: emptyList()
        var overallSuccess = true

        for (item in items) {
            val matchedProduct = localProducts.firstOrNull { it.sku.equals(item.sku, ignoreCase = true) }
                ?: localProducts.firstOrNull { it.productId.equals(item.productId, ignoreCase = true) }

            val canonicalProductId = matchedProduct?.productId ?: item.productId
            val piecesPerBox = matchedProduct?.piecesPerBox ?: 12
            val boxCount = item.finalQuantity
            val pieceCount = 0

            val pName = matchedProduct?.productName ?: item.productName
            val pSku = matchedProduct?.sku ?: item.sku
            val pUnit = matchedProduct?.unit ?: item.unit

            val result = saveSingleScan(
                staffId = staff.staffId,
                employeeId = staff.employeeId,
                staffName = staff.fullName,
                productId = canonicalProductId,
                canonicalProductId = canonicalProductId,
                productName = pName,
                sku = pSku,
                unit = pUnit,
                boxCount = boxCount,
                pieceCount = pieceCount,
                piecesPerBox = piecesPerBox,
                mode = "IN",
                notes = "YOLOv8 Camera Scan"
            )

            if (result.isFailure) {
                overallSuccess = false
            }
        }

        syncPendingScans()
        if (overallSuccess) Result.success(Unit) else Result.failure(Exception("Failed to save some scan items"))
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
