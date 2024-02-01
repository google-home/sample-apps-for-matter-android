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
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLayout(
  navController: NavHostController
) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center
            ) {
              Text(
                text = "Sample App"
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
      AppNavigation(navController = navController, innerPadding = innerPadding)
    }
  }