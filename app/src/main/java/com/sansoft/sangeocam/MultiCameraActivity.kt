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
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MultiCameraActivity : AppCompatActivity() {

    private lateinit var frontCameraPreview: PreviewView
    private lateinit var rearCameraPreview: PreviewView
    private lateinit var switchFrontCamera: SwitchMaterial
    private lateinit var switchRearCamera: SwitchMaterial
    private lateinit var btnCapturePhoto: MaterialButton

    private var frontImageCapture: ImageCapture? = null
    private var rearImageCapture: ImageCapture? = null
    private var currentLocation: Location? = null

    private var isFrontCameraActive = false
    private var isRearCameraActive = false

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_camera)

        setupToolbar()
        initializeViews()
        setupClickListeners()

        val fused = LocationServices.getFusedLocationProviderClient(this)
        if (checkCameraPermissions()) {
            fused.lastLocation.addOnSuccessListener { currentLocation = it }
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Multi Camera"
    }

    private fun initializeViews() {
        frontCameraPreview = findViewById(R.id.frontCameraPreview)
        rearCameraPreview = findViewById(R.id.rearCameraPreview)
        switchFrontCamera = findViewById(R.id.switchFrontCamera)
        switchRearCamera = findViewById(R.id.switchRearCamera)
        btnCapturePhoto = findViewById(R.id.btnCapturePhoto)
    }

    private fun setupClickListeners() {
        switchFrontCamera.setOnCheckedChangeListener { _, isChecked ->
            isFrontCameraActive = isChecked
            if (isChecked) {
                frontCameraPreview.visibility = View.VISIBLE
                startCamera(CameraSelector.LENS_FACING_FRONT, frontCameraPreview, true)
            } else {
                frontCameraPreview.visibility = View.GONE
                frontImageCapture = null
            }
        }

        switchRearCamera.setOnCheckedChangeListener { _, isChecked ->
            isRearCameraActive = isChecked
            if (isChecked) {
                rearCameraPreview.visibility = View.VISIBLE
                startCamera(CameraSelector.LENS_FACING_BACK, rearCameraPreview, false)
            } else {
                rearCameraPreview.visibility = View.GONE
                rearImageCapture = null
            }
        }

        btnCapturePhoto.setOnClickListener {
            capturePhotos()
        }
    }

    private fun startCamera(lensFacing: Int, previewView: PreviewView, isFront: Boolean) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageCapture = ImageCapture.Builder().build()

            if (isFront) {
                frontImageCapture = imageCapture
            } else {
                rearImageCapture = imageCapture
            }

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("MultiCamera", "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhotos() {
        if (!isFrontCameraActive && !isRearCameraActive) {
            Toast.makeText(this, "Enable at least one camera", Toast.LENGTH_SHORT).show()
            return
        }

        if (isFrontCameraActive) {
            captureFromCamera(frontImageCapture, "Front")
        }

        if (isRearCameraActive) {
            captureFromCamera(rearImageCapture, "Rear")
        }
    }

    private fun captureFromCamera(imageCapture: ImageCapture?, cameraName: String) {
        if (imageCapture == null) return

        val tempFile = File(cacheDir, "temp_$cameraName.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(this@MultiCameraActivity, "$cameraName capture failed", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                    val bmp = BitmapFactory.decodeFile(tempFile.absolutePath)
                    if (bmp != null) {
                        val addr = getAddressSync()
                        val stamped = addGeoTagOverlay(bmp, currentLocation, addr, cameraName)
                        saveStampedBitmap(stamped, cameraName)
                        tempFile.delete()

                        Toast.makeText(this@MultiCameraActivity, "$cameraName photo saved", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    private fun addGeoTagOverlay(bmp: Bitmap, loc: Location?, addr: String, cameraName: String): Bitmap {
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

        canvas.drawText("SanGeoCam - $cameraName Camera", 20f, overlayTop + 60f, paint)

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

    private fun saveStampedBitmap(bmp: Bitmap, cameraName: String) {
        val name = "MultiCam_${cameraName}_" + SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SanGeoCam")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // Save to database
            currentLocation?.let { loc ->
                MediaDatabase.getInstance(this).mediaDao().insert(
                    MediaItem(
                        filePath = it.toString(),
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        timestamp = System.currentTimeMillis(),
                        type = "photo"
                    )
                )
            }
        }
    }

    private fun checkCameraPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
