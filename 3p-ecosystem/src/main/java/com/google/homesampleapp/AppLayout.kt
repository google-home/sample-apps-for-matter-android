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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLayout(navController: NavHostController, appViewModel: AppViewModel = hiltViewModel()) {
  // FIXME[TJ]: What's a proper way to have a shared TopAppBar, but still allow composables
  // to modify its title.
  // What I have below does not work.
  // The idea was to have AppViewModel which is shared in all the screens that want
  // to modify that value. See [HomeScreen].
  val topAppBarTitle by appViewModel.topAppBarTitle.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(text = topAppBarTitle)
          }
        },
        navigationIcon = {
          IconButton(onClick = { navController.navigate("home") }) {
            Icon(Icons.Filled.Home, contentDescription = "Home")
          }
        },
        actions = {
          IconButton(onClick = { navController.navigate("settings") }) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
          }
        },
      )
    }
  ) { innerPadding ->
    AppNavigation(navController, innerPadding)
  }
}
