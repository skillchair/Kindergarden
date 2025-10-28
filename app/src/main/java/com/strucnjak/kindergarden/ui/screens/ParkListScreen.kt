package com.strucnjak.kindergarden.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.strucnjak.kindergarden.data.model.Park
import com.strucnjak.kindergarden.data.model.User
import com.strucnjak.kindergarden.data.repo.ParkRepo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkListScreen(onBack: () -> Unit) {
    val repo = remember { ParkRepo() }
    val context = LocalContext.current
    var allParks by remember { mutableStateOf<List<Park>>(emptyList()) }
    var nearbyParks by remember { mutableStateOf<List<Park>>(emptyList()) }
    var filteredParks by remember { mutableStateOf<List<Park>>(emptyList()) }
    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var lastFetchLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    var searchName by remember { mutableStateOf("") }
    var searchType by remember { mutableStateOf("") }
    var searchAuthor by remember { mutableStateOf("") }
    var selectedSortOption by remember { mutableStateOf(0) }
    val selectedRadius = 4
    var showFilterDialog by remember { mutableStateOf(false) }

    val fused = LocationServices.getFusedLocationProviderClient(context)

    DisposableEffect(Unit) {
        val db = FirebaseDatabase.getInstance().reference
        val usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                users = snapshot.children.mapNotNull { child ->
                    try {
                        child.getValue(User::class.java)?.copy(id = child.key ?: "")
                    } catch (e: Exception) {
                        null
                    }
                }.sortedByDescending { it.points }
            }
            override fun onCancelled(error: DatabaseError) { }
        }
        db.child("users").addValueEventListener(usersListener)

        try {
            @Suppress("MissingPermission")
            fused.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    userLocation = loc.latitude to loc.longitude
                }
            }
        } catch (_: Exception) { }

        onDispose {
            db.child("users").removeEventListener(usersListener)
        }
    }
    LaunchedEffect(userLocation) {
        if (userLocation != null) {
            val shouldFetch = lastFetchLocation == null ||
                com.strucnjak.kindergarden.util.Geo.distanceMeters(
                    lastFetchLocation!!.first, lastFetchLocation!!.second,
                    userLocation!!.first, userLocation!!.second
                ) >= 1000.0

            if (shouldFetch) {
                lastFetchLocation = userLocation
                repo.parksFlow(
                    userLat = userLocation!!.first,
                    userLng = userLocation!!.second,
                    radiusKm = 24.0
                ).collect { allParks = it }
            }
        } else {
            repo.parksFlow().collect { allParks = it }
        }
    }


    LaunchedEffect(allParks, userLocation, selectedRadius) {
        userLocation?.let { (lat, lng) ->
            val radiusMeters = when (selectedRadius) {
                1 -> 1000.0
                2 -> 5000.0
                3 -> 10000.0
                4 -> 20000.0
                else -> 20000.0
            }

            nearbyParks = allParks.mapNotNull { park ->
                val distance = com.strucnjak.kindergarden.util.Geo.distanceMeters(
                    lat, lng,
                    park.lat, park.lng
                )

                if (distance <= radiusMeters) {
                    park to distance
                } else null
            }.sortedBy { it.second }
                .map { it.first }
        } ?: run {
            nearbyParks = allParks
        }
    }

    LaunchedEffect(nearbyParks, searchName, searchType, searchAuthor, selectedSortOption, userLocation, users) {
        var result = nearbyParks

        val hasInvalidSearch = (searchName.isNotBlank() && searchName.length < 3) ||
                               (searchType.isNotBlank() && searchType.length < 3) ||
                               (searchAuthor.isNotBlank() && searchAuthor.length < 3)

        if (hasInvalidSearch) {
            filteredParks = emptyList()
            return@LaunchedEffect
        }

        if (searchName.isNotBlank() && searchName.length >= 3) {
            result = result.filter { park ->
                park.name.contains(searchName, ignoreCase = true)
            }
        }

        if (searchType.isNotBlank() && searchType.length >= 3) {
            result = result.filter { park ->
                park.type.contains(searchType, ignoreCase = true)
            }
        }

        if (searchAuthor.isNotBlank() && searchAuthor.length >= 3) {
            result = result.filter { park ->
                val author = users.find { it.id == park.authorId }
                val authorName = author?.name ?: ""
                val authorUsername = author?.username ?: ""

                authorUsername.equals(searchAuthor, ignoreCase = true) ||
                authorName.contains(searchAuthor, ignoreCase = true)
            }
        }

        result = when (selectedSortOption) {
            0 -> {
                userLocation?.let { (lat, lng) ->
                    result.sortedBy { park ->
                        com.strucnjak.kindergarden.util.Geo.distanceMeters(
                            lat, lng,
                            park.lat, park.lng
                        )
                    }
                } ?: result
            }
            1 -> result.sortedByDescending { it.timestamp }
            else -> result
        }

        filteredParks = result
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parkovi u Blizini") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Nazad")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.Settings, "Filteri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = searchName,
                    onValueChange = { searchName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pretraži po imenu") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchName.isNotEmpty()) {
                            IconButton(onClick = { searchName = "" }) {
                                Icon(Icons.Default.Clear, "Obriši")
                            }
                        }
                    },
                    singleLine = true,
                    isError = searchName.isNotEmpty() && searchName.length < 3
                )

                if (searchName.isNotEmpty() && searchName.length < 3) {
                    Text(
                        text = "Unesi najmanje 3 karaktera",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchType,
                    onValueChange = { searchType = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pretraži po tipu") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchType.isNotEmpty()) {
                            IconButton(onClick = { searchType = "" }) {
                                Icon(Icons.Default.Clear, "Obriši")
                            }
                        }
                    },
                    singleLine = true,
                    isError = searchType.isNotEmpty() && searchType.length < 3
                )

                if (searchType.isNotEmpty() && searchType.length < 3) {
                    Text(
                        text = "Unesi najmanje 3 karaktera",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchAuthor,
                    onValueChange = { searchAuthor = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pretraži po autoru") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchAuthor.isNotEmpty()) {
                            IconButton(onClick = { searchAuthor = "" }) {
                                Icon(Icons.Default.Clear, "Obriši")
                            }
                        }
                    },
                    singleLine = true,
                    isError = searchAuthor.isNotEmpty() && searchAuthor.length < 3
                )

                if (searchAuthor.isNotEmpty() && searchAuthor.length < 3) {
                    Text(
                        text = "Unesi najmanje 3 karaktera",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        append("Prikazano: ${filteredParks.size}")
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

                if (searchName.isNotEmpty() || searchType.isNotEmpty() || searchAuthor.isNotEmpty()) {
                    TextButton(onClick = {
                        searchName = ""
                        searchType = ""
                        searchAuthor = ""
                    }) {
                        Text("Obriši filtere", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (filteredParks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Parkovi sa navedenim kriterijumima nisu pronađeni.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(filteredParks) { park ->
                        val distance = userLocation?.let { (lat, lng) ->
                            com.strucnjak.kindergarden.util.Geo.distanceMeters(
                                lat, lng,
                                park.lat, park.lng
                            )
                        }
                        ParkListItem(park = park, distance = distance, onClick = { })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Sortiranje") },
            text = {
                Column {

                    Text("Sortiraj po:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSortOption = 0 }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedSortOption == 0, onClick = { selectedSortOption = 0 })
                        Spacer(Modifier.width(8.dp))
                        Text("Udaljenost (najbliži prvo)")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSortOption = 1 }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedSortOption == 1, onClick = { selectedSortOption = 1 })
                        Spacer(Modifier.width(8.dp))
                        Text("Datum (najnoviji prvo)")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFilterDialog = false }) {
                    Text("Primeni")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFilterDialog = false }) {
                    Text("Otkaži")
                }
            }
        )
    }
}

@Composable
private fun ParkListItem(park: Park, distance: Double?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (park.photoUrl.isNotEmpty()) {
                AsyncImage(
                    model = park.photoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = park.name, style = MaterialTheme.typography.titleMedium)
                Text(text = park.desc, style = MaterialTheme.typography.bodySmall)
                Text(text = "Tip: ${park.type}", style = MaterialTheme.typography.bodySmall)
                distance?.let {
                    val distanceText = if (it < 1000) {
                        "${it.toInt()}m daleko"
                    } else {
                        "${String.format("%.1f", it / 1000)}km daleko"
                    }
                    Text(
                        text = distanceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
