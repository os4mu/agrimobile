package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entities.ProductEntity
import com.example.data.entities.ScanTransactionEntity
import com.example.data.entities.StaffEntity
import com.example.data.entities.WarehouseSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY productName ASC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY productName ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE productId = :productId LIMIT 1")
    suspend fun getProductById(productId: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff WHERE employeeId = :employeeId LIMIT 1")
    suspend fun getStaffByEmployeeId(employeeId: String): StaffEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaff(staff: StaffEntity)

    @Query("DELETE FROM staff")
    suspend fun deleteAllStaff()
}

@Dao
interface ScanTransactionDao {
    @Query("SELECT * FROM scan_transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<ScanTransactionEntity>>

    @Query("SELECT * FROM scan_transactions WHERE staffId = :staffId ORDER BY timestamp DESC")
    fun getTransactionsByStaffFlow(staffId: String): Flow<List<ScanTransactionEntity>>

    @Query("SELECT * FROM scan_transactions WHERE syncStatus = 'PENDING'")
    suspend fun getPendingTransactions(): List<ScanTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: ScanTransactionEntity)

    @Query("UPDATE scan_transactions SET syncStatus = :status, syncErrorMessage = :errorMessage WHERE transactionId = :transactionId")
    suspend fun updateSyncStatusAndError(transactionId: String, status: String, errorMessage: String?)

    @Query("DELETE FROM scan_transactions WHERE transactionId LIKE 'TX-9%'")
    suspend fun deleteDemoTransactions()
}

@Dao
interface WarehouseSettingDao {
    @Query("SELECT * FROM warehouse_settings LIMIT 1")
    fun getSettingFlow(): Flow<WarehouseSettingEntity?>

    @Query("SELECT * FROM warehouse_settings LIMIT 1")
    suspend fun getSetting(): WarehouseSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: WarehouseSettingEntity)
}
