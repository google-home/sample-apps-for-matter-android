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
import android.os.SystemClock
import androidx.activity.result.ActivityResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import chip.devicecontroller.model.NodeState
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import com.google.android.gms.home.matter.commissioning.DeviceInfo
import com.google.android.gms.home.matter.commissioning.SharedDeviceData
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.*
import com.google.homesampleapp.Device
import com.google.homesampleapp.Devices
import com.google.homesampleapp.DevicesState
import com.google.homesampleapp.ErrorInfo
import com.google.homesampleapp.MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS
import com.google.homesampleapp.PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS
import com.google.homesampleapp.STATE_CHANGES_MONITORING_MODE
import com.google.homesampleapp.StateChangesMonitoringMode
import com.google.homesampleapp.TaskStatus
import com.google.homesampleapp.UserPreferences
import com.google.homesampleapp.chip.ChipClient
import com.google.homesampleapp.chip.ClustersHelper
import com.google.homesampleapp.chip.MatterConstants.OnOffAttribute
import com.google.homesampleapp.chip.SubscriptionHelper
import com.google.homesampleapp.commissioning.AppCommissioningService
import com.google.homesampleapp.convertToAppDeviceType
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.data.UserPreferencesRepository
import com.google.homesampleapp.getTimestampForNow
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
    private val chipClient: ChipClient,
    private val subscriptionHelper: SubscriptionHelper,
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

  /** IntentSender LiveData triggered by [commissionDevice]. */
  private val _commissionDeviceIntentSender = MutableLiveData<IntentSender?>()
  val commissionDeviceIntentSender: LiveData<IntentSender?>
    get() = _commissionDeviceIntentSender

  /** An error occurred. Let the fragment know about it. */
  private val _errorLiveData = MutableLiveData<ErrorInfo?>()
  val errorLiveData: LiveData<ErrorInfo?>
    get() = _errorLiveData

  // The last device id used for devices commissioned on the app's fabric.
  private var lastDeviceId = 0L

  // -----------------------------------------------------------------------------------------------
  // Repositories handling.

  // The initial setup event which triggers the Home fragment to get the data
  // it needs for its screen.
  // TODO: Clarify if this is really necessary and how that works?
  init {
    liveData { emit(devicesRepository.getAllDevices()) }
    liveData { emit(devicesStateRepository.getAllDevicesState()) }
    liveData { emit(userPreferencesRepository.getData()) }
  }

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
  //
  // See "docs/Google Home Mobile SDK.pdf" for a good overview of all the artifacts needed
  // to transfer control from the sample app's UI to the GPS CommissionDevice UI, and get a result
  // back.

  /**
   * Commission Device Step 2 (part 2). Triggered by the "Commission Device" button in the fragment.
   * Initiates a commission device task. The success callback of the
   * commissioningClient.commissionDevice() API provides the IntentSender to be used to launch the
   * "Commission Device" activity in Google Play Services. This viewModel provides two LiveData
   * objects to report on the result of this API call that can then be used by the Fragment who's
   * observing them:
   * 1. [commissionDeviceStatus] updates the fragment's UI according to the TaskStatus
   * 2. [commissionDeviceIntentSender] is the IntentSender to be used in the Fragment to launch the
   *    Google Play Services "Commission Device" activity.
   *
   * See [consumeCommissionDeviceIntentSender()] for proper management of the IntentSender in the
   * face of configuration changes that repost LiveData.
   */
  // CODELAB: commissionDevice
  fun commissionDevice(context: Context) {
    Timber.d("CommissionDevice: starting")
    _commissionDeviceStatus.postValue(TaskStatus.InProgress)

    val commissionDeviceRequest =
        CommissioningRequest.builder()
            .setCommissioningService(ComponentName(context, AppCommissioningService::class.java))
            .build()

    // The call to commissionDevice() creates the IntentSender that will eventually be launched
    // in the fragment to trigger the commissioning activity in GPS.
    Matter.getCommissioningClient(context)
        .commissionDevice(commissionDeviceRequest)
        .addOnSuccessListener { result ->
          Timber.d("ShareDevice: Success getting the IntentSender: result [${result}]")
          // Communication with fragment is via livedata
          _commissionDeviceIntentSender.postValue(result)
        }
        .addOnFailureListener { error ->
          Timber.e(error)
          _commissionDeviceStatus.postValue(
              TaskStatus.Failed("Setting up the IntentSender failed", error))
        }
  }
  // CODELAB SECTION END

  /**
   * Sample app has been invoked for multi-admin commissionning. TODO: Can we do it without going
   * through GMSCore? All we're missing is network location.
   */
  fun multiadminCommissioning(intent: Intent, context: Context) {
    Timber.d("multiadminCommissioning: starting")
    _commissionDeviceStatus.postValue(TaskStatus.InProgress)

    val sharedDeviceData = SharedDeviceData.fromIntent(intent)
    Timber.d("multiadminCommissioning: sharedDeviceData [${sharedDeviceData}]")
    Timber.d("multiadminCommissioning: manualPairingCode [${sharedDeviceData.manualPairingCode}]")

    val commissionRequestBuilder =
        CommissioningRequest.builder()
            .setCommissioningService(ComponentName(context, AppCommissioningService::class.java))

    // EXTRA_COMMISSIONING_WINDOW_EXPIRATION is a hint of how much time is remaining in the
    // commissioning window for multi-admin. It is based on the current system uptime.
    // If the user takes too long to select the target commissioning app, then there's not
    // enougj time to complete the multi-admin commissioning and we message it to the user.
    val commissioningWindowExpirationMillis =
        intent.getLongExtra(EXTRA_COMMISSIONING_WINDOW_EXPIRATION, -1L)
    val currentUptimeMillis = SystemClock.elapsedRealtime()
    val timeLeftSeconds = (commissioningWindowExpirationMillis - currentUptimeMillis) / 1000
    Timber.d(
        "commissionDevice: TargetCommissioner for MultiAdmin. " +
            "uptime [${currentUptimeMillis}] " +
            "commissioningWindowExpiration [${commissioningWindowExpirationMillis}] " +
            "-> expires in ${timeLeftSeconds} seconds")

    if (commissioningWindowExpirationMillis == -1L) {
      Timber.e(
          "EXTRA_COMMISSIONING_WINDOW_EXPIRATION not specified in multi-admin call. " +
              "Still going ahead with the multi-admin though.")
    } else if (timeLeftSeconds < MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS) {
      _errorLiveData.value =
          ErrorInfo(
              title = "Commissioning Window Expiration",
              message =
                  "The commissioning window will " +
                      "expire in ${timeLeftSeconds} seconds, not long enough to complete the commissioning.\n\n" +
                      "In the future, please select the target commissioning application faster to avoid this situation.")
      return
    }

    val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
    commissionRequestBuilder.setDeviceNameHint(deviceName)

    val vendorId = intent.getIntExtra(EXTRA_VENDOR_ID, -1)
    val productId = intent.getIntExtra(EXTRA_PRODUCT_ID, -1)
    val deviceType = intent.getIntExtra(EXTRA_DEVICE_TYPE, -1)
    val deviceInfo = DeviceInfo.builder().setProductId(productId).setVendorId(vendorId).build()
    commissionRequestBuilder.setDeviceInfo(deviceInfo)

    val manualPairingCode = intent.getStringExtra(EXTRA_MANUAL_PAIRING_CODE)
    commissionRequestBuilder.setOnboardingPayload(manualPairingCode)

    val commissioningRequest = commissionRequestBuilder.build()

    Timber.d(
        "multiadmin: commissioningRequest " +
            "onboardingPayload [${commissioningRequest.onboardingPayload}] " +
            "vendorId [${commissioningRequest.deviceInfo!!.vendorId}] " +
            "productId [${commissioningRequest.deviceInfo!!.productId}]")

    Matter.getCommissioningClient(context)
        .commissionDevice(commissioningRequest)
        .addOnSuccessListener { result ->
          // Communication with fragment is via livedata
          _commissionDeviceStatus.postValue(TaskStatus.InProgress)
          _commissionDeviceIntentSender.postValue(result)
        }
        .addOnFailureListener { error ->
          Timber.e(error)
          _commissionDeviceStatus.postValue(
              TaskStatus.Failed("Failed to to get the IntentSender.", error))
        }
  }

  // CODELAB FEATURED BEGIN
  /**
   * Consumes the value in [_commissionDeviceIntentSender] and sets it back to null. Needs to be
   * called to avoid re-processing the IntentSender after a configuration change (where the LiveData
   * is re-posted.
   */
  fun consumeCommissionDeviceIntentSender() {
    _commissionDeviceIntentSender.postValue(null)
  }
  // CODELAB FEATURED END

  // Called by the fragment in Step 5 of the Device Commissioning flow when the GPS activity
  // for commissioning the device has succeeded.
  fun commissionDeviceSucceeded(activityResult: ActivityResult, deviceName: String) {
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

    // Add the device to the devices repository.
    viewModelScope.launch {
      val deviceId = result.token?.toLong()!!
      // read device's vendor name and product name
      val vendorName =
          try {
            clustersHelper.readBasicClusterVendorNameAttribute(deviceId)
          } catch (ex: Exception) {
            Timber.e(ex, "Failed to read VendorName attribute")
            ""
          }

      val productName =
          try {
            clustersHelper.readBasicClusterProductNameAttribute(deviceId)
          } catch (ex: Exception) {
            Timber.e(ex, "Failed to read ProductName attribute")
            ""
          }

      try {
        Timber.d("Commissioning: Adding device to repository")
        devicesRepository.addDevice(
            Device.newBuilder()
                .setName(deviceName) // default name that can be overridden by user in next step
                .setDeviceId(deviceId)
                .setDateCommissioned(getTimestampForNow())
                .setVendorId(result.commissionedDeviceDescriptor.vendorId.toString())
                .setVendorName(vendorName)
                .setProductId(result.commissionedDeviceDescriptor.productId.toString())
                .setProductName(productName)
                // Note that deviceType is now deprecated. Need to get it by introspecting
                // the device information. This is done below.
                .setDeviceType(
                    convertToAppDeviceType(result.commissionedDeviceDescriptor.deviceType.toLong()))
                .build())
        Timber.d("Commissioning: Adding device state to repository: isOnline:true isOn:false")
        devicesStateRepository.addDeviceState(deviceId, isOnline = true, isOn = false)
        _commissionDeviceStatus.postValue(
            TaskStatus.Completed("Device added: [${deviceId}] [${deviceName}]"))
      } catch (e: Exception) {
        Timber.e("Adding device [${deviceId}] [${deviceName}] to app's repository failed", e)
        _commissionDeviceStatus.postValue(
            TaskStatus.Failed(
                "Adding device [${deviceId}] [${deviceName}] to app's repository failed", e))
      }

      // Introspect the device and update its deviceType.
      // TODO: Need to get capabilities information and store that in the devices repository.
      // (e.g on/off on which endpoint).
      val deviceMatterInfoList = clustersHelper.fetchDeviceMatterInfo(deviceId)
      Timber.d("*** MATTER DEVICE INFO ***")
      var gotDeviceType = false
      deviceMatterInfoList.forEach { deviceMatterInfo ->
        Timber.d("Processing endpoint [$deviceMatterInfo.endpoint]")
        // Endpoint 0 is the Root Node, so we disregard it.
        if (deviceMatterInfo.endpoint != 0) {
          if (gotDeviceType) {
            // TODO: Handle this properly once we have specific examples to learn from.
            Timber.w(
                "The device has more than one endpoint. We're simply using the first one to define the device type.")
            return@forEach
          }
          if (deviceMatterInfo.types.size > 1) {
            // TODO: Handle this properly once we have specific examples to learn from.
            Timber.w(
                "The endpoint has more than one type. We're simply using the first one to define the device type.")
          }
          devicesRepository.updateDeviceType(
              deviceId, convertToAppDeviceType(deviceMatterInfo.types.first()))
          gotDeviceType = true
        }
      }

      // update device name
      try {
        clustersHelper.writeBasicClusterNodeLabelAttribute(deviceId, deviceName)
      } catch (ex: Exception) {
        Timber.d(ex, "Failed to write NodeLabel")
      }
    }
  }

  // Called by the fragment in Step 5 of the Device Commissioning flow when the GPS activity for
  // commissioning the device has failed.
  fun commissionDeviceFailed(resultCode: Int) {
    Timber.d("CommissionDevice: Failed [${resultCode}")
    _commissionDeviceStatus.postValue(
        TaskStatus.Failed("Commission device failed [${resultCode}]", null))
  }

  /** Updates the status of [commissionDeviceStatus] to success with the given message. */
  fun setCommissioningCompletedStatusText(text: String) {
    _commissionDeviceStatus.postValue(TaskStatus.Completed(text))
  }

  fun updateDeviceStateOn(deviceUiModel: DeviceUiModel, isOn: Boolean) {
    Timber.d("updateDeviceStateOn: Device [${deviceUiModel}]  isOn [${isOn}]")
    viewModelScope.launch {
      Timber.d("Handling real device")
      clustersHelper.setOnOffDeviceStateOnOffCluster(deviceUiModel.device.deviceId, isOn, 1)
      devicesStateRepository.updateDeviceState(deviceUiModel.device.deviceId, true, isOn)
    }
  }

  // Called after we dismiss an error dialog. If we don't consume, a config change redisplays the
  // alert dialog.
  fun consumeErrorLiveData() {
    _errorLiveData.postValue(null)
  }

  // -----------------------------------------------------------------------------------------------
  // State Changes Monitoring

  /**
   * The way we monitor state changes is defined by constant [StateChangesMonitoringMode].
   * [StateChangesMonitoringMode.Subscription] is the preferred mode.
   * [StateChangesMonitoringMode.PeriodicRead] was used initially because of issues with
   * subscriptions. We left its associated code as it could be useful to some developers.
   */
  fun startMonitoringStateChanges() {
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> subscribeToDevicesPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> startDevicesPeriodicPing()
    }
  }

  fun stopMonitoringStateChanges() {
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> unsubscribeToDevicesPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> stopDevicesPeriodicPing()
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Subscription to periodic device updates.
  // See:
  //   - Spec section "8.5 Subscribe Interaction"
  //   - Matter primer:
  //
  // https://developers.home.google.com/matter/primer/interaction-model-reading#subscription_transaction

  private fun subscribeToDevicesPeriodicUpdates() {
    Timber.d("subscribeToDevicesPeriodicUpdates()")
    viewModelScope.launch {
      // For each one of the real devices
      val devicesList = devicesRepository.getAllDevices().devicesList
      devicesList.forEach { device ->
        val reportCallback =
            object : SubscriptionHelper.ReportCallbackForDevice(device.deviceId) {
              override fun onReport(nodeState: NodeState) {
                super.onReport(nodeState)
                // TODO: See HomeViewModel:CommissionDeviceSucceeded for device capabilities
                val onOffState =
                    subscriptionHelper.extractAttribute(nodeState, 1, OnOffAttribute) as Boolean?
                Timber.d("onOffState [${onOffState}]")
                if (onOffState == null) {
                  Timber.e("onReport(): WARNING -> onOffState is NULL. Ignoring.")
                  return
                }
                viewModelScope.launch {
                  devicesStateRepository.updateDeviceState(
                      device.deviceId, isOnline = true, isOn = onOffState)
                }
              }
            }

        try {
          val connectedDevicePointer = chipClient.getConnectedDevicePointer(device.deviceId)
          subscriptionHelper.awaitSubscribeToPeriodicUpdates(
              connectedDevicePointer,
              SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(device.deviceId),
              SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(device.deviceId),
              reportCallback)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer for ${device.deviceId}.")
          return@forEach
        }
      }
    }
  }

  private fun unsubscribeToDevicesPeriodicUpdates() {
    Timber.d("unsubscribeToPeriodicUpdates()")
    viewModelScope.launch {
      // For each one of the real devices
      val devicesList = devicesRepository.getAllDevices().devicesList
      devicesList.forEach { device ->
        try {
          val connectedDevicePtr = chipClient.getConnectedDevicePointer(device.deviceId)
          subscriptionHelper.awaitUnsubscribeToPeriodicUpdates(connectedDevicePtr)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer for ${device.deviceId}.")
          return@forEach
        }
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Task that runs periodically to update the devices state.

  private fun startDevicesPeriodicPing() {
    if (PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS == -1) {
      return
    }
    Timber.d(
        "${LocalDateTime.now()} startDevicesPeriodicPing every $PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS seconds")
    devicesPeriodicPingEnabled = true
    runDevicesPeriodicPing()
  }

  private fun runDevicesPeriodicPing() {
    viewModelScope.launch {
      while (devicesPeriodicPingEnabled) {
        // For each one of the real devices
        val devicesList = devicesRepository.getAllDevices().devicesList
        devicesList.forEach { device ->
          Timber.d("runDevicesPeriodicPing deviceId [${device.deviceId}]")
          var isOn = clustersHelper.getDeviceStateOnOffCluster(device.deviceId, 1)
          val isOnline: Boolean
          if (isOn == null) {
            Timber.e("runDevicesPeriodicUpdate: cannot get device on/off state -> OFFLINE")
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
        delay(PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS * 1000L)
      }
    }
  }

  private fun stopDevicesPeriodicPing() {
    devicesPeriodicPingEnabled = false
  }
}
