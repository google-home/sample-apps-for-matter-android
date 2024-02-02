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

package com.google.homesampleapp.screens.inspect

import androidx.lifecycle.*
import com.google.homesampleapp.chip.ClustersHelper
import com.google.homesampleapp.chip.DeviceMatterInfo
import com.google.homesampleapp.screens.common.DialogInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** The ViewModel for the [InspectScreen]. */
@HiltViewModel
class InspectViewModel @Inject constructor(private val clustersHelper: ClustersHelper) :
  ViewModel() {

  // The introspection info fetched from the device.
  private var _deviceMatterInfoList = MutableStateFlow<List<DeviceMatterInfo>?>(null)
  val deviceMatterInfoList: StateFlow<List<DeviceMatterInfo>?> = _deviceMatterInfoList.asStateFlow()

  // Controls whether the "Message" AlertDialog should be shown in the UI.
  private var _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  val msgDialogInfo: StateFlow<DialogInfo?> = _msgDialogInfo.asStateFlow()

  // -----------------------------------------------------------------------------------------------
  // Inspect device

  /** Inspect the device information. */
  fun inspectDevice(deviceId: Long) {
    Timber.d("inspectDevice [${deviceId}]")
    viewModelScope.launch {
      try {
        // Introspect the device.
        _deviceMatterInfoList.value = clustersHelper.fetchDeviceMatterInfo(deviceId)
        Timber.d("after fetch...")
      } catch (e: Exception) {
        Timber.e("*** EXCEPTION GETTING DEVICE MATTER INFO *****", e)
        _deviceMatterInfoList.value = emptyList()
        showMsgDialog("Error introspecting the device", e.toString())
      }
    }
  }

  // TODO: document what the ApplicationBasicCluster is...
  fun inspectApplicationBasicCluster(nodeId: Long) {
    Timber.d("inspectApplicationBasicCluster: nodeId [${nodeId}]")
    viewModelScope.launch {
      val attributeList = clustersHelper.readApplicationBasicClusterAttributeList(nodeId, 1)
      attributeList.forEach { Timber.d("inspectDevice attribute: [$it]") }
    }
  }

  // TODO: document what the BasicCluster is...
  fun inspectBasicCluster(deviceId: Long) {
    Timber.d("inspectBasicCluster: deviceId [${deviceId}]")
    viewModelScope.launch {
      val vendorId = clustersHelper.readBasicClusterVendorIDAttribute(deviceId, 0)
      Timber.d("vendorId [${vendorId}]")

      val attributeList = clustersHelper.readBasicClusterAttributeList(deviceId, 0)
      Timber.d("attributeList [${attributeList}]")
    }
  }

  // -----------------------------------------------------------------------------------------------
  // UI State update

  private fun showMsgDialog(title: String?, msg: String?, showConfirmButton: Boolean = true) {
    Timber.d("showMsgDialog [$title]")
    _msgDialogInfo.value = DialogInfo(title, msg, showConfirmButton)
  }

  // Called after user dismiss the Info dialog. If we don't consume, a config change redisplays the
  // alert dialog.
  fun dismissMsgDialog() {
    Timber.d("dismissMsgDialog()")
    _msgDialogInfo.value = null
  }
}
