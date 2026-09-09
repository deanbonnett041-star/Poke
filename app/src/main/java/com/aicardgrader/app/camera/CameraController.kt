package com.aicardgrader.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Longest side a captured photo is decoded to — plenty for grading analysis while keeping memory sane. */
private const val MAX_DECODE_DIMENSION = 2200

suspend fun bindCameraPreview(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView
): ImageCapture = suspendCoroutine { cont ->
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        try {
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
            cont.resume(imageCapture)
        } catch (t: Throwable) {
            cont.resumeWithException(t)
        }
    }, androidx.core.content.ContextCompat.getMainExecutor(context))
}

suspend fun capturePhotoToBitmap(context: Context, imageCapture: ImageCapture, tempFile: File): Bitmap =
    suspendCoroutine { cont ->
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
        imageCapture.takePicture(
            outputOptions,
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        cont.resume(decodeAndOrient(tempFile))
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            }
        )
    }

private fun decodeAndOrient(file: File): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)

    var sample = 1
    while ((bounds.outWidth / sample) > MAX_DECODE_DIMENSION || (bounds.outHeight / sample) > MAX_DECODE_DIMENSION) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
        ?: error("Could not decode captured photo")

    val rotationDegrees = try {
        val exif = ExifInterface(file.absolutePath)
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) {
        0
    }

    return if (rotationDegrees == 0) {
        decoded
    } else {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        rotated
    }
}
