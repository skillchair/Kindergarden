package com.strucnjak.kindergarden.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.strucnjak.kindergarden.data.model.Park
import com.strucnjak.kindergarden.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen(onBack: () -> Unit) {
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var parks by remember { mutableStateOf<List<Park>>(emptyList()) }
    var filteredParks by remember { mutableStateOf<List<Park>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSortOption by remember { mutableStateOf(0) }
    var showFilterDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val db = FirebaseDatabase.getInstance().reference

        val usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                users = snapshot.children.mapNotNull { it.getValue(User::class.java) }
                    .sortedByDescending { it.points }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        val parksListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                parks = snapshot.children.mapNotNull { it.getValue(Park::class.java) }
                    .sortedByDescending { it.timestamp }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        db.child("users").addValueEventListener(usersListener)
        db.child("parks").addValueEventListener(parksListener)

        onDispose {
            db.child("users").removeEventListener(usersListener)
            db.child("parks").removeEventListener(parksListener)
        }
    }

    LaunchedEffect(parks, searchQuery, selectedSortOption, users) {
        var result = parks

        if (searchQuery.isNotBlank() && searchQuery.length < 3) {
            filteredParks = emptyList()
            return@LaunchedEffect
        }

        if (searchQuery.isNotBlank() && searchQuery.length >= 3) {
            result = result.filter { park ->
                val nameMatch = park.name.contains(searchQuery, ignoreCase = true)
                val typeMatch = park.type.contains(searchQuery, ignoreCase = true)
                val descMatch = park.desc.contains(searchQuery, ignoreCase = true)

                val author = users.find { it.id == park.authorId }
                val authorName = author?.name ?: ""
                val authorUsername = author?.username ?: ""
                val authorMatch = authorUsername.equals(searchQuery, ignoreCase = true) ||
                    authorName.contains(searchQuery, ignoreCase = true)

                nameMatch || typeMatch || descMatch || authorMatch
            }
        }

        result = when (selectedSortOption) {
            0 -> result.sortedByDescending { it.timestamp }
            1 -> result.sortedBy { it.timestamp }
            else -> result
        }

        filteredParks = result
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rangiranje") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Nazad")
                    }
                },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(Icons.Default.Settings, "Sortiranje")
                        }
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
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Korisnici") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Parkovi") }
                )
            }

            when (selectedTab) {
                0 -> UsersRanking(users)
                1 -> {
                    Column {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            label = { Text("Pretraži po imenu, tipu ili autoru") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, "Obriši")
                                    }
                                }
                            },
                            singleLine = true
                        )

                        if (searchQuery.isNotEmpty() && searchQuery.length < 3) {
                            Text(
                                text = "Unesi najmanje 3 karaktera",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (filteredParks.isEmpty() && searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Nema rezultata za '$searchQuery'")
                            }
                        } else {
                            ParksRanking(filteredParks)
                        }
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
                        Text("Datum (najnoviji prvo)")
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
                        Text("Datum (najstariji prvo)")
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
private fun UsersRanking(users: List<User>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        itemsIndexed(users) { index, user ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${index + 1}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.width(50.dp)
                    )
                    if (user.photoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = user.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.name.ifBlank { user.username }, style = MaterialTheme.typography.titleMedium)
                        Text("${user.points} poena", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParksRanking(parks: List<Park>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        itemsIndexed(parks) { index, park ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${index + 1}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.width(50.dp)
                    )
                    if (park.photoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = park.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(park.name, style = MaterialTheme.typography.titleMedium)
                        Text(park.type, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
