package com.strucnjak.kindergarden.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Login : NavRoutes("login")
    data object Register : NavRoutes("register")
    data object Map : NavRoutes("map")
    data object Profile : NavRoutes("profile")
    data object Rankings : NavRoutes("rankings")
    data object Search : NavRoutes("search")
    data object ParkList : NavRoutes("parkList")
}
