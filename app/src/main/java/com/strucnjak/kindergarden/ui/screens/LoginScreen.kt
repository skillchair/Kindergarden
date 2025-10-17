package com.strucnjak.kindergarden.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    Column(Modifier.padding(16.dp)) {
        Text("Login", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            loading = true
            error = null
            scope.launch {
                val res = AuthRepo().login(email.trim(), password)
                loading = false
                if (res.isSuccess) onLoggedIn() else error = res.exceptionOrNull()?.localizedMessage
            }
        }, enabled = !loading) { Text(if (loading) "Loading..." else "Login") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRegister) { Text("Create account") }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
    }
}

