package com.google.homesampleapp

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.homesampleapp.screens.device.DeviceRoute
import com.google.homesampleapp.screens.home.HomeRoute
import com.google.homesampleapp.screens.inspect.InspectRoute
import com.google.homesampleapp.screens.settings.SettingsRoute

@Composable
fun AppNavigation(navController: NavHostController, innerPadding: PaddingValues) {
  NavHost(navController = navController, startDestination = "home") {
    composable("home") {
      HomeRoute(navController, innerPadding)
    }
    composable(
      "device/{deviceId}",
        arguments = listOf(navArgument("deviceId") { type = NavType.LongType }))
    {
      DeviceRoute(navController, innerPadding, it.arguments?.getLong("deviceId")!!)
    }
    composable(
      "inspect/{deviceId}",
      arguments = listOf(navArgument("deviceId") { type = NavType.LongType }))
    {
      InspectRoute(navController, innerPadding, it.arguments?.getLong("deviceId")!!)
    }
    composable("settings") {
      SettingsRoute(navController, innerPadding)
    }

  }
}