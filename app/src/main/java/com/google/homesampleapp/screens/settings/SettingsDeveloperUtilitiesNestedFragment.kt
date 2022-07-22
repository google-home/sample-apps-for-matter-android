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

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
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
class SettingsDeveloperUtilitiesNestedFragment :
    PreferenceFragmentCompat(), DummyDeviceDialogFragment.DummyDeviceDialogListener {

  @Inject internal lateinit var appPreferenceDataStore: AppPreferenceDataStore
  @Inject internal lateinit var userPreferencesRepository: UserPreferencesRepository

  // The fragment's ViewModel
  private val viewModel: DeveloperUtilitiesViewModel by viewModels()

  // The DialogFragment to capture device information when adding dummy device.
  private val dummyDeviceDialogFragment = DummyDeviceDialogFragment()

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.settings_developer_utiltities_screen, rootKey)
    preferenceManager.preferenceDataStore = appPreferenceDataStore
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

    // Create dummy device.
    val addDummyDevicePreference: Preference? = findPreference("adddummydevice")
    addDummyDevicePreference?.setOnPreferenceClickListener {
      Timber.d("adddummydevice!")
      // Show the Dialog to capture the device information.
      dummyDeviceDialogFragment.show(childFragmentManager, "DummyDeviceDialogFragment")
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

  // "Add Device" button was clicked, which triggered the display of the DummyDeviceDialogFragment.
  // The positive button on that DummyDeviceDialogFragment has been clicked.
  // Process the addition of a dummy device in the app.
  override fun onDialogPositiveClick(deviceType: String, isOnline: Boolean, isOn: Boolean) {
    Timber.d("onDialogPositiveClick deviceType [$deviceType] isOnline [$isOnline] isOn [$isOn]")
    viewModel.addDummyDevice(deviceType, isOnline, isOn)
    requireView()
        .findNavController()
        .navigate(R.id.action_settingsDeveloperUtilitiesFragment_to_homeFragment)
  }
}
