package com.aicardgrader.app.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.aicardgrader.app.grading.standardCardGuideRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class CaptureSide(val label: String, val instructions: String) {
    FRONT("Front of card", "Align the front of the card inside the guide, face up, in even lighting."),
    BACK("Back of card", "Flip the card over and align the back inside the guide.")
}

@Composable
fun CardCaptureScreen(
    side: CaptureSide,
    onCaptured: (Bitmap) -> Unit,
    onSkipBack: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingImport by remember { mutableStateOf<Bitmap?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    pendingImport = withContext(Dispatchers.IO) { decodeImportedImage(context, uri) }
                } catch (t: Throwable) {
                    importError = "Could not load that photo: ${t.message}"
                }
            }
        }
    }
    val launchPicker = { pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val importing = pendingImport
    if (importing != null) {
        ImportAdjustScreen(
            source = importing,
            title = "Adjust ${side.label.lowercase()}",
            onConfirm = { cropped ->
                pendingImport = null
                onCaptured(cropped)
            },
            onCancel = { pendingImport = null }
        )
        return
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            CameraContent(
                side = side,
                onCaptured = onCaptured,
                onSkipBack = onSkipBack,
                onClose = onClose,
                onImportClick = launchPicker
            )
        } else {
            PermissionRequest(
                onClose = onClose,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onImportClick = launchPicker
            )
        }

        importError?.let {
            Text(
                it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Red.copy(alpha = 0.6f))
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun PermissionRequest(onClose: () -> Unit, onRequest: () -> Unit, onImportClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera access needed", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "AI Card Grader needs camera access to photograph your card for grading. Photos stay on your device.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Allow camera access") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onImportClick) { Text("Or import a photo instead") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClose) { Text("Cancel") }
    }
}

@Composable
private fun CameraContent(
    side: CaptureSide,
    onCaptured: (Bitmap) -> Unit,
    onSkipBack: (() -> Unit)?,
    onClose: () -> Unit,
    onImportClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        try {
            imageCapture = bindCameraPreview(context, lifecycleOwner, previewView)
        } catch (t: Throwable) {
            errorMessage = "Could not start camera: ${t.message}"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        CaptureGuideOverlay(modifier = Modifier.fillMaxSize())

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
        }

        IconButton(
            onClick = onImportClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Import a photo instead", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(side.label, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(side.instructions, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        }

        errorMessage?.let {
            Text(
                it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Red.copy(alpha = 0.6f))
                    .padding(16.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isCapturing) {
                CircularProgressIndicator(color = Color.White)
            } else {
                ShutterButton(enabled = imageCapture != null) {
                    val capture = imageCapture ?: return@ShutterButton
                    isCapturing = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val tempFile = File.createTempFile("capture_", ".jpg", context.cacheDir)
                            val bitmap = capturePhotoToBitmap(context, capture, tempFile)
                            tempFile.delete()
                            onCaptured(bitmap)
                        } catch (t: Throwable) {
                            errorMessage = "Capture failed: ${t.message}"
                        } finally {
                            isCapturing = false
                        }
                    }
                }
            }
            if (onSkipBack != null && !isCapturing) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onSkipBack) {
                    Text("Skip back photo", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .background(Color.White.copy(alpha = if (enabled) 0.95f else 0.4f), CircleShape)
            .padding(6.dp)
            .background(Color.Transparent, CircleShape)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White, CircleShape)
        ) {}
    }
}

@Composable
private fun CaptureGuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val guide = standardCardGuideRect(size.width.toInt(), size.height.toInt())
        val left = guide.left.toFloat()
        val top = guide.top.toFloat()
        val right = guide.right.toFloat()
        val bottom = guide.bottom.toFloat()
        val dim = Color.Black.copy(alpha = 0.45f)

        // Dim everything outside the guide window using four opaque strips
        // (avoids needing a compositing layer for a true "punch a hole" clear).
        drawRect(color = dim, topLeft = Offset(0f, 0f), size = Size(size.width, top))
        drawRect(color = dim, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(color = dim, topLeft = Offset(0f, top), size = Size(left, bottom - top))
        drawRect(color = dim, topLeft = Offset(right, top), size = Size(size.width - right, bottom - top))

        drawRect(
            color = Color(0xFFFFC94A),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
            )
        )
    }
}
