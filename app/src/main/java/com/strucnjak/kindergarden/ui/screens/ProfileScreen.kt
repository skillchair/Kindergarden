package com.strucnjak.kindergarden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    Column(Modifier.padding(16.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("UID: ${user?.uid ?: "-"}")
        Text("Email: ${user?.email ?: "-"}")
        Spacer(Modifier.height(16.dp))
        Row { Button(onClick = onBack) { Text("Back") } Spacer(Modifier.width(8.dp))
            Button(onClick = { auth.signOut(); onBack() }) { Text("Sign out") } }
    }
}

