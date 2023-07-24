/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.homesampleapp.data

import android.content.Context
import com.google.homesampleapp.Device
import com.google.homesampleapp.Devices
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Singleton repository that updates and persists the set of devices in the homesampleapp fabric.
 */
@Singleton
class DevicesRepository @Inject constructor(@ApplicationContext context: Context) {

  // The datastore managed by DevicesRepository.
  private val devicesDataStore = context.devicesDataStore

  // The Flow to read data from the DataStore.
  val devicesFlow: Flow<Devices> =
      devicesDataStore.data.catch { exception ->
        // dataStore.data throws an IOException when an error is encountered when reading data
        if (exception is IOException) {
          Timber.e(exception, "Error reading devices.")
          emit(Devices.getDefaultInstance())
        } else {
          throw exception
        }
      }

  suspend fun incrementAndReturnLastDeviceId(): Long {
    val newLastDeviceId = devicesFlow.first().lastDeviceId + 1
    Timber.d("incrementAndReturnLastDeviceId(): newLastDeviceId [${newLastDeviceId}] ")
    devicesDataStore.updateData { devices ->
      devices.toBuilder().setLastDeviceId(newLastDeviceId).build()
    }
    return newLastDeviceId
  }

  suspend fun addDevice(device: Device) {
    Timber.d("addDevice: device [${device}]")
    devicesDataStore.updateData { devices -> devices.toBuilder().addDevices(device).build() }
  }

  suspend fun updateDevice(device: Device) {
    Timber.d("updateDevice: device [${device}]")
    val index = getIndex(device.deviceId)
    devicesDataStore.updateData { devices -> devices.toBuilder().setDevices(index, device).build() }
  }

  suspend fun updateDeviceType(deviceId: Long, deviceType: Device.DeviceType) {
    Timber.d("updateDeviceType: deviceId [${deviceId}] deviceType [${deviceType}]")
    val (index, device) = getIndexAndDevice(deviceId)
    if (index == null) {
      Timber.e(
          "Unable to get device information to update its type: deviceId [${deviceId}] deviceType [${deviceType}]")
      return
    }
    val deviceBuilder = Device.newBuilder(device)
    deviceBuilder.deviceType = deviceType
    devicesDataStore.updateData { devices ->
      devices.toBuilder().setDevices(index, deviceBuilder.build()).build()
    }
  }

  suspend fun removeDevice(deviceId: Long) {
    Timber.d("removeDevice: device [${deviceId}]")
    val index = getIndex(deviceId)
    if (index == -1) {
      throw Exception("Device not found: $deviceId")
    }
    devicesDataStore.updateData { devicesList ->
      devicesList.toBuilder().removeDevices(index).build()
    }
  }

  suspend fun getLastDeviceId(): Long {
    return devicesFlow.first().lastDeviceId
  }

  suspend fun getDevice(deviceId: Long): Device {
    val devices = devicesFlow.first()
    val index = getIndex(devices, deviceId)
    if (index == -1) {
      throw Exception("Device not found: $deviceId")
    }
    return devices.getDevices(index)
  }

  suspend fun getAllDevices(): Devices {
    return devicesFlow.first()
  }

  suspend fun clearAllData() {
    devicesDataStore.updateData { devicesList -> devicesList.toBuilder().clear().build() }
  }

  private suspend fun getIndex(deviceId: Long): Int {
    val devices = devicesFlow.first()
    return getIndex(devices, deviceId)
  }

  private fun getIndex(devices: Devices, deviceId: Long): Int {
    val devicesCount = devices.devicesCount
    for (index in 0 until devicesCount) {
      val device = devices.getDevices(index)
      if (deviceId == device.deviceId) {
        return index
      }
    }
    return -1
  }

  private suspend fun getIndexAndDevice(deviceId: Long): Pair<Int?, Device?> {
    val devices = devicesFlow.first()
    return getIndexAndDevice(devices, deviceId)
  }

  private fun getIndexAndDevice(devices: Devices, deviceId: Long): Pair<Int?, Device?> {
    val devicesCount = devices.devicesCount
    for (index in 0 until devicesCount) {
      val device = devices.getDevices(index)
      if (deviceId == device.deviceId) {
        return index to device
      }
    }
    return null to null
  }
}
