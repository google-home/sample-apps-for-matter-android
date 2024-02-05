/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.homesampleapp

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

// Constants for Navigation destinations
const val DEST_HOME = "home"
const val DEST_DEVICE = "device"
const val DEST_INSPECT = "inspect"
const val DEST_SETTINGS = "settings"
const val DEST_DEVELOPER_UTILITIES = "developer_utilities"
const val DEST_COMMISSIONABLE_DEVICES = "commissionable_devices"
const val DEST_THREAD = "thread"

@Composable
fun AppNavigation(
  navController: NavHostController,
  innerPadding: PaddingValues,
  updateTitle: (title: String) -> Unit,
  ) {
  // Lambdas to all destinations needed in our various routes.
  // [Top level Route Composables should not be passed the navController explicitly,
  // as NavController is an unstable type. Indirection like a lambda should be used
  // as the compiler considers lambdas stable.]
  val navigateToHome: () -> Unit = remember {
    { navController.navigate(DEST_HOME) }
  }
  val navigateToDevice: (deviceId: Long) -> Unit = remember {
    { navController.navigate("device/$it") }
  }
  val navigateToInspect: (deviceId: Long) -> Unit = remember {
    { navController.navigate("inspect/$it") }
  }
  val navigateToDeveloperUtilities: () -> Unit = remember {
    { navController.navigate(DEST_DEVELOPER_UTILITIES) }
  }
  val navigateToCommissionables: () -> Unit = remember {
    { navController.navigate(DEST_COMMISSIONABLE_DEVICES) }
  }
  val navigateToThread: () -> Unit = remember {
    { navController.navigate(DEST_THREAD) }
  }

  NavHost(navController = navController, startDestination = DEST_HOME) {
    // Home
    composable("home") { backStackEntry ->
        HomeRoute(innerPadding, updateTitle, navigateToDevice)
    }
    // Device
    composable(
      "$DEST_DEVICE/{deviceId}",
        arguments = listOf(navArgument("deviceId") { type = NavType.LongType }))
    {
      DeviceRoute(
        innerPadding,
        updateTitle,
        navigateToHome,
        navigateToInspect,
        it.arguments?.getLong("deviceId")!!)
    }
    // Inspect device
    composable(
      "$DEST_INSPECT/{deviceId}",
      arguments = listOf(navArgument("deviceId") { type = NavType.LongType }))
    {
      InspectRoute(innerPadding, updateTitle, it.arguments?.getLong("deviceId")!!)
    }
    // Settings
    composable(DEST_SETTINGS) {
      SettingsRoute(innerPadding, updateTitle, navigateToDeveloperUtilities)
    }
    // Developer Utilities
    composable(DEST_DEVELOPER_UTILITIES) {
      SettingsDeveloperUtilitiesRoute(innerPadding, updateTitle, navigateToCommissionables, navigateToThread)
    }
    // Commissionable devices
    composable(DEST_COMMISSIONABLE_DEVICES) {
      CommissionableRoute(innerPadding, updateTitle)
    }
    // Thread network utilities
    composable(DEST_THREAD) {
      ThreadRoute(innerPadding, updateTitle)
    }
  }
}