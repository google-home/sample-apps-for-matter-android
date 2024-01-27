package com.google.homesampleapp

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.homesampleapp.screens.home.HomeScreen
import com.google.homesampleapp.screens.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint

@Composable
fun AppNavigation(navController: NavHostController, innerPadding: PaddingValues) {
  NavHost(navController = navController, startDestination = "home") {
    composable("home") {
      val viewModel = hiltViewModel<HomeViewModel>()
      HomeScreen(navController, viewModel) } // Pass navController down
    // ... other composables ...
  }
}