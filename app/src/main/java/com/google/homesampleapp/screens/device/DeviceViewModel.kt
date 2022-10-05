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

package com.google.homesampleapp.screens.device

import android.content.IntentSender
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningWindow
import com.google.android.gms.home.matter.commissioning.ShareDeviceRequest
import com.google.android.gms.home.matter.common.DeviceDescriptor
import com.google.android.gms.home.matter.common.Discriminator
import com.google.homesampleapp.DISCRIMINATOR
import com.google.homesampleapp.ITERATION
import com.google.homesampleapp.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
import com.google.homesampleapp.PERIODIC_UPDATE_INTERVAL_DEVICE_SCREEN_SECONDS
import com.google.homesampleapp.SETUP_PIN_CODE
import com.google.homesampleapp.TaskStatus
import com.google.homesampleapp.chip.ChipClient
import com.google.homesampleapp.chip.ClustersHelper
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.isDummyDevice
import com.google.homesampleapp.screens.home.DeviceUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/** The ViewModel for the Device Fragment. See [DeviceFragment] for additional information. */
@HiltViewModel
class DeviceViewModel
@Inject
constructor(
    private val devicesRepository: DevicesRepository,
    private val devicesStateRepository: DevicesStateRepository,
    private val chipClient: ChipClient,
    private val clustersHelper: ClustersHelper
) : ViewModel() {

  // Controls whether a periodic ping to the device is enabled or not.
  private var devicePeriodicPingEnabled: Boolean = true

  /** The current status of the share device action sent via [shareDevice]. */
  private val _shareDeviceStatus = MutableLiveData<TaskStatus>(TaskStatus.NotStarted)
  val shareDeviceStatus: LiveData<TaskStatus>
    get() = _shareDeviceStatus

  /** Generic status about actions processed in this screen. */
  private val _statusInfo = MutableLiveData("")
  val statusInfo: LiveData<String>
    get() = _statusInfo

  /**
   * Device Sharing Step 1. Setup the LiveData that holds the IntentSender used to trigger Device
   * Sharing. The IntentSender is returned when calling the shareDevice() API of Google Play
   * Services (GPS) when a Device Sharing flow is initiated in Step 3. An observer of that
   * IntentSender is setup in Step 2.
   */
  private val _shareDeviceIntentSender = MutableLiveData<IntentSender?>()
  val shareDeviceIntentSender: LiveData<IntentSender?>
    get() = _shareDeviceIntentSender

  // -----------------------------------------------------------------------------------------------
  // Device Sharing

  /**
   * Share Device Step 3. Initiates a share device task. The success callback of the
   * commissioningClient.shareDevice() API provides the IntentSender to be used to launch the
   * "ShareDevice" activity in Google Play Services. This viewModel provides two LiveData objects to
   * report on the result of this API call that can then be used by the Fragment who's observing
   * them:
   * 1. [shareDeviceStatus] reports the result of the call which is displayed in the fragment
   * 2. [shareDeviceIntentSender] is the result of the shareDevice() call that can then be used in
   * the Fragment to launch the Google Play Services "Share Device" activity.
   *
   * After using the sender, [consumeShareDeviceIntentSender] should be called to avoid receiving
   * the sender again after a configuration change.
   */
  fun shareDevice(activity: FragmentActivity) {
    // CODELAB: shareDevice
  }

  // Called by the fragment in Step 5 of the Device Sharing flow.
  fun shareDeviceSucceeded(message: String) {
    _shareDeviceStatus.postValue(TaskStatus.Completed(message))
  }

  // Called by the fragment in Step 5 of the Device Sharing flow.
  fun shareDeviceFailed(message: String) {
    _shareDeviceStatus.postValue(TaskStatus.Failed(Throwable(message)))
  }

  /** Consumes the value in [shareDeviceIntentSender] and sets it back to null. */
  private fun consumeShareDeviceIntentSender() {
    _shareDeviceIntentSender.postValue(null)
  }

  /** Updates the status of [shareDeviceStatus] to success with the given message. */
  fun setSharingCompletedStatusText(text: String) {
    _shareDeviceStatus.postValue(TaskStatus.Completed(text))
  }

  // -----------------------------------------------------------------------------------------------
  // Operations on device

  fun removeDevice(deviceId: Long) {
    Timber.d("**************** remove device ****** [${deviceId}]")
    viewModelScope.launch { devicesRepository.removeDevice(deviceId) }
  }

  fun updateDeviceStateOn(deviceUiModel: DeviceUiModel, isOn: Boolean) {
    Timber.d("updateDeviceStateOn: isOn [${isOn}]")
    val deviceId = deviceUiModel.device.deviceId
    viewModelScope.launch {
      if (isDummyDevice(deviceUiModel.device.name)) {
        Timber.d("Handling test device")
        devicesStateRepository.updateDeviceState(deviceId, true, isOn)
      } else {
        // CODELAB: toggle
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Inspect device

  fun inspectDescriptorCluster(deviceUiModel: DeviceUiModel) {
    val nodeId = deviceUiModel.device.deviceId
    val name = deviceUiModel.device.name
    val divider = "-".repeat(20)
    if (isDummyDevice(deviceUiModel.device.name)) {
      Timber.d(
          "Inspect Dummy Device\n${divider} Inspect Dummy Device [${name}] [${nodeId}] $divider" +
              "\n[Device Types List]\nBogus data\n[Server Clusters]\nBogus data\n[Client Clusters]\nBogus data\n[Parts List]\nBogus data")
      Timber.d(
          "Inspect Dummy Device\n${divider} End of Inspect Dummy Device [${name}] [${nodeId}] $divider")
    } else {
      Timber.d("\n${divider} Inspect Device [${name}] [${nodeId}] $divider")
      viewModelScope.launch {
        val partsListAttribute =
            clustersHelper.readDescriptorClusterPartsListAttribute(
                chipClient.getConnectedDevicePointer(nodeId), 0)
        Timber.d("partsListAttribute [${partsListAttribute}]")

        partsListAttribute?.forEach { part ->
          Timber.d("part [$part] is [${part.javaClass}]")
          val endpoint =
              when (part) {
                is Int -> part.toInt()
                else -> return@forEach
              }
          Timber.d("Processing part [$part]")

          val deviceListAttribute =
              clustersHelper.readDescriptorClusterDeviceListAttribute(
                  chipClient.getConnectedDevicePointer(nodeId), endpoint)
          deviceListAttribute.forEach { Timber.d("device attribute: [${it}]") }

          val serverListAttribute =
              clustersHelper.readDescriptorClusterServerListAttribute(
                  chipClient.getConnectedDevicePointer(nodeId), endpoint)
          serverListAttribute.forEach { Timber.d("server attribute: [${it}]") }
        }
      }
    }
  }

  fun inspectApplicationBasicCluster(nodeId: Long) {
    Timber.d("inspectApplicationBasicCluster: nodeId [${nodeId}]")
    viewModelScope.launch {
      val attributeList = clustersHelper.readApplicationBasicClusterAttributeList(nodeId, 1)
      attributeList.forEach { Timber.d("inspectDevice attribute: [$it]") }
    }
  }

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
  // Open commissioning window

  fun openCommissioningWindowUsingOpenPairingWindowWithPin(deviceId: Long) {
    viewModelScope.launch {
      Timber.d("BEGIN openCommissioningWindowUsingOpenPairingWindowWithPin")
      // TODO: Should generate random 64 bit value
      Timber.d("*** calling chipClient.awaitGetDeviceBeingCommissionedPointer ***")
      val connectedDevicePointer = chipClient.awaitGetConnectedDevicePointer(deviceId)
      Timber.d("Calling chipClient.awaitOpenPairingWindowWithPIN")
      val duration = OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
      chipClient.awaitOpenPairingWindowWithPIN(
          connectedDevicePointer, duration, ITERATION, DISCRIMINATOR, SETUP_PIN_CODE)
      Timber.d("END openCommissioningWindowUsingOpenPairingWindowWithPin")
    }
  }

  // TODO: Was not working when tested. Use openCommissioningWindowUsingOpenPairingWindowWithPin
  // for now.
  fun openCommissioningWindowWithAdministratorCommissioningCluster(deviceId: Long) {
    viewModelScope.launch {
      Timber.d("openCommissioningWindowWithAdministratorCommissioningCluster [${deviceId}]")
      val salt = Random.nextBytes(32)
      val timedInvokeTimeoutMs = 10000
      val devicePtr = chipClient.awaitGetConnectedDevicePointer(deviceId)
      val verifier = chipClient.computePaseVerifier(devicePtr, SETUP_PIN_CODE, ITERATION, salt)
      clustersHelper.openCommissioningWindowAdministratorCommissioningCluster(
          deviceId,
          0,
          180,
          verifier.pakeVerifier,
          DISCRIMINATOR,
          ITERATION,
          salt,
          timedInvokeTimeoutMs)
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Task that runs periodically to update the device state.

  fun startDevicePeriodicPing(deviceUiModel: DeviceUiModel) {
    Timber.d(
        "${LocalDateTime.now()} startDevicePeriodicPing every $PERIODIC_UPDATE_INTERVAL_DEVICE_SCREEN_SECONDS seconds")
    devicePeriodicPingEnabled = true
    runDevicePeriodicUpdate(deviceUiModel)
  }

  private fun runDevicePeriodicUpdate(deviceUiModel: DeviceUiModel) {
    viewModelScope.launch {
      while (devicePeriodicPingEnabled) {
        // Do something here on the main thread
        Timber.d("[device ping] begin")
        var isOn = clustersHelper.getDeviceStateOnOffCluster(deviceUiModel.device.deviceId, 1)
        Timber.d("[device ping] response [${isOn}]")
        var isOnline: Boolean
        if (isOn == null) {
          Timber.e("[device ping] failed")
          isOn = false
          isOnline = false
        } else {
          isOnline = true
        }
        devicesStateRepository.updateDeviceState(
            deviceUiModel.device.deviceId, isOnline = isOnline, isOn = isOn)
        delay(PERIODIC_UPDATE_INTERVAL_DEVICE_SCREEN_SECONDS * 1000L)
      }
    }
  }

  fun stopDevicePeriodicPing() {
    devicePeriodicPingEnabled = false
  }
}
