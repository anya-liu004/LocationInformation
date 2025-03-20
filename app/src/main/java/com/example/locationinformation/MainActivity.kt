package com.example.locationinformation

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.example.locationinformation.ui.theme.LocationInformationTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.*
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.maps.model.CameraPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocationScreen()
        }
    }
}

@Composable
fun LocationScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var addressText by remember { mutableStateOf("Fetching location...") }
    var customMarkers by remember { mutableStateOf(listOf<LatLng>()) }
    val cameraPositionState = rememberCameraPositionState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchUserLocation(context) { location ->
                userLocation = location
                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                    LatLng(location.latitude, location.longitude), 15f
                )
                coroutineScope.launch {
                    addressText = getAddressFromLocation(context, location.latitude, location.longitude)
                }
            }
        } else {
            addressText = "Location permission denied"
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = addressText, modifier = Modifier.padding(8.dp))

        GoogleMap(
            modifier = Modifier.weight(1f),
            cameraPositionState = cameraPositionState,
            onMapClick = { latLng ->
                customMarkers = customMarkers + latLng
            }
        ) {
            userLocation?.let {
                Marker(
                    state = rememberMarkerState(position = LatLng(it.latitude, it.longitude)),
                    title = "Your Location"
                )
            }

            customMarkers.forEach { latLng ->
                Marker(
                    state = rememberMarkerState(position = latLng),
                    title = "Custom Marker"
                )
            }
        }
    }

}

private fun fetchUserLocation(context: Context, onLocationReceived: (Location) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED
    ) {
        Log.e("Location", "Location permission not granted")
        return
    }

    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            onLocationReceived(location)
        } else {
            Log.e("Location", "Failed to retrieve location")
        }
    }.addOnFailureListener { exception ->
        Log.e("Location", "Error getting location: ${exception.message}")
    }
}


suspend fun getAddressFromLocation(context: Context, latitude: Double, longitude: Double): String =
    withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0) ?: "Address not found"
        } catch (e: Exception) {
            "Error fetching address"
        }
    }