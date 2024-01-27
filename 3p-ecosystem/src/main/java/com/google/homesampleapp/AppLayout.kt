package com.google.homesampleapp

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLayout(
  navController: NavHostController
) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("My App") }
        )
      }
    ) { innerPadding ->
      AppNavigation(navController = navController, innerPadding = innerPadding)
    }
  }