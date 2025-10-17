package com.strucnjak.kindergarden.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.strucnjak.kindergarden.data.repo.AuthRepo
import com.strucnjak.kindergarden.data.repo.ParkRepo
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(onBack: () -> Unit, onRegistered: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        photoUri = uri
    }

    Column(Modifier.padding(16.dp)) {
        Text("Register", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full name") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        if (photoUri != null) {
            Image(
                painter = rememberAsyncImagePainter(photoUri),
                contentDescription = null,
                modifier = Modifier.height(120.dp).fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
        }
        Row {
            OutlinedButton(onClick = { pickImage.launch("image/*") }) { Text("Pick photo") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val uri = photoUri ?: return@Button
                loading = true
                scope.launch {
                    val res = ParkRepo().uploadImage(uri)
                    loading = false
                    if (res.isSuccess) photoUrl = res.getOrNull() else error = res.exceptionOrNull()?.localizedMessage
                }
            }, enabled = photoUri != null && !loading) { Text(if (loading) "Uploading..." else "Upload") }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            loading = true
            error = null
            scope.launch {
                val res = AuthRepo().register(email.trim(), password, username.trim(), name.trim(), phone.trim(), photoUrl)
                loading = false
                if (res.isSuccess) onRegistered() else error = res.exceptionOrNull()?.localizedMessage
            }
        }, enabled = !loading) { Text(if (loading) "Creating..." else "Create account") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Back") }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
    }
}

