package com.sansoft.sangeocam

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MapViewActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var fabCurrentLocation: FloatingActionButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_view)

        setupToolbar()
        initializeViews()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Photo Map"
    }

    private fun initializeViews() {
        fabCurrentLocation = findViewById(R.id.fabCurrentLocation)
        fabCurrentLocation.setOnClickListener {
            moveToCurrentLocation()
        }
    }

    private fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(currentLatLng)
                            .title("Current Location")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    )
                }
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = false
            moveToCurrentLocation()
        }

        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = true
        }

        // Load all photos from database and add markers
        loadPhotosOnMap()
    }

    private fun loadPhotosOnMap() {
        CoroutineScope(Dispatchers.IO).launch {
            val mediaItems = MediaDatabase.getInstance(this@MapViewActivity)
                .mediaDao()
                .getMediaByType("photo")

            withContext(Dispatchers.Main) {
                mediaItems.forEach { item ->
                    val position = LatLng(item.latitude, item.longitude)

                    // Try to load thumbnail
                    val thumbnail = try {
                        val uri = Uri.parse(item.filePath)
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    } catch (e: Exception) {
                        null
                    }

                    val markerOptions = MarkerOptions()
                        .position(position)
                        .title("Photo: ${SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(item.timestamp)}")
                        .snippet("Tap to view")

                    // Use custom thumbnail if available
                    thumbnail?.let {
                        val scaledBitmap = Bitmap.createScaledBitmap(it, 100, 100, false)
                        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap))
                    } ?: run {
                        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    }

                    googleMap.addMarker(markerOptions)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
