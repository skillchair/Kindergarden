package com.strucnjak.kindergarden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.strucnjak.kindergarden.data.model.User

@Composable
fun RankingsScreen() {
    var users by remember { mutableStateOf<List<User>>(emptyList()) }

    LaunchedEffect(Unit) {
        val ref = FirebaseDatabase.getInstance().reference.child("users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.getValue(User::class.java) }
                users = list.sortedByDescending { it.points }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) { }
        }
        ref.addValueEventListener(listener)
        awaitDispose { ref.removeEventListener(listener) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Rankings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            itemsIndexed(users) { index, user ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("#${index + 1}", modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.username.ifBlank { user.name })
                        Text("${user.points} pts", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

