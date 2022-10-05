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

package com.google.homesampleapp.screens.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import androidx.lifecycle.*
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import com.google.android.gms.home.matter.commissioning.DeviceInfo
import com.google.homesampleapp.*
import com.google.homesampleapp.chip.ClustersHelper
import com.google.homesampleapp.commissioning.AppCommissioningService
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Encapsulates all of the information on a specific device. Note that the app currently only
 * supports Matter devices with server attribute "ON/OFF".
 */
data class DeviceUiModel(
    // Device information that is persisted in a Proto DataStore. See DevicesRepository.
    val device: Device,

    // Device state information that is retrieved dynamically.
    // Whether the device is online or offline.
    var isOnline: Boolean,
    // Whether the device is on or off.
    var isOn: Boolean
)

/**
 * UI model that encapsulates the information about the devices to be displayed on the Home screen.
 */
data class DevicesUiModel(
    // The list of devices.
    val devices: List<DeviceUiModel>,
    // Making it so default is false, so that codelabinfo is not shown when we have not gotten
    // the userpreferences data yet.
    val showCodelabInfo: Boolean,
    // Whether offline devices should be shown.
    val showOfflineDevices: Boolean
)

/** The ViewModel for the Home Fragment. See [HomeFragment] for additional information. */
@HiltViewModel
internal class HomeViewModel
@Inject
constructor(
    private val devicesRepository: DevicesRepository,
    private val devicesStateRepository: DevicesStateRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val clustersHelper: ClustersHelper,
) : ViewModel() {

  // Controls whether a periodic ping to the devices is enabled or not.
  private var devicesPeriodicPingEnabled: Boolean = true

  /** The current status of the commission device action sent via [commissionDevice]. */
  private val _commissionDeviceStatus = MutableLiveData<TaskStatus>(TaskStatus.NotStarted)
  val commissionDeviceStatus: LiveData<TaskStatus>
    get() = _commissionDeviceStatus

  /** Generic status about actions processed in this screen. */
  private val _statusInfo = MutableLiveData("")
  val statusInfo: LiveData<String>
    get() = _statusInfo

  /** The IntentSender triggered by [commissionDevice]. */
  private val _commissionDeviceIntentSender = MutableLiveData<IntentSender?>()
  val commissionDeviceIntentSender: LiveData<IntentSender?>
    get() = _commissionDeviceIntentSender

  // The last device id used for devices commissioned on the app's fabric.
  private var lastDeviceId = 0L

  // -----------------------------------------------------------------------------------------------
  // Repositories handling.

  // The initial setup event which triggers the Home fragment to get the data
  // it needs for its screen.
  // TODO: Clarify if this is really necessary and how that works?
  val initialSetupEventDevices = liveData { emit(devicesRepository.getAllDevices()) }
  val initialSetupEventDevicesState = liveData { emit(devicesStateRepository.getAllDevicesState()) }
  val initialSetupEventUserPreferences = liveData { emit(userPreferencesRepository.getData()) }

  private val devicesFlow = devicesRepository.devicesFlow
  private val devicesStateFlow = devicesStateRepository.devicesStateFlow
  private val userPreferencesFlow = userPreferencesRepository.userPreferencesFlow

  // Every time the list of devices or user preferences are updated (emit is triggered),
  // we recreate the DevicesUiModel
  private val devicesUiModelFlow =
      combine(devicesFlow, devicesStateFlow, userPreferencesFlow) {
          devices: Devices,
          devicesStates: DevicesState,
          userPreferences: UserPreferences ->
        Timber.d("*** devicesUiModelFlow changed ***")
        return@combine DevicesUiModel(
            devices = processDevices(devices, devicesStates, userPreferences),
            showCodelabInfo = !userPreferences.hideCodelabInfo,
            showOfflineDevices = !userPreferences.hideOfflineDevices)
      }

  val devicesUiModelLiveData = devicesUiModelFlow.asLiveData()

  // Indicates whether the codelab dialog has already been shown.
  // If the pref is true to show the codelab dialog, we still only want to show it once,
  // when the Home screen is first shown.
  var codelabDialogHasBeenShown = false

  private fun processDevices(
      devices: Devices,
      devicesStates: DevicesState,
      userPreferences: UserPreferences
  ): List<DeviceUiModel> {
    val devicesUiModel = ArrayList<DeviceUiModel>()
    devices.devicesList.forEach { device ->
      Timber.d("processDevices() deviceId: [${device.deviceId}]}")
      val state = devicesStates.devicesStateList.find { it.deviceId == device.deviceId }
      if (userPreferences.hideOfflineDevices) {
        if (state?.online != true) return@forEach
      }
      if (state == null) {
        Timber.d("    deviceId setting default value for state")
        devicesUiModel.add(DeviceUiModel(device, isOnline = false, isOn = false))
      } else {
        Timber.d("    deviceId setting its own value for state")
        devicesUiModel.add(DeviceUiModel(device, state.online, state.on))
      }
    }
    return devicesUiModel
  }

  // -----------------------------------------------------------------------------------------------
  // Commission Device

  /**
   * Commission Device Step 3. Initiates a commission device task. The success callback of the
   * commissioningClient.commissionDevice() API provides the IntentSender to be used to launch the
   * "Commission Device" activity in Google Play Services. This viewModel provides two LiveData
   * objects to report on the result of this API call that can then be used by the Fragment who's
   * observing them:
   * 1. [commissionDeviceStatus] reports the result of the call which is displayed in the fragment
   * 2. [commissionDeviceIntentSender] is the result of the commissionDevice() call that can then be
   * used in the Fragment to launch the Google Play Services "Commission Device" activity.
   *
   * After using the sender, [consumeCommissionDeviceIntentSender()] should be called to avoid
   * receiving the sender again after a configuration change.
   */
  // CODELAB: commissionDevice
  fun commissionDevice(intent: Intent, context: Context) {
    Timber.d("commissionDevice")
    _commissionDeviceStatus.postValue(TaskStatus.InProgress)

    val isMultiAdminCommissioning = isMultiAdminCommissioning(intent)

    val commissionRequestBuilder =
        CommissioningRequest.builder()
            .setCommissioningService(ComponentName(context, AppCommissioningService::class.java))
    if (isMultiAdminCommissioning) {
      val deviceName = intent.getStringExtra("com.google.android.gms.home.matter.EXTRA_DEVICE_NAME")
      commissionRequestBuilder.setDeviceNameHint(deviceName)

      val vendorId = intent.getIntExtra("com.google.android.gms.home.matter.EXTRA_VENDOR_ID", -1)
      val productId = intent.getIntExtra("com.google.android.gms.home.matter.EXTRA_PRODUCT_ID", -1)
      val deviceType =
          intent.getIntExtra("com.google.android.gms.home.matter.EXTRA_DEVICE_Type", -1)
      val deviceInfo = DeviceInfo.builder().setProductId(productId).setVendorId(vendorId).build()
      commissionRequestBuilder.setDeviceInfo(deviceInfo)

      val manualPairingCode =
          intent.getStringExtra("com.google.android.gms.home.matter.EXTRA_MANUAL_PAIRING_CODE")
      commissionRequestBuilder.setOnboardingPayload(manualPairingCode)
    }
    val commissioningRequest = commissionRequestBuilder.build()

    Matter.getCommissioningClient(context)
        .commissionDevice(commissioningRequest)
        .addOnSuccessListener { result ->
          // Communication with fragment is via livedata
          _commissionDeviceStatus.postValue(TaskStatus.InProgress)
          _commissionDeviceIntentSender.postValue(result)
        }
        .addOnFailureListener { error ->
          _commissionDeviceStatus.postValue(TaskStatus.Failed(error))
          Timber.e(error)
        }
  }
  // CODELAB SECTION END

  /** Consumes the value in [_commissionDeviceIntentSender] and sets it back to null. */
  fun consumeCommissionDeviceIntentSender() {
    _commissionDeviceIntentSender.postValue(null)
  }

  // Called by the fragment in Step 5 of the Device Commissioning flow.
  fun commissionDeviceSucceeded(activityResult: ActivityResult, message: String) {
    val result =
        CommissioningResult.fromIntentSenderResult(activityResult.resultCode, activityResult.data)
    Timber.i("Device commissioned successfully! deviceName [${result.deviceName}]")
    Timber.i("Device commissioned successfully! room [${result.room}]")
    Timber.i(
        "Device commissioned successfully! DeviceDescriptor of device:\n" +
            "deviceType [${result.commissionedDeviceDescriptor.deviceType}]\n" +
            "productId [${result.commissionedDeviceDescriptor.productId}]\n" +
            "vendorId [${result.commissionedDeviceDescriptor.vendorId}]\n" +
            "hashCode [${result.commissionedDeviceDescriptor.hashCode()}]")

    // Update the data in the devices repository.
    viewModelScope.launch {
      try {
        val deviceId = result.token?.toLong()!!
        val currentDevice: Device = devicesRepository.getDevice(deviceId)
        val roomName =
            result.room?.name // needed 'cause smartcast impossible with open/custom getter
        val updatedDeviceBuilder =
            Device.newBuilder(currentDevice)
                .setDeviceType(
                    convertToAppDeviceType(result.commissionedDeviceDescriptor.deviceType))
                .setProductId(result.commissionedDeviceDescriptor.productId.toString())
                .setVendorId(result.commissionedDeviceDescriptor.vendorId.toString())
        if (result.deviceName != null) updatedDeviceBuilder.name = result.deviceName
        if (roomName != null) updatedDeviceBuilder.room = roomName
        devicesRepository.updateDevice(updatedDeviceBuilder.build())
        _commissionDeviceStatus.postValue(TaskStatus.Completed(message))
      } catch (e: Exception) {
        Timber.e(e)
      }
    }
  }

  // Called by the fragment in Step 5 of the Device Commissioning flow.
  fun commissionDeviceFailed(message: String) {
    _commissionDeviceStatus.postValue(TaskStatus.Failed(Throwable(message)))
  }

  /** Updates the status of [commissionDeviceStatus] to success with the given message. */
  fun setCommissioningCompletedStatusText(text: String) {
    _commissionDeviceStatus.postValue(TaskStatus.Completed(text))
  }

  fun updateDeviceStateOn(deviceUiModel: DeviceUiModel, isOn: Boolean) {
    Timber.d("updateDeviceStateOn: Device [${deviceUiModel}]  isOn [${isOn}]")
    viewModelScope.launch {
      if (isDummyDevice(deviceUiModel.device.name)) {
        Timber.d("Handling test device")
        devicesStateRepository.updateDeviceState(deviceUiModel.device.deviceId, true, isOn)
      } else {
        Timber.d("Handling real device")
        clustersHelper.setOnOffDeviceStateOnOffCluster(deviceUiModel.device.deviceId, isOn, 1)
        devicesStateRepository.updateDeviceState(deviceUiModel.device.deviceId, true, isOn)
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Task that runs periodically to update the devices state.

  fun startDevicesPeriodicPing() {
    Timber.d(
        "${LocalDateTime.now()} startDevicesPeriodicPing every $PERIODIC_UPDATE_INTERVAL_HOME_SCREEN_SECONDS seconds")
    devicesPeriodicPingEnabled = true
    runDevicesPeriodicPing()
  }

  private fun runDevicesPeriodicPing() {
    viewModelScope.launch {
      while (devicesPeriodicPingEnabled) {
        // For each ne of the real devices
        val devicesList = devicesRepository.getAllDevices().devicesList
        devicesList.forEach { device ->
          if (device.name.startsWith(DUMMY_DEVICE_NAME_PREFIX)) {
            return@forEach
          }
          Timber.d("runDevicesPeriodicPing deviceId [${device.deviceId}]")
          var isOn = clustersHelper.getDeviceStateOnOffCluster(device.deviceId, 1)
          val isOnline: Boolean
          if (isOn == null) {
            Timber.e("runDevicesPeriodicUpdate: flakiness with mDNS")
            isOn = false
            isOnline = false
          } else {
            isOnline = true
          }
          Timber.d("runDevicesPeriodicPing deviceId [${device.deviceId}] [${isOnline}] [${isOn}]")
          // TODO: only need to do it if state has changed
          devicesStateRepository.updateDeviceState(
              device.deviceId, isOnline = isOnline, isOn = isOn)
        }
        delay(PERIODIC_UPDATE_INTERVAL_HOME_SCREEN_SECONDS * 1000L)
      }
    }
  }

  fun stopDevicesPeriodicPing() {
    devicesPeriodicPingEnabled = false
  }
}
