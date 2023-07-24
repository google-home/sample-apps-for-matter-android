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

package com.google.homesampleapp.screens.inspect

import androidx.lifecycle.*
import com.google.homesampleapp.chip.ClustersHelper
import com.google.homesampleapp.chip.DeviceMatterInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

/** The ViewModel for the Inspect Fragment. See [InspectFragment] for additional information. */
@HiltViewModel
class InspectViewModel @Inject constructor(private val clustersHelper: ClustersHelper) :
    ViewModel() {

  /** Introspection device info from the clusters. */
  private val _instrospectionInfo = MutableLiveData<List<DeviceMatterInfo>>()
  val instrospectionInfo: LiveData<List<DeviceMatterInfo>>
    get() = _instrospectionInfo

  // -----------------------------------------------------------------------------------------------
  // Inspect device

  /** Inspect the device information. */
  fun inspectDevice(deviceId: Long) {
    Timber.d("inspectDevice [${deviceId}]")
    viewModelScope.launch {
      try {
        // Introspect the device.
        val deviceMatterInfoList = clustersHelper.fetchDeviceMatterInfo(deviceId)
        _instrospectionInfo.postValue(deviceMatterInfoList)
        if (deviceMatterInfoList.isEmpty()) {
          Timber.d("deviceMatterInfoList is empty")
        } else {
          for (deviceMatterInfo in deviceMatterInfoList) {
            Timber.d("[${deviceMatterInfo}]")
          }
        }
      } catch (e: Exception) {
        Timber.d("*** EXCEPTION GETTING DEVICE MATTER INFO *****")
        Timber.e(e)
        _instrospectionInfo.postValue(emptyList())
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
}
