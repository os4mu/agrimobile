package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.entities.StaffEntity
import com.example.ui.components.GridBackground
import com.example.ui.theme.AgriBorder
import com.example.ui.theme.AgriErrorBg
import com.example.ui.theme.AgriErrorText
import com.example.ui.theme.AgriLightGreen
import com.example.ui.theme.AgriPrimaryDarkGreen
import com.example.ui.theme.AgriSurfaceWhite
import com.example.ui.theme.AgriTextPrimary
import com.example.ui.theme.AgriTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StockOutStep {
    PRODUCT_LIST,
    DEDUCT_SCREEN,
    CONFIRMED
}

@Composable
fun StockOutScreen(
    allProducts: List<ProductEntity>,
    currentStaff: StaffEntity?,
    onBackClick: () -> Unit,
    onConfirmDeduction: (ProductEntity, Int, String) -> Unit
) {
    var currentStep by remember { mutableStateOf(StockOutStep.PRODUCT_LIST) }
    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var removalQuantity by remember { mutableIntStateOf(1) }
    var selectedReason by remember { mutableStateOf("Sold") }
    var confirmedTimeStr by remember { mutableStateOf("") }

    val canStockOut = currentStaff?.canStockOut != false ||
        currentStaff?.role?.equals("ADMIN", ignoreCase = true) == true ||
        currentStaff?.role?.equals("ADMINISTRATOR", ignoreCase = true) == true
    var showRbacDeniedDialog by remember { mutableStateOf(false) }

    if (showRbacDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showRbacDeniedDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Permission Denied",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgriErrorText
                )
            },
            text = {
                Text(
                    text = "You do not have permission to perform stock-out deductions. Please contact your warehouse administrator.",
                    fontSize = 14.sp,
                    color = AgriTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { showRbacDeniedDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgriPrimaryDarkGreen,
                        contentColor = AgriSurfaceWhite
                    )
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val availableCategories = remember(allProducts) {
        listOf("All") + allProducts.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filteredProducts = remember(allProducts, searchQuery, selectedCategory) {
        allProducts.filter { item ->
            val matchesCategory = (selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true))
            val matchesQuery = if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                item.productName.lowercase().contains(q) ||
                        item.sku.lowercase().contains(q) ||
                        item.category.lowercase().contains(q)
            } else {
                true
            }
            matchesCategory && matchesQuery
        }
    }

    BackHandler {
        when (currentStep) {
            StockOutStep.CONFIRMED -> {
                onBackClick()
            }
            StockOutStep.DEDUCT_SCREEN -> {
                currentStep = StockOutStep.PRODUCT_LIST
            }
            StockOutStep.PRODUCT_LIST -> {
                onBackClick()
            }
        }
    }

    GridBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            when (currentStep) {
                // ==========================================
                // STEP 1: PRODUCT LIST
                // ==========================================
                StockOutStep.PRODUCT_LIST -> {
                    // Header Bar
                    StockOutTopBar(
                        title = "Stock Out",
                        onBack = onBackClick
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = 16.dp,
                            bottom = 32.dp
                        )
                    ) {
                        item {
                            // Search Field
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text("Search product...", fontSize = 14.sp, color = AgriTextSecondary)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = AgriTextSecondary)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = AgriTextSecondary)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("stock_out_search_input"),
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AgriBorder,
                                    unfocusedBorderColor = AgriBorder,
                                    focusedContainerColor = AgriSurfaceWhite,
                                    unfocusedContainerColor = AgriSurfaceWhite
                                ),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Horizontal scrollable Category Filter Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableCategories.forEach { categoryName ->
                                    val isSelected = selectedCategory == categoryName
                                    val chipBg = if (isSelected) AgriPrimaryDarkGreen else AgriLightGreen
                                    val chipTextColor = if (isSelected) AgriSurfaceWhite else AgriPrimaryDarkGreen
                                    val chipBorderColor = if (isSelected) AgriPrimaryDarkGreen else AgriBorder

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(chipBg)
                                            .border(1.dp, chipBorderColor, RoundedCornerShape(20.dp))
                                            .clickable { selectedCategory = categoryName }
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                            .testTag("stock_out_category_chip_${categoryName}")
                                    ) {
                                        Text(
                                            text = categoryName,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = chipTextColor
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (selectedCategory == "All") "ALL PRODUCTS" else selectedCategory.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgriTextSecondary,
                                letterSpacing = 1.1.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))
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
                                        fontSize = 14.sp,
                                        color = AgriTextSecondary
                                    )
                                }
                            }
                        } else {
                            items(filteredProducts, key = { it.productId }) { item ->
                                StockOutProductCard(
                                    product = item,
                                    onClick = {
                                        if (!canStockOut) {
                                            showRbacDeniedDialog = true
                                        } else {
                                            selectedProduct = item
                                            removalQuantity = if (item.quantity > 0) 1 else 0
                                            selectedReason = "Sold"
                                            currentStep = StockOutStep.DEDUCT_SCREEN
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }

                // ==========================================
                // STEP 2: DEDUCT SCREEN
                // ==========================================
                StockOutStep.DEDUCT_SCREEN -> {
                    val prod = selectedProduct
                    if (prod != null) {
                        val currentStock = prod.quantity
                        val remainingStock = maxOf(0, currentStock - removalQuantity)
                        val isOverLimit = removalQuantity >= currentStock
                        val canDeduct = currentStock > 0 && removalQuantity in 1..currentStock

                        StockOutTopBar(
                            title = prod.productName,
                            onBack = { currentStep = StockOutStep.PRODUCT_LIST }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 20.dp)
                        ) {
                            // Product Summary Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(AgriSurfaceWhite)
                                    .border(1.dp, AgriBorder, RoundedCornerShape(20.dp))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Product",
                                        fontSize = 12.sp,
                                        color = AgriTextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = prod.productName,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgriTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${prod.category} • Current stock: $currentStock ${prod.unit}",
                                        fontSize = 13.sp,
                                        color = AgriTextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Quantity Selector Section
                            Text(
                                text = "How many are being removed?",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgriTextPrimary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Stepper Controls Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Decrease button
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (removalQuantity > 1) AgriPrimaryDarkGreen else Color(0xFFE0E0E0))
                                        .clickable(enabled = removalQuantity > 1) {
                                            if (removalQuantity > 1) removalQuantity--
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease",
                                        tint = if (removalQuantity > 1) AgriSurfaceWhite else Color(0xFF9E9E9E),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(28.dp))

                                Text(
                                    text = removalQuantity.toString(),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgriTextPrimary
                                )

                                Spacer(modifier = Modifier.width(28.dp))

                                // Increase button (locks if quantity would exceed current stock)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (!isOverLimit) AgriPrimaryDarkGreen else Color(0xFFE0E0E0))
                                        .clickable(enabled = !isOverLimit) {
                                            if (!isOverLimit) removalQuantity++
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase",
                                        tint = if (!isOverLimit) AgriSurfaceWhite else Color(0xFF9E9E9E),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Remaining After Deduct Preview Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF102018))
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Remaining after deduct",
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "$remainingStock ${prod.unit}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Reason Selection Chips
                            Text(
                                text = "Reason (optional)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgriTextPrimary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            val reasons = listOf("Sold", "Damaged", "Expired", "Other")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                reasons.forEach { reason ->
                                    val isSelected = selectedReason == reason
                                    val chipBg = if (isSelected) AgriPrimaryDarkGreen else AgriLightGreen
                                    val chipTextColor = if (isSelected) AgriSurfaceWhite else AgriPrimaryDarkGreen

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(chipBg)
                                            .border(1.dp, if (isSelected) AgriPrimaryDarkGreen else AgriBorder, RoundedCornerShape(20.dp))
                                            .clickable { selectedReason = reason }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = reason,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = chipTextColor
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Confirm Removal Button
                            Button(
                                onClick = {
                                    if (!canDeduct) return@Button
                                    confirmedTimeStr = SimpleDateFormat("h:mm a 'today'", Locale.US).format(Date())
                                    onConfirmDeduction(prod, removalQuantity, selectedReason)
                                    currentStep = StockOutStep.CONFIRMED
                                },
                                enabled = canDeduct,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF9B1C1C),
                                    contentColor = AgriSurfaceWhite,
                                    disabledContainerColor = Color(0xFFE0E3E0),
                                    disabledContentColor = Color(0xFF8E938E)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("confirm_removal_button")
                            ) {
                                Text(
                                    text = "Confirm removal",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // STEP 3: CONFIRMED
                // ==========================================
                StockOutStep.CONFIRMED -> {
                    val prod = selectedProduct
                    val staffName = currentStaff?.fullName ?: "Warehouse Staff"

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Green Checkmark Badge
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AgriLightGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Success",
                                tint = AgriPrimaryDarkGreen,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Stock updated",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgriTextPrimary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = prod?.productName ?: "Product",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = AgriTextPrimary
                        )

                        Text(
                            text = "-$removalQuantity ${prod?.unit ?: "units"} • ${maxOf(0, (prod?.quantity ?: 0) - removalQuantity)} remaining",
                            fontSize = 14.sp,
                            color = AgriTextSecondary
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Log Details Box (Black container)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF102018))
                                .padding(18.dp)
                        ) {
                            LogRow(label = "Logged by", value = staffName)
                            Spacer(modifier = Modifier.height(10.dp))
                            LogRow(label = "Reason", value = selectedReason)
                            Spacer(modifier = Modifier.height(10.dp))
                            LogRow(label = "Time", value = confirmedTimeStr.ifBlank { "Just now" })
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Done Button
                        Button(
                            onClick = onBackClick,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AgriPrimaryDarkGreen,
                                contentColor = AgriSurfaceWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("done_stock_out_button")
                        ) {
                            Text(
                                text = "Done",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StockOutTopBar(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgriPrimaryDarkGreen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = AgriSurfaceWhite
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = AgriSurfaceWhite
        )
    }
}

@Composable
fun StockOutProductCard(
    product: ProductEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AgriSurfaceWhite)
            .border(1.dp, AgriBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.productName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AgriTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${product.category} • ${product.quantity} ${product.unit} in stock",
                fontSize = 13.sp,
                color = AgriTextSecondary
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = AgriTextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun LogRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
