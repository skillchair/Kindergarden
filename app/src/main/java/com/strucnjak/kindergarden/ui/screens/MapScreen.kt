package com.strucnjak.kindergarden.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.strucnjak.kindergarden.data.model.Park
import com.strucnjak.kindergarden.data.repo.ParkRepo
import com.strucnjak.kindergarden.services.LocationService
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(onProfile: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ParkRepo() }
    var parks by remember { mutableStateOf<List<Park>>(emptyList()) }
    var search by remember { mutableStateOf(TextFieldValue("")) }
    var showAdd by remember { mutableStateOf(false) }

    // Permissions
    val permissions = rememberMultiplePermissionsState(permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    })
    LaunchedEffect(Unit) { permissions.launchMultiplePermissionRequest() }

    // Load parks
    LaunchedEffect(Unit) {
        repo.parksFlow().collect { parks = it }
    }

    LaunchedEffect(permissions.permissions.any { it.status.isGranted }) {
        if (permissions.permissions.any { it.status.isGranted }) {
            context.startService(Intent(context, LocationService::class.java))
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Kindergarden Map") }, actions = {
            TextButton(onClick = onProfile) { Text("Profile") }
        })
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            label = { Text("Search parks") }
        )
        Box(Modifier.fillMaxSize()) {
            OSMMap(
                context = context,
                parks = parks.filter { it.desc.contains(search.text, true) || it.type.contains(search.text, true) },
                myLocationEnabled = permissions.permissions.any { it.status.isGranted }
            )
            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(16.dp)
            ) { Icon(Icons.Default.Add, contentDescription = null) }
        }
    }

    if (showAdd) AddParkDialog(onDismiss = { showAdd = false }, onAdd = { desc, type ->
        val fused = LocationServices.getFusedLocationProviderClient(context)
        scope.launch {
            val last = try { fused.lastLocation.result } catch (_: Exception) { null }
            fused.lastLocation.addOnSuccessListener { loc ->
                val user = FirebaseAuth.getInstance().currentUser
                if (loc != null && user != null) {
                    val park = Park(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        desc = desc,
                        type = type,
                        authorId = user.uid,
                        timestamp = System.currentTimeMillis(),
                        photoUrl = ""
                    )
                    scope.launch {
                        val res = repo.addPark(park)
                        if (res.isSuccess) {
                            Toast.makeText(context, "Park added", Toast.LENGTH_SHORT).show()
                            showAdd = false
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.localizedMessage ?: "Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "No location or user", Toast.LENGTH_SHORT).show()
                }
            }
        }
    })
}

@Composable
private fun OSMMap(context: Context, parks: List<Park>, myLocationEnabled: Boolean) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                controller.setZoom(14.0)
                // Default center
                controller.setCenter(GeoPoint(45.8150, 15.9819))
                if (myLocationEnabled) {
                    val myLoc = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                    myLoc.enableMyLocation()
                    overlays.add(myLoc)
                }
            }
        },
        update = { map ->
            // Clear old park markers (keep first overlays like myLocation if any)
            map.overlays.removeAll(map.overlays.filterIsInstance<Marker>())
            parks.forEach { p ->
                val marker = Marker(map)
                marker.position = GeoPoint(p.lat, p.lng)
                marker.title = p.desc
                marker.subDescription = "Type: ${p.type}"
                map.overlays.add(marker)
            }
            map.invalidate()
        }
    )
}

@Composable
private fun AddParkDialog(onDismiss: () -> Unit, onAdd: (desc: String, type: String) -> Unit) {
    var desc by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Park") },
        text = {
            Column {
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(desc.trim(), type.trim()) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
