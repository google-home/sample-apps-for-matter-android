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
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.homesampleapp.R
import com.google.homesampleapp.data.AppPreferenceDataStore
import com.google.homesampleapp.data.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class SettingsDeveloperUtilitiesNestedFragment : PreferenceFragmentCompat() {

  @Inject internal lateinit var appPreferenceDataStore: AppPreferenceDataStore
  @Inject internal lateinit var userPreferencesRepository: UserPreferencesRepository

  // The fragment's ViewModel
  private val viewModel: DeveloperUtilitiesViewModel by viewModels()

  private lateinit var scanningPermissionsLauncher: ActivityResultLauncher<Array<String>>

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.settings_developer_utiltities_screen, rootKey)
    preferenceManager.preferenceDataStore = appPreferenceDataStore

    scanningPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
          if (results.values.any { granted -> !granted }) {
            // TODO --> error dialog and then go back to settings
            Timber.d("*** scanningPermissionsLauncher: Permissions were not granted.")
          } else {
            Timber.d("*** scanningPermissionsLauncher: Permissions were OK!")
            findNavController()
                .navigate(R.id.action_settingsDeveloperUtilitiesFragment_to_discoveryFragment)
          }
        }
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    val view = super.onCreateView(inflater, container, savedInstanceState)
    setupUiElements()
    setupObservers()
    return view
  }

  private fun setupUiElements() {
    // Discover commissionable Matter devices.
    val discoverPreference: Preference? = findPreference("commissionable")
    discoverPreference?.setOnPreferenceClickListener {
      Timber.d("discoverPreference onPreferenceClickListener()")
      logScanningPermissions()
      if (!allScanningPermissionsGranted()) {
        Timber.d("All scanning permissions NOT granted. Asking for them.")
        scanningPermissionsLauncher.launch(getRequiredScanningPermissions())
        true
      } else {
        // Check if Bluetooth is enabled.
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter.isEnabled) {
          findNavController()
              .navigate(R.id.action_settingsDeveloperUtilitiesFragment_to_discoveryFragment)
          true
        } else {
          Timber.d("Blutooth is not enabled) --> DISPLAY AN ERROR DIALOG") // FIXME
          true
        }
      }
    }

    // Thread network.
    val threadPreference: Preference? = findPreference("thread")
    threadPreference?.setOnPreferenceClickListener {
      Timber.d("threadPreference onPreferenceClickListener()")
      findNavController().navigate(R.id.action_settingsDeveloperUtilitiesFragment_to_threadFragment)
      true
    }

    // Log Repositories.
    val logReposPreference: Preference? = findPreference("logrepos")
    logReposPreference?.setOnPreferenceClickListener {
      Timber.d("logrepos!")
      viewModel.printRepositories()
      MaterialAlertDialogBuilder(requireContext())
          .setTitle("Repositories logged")
          .setMessage(Html.fromHtml(getString(R.string.log_repos_info), Html.FROM_HTML_MODE_LEGACY))
          .setPositiveButton(resources.getString(R.string.ok)) { _, _ ->
            // Nothing to do
          }
          .show()
      true
    }
  }

  private fun setupObservers() {
    // The user preferences in the datastore.
    userPreferencesRepository.userPreferencesLiveData.observe(viewLifecycleOwner) { userPreferences
      ->
      val useGpsForDeviceSharingCommissioningPref: SwitchPreferenceCompat? =
          findPreference("devicesharingcommissioning")
    }
  }

  // -----------------------------------------------------------------------------------------------
  // DeviceInfoDialogListener interface

  private fun allScanningPermissionsGranted() =
      getRequiredScanningPermissions().all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
      }

  private fun logScanningPermissions() {
    val permissions = getRequiredScanningPermissions()
    permissions.forEach { permission ->
      Timber.d(
          "Permission [${permission}] Granted [${ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED}]")
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
