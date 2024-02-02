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