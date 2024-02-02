package com.google.homesampleapp

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.homesampleapp.screens.commissionable.CommissionableRoute
import com.google.homesampleapp.screens.device.DeviceRoute
import com.google.homesampleapp.screens.home.HomeRoute
import com.google.homesampleapp.screens.inspect.InspectRoute
import com.google.homesampleapp.screens.settings.SettingsDeveloperUtilitiesRoute
import com.google.homesampleapp.screens.settings.SettingsRoute
import com.google.homesampleapp.screens.thread.ThreadRoute

@Composable
fun AppNavigation(
  navController: NavHostController,
  innerPadding: PaddingValues,
  ) {
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
    composable("developer_utilities") {
      SettingsDeveloperUtilitiesRoute(navController, innerPadding)
    }
    composable("commissionable_devices") {
      CommissionableRoute(navController, innerPadding)
    }
    composable("thread") {
      ThreadRoute(navController, innerPadding)
    }
  }
}