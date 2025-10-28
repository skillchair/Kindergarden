package com.strucnjak.kindergarden.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.compose.*
import com.strucnjak.kindergarden.data.model.Park
import com.strucnjak.kindergarden.data.model.User
import com.strucnjak.kindergarden.data.repo.ParkRepo
import com.strucnjak.kindergarden.services.LocationService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File


@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onProfile: () -> Unit, onRankings: () -> Unit, onParkList: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ParkRepo() }
    var parks by remember { mutableStateOf<List<Park>>(emptyList()) }
    var filteredParks by remember { mutableStateOf<List<Park>>(emptyList()) }
    var search by remember { mutableStateOf(TextFieldValue("")) }
    var showAdd by remember { mutableStateOf(false) }
    var followEnabled by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showRadiusDialog by remember { mutableStateOf(false) }
    var selectedRadius by remember { mutableStateOf(4) }
    var lastFetchLocation by remember { mutableStateOf<LatLng?>(null) }

    val fused = LocationServices.getFusedLocationProviderClient(context)
    var otherUsers by remember { mutableStateOf<Map<String, Pair<Double, Double>>>(emptyMap()) }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }

    val permissions = rememberMultiplePermissionsState(permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    })

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(44.0, 21.0), 10f)
    }
    val scope = rememberCoroutineScope()

    DisposableEffect(permissions.permissions.any { it.status.isGranted }) {
        val db = FirebaseDatabase.getInstance().reference
        var locationsListener: ValueEventListener? = null

        if (permissions.permissions.any { it.status.isGranted }) {
            try {
                context.startService(Intent(context, LocationService::class.java))
                @Suppress("MissingPermission")
                fused.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        userLocation = LatLng(loc.latitude, loc.longitude)
                    }
                }
            } catch (_: Exception) {
            }

            locationsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                    val allLocations = mutableMapOf<String, Pair<Double, Double>>()
                    val currentTime = System.currentTimeMillis()

                    snapshot.children.forEach { child ->
                        val uid = child.key ?: return@forEach
                        val lat = (child.child("lat").value as? Number)?.toDouble()
                        val lng = (child.child("lng").value as? Number)?.toDouble()
                        val timestamp = (child.child("timestamp").value as? Number)?.toLong()

                        if (lat != null && lng != null) {
                            if (uid == currentUid) {
                                allLocations[uid] = Pair(lat, lng)
                            } else if (timestamp != null && (currentTime - timestamp) <= 90000) {
                                allLocations[uid] = Pair(lat, lng)
                            }
                        }
                    }

                    currentUid?.let { id ->
                        allLocations[id]?.let { (lat, lng) ->
                            userLocation = LatLng(lat, lng)
                        }
                    }

                    otherUsers = allLocations
                }

                override fun onCancelled(error: DatabaseError) {
                }
            }

            db.child("locations").addValueEventListener(locationsListener)
        }

        val usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                users = snapshot.children.mapNotNull { child ->
                    try {
                        child.getValue(User::class.java)?.copy(id = child.key ?: "")
                    } catch (_: Exception) {
                        null
                    }
                }.sortedByDescending { it.points }
            }
            override fun onCancelled(error: DatabaseError) {
            }
        }

        db.child("users").addValueEventListener(usersListener)

        onDispose {
            locationsListener?.let { db.child("locations").removeEventListener(it) }
            db.child("users").removeEventListener(usersListener)
        }
    }
    LaunchedEffect(userLocation) {
        if (userLocation != null) {
            val shouldUpdateFetch = lastFetchLocation == null ||
                com.strucnjak.kindergarden.util.Geo.distanceMeters(
                    lastFetchLocation!!.latitude, lastFetchLocation!!.longitude,
                    userLocation!!.latitude, userLocation!!.longitude
                ) >= 1000.0

            if (shouldUpdateFetch) {
                lastFetchLocation = userLocation
            }

            repo.parksFlow(
                userLat = userLocation!!.latitude,
                userLng = userLocation!!.longitude,
                radiusKm = 24.0
            ).collect { parks = it }
        }
    }

    var hasInitiallyZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(userLocation) {
        if (userLocation != null && !hasInitiallyZoomed) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(userLocation!!, 15f),
                durationMs = 1000
            )
            hasInitiallyZoomed = true
        }
    }

    LaunchedEffect(followEnabled, userLocation) {
        if (followEnabled && userLocation != null) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(userLocation!!, 15f),
                durationMs = 500
            )
        }
    }

    LaunchedEffect(parks, search.text, selectedRadius, userLocation, users) {
        var result = parks

        if (search.text.isNotBlank() && search.text.length < 3) {
            filteredParks = emptyList()
            return@LaunchedEffect
        }

        if (search.text.isNotBlank() && search.text.length >= 3) {
            result = result.filter { park ->
                val author = users.find { it.id == park.authorId }
                val authorName = author?.name ?: ""
                val authorUsername = author?.username ?: ""

                val nameMatch = park.name.contains(search.text, ignoreCase = true)
                val typeMatch = park.type.contains(search.text, ignoreCase = true)
                val authorMatch = authorUsername.equals(search.text, ignoreCase = true) ||
                    authorName.contains(search.text, ignoreCase = true)

                nameMatch || typeMatch || authorMatch
            }
        }

        if (userLocation != null) {
            val radiusMeters = when (selectedRadius) {
                1 -> 1000.0
                2 -> 5000.0
                3 -> 10000.0
                4 -> 20000.0
                else -> 20000.0
            }

            result = result.filter { park ->
                val distance = com.strucnjak.kindergarden.util.Geo.distanceMeters(
                    userLocation!!.latitude, userLocation!!.longitude,
                    park.lat, park.lng
                )
                distance <= radiusMeters
            }
        }

        filteredParks = result
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Dozvola za Lokaciju") },
            text = { Text("Omogućite lokaciju da biste pronašli parkove i korisnike u blizini.") },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    permissions.launchMultiplePermissionRequest()
                }) {
                    Text("Dozvoli")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Otkaži")
                }
            }
        )
    }

    if (showRadiusDialog) {
        AlertDialog(
            onDismissRequest = { showRadiusDialog = false },
            title = { Text("Filter po Radijusu") },
            text = {
                Column {
                    Text("Prikaži parkove u radijusu:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    listOf(
                        1 to "1 km",
                        2 to "5 km",
                        3 to "10 km",
                        4 to "20 km"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedRadius = value }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedRadius == value,
                                onClick = { selectedRadius = value }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showRadiusDialog = false }) {
                    Text("Primeni")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRadiusDialog = false }) {
                    Text("Otkaži")
                }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Mapa") }, actions = {
            IconButton(onClick = onRankings) { Icon(Icons.Default.Star, "Rangiranje") }
            IconButton(onClick = onParkList) { Icon(Icons.AutoMirrored.Filled.List, "Parkovi") }
            IconButton(onClick = onProfile) { Icon(Icons.Default.Person, "Profil") }
            IconButton(onClick = { showRadiusDialog = true }) {
                Icon(Icons.Default.Explore, "Filter po Radijusu")
            }
            IconButton(onClick = {
                followEnabled = !followEnabled
                if (followEnabled && userLocation != null) {
                    scope.launch {
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLngZoom(userLocation!!, 15f),
                            durationMs = 1000
                        )
                    }
                }
            }) {
                Icon(
                    if (followEnabled) Icons.Default.MyLocation else Icons.Default.ModeOfTravel,
                    if (followEnabled) "Isključi Praćenje" else "Uključi Praćenje"
                )
            }
        })
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            label = { Text("Pretraži po imenu, tipu ili autoru") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (search.text.isNotEmpty()) {
                    IconButton(onClick = { search = TextFieldValue("") }) {
                        Icon(Icons.Default.Clear, "Obriši")
                    }
                }
            }
        )

        if (search.text.isNotEmpty() && search.text.length < 3) {
            Text(
                text = "Unesi najmanje 3 karaktera",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (search.text.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        append("Prikazano: ${filteredParks.size}/${parks.size}")
                        val radiusText = when (selectedRadius) {
                            1 -> "1km"
                            2 -> "5km"
                            3 -> "10km"
                            4 -> "20km"
                            else -> "20km"
                        }
                        append(" (radijus: $radiusText)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (search.text.isNotEmpty()) {
                    TextButton(onClick = {
                        selectedRadius = 4
                        search = TextFieldValue("")
                    }) {
                        Text("Obriši filtere", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false)
            ) {
                filteredParks.forEach { park ->
                    Marker(
                        state = MarkerState(position = LatLng(park.lat, park.lng)),
                        title = park.name,
                        snippet = park.desc,
                        onClick = {
                            scope.launch {
                                cameraPositionState.animate(
                                    update = CameraUpdateFactory.newLatLngZoom(LatLng(park.lat, park.lng), 17f),
                                    durationMs = 1000
                                )
                            }
                            true
                        }
                    )
                }

                if (userLocation != null) {
                    Marker(
                        state = MarkerState(position = userLocation!!),
                        title = "Ti si ovde",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                    )
                }

                otherUsers.forEach { (uid, pos) ->
                    if (uid == currentUid) return@forEach

                    val user = users.find { it.id == uid }
                    val username = user?.username ?: "Korisnik"

                    val title = if (userLocation != null) {
                        val distance = com.strucnjak.kindergarden.util.Geo.distanceMeters(
                            userLocation!!.latitude, userLocation!!.longitude,
                            pos.first, pos.second
                        )

                        if (distance <= 100.0) {
                            "$username u blizini (${distance.toInt()}m)"
                        } else if (distance < 1000.0) {
                            "$username (${distance.toInt()}m)"
                        } else {
                            val km = String.format("%.1f", distance / 1000.0)
                            "$username (${km}km)"
                        }
                    } else {
                        username
                    }

                    Marker(
                        state = MarkerState(position = LatLng(pos.first, pos.second)),
                        title = title,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
                    )
                }
            }
            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp)
            ) { Icon(Icons.Default.Add, null) }
        }
    }

    if (showAdd) AddParkDialog(onDismiss = { showAdd = false }, repo = repo, parks = parks, permissions = permissions)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
private fun AddParkDialog(
    onDismiss: () -> Unit,
    repo: ParkRepo,
    parks: List<Park>,
    permissions: com.google.accompanist.permissions.MultiplePermissionsState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var uploading by remember { mutableStateOf(false) }
    val fused = LocationServices.getFusedLocationProviderClient(context)

    val cameraFile = remember {
        File(context.cacheDir, "park_photo_${System.currentTimeMillis()}.jpg")
    }
    val cameraUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", cameraFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) imageUri = cameraUri
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(cameraUri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj Park") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ime Parka") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Opis") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Tip") })
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) {
                        Icon(Icons.Default.AddCircle, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Kamera")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AccountCircle, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Galerija")
                    }
                    if (imageUri != null) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { imageUri = null }) {
                            Icon(Icons.Default.Clear, "Ukloni sliku")
                        }
                    }
                }
                imageUri?.let {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(100.dp))
                }
                if (uploading) CircularProgressIndicator()
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!permissions.permissions.any { it.status.isGranted }) {
                        Toast.makeText(context, "Potrebna je dozvola za lokaciju", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    uploading = true
                    scope.launch {
                        try {
                            val user = FirebaseAuth.getInstance().currentUser
                            if (user == null) {
                                uploading = false
                                Toast.makeText(context, "Nema korisnika", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            @Suppress("MissingPermission")
                            val loc = fused.lastLocation.await()

                            if (loc == null) {
                                uploading = false
                                Toast.makeText(context, "Nema lokacije", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            val existingParks = parks.filter { park ->
                                val distance = com.strucnjak.kindergarden.util.Geo.distanceMeters(
                                    loc.latitude, loc.longitude,
                                    park.lat, park.lng
                                )
                                distance < 50.0
                            }

                            if (existingParks.isNotEmpty()) {
                                uploading = false
                                Toast.makeText(context, "Park već postoji na ovoj lokaciji", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            var photoUrl = ""
                            imageUri?.let { uri ->
                                val res = repo.uploadImage(uri)
                                photoUrl = res.getOrElse { error ->
                                    Toast.makeText(context, "Greška pri upload-u slike: ${error.message}", Toast.LENGTH_SHORT).show()
                                    ""
                                }
                            }

                            val key = com.google.firebase.database.FirebaseDatabase.getInstance().reference.child("parks").push().key ?: ""
                            val park = Park(
                                id = key,
                                name = name,
                                lat = loc.latitude,
                                lng = loc.longitude,
                                desc = desc,
                                type = type,
                                authorId = user.uid,
                                timestamp = System.currentTimeMillis(),
                                photoUrl = photoUrl
                            )

                            val res = repo.addPark(park)
                            uploading = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Park je dodat", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Greška pri dodavanju parka", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            uploading = false
                            Toast.makeText(context, e.message ?: "Greška", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !uploading && name.isNotBlank() && desc.isNotBlank()
            ) { Text("Dodaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Otkaži") } }
    )
}
