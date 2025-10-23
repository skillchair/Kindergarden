package com.strucnjak.kindergarden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.strucnjak.kindergarden.data.repo.AuthRepo
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onRegister: () -> Unit, onLoggedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Prijavi se", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Lozinka") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                loading = true
                error = null
                scope.launch {
                    val res = AuthRepo().login(email.trim(), password)
                    loading = false
                    if (res.isSuccess) {
                        onLoggedIn()
                    } else {
                        val exception = res.exceptionOrNull()
                        error = when {
                            exception?.message?.contains("credential", ignoreCase = true) == true -> "Pogrešan email ili lozinka"
                            exception?.message?.contains("network", ignoreCase = true) == true -> "Nema internet konekcije"
                            exception?.message?.contains("user", ignoreCase = true) == true -> "Korisnik ne postoji"
                            else -> "Pogrešan email ili lozinka"
                        }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Učitavanje..." else "Prijavi se")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onRegister,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Kreiraj nalog")
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
    }
}
