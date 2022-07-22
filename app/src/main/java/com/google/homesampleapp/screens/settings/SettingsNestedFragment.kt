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
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.navigation.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.homesampleapp.R
import com.google.homesampleapp.VERSION_NAME
import com.google.homesampleapp.data.AppPreferenceDataStore
import com.google.homesampleapp.data.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class SettingsNestedFragment :
    PreferenceFragmentCompat(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

  @Inject internal lateinit var appPreferenceDataStore: AppPreferenceDataStore
  @Inject internal lateinit var userPreferencesRepository: UserPreferencesRepository

  // Help and Feedback dialog.
  private lateinit var helpAndFeedbackAlertDialog: AlertDialog

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    // Enable the custom data store for the entire preferences hierarchy.
    setPreferencesFromResource(R.xml.settings_preferences_screen, rootKey)
    preferenceManager.preferenceDataStore = appPreferenceDataStore
  }

  override fun onPreferenceStartFragment(
      caller: PreferenceFragmentCompat,
      pref: Preference
  ): Boolean {
    view
        ?.findNavController()
        ?.navigate(R.id.action_settingsFragment_to_settingsDeveloperUtilitiesFragment)
    return true
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
    // Help and Feedback AlertDialog
    helpAndFeedbackAlertDialog =
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Help and Feedback")
            .setMessage(
                Html.fromHtml(getString(R.string.help_and_feedback), Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(resources.getString(R.string.ok)) { _, _ ->
              // Respond to positive button press
            }
            .create()

    // Help and Feedback
    val helpAndFeedbackAppPreference: Preference? = findPreference("help_and_feedback")
    helpAndFeedbackAppPreference?.setOnPreferenceClickListener {
      showHelpAndFeedbackAlertDialog()
      true
    }

    // About this app
    val aboutThisAppPreference: Preference? = findPreference("about_app")
    aboutThisAppPreference?.setOnPreferenceClickListener {
      Timber.d("about_app!")
      MaterialAlertDialogBuilder(requireContext())
          .setTitle("About this app")
          .setMessage(
              Html.fromHtml(
                  getString(R.string.about_app, VERSION_NAME), Html.FROM_HTML_MODE_LEGACY))
          .setPositiveButton(resources.getString(R.string.ok)) { _, _ ->
            // Nothing to do.
          }
          .show()
      true
    }
  }

  // Show the Help and Feedback AlertDialog.
  private fun showHelpAndFeedbackAlertDialog() {
    helpAndFeedbackAlertDialog.show()
    // Make the hyperlink clickable. Must be set after show().
    val msgTextView: TextView? = helpAndFeedbackAlertDialog.findViewById(android.R.id.message)
    msgTextView?.movementMethod = LinkMovementMethod.getInstance()
  }

  private fun setupObservers() {
    userPreferencesRepository.userPreferencesLiveData.observe(viewLifecycleOwner) { userPreferences
      ->
      Timber.d(
          "userPreferencesRepository.userPreferencesLiveData.observe called\n[${userPreferences}]")
      Timber.d(
          "Setting codelab [${!userPreferences.hideCodelabInfo}] offline_devices [${!userPreferences.hideOfflineDevices}]")
      // This is for "show" (inverse of the "hide" proto value).
      val codelabPref: SwitchPreferenceCompat? = findPreference("codelab")
      codelabPref?.isChecked = !userPreferences.hideCodelabInfo
      // This is for "show" (inverse of the "hide" proto value).
      val offlineDevicesPref: SwitchPreferenceCompat? = findPreference("offline_devices")
      offlineDevicesPref?.isChecked = !userPreferences.hideOfflineDevices
    }
  }
}
