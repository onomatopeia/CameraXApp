package com.migalska.cameraxapp

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.migalska.cameraxapp.ui.theme.CameraXAppTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    private lateinit var cameraExecutor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        cameraExecutor = Executors.newSingleThreadExecutor()
        setContent {
            CameraXAppTheme {
                val imageCaptureUseCase = remember {
                    ImageCapture.Builder().build()
                }
                Box (modifier = Modifier.fillMaxSize()) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        imageCaptureUseCase = imageCaptureUseCase,
                    )
                    IconButton(
                        onClick = { captureImage(imageCaptureUseCase) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .size(96.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.capture_icon),
                            contentDescription = "",
                        )
                    }
                }
            }
        }
    }



    private fun captureImage(imageCaptureUseCase: ImageCapture) {
        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME, java.util.Locale.US)
            .format(System.currentTimeMillis())
        println("name $name")
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, PHOTO_TYPE)
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCaptureUseCase.takePicture(
            outputFileOptions,
            cameraExecutor,
            object: ImageCapture.OnImageSavedCallback{
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    println("the uri is ${outputFileResults.savedUri}")
                }

                override fun onError(exception: ImageCaptureException) {
                    println("Error $exception")
                }

            }
        )
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_TYPE = "image/jpeg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FILL_CENTER,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    imageCaptureUseCase: ImageCapture
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    AndroidView(
        modifier = modifier,
        factory = {context ->
        val previewView = PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            this.scaleType = scaleType
        }

        // bind preview view with the preview use case
        val previewUseCase = Preview.Builder().build()
        previewUseCase.setSurfaceProvider(previewView.surfaceProvider)

        // reference to the camera provider
        coroutineScope.launch {
            val cameraProvider = context.cameraProvider()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, previewUseCase, imageCaptureUseCase)
        }

        previewView
    })


}

suspend fun Context.cameraProvider(): ProcessCameraProvider = suspendCoroutine {
    val listenableFuture = ProcessCameraProvider.getInstance(this)
    listenableFuture.addListener({
        it.resume(listenableFuture.get())
    }, ContextCompat.getMainExecutor(this))
}