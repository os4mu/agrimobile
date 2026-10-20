package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.ProductEntity
import com.example.data.entities.ScanTransactionEntity
import com.example.data.entities.ScannedItemPayload
import com.example.data.entities.StaffEntity
import com.example.data.repository.ProductRepository
import com.example.ui.theme.AgriBorder
import com.example.ui.theme.AgriErrorText
import com.example.ui.theme.AgriPrimaryDarkGreen
import com.example.ui.theme.AgriSurfaceWhite
import com.example.ui.theme.AgriTextPrimary
import com.example.ui.theme.AgriTextSecondary
import java.util.Calendar

enum class Screen {
    HOME,
    RESTOCK,
    MY_SCANS,
    SCAN_CAMERA
}

@Composable
fun MainScreen(
    currentStaff: StaffEntity?,
    allProducts: List<ProductEntity>,
    recentScans: List<ScanTransactionEntity>,
    onSaveScanBatch: (List<ScannedItemPayload>) -> Unit,
    onSyncNow: (onComplete: (Int) -> Unit) -> Unit = {},
    onLogout: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    val lastSyncTime by ProductRepository.lastSyncTime.collectAsState()
    val hasSynced = lastSyncTime > 0L

    val priorityAlertProducts = remember(allProducts, hasSynced) {
        if (!hasSynced) emptyList()
        else ProductRepository.getPrioritySortedAlertProducts(allProducts)
    }

    val alertCount = priorityAlertProducts.size

    val scansTodayCount = remember(recentScans) {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        recentScans.count { it.timestamp >= todayStart }
    }

    if (currentScreen != Screen.HOME) {
        BackHandler {
            currentScreen = Screen.HOME
        }
    }

    Scaffold(
        bottomBar = {
            if (currentScreen != Screen.SCAN_CAMERA) {
                NavigationBar(
                    containerColor = AgriSurfaceWhite,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(1.dp, AgriBorder)
                ) {
                    // Home Navigation Item
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = {
                            Text(
                                text = "Home",
                                fontWeight = if (currentScreen == Screen.HOME) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = currentScreen == Screen.HOME,
                        onClick = {
                            currentScreen = Screen.HOME
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AgriPrimaryDarkGreen,
                            selectedTextColor = AgriPrimaryDarkGreen,
                            unselectedIconColor = AgriTextSecondary,
                            unselectedTextColor = AgriTextSecondary,
                            indicatorColor = Color(0xFFF0F4F1)
                        ),
                        modifier = Modifier.testTag("nav_home")
                    )

                    // My Scans Navigation Item (CameraAlt icon)
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = "My Scans") },
                        label = {
                            Text(
                                text = "My Scans",
                                fontWeight = if (currentScreen == Screen.MY_SCANS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = currentScreen == Screen.MY_SCANS,
                        onClick = {
                            currentScreen = Screen.MY_SCANS
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AgriPrimaryDarkGreen,
                            selectedTextColor = AgriPrimaryDarkGreen,
                            unselectedIconColor = AgriTextSecondary,
                            unselectedTextColor = AgriTextSecondary,
                            indicatorColor = Color(0xFFF0F4F1)
                        ),
                        modifier = Modifier.testTag("nav_my_scans")
                    )

                    // Restock Navigation Item (Warning icon with offset red circular badge)
                    NavigationBarItem(
                        icon = {
                            Box {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Restock"
                                )
                                if (alertCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 10.dp, y = (-6).dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(AgriErrorText),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (alertCount > 99) "99+" else alertCount.toString(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AgriSurfaceWhite
                                        )
                                    }
                                }
                            }
                        },
                        label = {
                            Text(
                                text = "Restock",
                                fontWeight = if (currentScreen == Screen.RESTOCK) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = currentScreen == Screen.RESTOCK,
                        onClick = {
                            currentScreen = Screen.RESTOCK
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AgriPrimaryDarkGreen,
                            selectedTextColor = AgriPrimaryDarkGreen,
                            unselectedIconColor = AgriTextSecondary,
                            unselectedTextColor = AgriTextSecondary,
                            indicatorColor = Color(0xFFF0F4F1)
                        ),
                        modifier = Modifier.testTag("nav_restock")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    currentStaff = currentStaff,
                    allProducts = allProducts,
                    scansTodayCount = scansTodayCount,
                    recentScans = recentScans,
                    onNavigateToScan = { _ ->
                        currentScreen = Screen.SCAN_CAMERA
                    },
                    onNavigateToRestockTab = { currentScreen = Screen.RESTOCK },
                    onLogout = onLogout
                )

                Screen.RESTOCK -> RestockScreen(
                    allProducts = allProducts,
                    onNavigateToScan = { _ ->
                        currentScreen = Screen.SCAN_CAMERA
                    },
                    recentScans = recentScans,
                    currentStaff = currentStaff,
                    onLogout = onLogout
                )

                Screen.MY_SCANS -> MyScansScreen(
                    recentScans = recentScans,
                    allProducts = allProducts,
                    onNavigateToScan = {
                        currentScreen = Screen.SCAN_CAMERA
                    },
                    onSyncNow = onSyncNow,
                    currentStaff = currentStaff,
                    onLogout = onLogout
                )

                Screen.SCAN_CAMERA -> ScanProductScreen(
                    onBackClick = { currentScreen = Screen.HOME },
                    onSaveScanBatch = { items ->
                        onSaveScanBatch(items)
                        currentScreen = Screen.MY_SCANS
                    }
                )
            }
        }
    }
}
