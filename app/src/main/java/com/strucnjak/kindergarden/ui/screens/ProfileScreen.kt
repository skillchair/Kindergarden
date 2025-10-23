package com.strucnjak.kindergarden.ui.screens

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.strucnjak.kindergarden.data.model.User
import com.strucnjak.kindergarden.data.repo.ParkRepo
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseDatabase.getInstance().reference
    val repo = remember { ParkRepo() }

    var user by remember { mutableStateOf<User?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var showImagePicker by remember { mutableStateOf(false) }

    val cameraFile = remember {
        File(context.cacheDir, "profile_photo_${System.currentTimeMillis()}.jpg")
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

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        val snapshot = db.child("users").child(uid).get().await()
        user = snapshot.getValue(User::class.java)
    }

    LaunchedEffect(imageUri) {
        val uri = imageUri
        if (uri != null) {
            uploading = true
            try {
                val res = repo.uploadImage(uri)
                val photoUrl = res.getOrElse { error ->
                    uploading = false
                    imageUri = null
                    Toast.makeText(context, "Otpremanje nije uspelo: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@LaunchedEffect
                }

                if (photoUrl.isNotEmpty()) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        db.child("users").child(uid).child("photoUrl").setValue(photoUrl).await()
                        user = user?.copy(photoUrl = photoUrl)
                        Toast.makeText(context, "Profilna slika je ažurirana", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Otpremanje nije uspelo - URL je prazan", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Greška: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            uploading = false
            imageUri = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Nazad")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        auth.signOut()
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, "Odjavi se")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                AsyncImage(
                    model = user?.photoUrl?.ifEmpty { null },
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                )
                IconButton(
                    onClick = { showImagePicker = true },
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(Icons.Default.AddCircle, "Promeni sliku")
                }
            }
            if (uploading) CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(user?.name ?: "Nepoznato", style = MaterialTheme.typography.headlineMedium)
            Text(user?.username ?: auth.currentUser?.email ?: "")
            Spacer(Modifier.height(8.dp))
            Text("Poeni: ${user?.points ?: 0}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Telefon: ${user?.phone ?: "Nije postavljeno"}")
                }
            }
        }
    }

    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            title = { Text("Promeni profilnu sliku") },
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

                    if (!user?.photoUrl.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                showImagePicker = false
                                uploading = true
                                scope.launch {
                                    try {

                                        val uid = auth.currentUser?.uid
                                        if (uid != null) {
                                            db.child("users").child(uid).child("photoUrl").setValue("").await()
                                            user = user?.copy(photoUrl = "")
                                            Toast.makeText(context, "Profilna slika je obrisana", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Greška: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    uploading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Obriši sliku")
                        }
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
