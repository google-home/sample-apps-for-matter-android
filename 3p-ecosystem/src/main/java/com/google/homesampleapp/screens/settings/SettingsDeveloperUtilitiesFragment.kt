/*
 * Copyright 2022 Google LLC
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

package com.google.homesampleapp.screens.settings

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.homesampleapp.R
import com.google.homesampleapp.databinding.FragmentDeveloperUtilitiesSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preference
import timber.log.Timber

/** Shows Developer Utilities . */
@AndroidEntryPoint
class SettingsDeveloperUtilitiesFragment : Fragment() {

  // The fragment's ViewModel
  private val viewModel: DeveloperUtilitiesViewModel by viewModels()

  private lateinit var binding: FragmentDeveloperUtilitiesSettingsBinding
  private lateinit var scanningPermissionsLauncher: ActivityResultLauncher<Array<String>>

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {

    binding =
      DataBindingUtil.inflate<FragmentDeveloperUtilitiesSettingsBinding>(
        inflater,
        R.layout.fragment_developer_utilities_settings,
        container,
        false
      ).apply {
        composeView.apply {
          // Dispose the Composition when the view's LifecycleOwner is destroyed
          setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
          setContent {
            MaterialTheme {
              ProvidePreferenceLocals {
                SettingsDeveloperUtilitiesScreen()
              }
            }
          }
        }
      }
    setupUiElements()

    scanningPermissionsLauncher =
      registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.any { granted -> !granted }) {
          // TODO --> error dialog and then go back to settings
          Timber.d("*** scanningPermissionsLauncher: Permissions were not granted.")
        } else {
          Timber.d("*** scanningPermissionsLauncher: Permissions were OK!")
          findNavController().navigate(R.id.action_settingsDeveloperUtilitiesFragment_to_discoveryFragment)
        }
      }

    return binding.root
  }

  private fun setupUiElements() {
    binding.topAppBar.setNavigationOnClickListener {
      // navigate back.
      Timber.d("topAppBar.setNavigationOnClickListener()")
      findNavController().popBackStack()
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Composables

  @Composable
  private fun SettingsDeveloperUtilitiesScreen(
  ) {
    var showLogReposDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize() /*contentPadding = contentPadding*/) {
      preference(
        key = "commissionable_devices_preference",
        icon = {
          Icon(
            painter = painterResource(id = R.drawable.ic_baseline_search_24),
            contentDescription = null // decorative element
          )
        },
        title = { Text(text = "Commissionable devices") },
        summary = { Text(text = "Discover commissionable Matter devices") },
        onClick = {
          logScanningPermissions()
          if (!allScanningPermissionsGranted()) {
            Timber.d("All scanning permissions NOT granted. Asking for them.")
            scanningPermissionsLauncher.launch(getRequiredScanningPermissions())
          } else {
            // Check if Bluetooth is enabled.
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter.isEnabled) {
              findNavController()
                .navigate(R.id.action_settingsDeveloperUtilitiesFragment_to_discoveryFragment)
            } else {
              Timber.d("Blutooth is not enabled) --> DISPLAY AN ERROR DIALOG") // FIXME
            }
          }
        }
      )
      preference(
        key = "thread_preference",
        icon = {
          Icon(
            painter = painterResource(id = R.drawable.baseline_device_hub_24),
            contentDescription = null // decorative element
          )
        },
        title = { Text(text = "Thread network") },
        summary = { Text(text = "Information about the thread network") },
        onClick = {
          findNavController().navigate(R.id.action_settingsDeveloperUtilitiesFragment_to_threadFragment)
        }
      )
      preference(
        key = "logrepos_preference",
        icon = {
          Icon(
            painter = painterResource(id = R.drawable.ic_outline_storage_24),
            contentDescription = null // decorative element
          )
        },
        title = { Text(text = "Log repositories content") },
        summary = { Text(text = "View in the logs the content of repositories used in the app") },
        onClick = { showLogReposDialog = true }
      )
    }
    if (showLogReposDialog) {
      viewModel.printRepositories()
      HtmlInfoDialog(
        "Repositories logged",
        getString(R.string.log_repos_info),
        onClick = { showLogReposDialog = false })
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Permissions handling

  private fun allScanningPermissionsGranted() =
    getRequiredScanningPermissions().all {
      ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

  private fun logScanningPermissions() {
    val permissions = getRequiredScanningPermissions()
    permissions.forEach { permission ->
      Timber.d(
        "Permission [${permission}] Granted [${
          ContextCompat.checkSelfPermission(
            requireContext(),
            permission
          ) == PackageManager.PERMISSION_GRANTED
        }]"
      )
    }
  }

  private fun getRequiredScanningPermissions(): Array<String> {
    Timber.d("getRequiredScanningPermissions(): Build.VERSION.SDK_INT is ${Build.VERSION.SDK_INT}")
    return when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        arrayOf(
          Manifest.permission.BLUETOOTH_SCAN,
          Manifest.permission.ACCESS_FINE_LOCATION,
        )

      else ->
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
  }
}
