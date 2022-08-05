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
import com.google.homesampleapp.*
import com.google.homesampleapp.chip.ClustersHelper
import com.google.homesampleapp.chip.DeviceMatterInfo
import com.google.homesampleapp.data.DevicesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

/** The ViewModel for the Inspect Fragment. See [InspectFragment] for additional information. */
@HiltViewModel
class InspectViewModel
@Inject
constructor(
    private val devicesRepository: DevicesRepository,
    private val clustersHelper: ClustersHelper
) : ViewModel() {

  /** Introspection device info from the clusters. */
  private val _instrospectionInfo = MutableLiveData<List<DeviceMatterInfo>>()
  val instrospectionInfo: LiveData<List<DeviceMatterInfo>>
    get() = _instrospectionInfo

  // -----------------------------------------------------------------------------------------------
  // Inspect device

  /** Inspect the device information. FIXME: is this the same as the other function? */
  fun inspectDevice(deviceId: Long) {
    Timber.d("inspectDevice [${deviceId}]")
    viewModelScope.launch {
      try {
        val currentDevice: Device = devicesRepository.getDevice(deviceId)
        // Introspect the device.
        val deviceMatterInfoList = clustersHelper.fetchDeviceMatterInfo(deviceId, 0)
        _instrospectionInfo.postValue(deviceMatterInfoList)
        for (deviceMatterInfo in deviceMatterInfoList) {
          Timber.d("[${deviceMatterInfo}]")
          // FIXME: Does it make sense to get the descriptor cluster on endpoints other than 0?
          val deviceMatterInfoListEndpoint =
              clustersHelper.fetchDeviceMatterInfo(deviceId, deviceMatterInfo.endpoint)
          if (deviceMatterInfoListEndpoint.isEmpty()) {
            Timber.d("No parts in ${deviceMatterInfo.endpoint}")
          } else {
            for (deviceMatterInfoEndpoint in deviceMatterInfoListEndpoint) {
              Timber.d(
                  "*** FIXME: MATTER DEVICE INFO ENDPOINT [${deviceMatterInfoEndpoint.endpoint}***")
              Timber.d("[${deviceMatterInfoEndpoint}]")
            }
          }
        }
      } catch (e: Exception) {
        Timber.d("*** EXCEPTION GETTING DEVICE MATTER INFO *****")
        Timber.e(e)
        _instrospectionInfo.postValue(emptyList())
      }
    }
  }

  // FIXME: document what the ApplicationBasicCluster is...
  fun inspectApplicationBasicCluster(nodeId: Long) {
    Timber.d("inspectApplicationBasicCluster: nodeId [${nodeId}]")
    viewModelScope.launch {
      val attributeList = clustersHelper.readApplicationBasicClusterAttributeList(nodeId, 1)
      attributeList.forEach { Timber.d("inspectDevice attribute: [$it]") }
    }
  }

  // FIXME: document what the BasicCluster is...
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
