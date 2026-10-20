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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GridBackground
import com.example.ui.theme.AgriBorder
import com.example.ui.theme.AgriErrorBg
import com.example.ui.theme.AgriErrorText
import com.example.ui.theme.AgriPrimaryDarkGreen
import com.example.ui.theme.AgriSurfaceWhite
import com.example.ui.theme.AgriTextPrimary
import com.example.ui.theme.AgriTextSecondary

@Composable
fun LoginScreen(
    initialError: String? = null,
    onLoginClick: (String, String, Boolean, (Result<Unit>) -> Unit) -> Unit
) {
    var employeeId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var keepLoggedIn by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(initialError) }

    val isFormValid = employeeId.isNotBlank() && password.isNotBlank()

    GridBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top header: 48dp dark green circle with white leaf icon + AgriStock title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AgriPrimaryDarkGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = AgriSurfaceWhite,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "AgriStock",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriPrimaryDarkGreen
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "WAREHOUSE STAFF ACCESS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgriPrimaryDarkGreen,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sign in with your Employee ID and password to scan inventory and check stock levels. Don't have an account? Ask your administrator to create one for you.",
                    fontSize = 13.sp,
                    color = AgriTextSecondary,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Form Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(AgriSurfaceWhite)
                        .border(1.dp, AgriBorder, RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    // Show login errors as a light red rounded box above Employee ID field
                    if (errorMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AgriErrorBg)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = AgriErrorText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = "Employee ID",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = employeeId,
                        onValueChange = {
                            employeeId = it
                            errorMessage = null
                        },
                        placeholder = {
                            Text("e.g., EMP-003", color = AgriTextSecondary, fontSize = 14.sp)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = AgriTextSecondary)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("employee_id_input"),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgriPrimaryDarkGreen,
                            unfocusedBorderColor = AgriBorder,
                            focusedContainerColor = AgriSurfaceWhite,
                            unfocusedContainerColor = AgriSurfaceWhite
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Password",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        placeholder = {
                            Text("Enter password", color = AgriTextSecondary, fontSize = 14.sp)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = AgriTextSecondary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = AgriTextSecondary
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgriPrimaryDarkGreen,
                            unfocusedBorderColor = AgriBorder,
                            focusedContainerColor = AgriSurfaceWhite,
                            unfocusedContainerColor = AgriSurfaceWhite
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Keep me logged in & Need help? row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { keepLoggedIn = !keepLoggedIn }
                        ) {
                            Checkbox(
                                checked = keepLoggedIn,
                                onCheckedChange = { keepLoggedIn = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AgriPrimaryDarkGreen,
                                    uncheckedColor = AgriTextSecondary
                                )
                            )
                            Text(
                                text = "Keep me logged in",
                                fontSize = 13.sp,
                                color = AgriTextPrimary
                            )
                        }

                        Text(
                            text = "Need help?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgriPrimaryDarkGreen,
                            modifier = Modifier.clickable {
                                errorMessage = "Please contact your warehouse administrator for login support."
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Login Button (56.dp, 16.dp corners)
                    Button(
                        onClick = {
                            if (!isFormValid) return@Button
                            isLoading = true
                            errorMessage = null
                            onLoginClick(employeeId, password, keepLoggedIn) { result ->
                                isLoading = false
                                result.onFailure {
                                    errorMessage = it.message ?: "Sign in failed"
                                }
                            }
                        },
                        enabled = isFormValid && !isLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AgriPrimaryDarkGreen,
                            contentColor = AgriSurfaceWhite,
                            disabledContainerColor = Color(0xFFE0E3E0),
                            disabledContentColor = Color(0xFF8E938E)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("login_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = AgriSurfaceWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Login",
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
}
