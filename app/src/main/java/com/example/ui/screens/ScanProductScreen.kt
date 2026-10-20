package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camera.CameraManager
import com.example.cv.DetectedProductItem
import com.example.cv.DetectionResult
import com.example.cv.YOLODetector
import com.example.ui.theme.AgriBorder
import com.example.ui.theme.AgriLightGreen
import com.example.ui.theme.AgriPrimaryDarkGreen
import com.example.ui.theme.AgriSurfaceWhite
import com.example.ui.theme.AgriTextPrimary
import com.example.ui.theme.AgriTextSecondary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SUGGESTION_MIN_CONFIDENCE = 0.08f

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanProductScreen(
    onBackClick: () -> Unit,
    onSaveScanBatch: (List<com.example.data.entities.ScannedItemPayload>) -> Unit = {},
    onSaveScan: ((String, String, String, Int, Int, Float) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    val yoloDetector = remember { YOLODetector(context) }
    val cameraManager = remember { CameraManager(context) }

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectionResult by remember { mutableStateOf<DetectionResult?>(null) }
    var productQuantities by remember { mutableStateOf<List<Int>>(emptyList()) }
    var editingProductIndex by remember { mutableIntStateOf(-1) }

    val activeCount = detectionResult?.boundingBoxes?.size ?: 0

    var selectedQty by remember { mutableIntStateOf(0) }
    var isScanning by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var showLabelCorrectionDialog by remember { mutableStateOf(false) }

    var selectedFilterProductName by remember { mutableStateOf<String?>(null) }

    // Bottom sheet expansion state & drag tracking
    var isSheetExpanded by remember { mutableStateOf(true) }
    var sheetDragOffsetY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.stopCamera()
        }
    }

    BackHandler {
        if (showLabelCorrectionDialog) {
            showLabelCorrectionDialog = false
        } else if (editingProductIndex != -1) {
            editingProductIndex = -1
        } else if (capturedBitmap != null || detectionResult != null) {
            capturedBitmap = null
            detectionResult = null
            productQuantities = emptyList()
            editingProductIndex = -1
            selectedFilterProductName = null
            isSheetExpanded = true
            sheetDragOffsetY = 0f
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1713))
    ) {
        // ==========================================
        // LAYER 1: FULL-SCREEN CAMERA VIEWPORT & REAL-TIME OVERLAY
        // ==========================================
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (capturedBitmap != null) {
                // Captured/Frozen Still Image Display (Full Screen)
                Image(
                    bitmap = capturedBitmap!!.asImageBitmap(),
                    contentDescription = "Captured Frame",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (cameraPermissionState.status.isGranted) {
                // CameraX Continuous Live Stream Viewport
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        cameraManager.startLiveDetectionCamera(
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,
                            yoloDetector = yoloDetector,
                            onDetectionResult = { liveRes ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    if (capturedBitmap == null) {
                                        detectionResult = liveRes
                                        productQuantities = liveRes.detectedProducts.map { it.quantity }
                                        selectedQty = liveRes.quantity
                                    }
                                }
                            }
                        )
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Camera Permission Needed State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AgriLightGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Camera Access",
                            tint = AgriLightGreen,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Camera Permission Required",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Agri-Stock requires live camera access to scan and detect warehouse products in real time.",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AgriLightGreen,
                            contentColor = AgriPrimaryDarkGreen
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Grant Camera Access", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Continuous Green Bounding Box & Badge Overlay
            val activeBoxes = detectionResult?.boundingBoxes ?: emptyList()
            if (activeBoxes.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    activeBoxes.forEach { box ->
                        val origW = box.origWidth.toFloat().coerceAtLeast(1f)
                        val origH = box.origHeight.toFloat().coerceAtLeast(1f)

                        val scale = maxOf(size.width / origW, size.height / origH)
                        val scaledW = origW * scale
                        val scaledH = origH * scale

                        val offsetX = (size.width - scaledW) / 2f
                        val offsetY = (size.height - scaledH) / 2f

                        val left = box.rect.left * scale + offsetX
                        val top = box.rect.top * scale + offsetY
                        val width = box.rect.width() * scale
                        val height = box.rect.height() * scale

                        // Semi-transparent green highlight fill
                        drawRect(
                            color = Color(0x333DDC84),
                            topLeft = Offset(left, top),
                            size = Size(width, height)
                        )

                        // Green Bounding Box outline
                        drawRect(
                            color = Color(0xFF3DDC84),
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Label & Confidence Badge overlay
                        val text = "${box.label} ${(box.confidence * 100).toInt()}%"
                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 12.dp.toPx()
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val bgPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#CC1B5E20")
                            style = android.graphics.Paint.Style.FILL
                        }
                        val textWidth = textPaint.measureText(text)
                        val textHeight = 14.dp.toPx()
                        val tagPadding = 4.dp.toPx()

                        val tagLeft = left.coerceIn(0f, (size.width - textWidth - tagPadding * 2).coerceAtLeast(0f))
                        val tagTop = (top - textHeight - tagPadding * 2).coerceIn(0f, (size.height - textHeight - tagPadding * 2).coerceAtLeast(0f))
                        val tagRight = tagLeft + textWidth + tagPadding * 2
                        val tagBottom = tagTop + textHeight + tagPadding * 2

                        drawContext.canvas.nativeCanvas.drawRoundRect(
                            tagLeft, tagTop, tagRight, tagBottom, 6.dp.toPx(), 6.dp.toPx(), bgPaint
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            text, tagLeft + tagPadding, tagBottom - tagPadding - 1.dp.toPx(), textPaint
                        )
                    }
                }
            }
        }

        // ==========================================
        // LAYER 2: TOP FLOATING HUD
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp)
        ) {
            // Floating Header Bar (Dark Glass Overlay)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button (Floating Circle)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable {
                            if (showLabelCorrectionDialog) {
                                showLabelCorrectionDialog = false
                            } else if (editingProductIndex != -1) {
                                editingProductIndex = -1
                            } else if (capturedBitmap != null || detectionResult != null) {
                                capturedBitmap = null
                                detectionResult = null
                                productQuantities = emptyList()
                                editingProductIndex = -1
                                selectedFilterProductName = null
                                isSheetExpanded = true
                                sheetDragOffsetY = 0f
                            } else {
                                onBackClick()
                            }
                        }
                        .testTag("back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Center Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AgriPrimaryDarkGreen.copy(alpha = 0.85f))
                        .border(1.dp, AgriLightGreen.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (capturedBitmap != null) Color(0xFFFFB74D) else AgriLightGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (capturedBitmap != null) {
                                if (activeCount > 0) "$activeCount Detected" else "Review Frame"
                            } else {
                                "OpenCV & YOLOv8 Live Camera"
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Flashlight / Torch Button (Floating Circle)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isTorchEnabled) Color(0xFFFFD600).copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.55f))
                        .border(
                            1.dp,
                            if (isTorchEnabled) Color(0xFFFFD600) else Color.White.copy(alpha = 0.15f),
                            CircleShape
                        )
                        .clickable {
                            isTorchEnabled = !isTorchEnabled
                            cameraManager.toggleTorch(isTorchEnabled)
                        }
                        .testTag("torch_toggle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Torch",
                        tint = if (isTorchEnabled) Color(0xFFFFD600) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ==========================================
        // LAYER 3: BOTTOM CONTROLS / DRAGGABLE STAFF REVIEW SHEET
        // ==========================================
        if (capturedBitmap == null) {
            // ------------------------------------------
            // LIVE CAMERA BOTTOM CONTROLS DOCK
            // ------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Center Helper Guidance Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Align products in frame & tap SNAP to detect",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Floating Action Dock (Live Camera Shutter)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(36.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Main SNAP / Shutter Button (Prominent Glowing Shutter)
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(2.5.dp, AgriLightGreen, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(AgriLightGreen)
                            .clickable(enabled = !isScanning) {
                                isScanning = true
                                coroutineScope.launch {
                                    val frame = cameraManager.getLatestFrame()
                                    if (frame == null) {
                                        android.widget.Toast.makeText(context, "Camera not ready", android.widget.Toast.LENGTH_SHORT).show()
                                        isScanning = false
                                        return@launch
                                    }
                                    val frameCopy = frame.copy(Bitmap.Config.ARGB_8888, false)
                                    capturedBitmap = frameCopy
                                    val res = withContext(Dispatchers.Default) {
                                        yoloDetector.detect(frameCopy)
                                    }
                                    detectionResult = res
                                    productQuantities = res.detectedProducts.map { it.quantity }
                                    selectedQty = res.quantity
                                    isSheetExpanded = true
                                    sheetDragOffsetY = 0f
                                    isScanning = false
                                }
                            }
                            .testTag("scan_product_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = AgriPrimaryDarkGreen,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "SNAP",
                                tint = AgriPrimaryDarkGreen,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // ------------------------------------------
            // POST-CAPTURE: REAL DRAGGABLE STAFF REVIEW BOTTOM SHEET
            // ------------------------------------------
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter)
            ) {
                val screenMaxHeight = this.maxHeight
                val collapsedHeight = 110.dp
                val expandedHeight = (screenMaxHeight * 0.72f).coerceAtLeast(360.dp)

                // Drag gesture calculation
                val dragDeltaDp = with(density) { sheetDragOffsetY.toDp() }
                val targetBaseHeight = if (isSheetExpanded) expandedHeight else collapsedHeight
                val currentTargetHeight = (targetBaseHeight - dragDeltaDp).coerceIn(collapsedHeight, screenMaxHeight * 0.85f)

                val animatedSheetHeight by animateDpAsState(
                    targetValue = currentTargetHeight,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "sheetHeightAnim"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(animatedSheetHeight)
                        .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(AgriSurfaceWhite)
                        .testTag("staff_review_bottom_sheet")
                ) {
                    // ==========================================
                    // DRAGGABLE HANDLE & HEADER AREA
                    // ==========================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(isSheetExpanded) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (sheetDragOffsetY > 60f) {
                                            isSheetExpanded = false
                                        } else if (sheetDragOffsetY < -60f) {
                                            isSheetExpanded = true
                                        }
                                        sheetDragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        sheetDragOffsetY = 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        sheetDragOffsetY += dragAmount
                                    }
                                )
                            }
                            .clickable {
                                isSheetExpanded = !isSheetExpanded
                                sheetDragOffsetY = 0f
                            }
                            .padding(top = 12.dp, bottom = 8.dp, start = 20.dp, end = 20.dp)
                    ) {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 48.dp, height = 5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFB0BEC5))
                                    .testTag("bottom_sheet_drag_handle")
                            )
                        }

                        // Header Bar: Title, Count Badge & Chevron
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column {
                                    Text(
                                        text = "STAFF REVIEW DETECTED STACK",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AgriPrimaryDarkGreen,
                                        letterSpacing = 0.6.sp
                                    )
                                    Text(
                                        text = if (isSheetExpanded) {
                                            "Swipe down to view camera • Edit counts below"
                                        } else {
                                            "Swipe or tap up to review detected items"
                                        },
                                        fontSize = 11.sp,
                                        color = AgriTextSecondary
                                    )
                                }
                            }

                            val rawCount = detectionResult?.rawDetections?.size ?: 0
                            val prodCount = detectionResult?.detectedProducts?.size ?: 0
                            val totalActiveQty = productQuantities.sum()
                            val activeProdCount = productQuantities.count { it > 0 }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AgriLightGreen)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (productQuantities.isNotEmpty()) {
                                            "$totalActiveQty Detected • $activeProdCount Products"
                                        } else {
                                            "$rawCount Detected • $prodCount Products"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgriPrimaryDarkGreen
                                    )
                                }

                                Icon(
                                    imageVector = if (isSheetExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                    contentDescription = if (isSheetExpanded) "Collapse Sheet" else "Expand Sheet",
                                    tint = AgriPrimaryDarkGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // ==========================================
                    // EXPANDED BODY
                    // ==========================================
                    if (animatedSheetHeight > 130.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 20.dp)
                        ) {
                            val result = detectionResult
                            val products = result?.detectedProducts ?: emptyList()

                            if (products.isNotEmpty()) {
                                // Dynamic Detected Product Filters
                                val dynamicOptions = listOf("All Detections (${products.size})") + products.map { it.productName }.distinct()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    dynamicOptions.forEachIndexed { idx, option ->
                                        val isAll = idx == 0
                                        val isSelected = if (isAll) selectedFilterProductName == null else selectedFilterProductName == option
                                        val chipBg = if (isSelected) AgriPrimaryDarkGreen else AgriLightGreen.copy(alpha = 0.35f)
                                        val chipTextColor = if (isSelected) AgriSurfaceWhite else AgriPrimaryDarkGreen

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(chipBg)
                                                .border(1.dp, if (isSelected) AgriPrimaryDarkGreen else AgriBorder, RoundedCornerShape(16.dp))
                                                .clickable {
                                                    selectedFilterProductName = if (isAll) null else option
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                                .testTag("detected_filter_chip_$idx")
                                        ) {
                                            Text(
                                                text = option,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = chipTextColor
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Scrollable List / Helper & Suggestion Card
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (products.isEmpty()) {
                                    // Requirement 2: Helper text when nothing is detected above 0.20
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp, horizontal = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Nothing recognised. Move closer, show the front label of the bag, and use good light.",
                                            fontSize = 14.sp,
                                            color = AgriTextSecondary,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 20.sp
                                        )

                                        // Requirement 2: Candidate suggestion card if topScore in 0.08..0.20
                                        val topScore = yoloDetector.lastTopScore
                                        val topClass = yoloDetector.lastTopClass
                                        val topClassId = yoloDetector.lastTopClassId

                                        if (topScore >= SUGGESTION_MIN_CONFIDENCE && topScore < com.example.detection.YOLODetector.DEFAULT_CONFIDENCE_THRESHOLD && topClassId >= 0) {
                                            val catalogItem = com.example.data.ProductCatalog.resolve(label = topClass, classIndex = topClassId)
                                            val candidateName = catalogItem.productName
                                            val scorePercent = (topScore * 100).toInt()

                                            Spacer(modifier = Modifier.height(20.dp))

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(AgriLightGreen)
                                                    .border(1.dp, AgriBorder, RoundedCornerShape(16.dp))
                                                    .padding(16.dp)
                                            ) {
                                                Text(
                                                    text = "Possible: $candidateName ($scorePercent%). Tap to add",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AgriPrimaryDarkGreen
                                                )

                                                Spacer(modifier = Modifier.height(10.dp))

                                                Button(
                                                    onClick = {
                                                        val newItem = DetectedProductItem(
                                                            productId = catalogItem.productId,
                                                            sku = catalogItem.sku,
                                                            productName = catalogItem.productName,
                                                            categoryId = catalogItem.categoryId,
                                                            category = catalogItem.category,
                                                            uomLabel = catalogItem.uomLabel,
                                                            unit = catalogItem.unit,
                                                            reorderPoint = catalogItem.reorderPoint,
                                                            confidence = topScore,
                                                            quantity = 1,
                                                            classIndex = catalogItem.classIndex,
                                                            label = catalogItem.yoloLabel
                                                        )
                                                        val updatedProducts = (detectionResult?.detectedProducts ?: emptyList()) + newItem
                                                        val updatedQuantities = productQuantities + 1
                                                        detectionResult = (detectionResult ?: DetectionResult(
                                                            productName = catalogItem.productName,
                                                            sku = catalogItem.sku,
                                                            confidence = topScore,
                                                            quantity = 1
                                                        )).copy(
                                                            detectedProducts = updatedProducts,
                                                            quantity = updatedQuantities.sum()
                                                        )
                                                        productQuantities = updatedQuantities
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = AgriPrimaryDarkGreen,
                                                        contentColor = AgriSurfaceWhite
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                ) {
                                                    Text(text = "Add 1", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val indexedProducts = products.mapIndexed { origIdx, item -> Pair(origIdx, item) }
                                        .filter { (_, item) -> selectedFilterProductName == null || item.productName == selectedFilterProductName }

                                    indexedProducts.forEach { (index, productItem) ->
                                        val currentQty = productQuantities.getOrElse(index) { productItem.quantity }
                                        val isExcluded = currentQty == 0

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isExcluded) Color(0xFFF3F5F4) else Color(0xFFF7FBF8))
                                                .border(
                                                    1.dp,
                                                    if (isExcluded) Color(0xFFDCDCDC) else AgriBorder,
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .padding(12.dp)
                                        ) {
                                            // Product Name + Action Buttons Row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = productItem.productName,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isExcluded) Color(0xFF757575) else AgriTextPrimary
                                                        )
                                                        if (isExcluded) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(Color(0xFFFFEBEE))
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "EXCLUDED",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFFD32F2F)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = "SKU: ${productItem.sku} • ${(productItem.confidence * 100).toInt()}% Conf",
                                                        fontSize = 11.sp,
                                                        color = if (isExcluded) Color(0xFF9E9E9E) else AgriTextSecondary
                                                    )
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = {
                                                            val newList = productQuantities.toMutableList()
                                                            if (index < newList.size) {
                                                                newList[index] = if (isExcluded) maxOf(1, productItem.quantity) else 0
                                                                productQuantities = newList
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .testTag("toggle_exclude_button_${index}")
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isExcluded) Icons.Default.Refresh else Icons.Default.Delete,
                                                            contentDescription = if (isExcluded) "Restore Item" else "Exclude Item",
                                                            tint = if (isExcluded) AgriPrimaryDarkGreen else Color(0xFFD32F2F),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            editingProductIndex = index
                                                            showLabelCorrectionDialog = true
                                                        },
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .testTag("correct_label_button_${index}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Correct Label",
                                                            tint = AgriPrimaryDarkGreen,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Stepper Row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Quantity Detected:",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isExcluded) Color(0xFF9E9E9E) else AgriTextSecondary
                                                )

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(30.dp)
                                                            .clip(CircleShape)
                                                            .background(if (currentQty > 0) AgriLightGreen else Color(0xFFE0E0E0))
                                                            .clickable(enabled = currentQty > 0) {
                                                                if (currentQty > 0) {
                                                                    val newList = productQuantities.toMutableList()
                                                                    if (index < newList.size) {
                                                                        newList[index] = currentQty - 1
                                                                        productQuantities = newList
                                                                    }
                                                                }
                                                            }
                                                            .testTag("qty_decrease_button_${index}"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (currentQty == 1) Icons.Default.Delete else Icons.Default.Remove,
                                                            contentDescription = if (currentQty == 1) "Remove" else "Decrease",
                                                            tint = if (currentQty == 1) Color(0xFFD32F2F) else if (currentQty > 1) AgriPrimaryDarkGreen else Color(0xFF9E9E9E),
                                                            modifier = Modifier.size(if (currentQty == 1) 16.dp else 14.dp)
                                                        )
                                                    }

                                                    Text(
                                                        text = if (currentQty == 0) "0 ${productItem.unit} (Excluded)" else "$currentQty ${productItem.unit}",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isExcluded) Color(0xFF888888) else AgriTextPrimary
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .size(30.dp)
                                                            .clip(CircleShape)
                                                            .background(AgriLightGreen)
                                                            .clickable {
                                                                val newList = productQuantities.toMutableList()
                                                                if (index < newList.size) {
                                                                    newList[index] = currentQty + 1
                                                                    productQuantities = newList
                                                                }
                                                            }
                                                            .testTag("qty_increase_button_${index}"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = "Increase",
                                                            tint = AgriPrimaryDarkGreen,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Bottom Sticky Action Buttons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        capturedBitmap = null
                                        detectionResult = null
                                        productQuantities = emptyList()
                                        editingProductIndex = -1
                                        selectedFilterProductName = null
                                        isSheetExpanded = true
                                        sheetDragOffsetY = 0f
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("retake_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AgriLightGreen,
                                        contentColor = AgriPrimaryDarkGreen
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Retake",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "RETAKE",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                val hasActiveItems = if (detectionResult?.detectedProducts.isNullOrEmpty()) {
                                    selectedQty > 0
                                } else {
                                    productQuantities.any { it > 0 }
                                }

                                Button(
                                    onClick = {
                                        if (isSaving || !hasActiveItems) return@Button
                                        val res = detectionResult ?: return@Button
                                        isSaving = true

                                        val itemsToSave: List<com.example.data.entities.ScannedItemPayload> = if (res.detectedProducts.isNotEmpty()) {
                                            res.detectedProducts.mapIndexedNotNull { idx, item ->
                                                val finalQty = productQuantities.getOrElse(idx) { item.quantity }
                                                if (finalQty <= 0) {
                                                    null
                                                } else {
                                                    val detectedQty = maxOf(1, item.quantity)
                                                    val catalogItem = com.example.data.ProductCatalog.resolve(
                                                        label = item.label,
                                                        classIndex = item.classIndex,
                                                        nameOrSku = if (item.productName.isNotBlank()) item.productName else item.sku
                                                    )
                                                    com.example.data.entities.ScannedItemPayload(
                                                        productId = catalogItem.productId,
                                                        sku = catalogItem.sku,
                                                        productName = catalogItem.productName,
                                                        categoryId = catalogItem.categoryId,
                                                        category = catalogItem.category,
                                                        uomLabel = catalogItem.uomLabel,
                                                        unit = catalogItem.unit,
                                                        reorderPoint = catalogItem.reorderPoint,
                                                        detectedQuantity = detectedQty,
                                                        finalQuantity = finalQty,
                                                        confidence = item.confidence
                                                    )
                                                }
                                            }
                                        } else {
                                            val validSelectedQty = selectedQty
                                            if (validSelectedQty <= 0) {
                                                emptyList()
                                            } else {
                                                val catalogItem = com.example.data.ProductCatalog.resolve(
                                                    label = "",
                                                    classIndex = -1,
                                                    nameOrSku = if (res.productName.isNotBlank()) res.productName else res.sku
                                                )
                                                val validDetectedQty = maxOf(1, res.quantity)
                                                listOf(
                                                    com.example.data.entities.ScannedItemPayload(
                                                        productId = catalogItem.productId,
                                                        sku = catalogItem.sku,
                                                        productName = catalogItem.productName,
                                                        categoryId = catalogItem.categoryId,
                                                        category = catalogItem.category,
                                                        uomLabel = catalogItem.uomLabel,
                                                        unit = catalogItem.unit,
                                                        reorderPoint = catalogItem.reorderPoint,
                                                        detectedQuantity = validDetectedQty,
                                                        finalQuantity = validSelectedQty,
                                                        confidence = res.confidence
                                                    )
                                                )
                                            }
                                        }

                                        if (itemsToSave.isEmpty()) {
                                            isSaving = false
                                            return@Button
                                        }

                                        onSaveScanBatch(itemsToSave)
                                        if (onSaveScan != null && itemsToSave.isNotEmpty()) {
                                            val first = itemsToSave.first()
                                            onSaveScan(first.sku, first.productName, first.unit, first.detectedQuantity, first.finalQuantity, first.confidence)
                                        }
                                    },
                                    enabled = !isSaving && hasActiveItems,
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(50.dp)
                                        .testTag("confirm_save_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AgriPrimaryDarkGreen,
                                        contentColor = AgriSurfaceWhite,
                                        disabledContainerColor = AgriPrimaryDarkGreen.copy(alpha = 0.4f),
                                        disabledContentColor = AgriSurfaceWhite.copy(alpha = 0.7f)
                                    )
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(
                                            color = AgriSurfaceWhite,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "SAVING...",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (hasActiveItems) "CONFIRM & SAVE" else "NO ITEMS (ALL 0)",
                                                fontSize = 14.sp,
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
        }

        // ==========================================
        // SCANNING / INFERENCE LOADING OVERLAY
        // ==========================================
        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(AgriSurfaceWhite)
                        .padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = AgriPrimaryDarkGreen,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Processing YOLOv8 Inference...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgriTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Running OpenCV & TFLite detection",
                        fontSize = 12.sp,
                        color = AgriTextSecondary
                    )
                }
            }
        }

        // ==========================================
        // PRODUCT LABEL CORRECTION DIALOG
        // ==========================================
        if (showLabelCorrectionDialog && detectionResult != null && editingProductIndex >= 0) {
            val products = detectionResult!!.detectedProducts
            if (editingProductIndex < products.size) {
                val currentItem = products[editingProductIndex]
                val availableProducts = com.example.data.ProductCatalog.PRIMARY_PRODUCTS

                AlertDialog(
                    onDismissRequest = { showLabelCorrectionDialog = false },
                    title = {
                        Text(
                            text = "Select Correct Product Record",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgriTextPrimary
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            availableProducts.forEach { catItem ->
                                val isSelected = currentItem.productId == catItem.productId || currentItem.sku == catItem.sku
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AgriLightGreen else Color.Transparent)
                                        .clickable {
                                            val updatedList = products.toMutableList()
                                            updatedList[editingProductIndex] = currentItem.copy(
                                                productId = catItem.productId,
                                                sku = catItem.sku,
                                                productName = catItem.productName,
                                                category = catItem.category,
                                                uomLabel = catItem.uomLabel,
                                                unit = catItem.uomLabel,
                                                classIndex = catItem.classIndex,
                                                label = catItem.yoloLabel
                                            )
                                            detectionResult = detectionResult!!.copy(detectedProducts = updatedList)
                                            showLabelCorrectionDialog = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = catItem.productName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = AgriTextPrimary
                                        )
                                        Text(
                                            text = "${catItem.productId} • ${catItem.sku}",
                                            fontSize = 11.sp,
                                            color = AgriTextSecondary
                                        )
                                    }
                                    Text(
                                        text = catItem.uomLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AgriPrimaryDarkGreen
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLabelCorrectionDialog = false }) {
                            Text("Cancel", color = AgriPrimaryDarkGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }
}
