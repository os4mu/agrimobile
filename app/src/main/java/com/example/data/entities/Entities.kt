package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val productId: String,
    val productName: String,
    val sku: String,
    val category: String,
    val quantity: Int,
    val unit: String,
    val lowStockThreshold: Int,
    val piecesPerBox: Int = 12
)

@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey val staffId: String,
    val employeeId: String,
    val fullName: String,
    val role: String,
    val assignedWarehouse: String,
    val status: String
)

@Entity(tableName = "scan_transactions")
data class ScanTransactionEntity(
    @PrimaryKey val transactionId: String,
    val staffId: String,
    val productId: String,
    val scanType: String,
    val boxesScanned: Int,
    val loosePiecesScanned: Int,
    val totalPiecesChange: Int,
    val timestamp: Long,
    val notes: String = "",
    val syncStatus: String = "PENDING",
    val syncErrorMessage: String? = null
)

@Entity(tableName = "warehouse_settings")
data class WarehouseSettingEntity(
    @PrimaryKey val id: Int = 1,
    val warehouseName: String
)

data class ScannedItemPayload(
    val productId: String,
    val sku: String,
    val productName: String,
    val categoryId: String,
    val category: String,
    val uomLabel: String,
    val unit: String,
    val reorderPoint: Int,
    val detectedQuantity: Int,
    val finalQuantity: Int,
    val confidence: Float
)
