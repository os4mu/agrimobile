package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.example.data.AgriStockDatabase
import com.example.data.repository.AuthRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.ScanRepository
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MainScreen
import com.example.ui.theme.AgriStockTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AgriStockDatabase.getDatabase(this)
        ProductRepository.init(database.productDao(), this)

        val authRepository = AuthRepository(database.staffDao(), this)
        val scanRepository = ScanRepository(database.scanTransactionDao())

        setContent {
            AgriStockTheme {
                val currentStaff by authRepository.currentStaff.collectAsState()
                val scope = rememberCoroutineScope()

                val productsFlow = remember {
                    ProductRepository.getAllProductsFlow() ?: flowOf(emptyList())
                }
                val allProducts by productsFlow.collectAsState(initial = emptyList())

                val scansFlow = remember(currentStaff) {
                    if (currentStaff != null) {
                        scanRepository.getTransactionsByStaff(currentStaff!!.staffId)
                    } else {
                        flowOf(emptyList())
                    }
                }
                val recentScans by scansFlow.collectAsState(initial = emptyList())

                if (currentStaff == null) {
                    LoginScreen(
                        onLoginClick = { empId, pwd, onResult ->
                            scope.launch {
                                val result = authRepository.login(empId, pwd)
                                onResult(result.map { })
                            }
                        }
                    )
                } else {
                    MainScreen(
                        currentStaff = currentStaff,
                        allProducts = allProducts,
                        recentScans = recentScans,
                        onSaveScan = { product, boxes, pieces, mode, notes ->
                            scope.launch {
                                scanRepository.saveScanBatch(
                                    staffId = currentStaff!!.staffId,
                                    productId = product.productId,
                                    canonicalProductId = product.productId,
                                    boxCount = boxes,
                                    pieceCount = pieces,
                                    piecesPerBox = product.piecesPerBox,
                                    mode = mode,
                                    notes = notes
                                )
                            }
                        },
                        onLogout = {
                            authRepository.logout()
                        }
                    )
                }
            }
        }
    }
}
