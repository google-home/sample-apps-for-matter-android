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

package com.google.homesampleapp.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.data.UserPreferencesRepository
import com.google.homesampleapp.screens.common.DialogInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class DeveloperUtilitiesViewModel
@Inject
constructor(
  private val devicesRepository: DevicesRepository,
  private val userPreferencesRepository: UserPreferencesRepository,
  private val devicesStateRepository: DevicesStateRepository,
) : ViewModel() {

  // Controls whether the "Message" AlertDialog should be shown in the UI.
  private var _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  val msgDialogInfo: StateFlow<DialogInfo?> = _msgDialogInfo.asStateFlow()

  // Controls whether the "Show Log Repos" AlertDialog should be shown in the UI.
  private var _showLogReposDialog = MutableStateFlow(false)
  val showLogReposDialog: StateFlow<Boolean> = _showLogReposDialog.asStateFlow()

  // -----------------------------------------------------------------------------------------------
  // Log repositories

  fun printRepositories() {
    _showLogReposDialog.value = true
    viewModelScope.launch {
      val divider = "-".repeat(20)
      val userPreferences = userPreferencesRepository.getData()
      Timber.d(
        "UserPreferences Repository\n${divider} [UserPreferences Repository] ${divider}\n${userPreferences}\n${divider} End of [UserPreferences Repository] $divider"
      )
      val devices = devicesRepository.getAllDevices()
      Timber.d(
        "Devices Repository\n${divider} [Devices Repository] ${divider}\n${devices}\n${divider} End of [Devices Repository] $divider"
      )
      val devicesState = devicesStateRepository.getAllDevicesState()
      Timber.d(
        "DevicesState Repository\n${divider} [DevicesState Repository] ${divider}\n${devicesState}\n${divider} End of [DevicesState Repository] $divider"
      )
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Permissions handling for scanning commissionable devices

  fun logScanningPermissions(context: Context) {
    val permissions = getRequiredScanningPermissions()
    permissions.forEach { permission ->
      Timber.d(
        "Permission [${permission}] Granted [${
          ContextCompat.checkSelfPermission(
            context,
            permission,
          ) == PackageManager.PERMISSION_GRANTED
        }]"
      )
    }
  }

  fun allScanningPermissionsGranted(context: Context): Boolean {
    return getRequiredScanningPermissions().all {
      ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
  }

  fun getRequiredScanningPermissions(): Array<String> {
    Timber.d("getRequiredScanningPermissions(): Build.VERSION.SDK_INT is ${Build.VERSION.SDK_INT}")
    return when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION)
      else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
  }

  // -----------------------------------------------------------------------------------------------
  // State related functions

  fun showMsgDialog(title: String, msg: String) {
    _msgDialogInfo.value = DialogInfo(title, msg)
  }

  // Called after user dismisses the Info dialog. If we don't consume, a config change redisplays
  // the alert dialog.
  fun dismissMsgDialog() {
    _msgDialogInfo.value = null
  }

  fun dismissLogRepositoriesDialog() {
    _showLogReposDialog.value = false
  }
}
