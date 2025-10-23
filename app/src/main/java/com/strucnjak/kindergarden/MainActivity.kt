package com.strucnjak.kindergarden

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cloudinary.android.MediaManager
import com.strucnjak.kindergarden.ui.navigation.NavRoutes
import com.strucnjak.kindergarden.ui.screens.*
import com.strucnjak.kindergarden.ui.theme.KindergardenTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET
        val config = hashMapOf(
            "cloud_name" to cloudName,
            "unsigned" to "true",
            "upload_preset" to uploadPreset
        )

        try {
            MediaManager.init(this, config)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent { AppRoot() }

        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        ActivityCompat.requestPermissions(this, permissions, 100)
    }
}

@Composable
fun AppRoot() {
    KindergardenTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val nav = rememberNavController()
            val startDestination = if (FirebaseAuth.getInstance().currentUser != null) {
                NavRoutes.Map.route
            } else {
                NavRoutes.Login.route
            }


            NavHost(navController = nav, startDestination = startDestination) {
                composable(NavRoutes.Login.route) {
                    LoginScreen(
                        onRegister = { nav.navigate(NavRoutes.Register.route) },
                        onLoggedIn = { nav.navigate(NavRoutes.Map.route) { popUpTo(NavRoutes.Login.route) { inclusive = true } } }
                    )
                }
                composable(NavRoutes.Register.route) {
                    RegisterScreen(
                        onBack = { nav.popBackStack() },
                        onRegistered = { nav.navigate(NavRoutes.Map.route) { popUpTo(NavRoutes.Login.route) { inclusive = true } } }
                    )
                }
                composable(NavRoutes.Map.route) {
                    MapScreen(
                        onProfile = { nav.navigate(NavRoutes.Profile.route) },
                        onRankings = { nav.navigate(NavRoutes.Rankings.route) },
                        onParkList = { nav.navigate(NavRoutes.ParkList.route) }
                    )
                }
                composable(NavRoutes.Profile.route) {
                    ProfileScreen(
                        onBack = { nav.popBackStack() },
                        onLogout = { nav.navigate(NavRoutes.Login.route) { popUpTo(0) } }
                    )
                }
                composable(NavRoutes.Rankings.route) { RankingsScreen(onBack = { nav.popBackStack() }) }
                composable(NavRoutes.Search.route) { SearchScreen() }
                composable(NavRoutes.ParkList.route) { ParkListScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}
