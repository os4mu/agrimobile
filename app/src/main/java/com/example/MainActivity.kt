package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.AgriStockDatabase
import com.example.data.NetworkMonitor
import com.example.data.repository.AuthRepository
import com.example.data.repository.ProductRepository
import com.example.data.repository.ScanRepository
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MainScreen
import com.example.ui.theme.AgriPrimaryDarkGreen
import com.example.ui.theme.AgriStockTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AgriStockDatabase.getDatabase(this)
        ProductRepository.init(database.productDao(), this)

        val authRepository = AuthRepository(database.staffDao(), this)
        val scanRepository = ScanRepository(database.scanTransactionDao(), database.productDao(), database.staffDao(), this.applicationContext)

        setContent {
            AgriStockTheme {
                val currentStaff by authRepository.currentStaff.collectAsState()
                var isRestoringSession by remember { mutableStateOf(true) }
                var revocationError by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    val restoreResult = authRepository.restoreSession()
                    restoreResult.onFailure { ex ->
                        revocationError = ex.message
                    }
                    if (authRepository.currentStaff.value != null) {
                        scope.launch { scanRepository.syncPendingScans() }
                    }
                    isRestoringSession = false
                }

                LaunchedEffect(currentStaff) {
                    if (currentStaff != null) {
                        NetworkMonitor(this@MainActivity).isOnline.collect { isOnline ->
                            if (isOnline) {
                                scanRepository.syncPendingScans()
                            }
                        }
                    }
                }

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

                if (isRestoringSession) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AgriPrimaryDarkGreen,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else if (currentStaff == null) {
                    LoginScreen(
                        initialError = revocationError,
                        onLoginClick = { empId, pwd, keepLoggedIn, onResult ->
                            scope.launch {
                                val result = authRepository.login(empId, pwd, keepLoggedIn)
                                onResult(result.map { })
                                if (result.isSuccess) {
                                    scanRepository.syncPendingScans()
                                }
                            }
                        }
                    )
                } else {
                    MainScreen(
                        currentStaff = currentStaff,
                        allProducts = allProducts,
                        recentScans = recentScans,
                        onSaveScanBatch = { items ->
                            scope.launch {
                                scanRepository.saveScanBatch(currentStaff!!, items)
                            }
                        },
                        onSyncNow = { onComplete ->
                            scope.launch {
                                val count = scanRepository.syncPendingScans()
                                onComplete(count)
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
