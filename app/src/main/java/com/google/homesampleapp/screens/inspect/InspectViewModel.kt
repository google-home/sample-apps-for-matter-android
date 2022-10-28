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
import com.google.homesampleapp.chip.DeviceEndpointInfo
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
  private val _introspectionInfo = MutableLiveData<List<DeviceEndpointInfo>>()
  val introspectionInfo: LiveData<List<DeviceEndpointInfo>>
    get() = _introspectionInfo

  // -----------------------------------------------------------------------------------------------
  // Inspect device

  /** Inspect the device information. FIXME: is this the same as the other function? */
  fun inspectDevice(deviceId: Long) {
    Timber.d("inspectDevice [${deviceId}]")
    viewModelScope.launch {
      try {
        val currentDevice: Device = devicesRepository.getDevice(deviceId)
        // Introspect the device.
        //val deviceMatterInfoList = clustersHelper.fetchAllDeviceEndpointsInfo(deviceId)
        //Timber.d("*** deviceMatterInfoList: [${deviceMatterInfoList}] *****")
        val fabricsInfo = clustersHelper.fetchAllFabricsInfo(deviceId)
        Timber.d("*** fabricsInfo: [${fabricsInfo}] *****")
        // FIXME have the fragment show the fabg
        //_introspectionInfo.postValue(deviceMatterInfoList)
      } catch (e: Exception) {
        Timber.d("*** EXCEPTION GETTING DEVICE MATTER INFO *****")
        Timber.e(e)
        _introspectionInfo.postValue(emptyList())
      }
    }
  }

  // FIXME: document what the ApplicationBasicCluster is...
  fun inspectApplicationBasicCluster(nodeId: Long) {
    Timber.d("inspectApplicationBasicCluster: nodeId [${nodeId}]")
    viewModelScope.launch {
      val attributeList = clustersHelper.readApplicationBasicClusterAttributeList(nodeId, 0)
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
