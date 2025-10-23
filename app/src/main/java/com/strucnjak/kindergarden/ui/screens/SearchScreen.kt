package com.strucnjak.kindergarden.ui.screens

import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.strucnjak.kindergarden.data.model.Park
import com.strucnjak.kindergarden.data.repo.ParkRepo
import com.strucnjak.kindergarden.util.Geo

@Composable
fun SearchScreen() {
    val repo = remember { ParkRepo() }
    var parks by remember { mutableStateOf<List<Park>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("1000") }
    var current by remember { mutableStateOf<Location?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        parks = repo.searchByText("")
    }

    LaunchedEffect(Unit) {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        try {
            @Suppress("MissingPermission")
            fused.lastLocation.addOnSuccessListener { current = it }
        } catch (_: Exception) { }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        if (current == null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = "Uključi lokaciju kako bi pronašao parkove i korisnike u blizini.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Text (desc/type)") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = radius, onValueChange = { radius = it.filter { c -> c.isDigit() } }, label = { Text("Radius meters") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        val filtered = remember(parks, query, radius, current) {
            val list = parks.filter { it.desc.contains(query, true) || it.type.contains(query, true) }
            val r = radius.toDoubleOrNull() ?: Double.POSITIVE_INFINITY
            if (current == null || !r.isFinite()) list else list.filter {
                Geo.distanceMeters(current!!.latitude, current!!.longitude, it.lat, it.lng) <= r
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered) { p ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(p.desc)
                    Text("Type: ${p.type}", style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }
}
