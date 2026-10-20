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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.AgriSuccessBg
import com.example.ui.theme.AgriSuccessText
import com.example.ui.theme.AgriSurfaceWhite
import com.example.ui.theme.AgriTextPrimary
import com.example.ui.theme.AgriTextSecondary
import com.example.ui.theme.AgriWarningBg
import com.example.ui.theme.AgriWarningText

@Composable
fun RestockScreen(
    allProducts: List<ProductEntity>,
    onNavigateToScan: (ProductEntity) -> Unit,
    recentScans: List<ScanTransactionEntity> = emptyList(),
    currentStaff: StaffEntity? = null,
    onLogout: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var showProfileDialog by remember { mutableStateOf(false) }

    val pendingScansCount = remember(recentScans) { recentScans.count { it.syncStatus == "PENDING" } }

    val lastSyncTime by ProductRepository.lastSyncTime.collectAsState()
    val hasSynced = lastSyncTime > 0L

    val priorityAlertProducts = remember(allProducts, hasSynced) {
        if (!hasSynced) emptyList()
        else ProductRepository.getPrioritySortedAlertProducts(allProducts)
    }

    val outOfStockItems = remember(priorityAlertProducts) {
        priorityAlertProducts.filter { it.quantity <= 0 }
    }

    val lowStockItems = remember(priorityAlertProducts) {
        priorityAlertProducts.filter { it.quantity > 0 && it.quantity <= it.lowStockThreshold }
    }

    val filteredProducts = remember(allProducts, searchQuery) {
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim().lowercase()
            allProducts.filter {
                it.productName.lowercase().contains(query) ||
                        it.sku.lowercase().contains(query) ||
                        it.category.lowercase().contains(query)
            }
        } else {
            emptyList()
        }
    }

    GridBackground {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Screen Top Bar: bold "Restock" on left, 44dp profile circle button on right
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
                    text = "Restock",
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
                item {
                    Text(
                        text = "Restock Priority",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriTextPrimary
                    )

                    Text(
                        text = "Scan these items first",
                        fontSize = 14.sp,
                        color = AgriTextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Search Field: 28.dp corners, fully rounded
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search products or categories...",
                                fontSize = 14.sp,
                                color = AgriTextSecondary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = AgriTextSecondary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = AgriTextSecondary
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restock_search_input"),
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgriBorder,
                            unfocusedBorderColor = AgriBorder,
                            focusedContainerColor = AgriSurfaceWhite,
                            unfocusedContainerColor = AgriSurfaceWhite
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (searchQuery.isBlank()) {
                    if (outOfStockItems.isEmpty() && lowStockItems.isEmpty()) {
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
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AgriPrimaryDarkGreen,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "All products are stocked",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgriTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "No items currently require restocking.",
                                        fontSize = 13.sp,
                                        color = AgriTextSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        // Section 1: OUT OF STOCK
                        if (outOfStockItems.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    Text(
                                        text = "OUT OF STOCK",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgriErrorText,
                                        letterSpacing = 1.1.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AgriErrorBg)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = outOfStockItems.size.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AgriErrorText
                                        )
                                    }
                                }
                            }

                            items(outOfStockItems, key = { "out_${it.productId}" }) { item ->
                                RestockProductCard(
                                    product = item,
                                    onScanClick = { onNavigateToScan(item) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        // Section 2: LOW STOCK
                        if (lowStockItems.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    Text(
                                        text = "LOW STOCK",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgriWarningText,
                                        letterSpacing = 1.1.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AgriWarningBg)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = lowStockItems.size.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AgriWarningText
                                        )
                                    }
                                }
                            }

                            items(lowStockItems, key = { "low_${it.productId}" }) { item ->
                                RestockProductCard(
                                    product = item,
                                    onScanClick = { onNavigateToScan(item) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                } else {
                    // Search Results View
                    item {
                        Text(
                            text = "SEARCH RESULTS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgriTextSecondary,
                            letterSpacing = 1.1.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    if (filteredProducts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching products found",
                                    fontSize = 15.sp,
                                    color = AgriTextSecondary
                                )
                            }
                        }
                    } else {
                        items(filteredProducts, key = { "search_${it.productId}" }) { item ->
                            RestockProductCard(
                                product = item,
                                onScanClick = { onNavigateToScan(item) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
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
fun RestockProductCard(
    product: ProductEntity,
    onScanClick: () -> Unit
) {
    val isOutOfStock = product.quantity <= 0
    val isLowStock = !isOutOfStock && product.quantity <= product.lowStockThreshold
    val shortage = maxOf(1, product.lowStockThreshold - product.quantity)

    val (badgeBg, badgeText, statusLabel) = when {
        isOutOfStock -> Triple(AgriErrorBg, AgriErrorText, "Out of Stock")
        isLowStock -> Triple(AgriWarningBg, AgriWarningText, "Low Stock")
        else -> Triple(AgriSuccessBg, AgriSuccessText, "OK")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AgriSurfaceWhite)
            .border(1.dp, AgriBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Row 1: product name in bold + status badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = product.productName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AgriTextPrimary,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeText
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 2: SKU & Category
        Text(
            text = "SKU: ${product.sku} • ${product.category}",
            fontSize = 13.sp,
            color = AgriTextSecondary
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Row 3: quantity & alert threshold
        Text(
            text = "${product.quantity} ${product.unit} left • Alert at ${product.lowStockThreshold}",
            fontSize = 13.sp,
            color = AgriTextSecondary
        )

        // Row 4: Needs X more
        if (product.quantity <= product.lowStockThreshold) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Needs $shortage more",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOutOfStock) AgriErrorText else AgriWarningText
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 5: Full-width Scan button, 48dp high, dark green, 16dp corners
        Button(
            onClick = onScanClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AgriPrimaryDarkGreen,
                contentColor = AgriSurfaceWhite
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("scan_button_${product.productId}")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Scan ${product.productName}",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
