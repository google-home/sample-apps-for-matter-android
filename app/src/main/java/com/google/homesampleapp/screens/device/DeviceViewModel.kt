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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningWindow
import com.google.android.gms.home.matter.commissioning.ShareDeviceRequest
import com.google.android.gms.home.matter.common.DeviceDescriptor
import com.google.android.gms.home.matter.common.Discriminator
import com.google.homesampleapp.BackgroundWorkAlertDialogAction
import com.google.homesampleapp.DISCRIMINATOR
import com.google.homesampleapp.ITERATION
import com.google.homesampleapp.OPEN_COMMISSIONING_WINDOW_API
import com.google.homesampleapp.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
import com.google.homesampleapp.OpenCommissioningWindowApi
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

  /**
   * The current status of the share device task. The enum it is based on is used by the Fragment to
   * properly react to the processing happening with the share device task.
   */
  private val _shareDeviceStatus = MutableLiveData<TaskStatus>(TaskStatus.NotStarted)
  val shareDeviceStatus: LiveData<TaskStatus>
    get() = _shareDeviceStatus

  /**
   * Actions that drive showing/hiding a "background work" alert dialog. The enum it is based on is
   * used by the Fragment to properly react on the management of that dialog.
   */
  private val _backgroundWorkAlertDialogAction =
      MutableLiveData<BackgroundWorkAlertDialogAction>(BackgroundWorkAlertDialogAction.Hide)
  val backgroundWorkAlertDialogAction: LiveData<BackgroundWorkAlertDialogAction>
    get() = _backgroundWorkAlertDialogAction

  /** IntentSender LiveData triggered by [shareDevice]. */
  private val _shareDeviceIntentSender = MutableLiveData<IntentSender?>()
  val shareDeviceIntentSender: LiveData<IntentSender?>
    get() = _shareDeviceIntentSender

  // -----------------------------------------------------------------------------------------------
  // Device Sharing (aka Multi-Admin)
  //
  // See "docs/Google Home Mobile SDK.pdf" for a good overview of all the artifacts needed
  // to transfer control from the sample app's UI to the GPS ShareDevice UI, and get a result back.

  /**
   * Share Device Step 2 (part 2). Triggered by the "Share Device" button in the fragment. Initiates
   * a share device task. The success callback of the commissioningClient.shareDevice() API provides
   * the IntentSender to be used to launch the "Share Device" activity in Google Play Services. This
   * viewModel provides two LiveData objects to report on the result of this API call that can then
   * be used by the Fragment who's observing them:
   * 1. [shareDeviceStatus] updates the fragment's UI according to the TaskStatus
   * 2. [shareDeviceIntentSender] is the IntentSender to be used in the Fragment to launch the
   * Google Play Services "Share Device" activity (step 3).
   *
   * See [consumeShareDeviceIntentSender()] for proper management of the IntentSender in the face of
   * configuration changes that repost LiveData.
   */
  fun shareDevice(activity: FragmentActivity, deviceId: Long) {
    Timber.d("ShareDevice: starting")
    stopDevicePeriodicPing()
    _shareDeviceStatus.postValue(TaskStatus.InProgress)
    _backgroundWorkAlertDialogAction.postValue(
        BackgroundWorkAlertDialogAction.Show(
            "Opening Pairing Window", "This may take a few seconds."))

    viewModelScope.launch {
      // First we need to open a commissioning window.
      try {
        when (OPEN_COMMISSIONING_WINDOW_API) {
          OpenCommissioningWindowApi.ChipDeviceController ->
              openCommissioningWindowUsingOpenPairingWindowWithPin(deviceId)
          OpenCommissioningWindowApi.AdministratorCommissioningCluster ->
              openCommissioningWindowWithAdministratorCommissioningCluster(deviceId)
        }
      } catch (e: Throwable) {
        val msg = "Failed to open the commissioning window"
        Timber.d("ShareDevice: ${msg} [${e}]")
        _backgroundWorkAlertDialogAction.postValue(BackgroundWorkAlertDialogAction.Hide)
        _shareDeviceStatus.postValue(TaskStatus.Failed(msg, e))
        return@launch
      }

      // Second, we get the IntentSender and post it as LiveData for the fragment to pick it up
      // and trigger the GPS ShareDevice activity.
      // CODELAB: shareDevice
      Timber.d("ShareDevice: Setting up the IntentSender")
      val shareDeviceRequest =
          ShareDeviceRequest.builder()
              .setDeviceDescriptor(DeviceDescriptor.builder().build())
              .setDeviceName("temp device name")
              .setCommissioningWindow(
                  CommissioningWindow.builder()
                      .setDiscriminator(Discriminator.forLongValue(DISCRIMINATOR))
                      .setPasscode(SETUP_PIN_CODE)
                      .setWindowOpenMillis(SystemClock.elapsedRealtime())
                      .setDurationSeconds(OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS.toLong())
                      .build())
              .build()

      Timber.d(
          "ShareDevice: shareDeviceRequest " +
              "onboardingPayload [${shareDeviceRequest.commissioningWindow.passcode}] " +
              "discriminator [${shareDeviceRequest.commissioningWindow.discriminator}]")

      // The call to shareDevice() creates the IntentSender that will eventually be launched
      // in the fragment to trigger the multi-admin activity in GPS (step 3).
      Matter.getCommissioningClient(activity)
          .shareDevice(shareDeviceRequest)
          .addOnSuccessListener { result ->
            Timber.d("ShareDevice: Success getting the IntentSender: result [${result}]")
            // Communication with fragment is via livedata
            _backgroundWorkAlertDialogAction.postValue(BackgroundWorkAlertDialogAction.Hide)
            _shareDeviceIntentSender.postValue(result)
          }
          .addOnFailureListener { error ->
            Timber.e(error)
            _backgroundWorkAlertDialogAction.postValue(BackgroundWorkAlertDialogAction.Hide)
            _shareDeviceStatus.postValue(
                TaskStatus.Failed("Setting up the IntentSender failed", error))
          }
      // CODELAB SECTION END
    }
  }

  // CODELAB FEATURED BEGIN
  /**
   * Consumes the value in [_shareDeviceIntentSender] and sets it back to null. Needs to be called
   * to avoid re-processing an IntentSender after a configuration change where the LiveData is
   * re-posted.
   */
  fun consumeShareDeviceIntentSender() {
    _shareDeviceIntentSender.postValue(null)
  }
  // CODELAB FEATURED END

  // Called by the fragment in Step 5 of the Device Sharing flow when the GPS activity for
  // Device Sharing has succeeded.
  fun shareDeviceSucceeded(deviceUiModel: DeviceUiModel) {
    _shareDeviceStatus.postValue(TaskStatus.Completed("Device sharing completed successfully"))
    startDevicePeriodicPing(deviceUiModel)
  }

  // Called by the fragment in Step 5 of the Device Sharing flow when the GPS activity for
  // Device Sharing has failed.
  fun shareDeviceFailed(deviceUiModel: DeviceUiModel, resultCode: Int) {
    Timber.d("ShareDevice: Failed with errorCode [${resultCode}]")
    _shareDeviceStatus.postValue(TaskStatus.Failed("Device sharing failed [${resultCode}]", null))
    startDevicePeriodicPing(deviceUiModel)
  }

  // -----------------------------------------------------------------------------------------------
  // Operations on device

  fun removeDevice(deviceId: Long) {
    Timber.d("Removing device [${deviceId}]")
    // TODO: send message to device to unlink.
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
        Timber.d("Handling real device")
        try {
          clustersHelper.setOnOffDeviceStateOnOffCluster(deviceUiModel.device.deviceId, isOn, 1)
          devicesStateRepository.updateDeviceState(deviceUiModel.device.deviceId, true, isOn)
        } catch (e: Throwable) {
          Timber.e("Failed setting on/off state")
        }
        // CODELAB SECTION END
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

  suspend fun openCommissioningWindowUsingOpenPairingWindowWithPin(deviceId: Long) {
    // TODO: Should generate random 64 bit value for SETUP_PIN_CODE (taking into account
    // spec constraints)
    Timber.d("ShareDevice: chipClient.awaitGetConnectedDevicePointer(${deviceId})")
    val connectedDevicePointer = chipClient.awaitGetConnectedDevicePointer(deviceId)
    val duration = OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
    Timber.d(
        "ShareDevice: chipClient.chipClient.awaitOpenPairingWindowWithPIN " +
            "duration [${duration}] iteration [${ITERATION}] discriminator [${DISCRIMINATOR}] " +
            "setupPinCode [${SETUP_PIN_CODE}]")
    chipClient.awaitOpenPairingWindowWithPIN(
        connectedDevicePointer, duration, ITERATION, DISCRIMINATOR, SETUP_PIN_CODE)
    Timber.d("ShareDevice: After chipClient.awaitOpenPairingWindowWithPIN")
  }

  // TODO: Was not working when tested. Use openCommissioningWindowUsingOpenPairingWindowWithPin
  // for now.
  suspend fun openCommissioningWindowWithAdministratorCommissioningCluster(deviceId: Long) {
    Timber.d(
        "ShareDevice: openCommissioningWindowWithAdministratorCommissioningCluster [${deviceId}]")
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

  // -----------------------------------------------------------------------------------------------
  // Task that runs periodically to update the device state.

  fun startDevicePeriodicPing(deviceUiModel: DeviceUiModel) {
    Timber.d(
        "${LocalDateTime.now()} startDevicePeriodicPing every $PERIODIC_UPDATE_INTERVAL_DEVICE_SCREEN_SECONDS seconds")
    devicePeriodicPingEnabled = true
    runDevicePeriodicUpdate(deviceUiModel)
  }

  private fun runDevicePeriodicUpdate(deviceUiModel: DeviceUiModel) {
    if (PERIODIC_UPDATE_INTERVAL_DEVICE_SCREEN_SECONDS == -1) {
      return
    }
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

  // -----------------------------------------------------------------------------------------------
  // Utiltity functions for testing.

  fun testBackgroundWorkAlertDialog(seconds: Int) {
    viewModelScope.launch {
      _backgroundWorkAlertDialogAction.postValue(
          BackgroundWorkAlertDialogAction.Show("Testing", "Delay of ${seconds} seconds"))
      delay(seconds.toLong() * 1000)
      _backgroundWorkAlertDialogAction.postValue(BackgroundWorkAlertDialogAction.Hide)
    }
  }
}
