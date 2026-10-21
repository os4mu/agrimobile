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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.ProductEntity
import com.example.data.entities.ScanTransactionEntity
import com.example.data.entities.StaffEntity
import com.example.ui.components.GridBackground
import com.example.ui.theme.AgriBorder
import com.example.ui.theme.AgriErrorBg
import com.example.ui.theme.AgriErrorText
import com.example.ui.theme.AgriLightGreen
import com.example.ui.theme.AgriPrimaryDarkGreen
import com.example.ui.theme.AgriSuccessBg
import com.example.ui.theme.AgriSuccessText
import com.example.ui.theme.AgriSurfaceWhite
import com.example.ui.theme.AgriTextPrimary
import com.example.ui.theme.AgriTextSecondary
import com.example.ui.theme.AgriWarningBg
import com.example.ui.theme.AgriWarningText
import com.example.util.SyncUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MyScansScreen(
    recentScans: List<ScanTransactionEntity>,
    allProducts: List<ProductEntity>,
    onNavigateToScan: () -> Unit,
    onSyncNow: (onComplete: (Int) -> Unit) -> Unit = {},
    currentStaff: StaffEntity? = null,
    onLogout: () -> Unit = {}
) {
    var showProfileDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncResultMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())

    val pendingScans = remember(recentScans) {
        recentScans.filter { it.syncStatus == "PENDING" }
    }
    val pendingCount = pendingScans.size

    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var selectedDateFilter by remember { mutableStateOf("ALL_TIME") }

    val filteredScans = remember(recentScans, selectedTypeFilter, selectedDateFilter) {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfYesterday = startOfToday - (24 * 60 * 60 * 1000L)
        val startOf7DaysAgo = startOfToday - (6 * 24 * 60 * 60 * 1000L)

        recentScans.filter { scan ->
            val matchesType = when (selectedTypeFilter) {
                "IN" -> scan.scanType.equals("IN", ignoreCase = true)
                "OUT" -> scan.scanType.equals("OUT", ignoreCase = true) ||
                        scan.scanType.equals("STOCK_OUT", ignoreCase = true) ||
                        scan.totalPiecesChange < 0
                else -> true
            }

            val matchesDate = when (selectedDateFilter) {
                "TODAY" -> scan.timestamp >= startOfToday
                "YESTERDAY" -> scan.timestamp >= startOfYesterday && scan.timestamp < startOfToday
                "LAST_7_DAYS" -> scan.timestamp >= startOf7DaysAgo
                else -> true
            }

            matchesType && matchesDate
        }
    }

    GridBackground {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Screen Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgriSurfaceWhite)
                    .border(1.dp, AgriBorder)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity History",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgriTextPrimary
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AgriLightGreen)
                        .clickable { showProfileDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = AgriPrimaryDarkGreen,
                        modifier = Modifier.size(22.dp)
                    )
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
                // Pending Scans Header Card with "Sync now" button
                if (pendingCount > 0 || syncResultMessage != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(AgriWarningBg)
                                .border(1.dp, AgriBorder, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.SyncProblem,
                                            contentDescription = null,
                                            tint = AgriWarningText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "$pendingCount scan(s) pending sync",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AgriTextPrimary
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            isSyncing = true
                                            syncResultMessage = null
                                            onSyncNow { uploadedCount ->
                                                isSyncing = false
                                                val isOnline = SyncUtils.isOnline(context)
                                                syncResultMessage = when {
                                                    uploadedCount > 0 -> "Synced $uploadedCount scan(s)"
                                                    !isOnline -> "Still offline"
                                                    else -> "Sync completed"
                                                }
                                            }
                                        },
                                        enabled = !isSyncing,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AgriPrimaryDarkGreen,
                                            contentColor = AgriSurfaceWhite
                                        ),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        if (isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = AgriSurfaceWhite,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Sync,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Sync now",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                if (syncResultMessage != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = syncResultMessage!!,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AgriTextSecondary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Filter Chips Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        // Row 1: Transaction Type Chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val typeOptions = listOf(
                                "ALL" to "All Activity",
                                "IN" to "📥 Stock In",
                                "OUT" to "📤 Stock Out"
                            )
                            items(typeOptions) { (key, label) ->
                                val isSelected = selectedTypeFilter == key
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) AgriPrimaryDarkGreen else AgriSurfaceWhite)
                                        .border(1.dp, if (isSelected) AgriPrimaryDarkGreen else AgriBorder, RoundedCornerShape(20.dp))
                                        .clickable { selectedTypeFilter = key }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) AgriSurfaceWhite else AgriTextPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Row 2: Date Range Chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val dateOptions = listOf(
                                "ALL_TIME" to "All Time",
                                "TODAY" to "Today",
                                "YESTERDAY" to "Yesterday",
                                "LAST_7_DAYS" to "Last 7 Days"
                            )
                            items(dateOptions) { (key, label) ->
                                val isSelected = selectedDateFilter == key
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) AgriPrimaryDarkGreen else AgriSurfaceWhite)
                                        .border(1.dp, if (isSelected) AgriPrimaryDarkGreen else AgriBorder, RoundedCornerShape(20.dp))
                                        .clickable { selectedDateFilter = key }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) AgriSurfaceWhite else AgriTextPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Record Counter Subtitle
                        Text(
                            text = "Showing ${filteredScans.size} of ${recentScans.size} records",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AgriTextSecondary
                        )
                    }
                }

                if (filteredScans.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(AgriSurfaceWhite)
                                .border(1.dp, AgriBorder, RoundedCornerShape(20.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = AgriTextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No activity history found",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgriTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (recentScans.isEmpty()) "Activity you log will appear here." else "No records match the selected filters.",
                                    fontSize = 13.sp,
                                    color = AgriTextSecondary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                if (recentScans.isNotEmpty()) {
                                    Button(
                                        onClick = {
                                            selectedTypeFilter = "ALL"
                                            selectedDateFilter = "ALL_TIME"
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AgriPrimaryDarkGreen,
                                            contentColor = AgriSurfaceWhite
                                        )
                                    ) {
                                        Text("Reset Filters", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = onNavigateToScan,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AgriPrimaryDarkGreen,
                                            contentColor = AgriSurfaceWhite
                                        )
                                    ) {
                                        Text("Start Scanning", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filteredScans, key = { it.transactionId }) { tx ->
                        val product = allProducts.find { it.productId == tx.productId }
                        val productName = product?.productName ?: tx.productId
                        val isSynced = tx.syncStatus == "SYNCED"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(AgriSurfaceWhite)
                                .border(1.dp, AgriBorder, RoundedCornerShape(20.dp))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (tx.scanType == "IN") AgriSuccessBg else AgriErrorBg)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = tx.scanType,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tx.scanType == "IN") AgriSuccessText else AgriErrorText
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = productName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgriTextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "${tx.boxesScanned} boxes • ${tx.loosePiecesScanned} loose (${if (tx.totalPiecesChange > 0) "+${tx.totalPiecesChange}" else tx.totalPiecesChange} pcs)",
                                    fontSize = 13.sp,
                                    color = AgriTextSecondary
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = dateFormat.format(Date(tx.timestamp)),
                                    fontSize = 11.sp,
                                    color = AgriTextSecondary
                                )

                                if (!isSynced && !tx.syncErrorMessage.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tx.syncErrorMessage,
                                        fontSize = 11.sp,
                                        color = AgriTextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSynced) AgriSuccessBg else AgriWarningBg)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isSynced) Icons.Default.Check else Icons.Default.SyncProblem,
                                        contentDescription = null,
                                        tint = if (isSynced) AgriSuccessText else AgriWarningText,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isSynced) "Synced" else "Pending Sync",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSynced) AgriSuccessText else AgriWarningText
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        UserProfileDialog(
            currentStaff = currentStaff,
            pendingScansCount = pendingCount,
            onDismiss = { showProfileDialog = false },
            onLogout = {
                showProfileDialog = false
                onLogout()
            }
        )
    }
}
