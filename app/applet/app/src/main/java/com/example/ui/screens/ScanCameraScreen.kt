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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.ProductEntity
import com.example.ui.components.GridBackground
import com.example.ui.theme.AgriBorder
import com.example.ui.theme.AgriErrorBg
import com.example.ui.theme.AgriErrorText
import com.example.ui.theme.AgriPrimaryDarkGreen
import com.example.ui.theme.AgriSuccessBg
import com.example.ui.theme.AgriSuccessText
import com.example.ui.theme.AgriSurfaceWhite
import com.example.ui.theme.AgriTextPrimary
import com.example.ui.theme.AgriTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanCameraScreen(
    allProducts: List<ProductEntity>,
    preselectedProduct: ProductEntity?,
    onSaveScan: (ProductEntity, Int, Int, String, String) -> Unit,
    onClose: () -> Unit
) {
    var selectedProduct by remember { mutableStateOf(preselectedProduct ?: allProducts.firstOrNull()) }
    var mode by remember { mutableStateOf("IN") }
    var boxesText by remember { mutableStateOf("1") }
    var loosePiecesText by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    GridBackground {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgriSurfaceWhite)
                    .border(1.dp, AgriBorder)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AgriTextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan Inventory",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriTextPrimary
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AgriTextSecondary)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Select Product",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgriTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedProduct?.productName ?: "Select a product",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("product_dropdown"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgriPrimaryDarkGreen,
                            unfocusedBorderColor = AgriBorder
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        allProducts.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.productName, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    selectedProduct = product
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Scan Mode",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgriTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (mode == "IN") AgriSuccessBg else AgriSurfaceWhite)
                            .border(
                                1.dp,
                                if (mode == "IN") AgriSuccessText else AgriBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { mode = "IN" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Stock IN (+)",
                            fontWeight = FontWeight.Bold,
                            color = if (mode == "IN") AgriSuccessText else AgriTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (mode == "OUT") AgriErrorBg else AgriSurfaceWhite)
                            .border(
                                1.dp,
                                if (mode == "OUT") AgriErrorText else AgriBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { mode = "OUT" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Stock OUT (-)",
                            fontWeight = FontWeight.Bold,
                            color = if (mode == "OUT") AgriErrorText else AgriTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = boxesText,
                        onValueChange = { boxesText = it },
                        label = { Text("Boxes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("boxes_scanned_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgriPrimaryDarkGreen,
                            unfocusedBorderColor = AgriBorder
                        )
                    )

                    OutlinedTextField(
                        value = loosePiecesText,
                        onValueChange = { loosePiecesText = it },
                        label = { Text("Loose Pieces") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("loose_pieces_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgriPrimaryDarkGreen,
                            unfocusedBorderColor = AgriBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Batch No. (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_notes_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgriPrimaryDarkGreen,
                        unfocusedBorderColor = AgriBorder
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val product = selectedProduct ?: return@Button
                        val boxes = boxesText.toIntOrNull() ?: 0
                        val pieces = loosePiecesText.toIntOrNull() ?: 0
                        onSaveScan(product, boxes, pieces, mode, notes)
                    },
                    enabled = selectedProduct != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgriPrimaryDarkGreen,
                        contentColor = AgriSurfaceWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_scan_button")
                ) {
                    Text(
                        text = "Confirm Scan",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
