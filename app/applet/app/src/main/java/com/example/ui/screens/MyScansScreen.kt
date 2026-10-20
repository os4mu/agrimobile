package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.ProductEntity
import com.example.data.entities.ScanTransactionEntity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyScansScreen(
    recentScans: List<ScanTransactionEntity>,
    allProducts: List<ProductEntity>,
    onNavigateToScan: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())

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
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Recent Scans",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgriTextPrimary
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AgriLightGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = AgriPrimaryDarkGreen,
                        modifier = Modifier.size(20.dp)
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
                if (recentScans.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(AgriSurfaceWhite)
                                .border(1.dp, AgriBorder, RoundedCornerShape(16.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = AgriTextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No scans recorded yet",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgriTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Scans you log will appear here.",
                                    fontSize = 13.sp,
                                    color = AgriTextSecondary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onNavigateToScan,
                                    shape = RoundedCornerShape(12.dp),
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
                } else {
                    items(recentScans, key = { it.transactionId }) { tx ->
                        val product = allProducts.find { it.productId == tx.productId }
                        val productName = product?.productName ?: tx.productId
                        val isSynced = tx.syncStatus == "SYNCED"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(AgriSurfaceWhite)
                                .border(1.dp, AgriBorder, RoundedCornerShape(16.dp))
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
                                        text = if (isSynced) "Synced" else "Pending",
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
}
