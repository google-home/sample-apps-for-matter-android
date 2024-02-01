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

package com.google.homesampleapp.screens.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.homesampleapp.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Class responsible for the information related to the user preferences.
 *
 * This is a shared ViewModel as multiple fragments in the app can update these user preferences and
 * be interested in observing its data.
 */
@HiltViewModel
class UserPreferencesViewModel
@Inject
constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

  // Controls whether the "Codelab" AlertDialog should be shown in the UI.
  private var _showCodelabAlertDialog = MutableStateFlow<Boolean>(false)
  val showCodelabAlertDialog: StateFlow<Boolean> = _showCodelabAlertDialog.asStateFlow()

  // Controls whether the "Offline" devices should be shown in the UI.
  private var _showOfflineDevices = MutableStateFlow<Boolean>(false)
  val showOfflineDevices: StateFlow<Boolean> = _showOfflineDevices.asStateFlow()

  init {
    viewModelScope.launch {
      val userPreferences = userPreferencesRepository.getData()
      _showCodelabAlertDialog.value = !userPreferences.hideCodelabInfo
      _showOfflineDevices.value = !userPreferences.hideOfflineDevices
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Model data accessors

  fun updateHideCodelabInfo(value: Boolean) {
    viewModelScope.launch {
      userPreferencesRepository.updateHideCodelabInfo(value)
      _showCodelabAlertDialog.value = value
    }
  }
}
