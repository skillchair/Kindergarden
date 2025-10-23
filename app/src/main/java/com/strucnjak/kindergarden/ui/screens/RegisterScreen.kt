package com.strucnjak.kindergarden.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.strucnjak.kindergarden.data.repo.AuthRepo
import com.strucnjak.kindergarden.data.repo.ParkRepo
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RegisterScreen(onBack: () -> Unit, onRegistered: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showImagePicker by remember { mutableStateOf(false) }

    val cameraFile = remember {
        File(context.cacheDir, "register_photo_${System.currentTimeMillis()}.jpg")
    }
    val cameraUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", cameraFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) photoUri = cameraUri
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        photoUri = uri
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(cameraUri)
    }


    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Registruj se", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Lozinka") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Potvrdi Lozinku") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = confirmPassword.isNotEmpty() && password != confirmPassword,
            modifier = Modifier.fillMaxWidth()
        )
        if (confirmPassword.isNotEmpty() && password != confirmPassword) {
            Text("Lozinke se ne poklapaju", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Korisničko ime") },
            singleLine = true,
            isError = username.isNotEmpty() && username.length < 3,
            modifier = Modifier.fillMaxWidth()
        )
        if (username.isNotEmpty() && username.length < 3) {
            Text("Korisničko ime mora imati najmanje 3 karaktera", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Puno ime") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefon") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))

        val ctx = LocalContext.current
        var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(photoUri) {
            imageBitmap = photoUri?.let { uri ->
                try {
                    if (Build.VERSION.SDK_INT >= 28) {
                        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
                        ImageDecoder.decodeBitmap(src).asImageBitmap()
                    } else {
                        ctx.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        }
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }

        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = null,
                modifier = Modifier.height(120.dp).fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showImagePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (uploading) "Otpremanje..." else "Dodaj sliku")
            }

            if (photoUri != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { photoUri = null }) {
                    Icon(Icons.Default.Clear, "Ukloni sliku")
                }
            }
        }

        if (uploading) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator()
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            error = null

            if (email.isBlank()) {
                error = "Email je obavezan"
                return@Button
            }
            if (!email.contains("@") || !email.contains(".")) {
                error = "Email adresa nije validna"
                return@Button
            }
            if (username.isBlank()) {
                error = "Korisničko ime je obavezno"
                return@Button
            }
            if (username.length < 3) {
                error = "Korisničko ime mora imati najmanje 3 karaktera"
                return@Button
            }
            if (name.isBlank()) {
                error = "Puno ime je obavezno"
                return@Button
            }
            if (password.isBlank()) {
                error = "Lozinka je obavezna"
                return@Button
            }
            if (password.length < 6) {
                error = "Lozinka treba da ima najmanje 6 karaktera"
                return@Button
            }
            if (password != confirmPassword) {
                error = "Lozinke se ne poklapaju"
                return@Button
            }

            loading = true
            scope.launch {
                try {
                    uploading = true
                    var uploadedPhotoUrl: String? = null

                    photoUri?.let { uri ->
                        val res = ParkRepo().uploadImage(uri)
                        if (res.isSuccess) {
                            uploadedPhotoUrl = res.getOrNull()
                        } else {
                            uploading = false
                            loading = false
                            val errorMsg = res.exceptionOrNull()?.message ?: "Greška pri otpremanju slike"
                            error = "Greška pri otpremanju slike: $errorMsg"
                            return@launch
                        }
                    }
                    uploading = false

                    val res = AuthRepo().register(email.trim(), password, username.trim(), name.trim(), phone.trim(), uploadedPhotoUrl)
                    loading = false
                    if (res.isSuccess) {
                        onRegistered()
                    } else {
                        val errorMsg = res.exceptionOrNull()?.message ?: "Greška pri registraciji"
                        error = errorMsg
                    }
                } catch (e: Exception) {
                    uploading = false
                    loading = false
                    val errorMsg = e.message ?: "Greška pri registraciji"
                    error = errorMsg
                }
            }
        }, enabled = !loading && !uploading && password == confirmPassword && password.isNotEmpty() && email.isNotEmpty() && username.isNotEmpty() && name.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (uploading) "Otpremanje slike..." else if (loading) "Kreiranje..." else "Kreiraj nalog")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Nazad") }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error!!,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            title = { Text("Dodaj profilnu sliku") },
            text = {
                Column {
                    Button(
                        onClick = {
                            showImagePicker = false
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Kamera")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            showImagePicker = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Galerija")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImagePicker = false }) {
                    Text("Otkaži")
                }
            }
        )
    }
}
