package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ProductDao
import com.example.data.dao.ScanTransactionDao
import com.example.data.dao.StaffDao
import com.example.data.dao.WarehouseSettingDao
import com.example.data.entities.ProductEntity
import com.example.data.entities.ScanTransactionEntity
import com.example.data.entities.StaffEntity
import com.example.data.entities.WarehouseSettingEntity
import com.example.data.repository.Catalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProductEntity::class,
        StaffEntity::class,
        ScanTransactionEntity::class,
        WarehouseSettingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AgriStockDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun staffDao(): StaffDao
    abstract fun scanTransactionDao(): ScanTransactionDao
    abstract fun warehouseSettingDao(): WarehouseSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AgriStockDatabase? = null

        fun getDatabase(context: Context): AgriStockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgriStockDatabase::class.java,
                    "agri_stock_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(context.applicationContext))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    populateInitialData(database)
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    database.scanTransactionDao().deleteDemoTransactions()
                }
            }
        }

        private suspend fun populateInitialData(database: AgriStockDatabase) {
            val productDao = database.productDao()
            if (productDao.getProductCountDirect() == 0) {
                productDao.insertAllIfAbsent(Catalog.defaultProducts)
            }

            val settingDao = database.warehouseSettingDao()
            if (settingDao.getSetting() == null) {
                settingDao.insertSetting(WarehouseSettingEntity(1, "UniFam Store"))
            }
        }
    }
}
