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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.homesampleapp.Device
import com.google.homesampleapp.screens.home.DeviceUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Class responsible for the information related to the currently selected device. */
@HiltViewModel
class SelectedDeviceViewModel @Inject constructor() : ViewModel() {

  // The deviceId currently selected, made available as LiveData.
  // TODO: Since we are using Flows and StateFlows elsewhere, recommendation is to use them here as
  //  well for consistency (and because LiveData is falling out of fashion anyway).
  private val _selectedDeviceIdLiveData = MutableLiveData(-1L)
  val selectedDeviceIdLiveData: LiveData<Long> = _selectedDeviceIdLiveData

  // The device currently selected, made available as LiveData.
  private val _selectedDeviceLiveData =
      MutableLiveData(DeviceUiModel(Device.getDefaultInstance(), isOnline = false, isOn = false))
  val selectedDeviceLiveData: LiveData<DeviceUiModel> = _selectedDeviceLiveData

  fun setSelectedDevice(deviceUiModel: DeviceUiModel) {
    _selectedDeviceIdLiveData.value = deviceUiModel.device.deviceId
    _selectedDeviceLiveData.value = deviceUiModel
  }

  fun resetSelectedDevice() {
    _selectedDeviceIdLiveData.value = -1L
    _selectedDeviceLiveData.value = null
  }
}
