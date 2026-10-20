package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    onLoginClick: (String, String, (Result<Unit>) -> Unit) -> Unit
) {
    var employeeId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    GridBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AgriSurfaceWhite)
                    .border(1.dp, AgriBorder, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AgriStock",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgriPrimaryDarkGreen
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "WAREHOUSE STAFF ACCESS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgriTextSecondary,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sign in with your Employee ID and password to scan inventory and check stock levels. Don't have an account? Ask your administrator to create one for you.",
                    fontSize = 13.sp,
                    color = AgriTextSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AgriErrorBg)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = AgriErrorText,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = employeeId,
                    onValueChange = {
                        employeeId = it
                        errorMessage = null
                    },
                    label = { Text("Employee ID (e.g. EMP-001)") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = AgriTextSecondary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("employee_id_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgriPrimaryDarkGreen,
                        unfocusedBorderColor = AgriBorder
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = AgriTextSecondary)
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgriPrimaryDarkGreen,
                        unfocusedBorderColor = AgriBorder
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (employeeId.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter both Employee ID and password."
                            return@Button
                        }
                        isLoading = true
                        errorMessage = null
                        onLoginClick(employeeId, password) { result ->
                            isLoading = false
                            result.onFailure {
                                errorMessage = it.message ?: "Sign in failed"
                            }
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgriPrimaryDarkGreen,
                        contentColor = AgriSurfaceWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AgriSurfaceWhite,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Sign In",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
