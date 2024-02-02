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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLayout(
  navController: NavHostController,
  appViewModel: AppViewModel = hiltViewModel()
) {
  val topAppBarTitle by appViewModel.topAppBarTitle.collectAsState()

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center
            ) {
              Text(
                text = topAppBarTitle
              )
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
          }
        )
      }
    ) { innerPadding ->
      AppNavigation(navController, innerPadding)
    }
  }