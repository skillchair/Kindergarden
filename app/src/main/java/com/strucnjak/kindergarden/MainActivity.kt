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
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.strucnjak.kindergarden.ui.navigation.NavRoutes
import com.strucnjak.kindergarden.ui.screens.LoginScreen
import com.strucnjak.kindergarden.ui.screens.MapScreen
import com.strucnjak.kindergarden.ui.screens.ProfileScreen
import com.strucnjak.kindergarden.ui.screens.RegisterScreen
import com.strucnjak.kindergarden.ui.screens.RankingsScreen
import com.strucnjak.kindergarden.ui.screens.SearchScreen
import com.strucnjak.kindergarden.ui.theme.KindergardenTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppRoot() {
    KindergardenTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            // Ask for notifications permission on T+
            if (Build.VERSION.SDK_INT >= 33) {
                val notifPerm = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
                LaunchedEffect(Unit) { notifPerm.launchPermissionRequest() }
            }
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = NavRoutes.Login.route) {
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
                        onProfile = { nav.navigate(NavRoutes.Profile.route) }
                    )
                }
                composable(NavRoutes.Profile.route) { ProfileScreen(onBack = { nav.popBackStack() }) }
                composable(NavRoutes.Rankings.route) { RankingsScreen() }
                composable(NavRoutes.Search.route) { SearchScreen() }
            }
        }
    }
}
