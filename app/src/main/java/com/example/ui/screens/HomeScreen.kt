package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.ProductEntity
import com.example.data.entities.ScanTransactionEntity
import com.example.data.entities.StaffEntity
import com.example.data.repository.ProductRepository
import com.example.ui.components.GridBackground
import com.example.ui.theme.AgriBorder
import com.example.ui.theme.AgriErrorBg
import com.example.ui.theme.AgriErrorText
import com.example.ui.theme.AgriLightGreen
import com.example.ui.theme.AgriPrimaryDarkGreen
import com.example.ui.theme.AgriSecondaryGreen
import com.example.ui.theme.AgriSurfaceWhite
import com.example.ui.theme.AgriTextPrimary
import com.example.ui.theme.AgriTextSecondary
import com.example.ui.theme.AgriWarningBg
import com.example.ui.theme.AgriWarningText
import com.example.util.Constants

@Composable
fun HomeScreen(
    currentStaff: StaffEntity?,
    allProducts: List<ProductEntity>,
    scansTodayCount: Int,
    recentScans: List<ScanTransactionEntity> = emptyList(),
    onNavigateToScan: (ProductEntity?) -> Unit,
    onNavigateToRestockTab: () -> Unit,
    onLogout: () -> Unit
) {
    var showProfileDialog by remember { mutableStateOf(false) }
    val lastSyncTime by ProductRepository.lastSyncTime.collectAsState()
    val hasSynced = lastSyncTime > 0L

    val priorityAlertProducts = remember(allProducts, hasSynced) {
        if (!hasSynced) emptyList()
        else ProductRepository.getPrioritySortedAlertProducts(allProducts)
    }

    val alertCount = priorityAlertProducts.size
    val pendingScansCount = remember(recentScans) { recentScans.count { it.syncStatus == "PENDING" } }
    val staffName = currentStaff?.fullName ?: "Staff"
    val staffId = currentStaff?.employeeId ?: "N/A"

    GridBackground {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar: white, 1.dp bottom border
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgriSurfaceWhite)
                    .border(1.dp, AgriBorder)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AgriPrimaryDarkGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = AgriSurfaceWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "AgriStock",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriTextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Logout pill button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(AgriLightGreen)
                            .clickable { onLogout() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Logout",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgriPrimaryDarkGreen
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // 44dp light green profile button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AgriLightGreen)
                            .clickable { showProfileDialog = true }
                            .testTag("profile_icon_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User Profile",
                            tint = AgriPrimaryDarkGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 16.dp,
                    bottom = 90.dp
                )
            ) {
                item {
                    Text(
                        text = "TODAY'S MANIFEST",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriTextSecondary,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = Constants.STORE_NAME,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriTextPrimary
                    )

                    Text(
                        text = "Welcome back, $staffName ($staffId)",
                        fontSize = 14.sp,
                        color = AgriTextSecondary
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // "Scan Product" Card (Dark green background)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(AgriPrimaryDarkGreen)
                            .clickable { onNavigateToScan(null) }
                            .padding(16.dp)
                            .testTag("start_scanning_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AgriSecondaryGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = AgriSurfaceWhite,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scan Product",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgriSurfaceWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Scan inventory using camera",
                                    fontSize = 13.sp,
                                    color = AgriLightGreen
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = AgriSurfaceWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // "My Scans Today" Slim Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AgriSurfaceWhite)
                            .border(1.dp, AgriBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(AgriLightGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = AgriPrimaryDarkGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "My Scans Today",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgriTextPrimary
                                )
                            }
                            Text(
                                text = scansTodayCount.toString(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgriPrimaryDarkGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Section Header: Needs Restocking Now
                    Text(
                        text = "PRIORITY LIST",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriTextSecondary,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Needs Restocking Now",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriTextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (alertCount == 0) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(AgriSurfaceWhite)
                                .border(1.dp, AgriBorder, RoundedCornerShape(16.dp))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AgriPrimaryDarkGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "All products are stocked",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgriTextPrimary
                                )
                            }
                        }
                    }
                } else {
                    val displayItems = priorityAlertProducts.take(3)
                    items(displayItems.size) { index ->
                        val item = displayItems[index]
                        HomeRestockRowCard(
                            product = item,
                            onScanClick = { onNavigateToScan(item) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (alertCount > 3) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Showing top 3 of $alertCount items. Open the Restock tab to see all.",
                                fontSize = 12.sp,
                                color = AgriTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        UserProfileDialog(
            currentStaff = currentStaff,
            pendingScansCount = pendingScansCount,
            onDismiss = { showProfileDialog = false },
            onLogout = {
                showProfileDialog = false
                onLogout()
            }
        )
    }
}

@Composable
fun HomeRestockRowCard(
    product: ProductEntity,
    onScanClick: () -> Unit
) {
    val isOutOfStock = product.quantity <= 0
    val shortage = maxOf(1, product.lowStockThreshold - product.quantity)

    val (chipBg, iconColor, statusText, statusColor) = if (isOutOfStock) {
        Quadruple(AgriErrorBg, AgriErrorText, "OUT · Needs $shortage more", AgriErrorText)
    } else {
        Quadruple(AgriWarningBg, AgriWarningText, "LOW · Needs $shortage more", AgriWarningText)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AgriSurfaceWhite)
            .border(1.dp, AgriBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 40.dp icon chip
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(chipBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.productName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AgriTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 56.dp dark green rounded-square button with white camera icon
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AgriPrimaryDarkGreen)
                .clickable { onScanClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Scan ${product.productName}",
                tint = AgriSurfaceWhite,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun UserProfileDialog(
    currentStaff: StaffEntity?,
    pendingScansCount: Int = 0,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val fullName = currentStaff?.fullName ?: "Warehouse Staff"
    val employeeId = currentStaff?.employeeId ?: "N/A"
    val roleUpper = (currentStaff?.role ?: "STAFF").uppercase()
    val initialLetter = fullName.trim().firstOrNull()?.uppercase() ?: "S"
    var showLogoutConfirm by remember { mutableStateOf(false) }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Unsynced Scans Warning",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgriTextPrimary
                )
            },
            text = {
                Text(
                    text = "$pendingScansCount scan(s) are not synced yet. They will upload the next time you log in.",
                    fontSize = 14.sp,
                    color = AgriTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgriErrorBg,
                        contentColor = AgriErrorText
                    )
                ) {
                    Text(text = "Logout", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = AgriTextSecondary)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = AgriPrimaryDarkGreen,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "User Profile",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriTextPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = AgriTextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Summary Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AgriLightGreen)
                        .border(1.dp, AgriBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 64dp dark green circle avatar with white first letter
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AgriPrimaryDarkGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initialLetter,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgriSurfaceWhite
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = fullName,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgriTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AgriSecondaryGreen)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = roleUpper,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgriSurfaceWhite
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• Active Session",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgriPrimaryDarkGreen
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Info Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AgriSurfaceWhite)
                        .border(1.dp, AgriBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    ProfileDetailRow(
                        icon = Icons.Default.Badge,
                        label = "Employee ID",
                        value = employeeId
                    )
                    HorizontalDivider(color = AgriBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                    ProfileDetailRow(
                        icon = Icons.Default.Security,
                        label = "Account Role",
                        value = roleUpper
                    )
                    HorizontalDivider(color = AgriBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                    ProfileDetailRow(
                        icon = Icons.Default.Store,
                        label = "Store",
                        value = Constants.STORE_NAME
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Full-width Logout Button
                Button(
                    onClick = {
                        if (pendingScansCount > 0) {
                            showLogoutConfirm = true
                        } else {
                            onLogout()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgriErrorBg,
                        contentColor = AgriErrorText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Logout",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgriTextPrimary
                )
            }
        }
    )
}

@Composable
fun ProfileDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AgriTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = AgriTextSecondary
            )
        }
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AgriTextPrimary
        )
    }
}
