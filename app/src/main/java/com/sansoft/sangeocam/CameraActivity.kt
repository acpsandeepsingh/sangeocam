package com.sansoft.sangeocam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var captureButton: FloatingActionButton
    private lateinit var switchCameraButton: FloatingActionButton
    private lateinit var videoButton: FloatingActionButton
    private lateinit var recordingIndicator: TextView

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var currentLocation: Location? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isVideoMode = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) startCamera() else {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder = findViewById(R.id.viewFinder)
        captureButton = findViewById(R.id.captureButton)
        switchCameraButton = findViewById(R.id.switchCameraButton)
        videoButton = findViewById(R.id.videoButton)
        recordingIndicator = findViewById(R.id.recordingIndicator)

        val fused = LocationServices.getFusedLocationProviderClient(this)
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            fused.lastLocation.addOnSuccessListener { currentLocation = it }
        }

        if (allPermissionsGranted()) startCamera() else requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)

        captureButton.setOnClickListener {
            if (isVideoMode) {
                captureVideo()
            } else {
                takePhotoAndStamp()
            }
        }

        switchCameraButton.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }

        videoButton.setOnClickListener {
            isVideoMode = !isVideoMode
            updateUIForMode()
            startCamera()
        }
    }

    private fun updateUIForMode() {
        if (isVideoMode) {
            videoButton.setImageResource(R.drawable.ic_camera)
            captureButton.setImageResource(R.drawable.ic_videocam)
        } else {
            videoButton.setImageResource(R.drawable.ic_videocam)
            captureButton.setImageResource(R.drawable.ic_camera)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }
            provider.unbindAll()
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            if (isVideoMode) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                provider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
            } else {
                imageCapture = ImageCapture.Builder().build()
                provider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            return
        }

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SanGeoCam")
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (checkPermission(Manifest.permission.RECORD_AUDIO)) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        recordingIndicator.visibility = View.VISIBLE
                        captureButton.setImageResource(R.drawable.ic_stop)
                    }
                    is VideoRecordEvent.Finalize -> {
                        recordingIndicator.visibility = View.GONE
                        captureButton.setImageResource(R.drawable.ic_videocam)
                        if (!recordEvent.hasError()) {
                            Toast.makeText(this, "Video saved!", Toast.LENGTH_SHORT).show()
                            // Save video metadata to database
                            currentLocation?.let { loc ->
                                MediaDatabase.getInstance(this).mediaDao().insert(
                                    MediaItem(
                                        filePath = recordEvent.outputResults.outputUri.toString(),
                                        latitude = loc.latitude,
                                        longitude = loc.longitude,
                                        timestamp = System.currentTimeMillis(),
                                        type = "video"
                                    )
                                )
                            }
                        }
                    }
                }
            }
    }

    private fun takePhotoAndStamp() {
        val capture = imageCapture ?: return
        val tempFile = File(cacheDir, "temp.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(tempFile).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                    val bmp = BitmapFactory.decodeFile(tempFile.absolutePath)
                    if (bmp == null) {
                        Toast.makeText(this@CameraActivity, "Failed to decode photo", Toast.LENGTH_SHORT).show()
                        tempFile.delete()
                        return
                    }
                    val stamped = addGeoTagOverlay(bmp, currentLocation, getAddressSync())
                    val uri = saveStampedBitmap(stamped)
                    tempFile.delete()

                    // Save to database
                    currentLocation?.let { loc ->
                        MediaDatabase.getInstance(this@CameraActivity).mediaDao().insert(
                            MediaItem(
                                filePath = uri.toString(),
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                timestamp = System.currentTimeMillis(),
                                type = "photo"
                            )
                        )
                    }

                    Toast.makeText(this@CameraActivity, "Photo stamped & saved", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun addGeoTagOverlay(bmp: Bitmap, loc: Location?, addr: String): Bitmap {
        val copy = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(copy)
        val overlayHeight = (copy.height * 0.2f).toInt()
        val overlayTop = copy.height - overlayHeight

        val rectPaint = Paint().apply { color = 0xAA000000.toInt() }
        canvas.drawRect(RectF(0f, overlayTop.toFloat(), copy.width.toFloat(), copy.height.toFloat()), rectPaint)

        val paint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = overlayHeight / 6f
            isAntiAlias = true
            setShadowLayer(4f, 2f, 2f, 0xFF000000.toInt())
        }

        canvas.drawText("SanGeoCam", 20f, overlayTop + 60f, paint)

        val lines = mutableListOf<String>().apply {
            add(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            loc?.let { add("📍 ${String.format("%.6f, %.6f", it.latitude, it.longitude)}") }
            if (addr.isNotEmpty()) add("🏠 $addr")
        }
        val lineSpacing = overlayHeight / (lines.size + 2)
        lines.forEachIndexed { i, line ->
            val y = overlayTop + 100f + (i * lineSpacing)
            canvas.drawText(line, 20f, y, paint)
        }
        return copy
    }

    private fun getAddressSync(): String {
        return try {
            currentLocation?.let {
                Geocoder(this).getFromLocation(it.latitude, it.longitude, 1)
                    ?.firstOrNull()?.getAddressLine(0) ?: ""
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun saveStampedBitmap(bmp: Bitmap): android.net.Uri {
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SanGeoCam")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        contentResolver.openOutputStream(uri)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return uri
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
    private fun checkPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
