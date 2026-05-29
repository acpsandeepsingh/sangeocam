package com.sansoft.sangeocam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var currentLocation: Location? = null
    private var currentAddress: String = ""

    private lateinit var recyclerViewFeatures: RecyclerView
    private lateinit var fabCamera: ExtendedFloatingActionButton
    private lateinit var fabVideo: FloatingActionButton
    private lateinit var featuresAdapter: FeaturesAdapter

    private lateinit var geocoder: Geocoder

    private val featuresList = listOf(
        Feature(
            FeatureType.TIMESTAMP_GEOTAG,
            "Timestamp & Geotag",
            "Add date, time and GPS coordinates",
            R.drawable.ic_timestamp
        ),
        Feature(
            FeatureType.MAP_VIEW,
            "Map View",
            "View photos on map",
            R.drawable.ic_map
        ),
        Feature(
            FeatureType.VIDEO_LOCATION,
            "Video GPS",
            "Record video with location",
            R.drawable.ic_video
        ),
        Feature(
            FeatureType.WEATHER,
            "Weather Info",
            "Add weather overlay",
            R.drawable.ic_weather_cloud
        ),
        Feature(
            FeatureType.OFFLINE_MAPS,
            "Offline Maps",
            "Cache maps for offline use",
            R.drawable.ic_offline
        ),
        Feature(
            FeatureType.MULTI_CAMERA,
            "Multi Camera",
            "Switch between cameras",
            R.drawable.ic_switch_camera
        ),
        Feature(
            FeatureType.SHARING,
            "Share Media",
            "Social media integration",
            R.drawable.ic_share
        )
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.values.all { it }
        if (allPermissionsGranted) {
            initializeLocationServices()
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions required for GPS Camera features", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupRecyclerView()
        setupClickListeners()
        requestPermissions()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdateDelayMillis(15000)
            .build()
    }

    private fun initializeViews() {
        recyclerViewFeatures = findViewById(R.id.recyclerViewFeatures)
        fabCamera = findViewById(R.id.fabCamera)
        fabVideo = findViewById(R.id.fabVideo)
    }

    private fun setupRecyclerView() {
        featuresAdapter = FeaturesAdapter(featuresList) { feature ->
            handleFeatureClick(feature)
        }
        recyclerViewFeatures.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = featuresAdapter
        }
    }

    private fun setupClickListeners() {
        fabCamera.setOnClickListener {
            if (hasRequiredPermissions()) {
                openCameraActivity()
            } else {
                requestPermissions()
            }
        }

        fabVideo.setOnClickListener {
            if (hasRequiredPermissions()) {
                openCameraActivity(isVideoMode = true)
            } else {
                requestPermissions()
            }
        }
    }

    private fun openCameraActivity(isVideoMode: Boolean = false) {
        val intent = Intent(this, CameraActivity::class.java)
        intent.putExtra("IS_VIDEO_MODE", isVideoMode)
        startActivity(intent)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>().apply {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            add(Manifest.permission.RECORD_AUDIO)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun initializeLocationServices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLocation = it
                    getAddressFromLocation(it)
                    Log.d("SanGeoCam", "Location updated: ${it.latitude}, ${it.longitude}")
                }
            }.addOnFailureListener {
                Log.e("SanGeoCam", "Failed to get location", it)
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            locationResult.lastLocation?.let { location ->
                currentLocation = location
                getAddressFromLocation(location)
                Log.d("SanGeoCam", "Location callback: ${location.latitude}, ${location.longitude}")
            }
        }
    }

    private fun getAddressFromLocation(location: Location) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                addresses?.firstOrNull()?.let { address ->
                    currentAddress = formatAddress(address)
                    Log.d("SanGeoCam", "Address resolved: $currentAddress")
                }
            } catch (e: Exception) {
                Log.e("SanGeoCam", "Error getting address", e)
                currentAddress = "Address not available"
            }
        }
    }

    private fun formatAddress(address: Address): String {
        val addressLine = address.getAddressLine(0) ?: ""
        val locality = address.locality ?: ""
        val adminArea = address.adminArea ?: ""
        val countryName = address.countryName ?: ""

        return when {
            addressLine.isNotEmpty() -> addressLine
            locality.isNotEmpty() && adminArea.isNotEmpty() -> "$locality, $adminArea, $countryName"
            locality.isNotEmpty() -> "$locality, $countryName"
            adminArea.isNotEmpty() -> "$adminArea, $countryName"
            else -> countryName.ifEmpty { "Unknown Location" }
        }
    }

    private fun handleFeatureClick(feature: Feature) {
        when (feature.type) {
            FeatureType.TIMESTAMP_GEOTAG -> {
                if (hasRequiredPermissions()) {
                    openCameraActivity()
                } else {
                    Toast.makeText(this, "Camera and Location permissions needed", Toast.LENGTH_SHORT).show()
                    requestPermissions()
                }
            }
            FeatureType.MAP_VIEW -> {
                startActivity(Intent(this, MapViewActivity::class.java))
            }
            FeatureType.VIDEO_LOCATION -> {
                if (hasRequiredPermissions()) {
                    openCameraActivity(isVideoMode = true)
                } else {
                    Toast.makeText(this, "Camera, Location and Audio permissions needed", Toast.LENGTH_SHORT).show()
                    requestPermissions()
                }
            }
            FeatureType.WEATHER -> {
                fetchWeatherData()
            }
            FeatureType.OFFLINE_MAPS -> {
                startActivity(Intent(this, OfflineMapsActivity::class.java))
            }
            FeatureType.MULTI_CAMERA -> {
                startActivity(Intent(this, MultiCameraActivity::class.java))
            }
            FeatureType.SHARING -> {
                Toast.makeText(this, "Share feature: Take a photo first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchWeatherData() {
        currentLocation?.let { location ->
            Toast.makeText(
                this,
                "Weather at ${location.latitude}, ${location.longitude}\nIntegrate OpenWeatherMap API",
                Toast.LENGTH_LONG
            ).show()
        } ?: run {
            Toast.makeText(this, "Location not available for weather data", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasRequiredPermissions()) {
            initializeLocationServices()
        }
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
