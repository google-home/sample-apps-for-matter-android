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

package com.google.homesampleapp.screens.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import chip.devicecontroller.AttestationInfo
import chip.devicecontroller.DeviceAttestationDelegate
import chip.devicecontroller.model.NodeState
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import com.google.android.gms.home.matter.commissioning.DeviceInfo
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.*
import com.google.homesampleapp.Device
import com.google.homesampleapp.Devices
import com.google.homesampleapp.DevicesState
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
import com.google.homesampleapp.screens.common.DialogInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

// -----------------------------------------------------------------------------
// Data structures

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
  var isOn: Boolean,
)

/**
 * UI model that encapsulates the information about the devices to be displayed on the Home screen.
 */
data class DevicesListUiModel(
  // The list of devices.
  val devices: List<DeviceUiModel>,
  // Making it so default is false, so that codelabinfo is not shown when we have not gotten
  // the userpreferences data yet.
  val showCodelabInfo: Boolean,
  // Whether offline devices should be shown.
  val showOfflineDevices: Boolean,
)

// -----------------------------------------------------------------------------
// ViewModel

/** The ViewModel for the [HomeScreen]. */
@HiltViewModel
class HomeViewModel
@Inject
constructor(
  private val devicesRepository: DevicesRepository,
  private val devicesStateRepository: DevicesStateRepository,
  private val userPreferencesRepository: UserPreferencesRepository,
  private val clustersHelper: ClustersHelper,
  private val chipClient: ChipClient,
  private val subscriptionHelper: SubscriptionHelper,
) : ViewModel() {

  // Controls whether the "Message" AlertDialog should be shown in the UI.
  private var _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  val msgDialogInfo: StateFlow<DialogInfo?> = _msgDialogInfo.asStateFlow()

  // Controls whether the "New Device" AlertDialog should be shown in the UI.
  private var _showNewDeviceNameAlertDialog = MutableStateFlow(false)
  val showNewDeviceNameAlertDialog: StateFlow<Boolean> = _showNewDeviceNameAlertDialog.asStateFlow()

  /** The current status of multiadmin commissioning. */
  private val _multiadminCommissionDeviceTaskStatus =
    MutableStateFlow<TaskStatus>(TaskStatus.NotStarted)
  val multiadminCommissionDeviceTaskStatus: StateFlow<TaskStatus> =
    _multiadminCommissionDeviceTaskStatus.asStateFlow()

  // Controls whether a Device Attestation failure is ignored or not.
  // FIXME: set to true for now until issues with attestation resolved.
  private var _deviceAttestationFailureIgnored = MutableStateFlow(true)
  val deviceAttestationFailureIgnored: StateFlow<Boolean> =
    _deviceAttestationFailureIgnored.asStateFlow()

  // Controls whether a periodic ping to the devices is enabled or not.
  private var devicesPeriodicPingEnabled: Boolean = true

  // Saves the result of the GPS Commissioning action (step 4).
  // It is then used in step 5 to complete the commissioning.
  private var gpsCommissioningResult: CommissioningResult? = null

  // -----------------------------------------------------------------------------------------------
  // Repositories handling.

  // The initial setup event which triggers the Home screen to get the data it needs.
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
  // we recreate the DevicesListUiModel
  private val devicesListUiModelFlow =
    combine(devicesFlow, devicesStateFlow, userPreferencesFlow) {
      devices: Devices,
      devicesStates: DevicesState,
      userPreferences: UserPreferences ->
      Timber.d("*** devicesListUiModelFlow changed ***")
      return@combine DevicesListUiModel(
        devices = processDevices(devices, devicesStates, userPreferences),
        showCodelabInfo = !userPreferences.hideCodelabInfo,
        showOfflineDevices = !userPreferences.hideOfflineDevices,
      )
    }

  val devicesUiModelLiveData = devicesListUiModelFlow.asLiveData()

  private fun processDevices(
    devices: Devices,
    devicesStates: DevicesState,
    userPreferences: UserPreferences,
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
   * Sample app has been invoked for multi-admin commissionning. TODO: Can we do it without going
   * through GMSCore? All we're missing is network location.
   */
  fun multiadminCommissioning(intent: Intent, context: Context) {
    Timber.d("multiadminCommissioning: starting")

    val sharedDeviceData = fromIntent(intent)
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
        "-> expires in $timeLeftSeconds seconds"
    )

    if (commissioningWindowExpirationMillis == -1L) {
      Timber.e(
        "EXTRA_COMMISSIONING_WINDOW_EXPIRATION not specified in multi-admin call. " +
          "Still going ahead with the multi-admin though."
      )
    } else if (timeLeftSeconds < MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS) {
      showMsgDialog(
        "Commissioning Window Expiration",
        "The commissioning window will " +
          "expire in ${timeLeftSeconds} seconds, not long enough to " +
          "complete the commissioning.\n\n" +
          "In the future, please select the target commissioning application faster " +
          "to avoid this situation.",
      )
      return
    }

    val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
    commissionRequestBuilder.setDeviceNameHint(deviceName)

    val vendorId = intent.getIntExtra(EXTRA_VENDOR_ID, -1)
    val productId = intent.getIntExtra(EXTRA_PRODUCT_ID, -1)
    val deviceInfo = DeviceInfo.builder().setProductId(productId).setVendorId(vendorId).build()
    commissionRequestBuilder.setDeviceInfo(deviceInfo)

    val manualPairingCode = intent.getStringExtra(EXTRA_MANUAL_PAIRING_CODE)
    commissionRequestBuilder.setOnboardingPayload(manualPairingCode)

    val commissioningRequest = commissionRequestBuilder.build()

    Timber.d(
      "multiadmin: commissioningRequest " +
        "onboardingPayload [${commissioningRequest.onboardingPayload}] " +
        "vendorId [${commissioningRequest.deviceInfo!!.vendorId}] " +
        "productId [${commissioningRequest.deviceInfo!!.productId}]"
    )
  }

  // This is step 4 of the commissioning flow where GPS takes over.
  // We save the result we get from GPS, which will be used by commissionedDeviceNameCaptured
  // after the device name is captured.
  fun gpsCommissioningDeviceSucceeded(activityResult: ActivityResult) {
    gpsCommissioningResult =
      CommissioningResult.fromIntentSenderResult(activityResult.resultCode, activityResult.data)
    Timber.i(
      "Device commissioned successfully! deviceName [${gpsCommissioningResult!!.deviceName}]"
    )
    Timber.i("Device commissioned successfully! room [${gpsCommissioningResult!!.room}]")
    Timber.i(
      "Device commissioned successfully! DeviceDescriptor of device:\n" +
        "productId [${gpsCommissioningResult!!.commissionedDeviceDescriptor.productId}]\n" +
        "vendorId [${gpsCommissioningResult!!.commissionedDeviceDescriptor.vendorId}]\n" +
        "hashCode [${gpsCommissioningResult!!.commissionedDeviceDescriptor.hashCode()}]"
    )

    // Now we need to capture the device name.
    _showNewDeviceNameAlertDialog.value = true
  }

  // Called when the device name has been captured in the UI.
  // This follows a successful gps commissioning (see gpsCommissioningDeviceSucceeded)
  fun onCommissionedDeviceNameCaptured(deviceName: String) {
    // Add the device to the devices repository.
    _showNewDeviceNameAlertDialog.value = false
    viewModelScope.launch {
      val deviceId = gpsCommissioningResult?.token?.toLong()!!
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
            .setVendorId(gpsCommissioningResult?.commissionedDeviceDescriptor?.vendorId.toString())
            .setVendorName(vendorName)
            .setProductId(
              gpsCommissioningResult?.commissionedDeviceDescriptor?.productId.toString()
            )
            .setProductName(productName)
            // Note that deviceType is now deprecated. Need to get it by introspecting
            // the device information. This is done below.
            .setDeviceType(
              convertToAppDeviceType(
                gpsCommissioningResult?.commissionedDeviceDescriptor?.deviceType?.toLong()!!
              )
            )
            .build()
        )
        Timber.d("Commissioning: Adding device state to repository: isOnline:true isOn:false")
        devicesStateRepository.addDeviceState(deviceId, isOnline = true, isOn = false)
      } catch (e: Exception) {
        val title = "Adding device to app's repository failed"
        val msg = "Adding device [${deviceId}] [${deviceName}] to app's repository failed."
        Timber.e(msg, e)
        showMsgDialog(title, "$msg\n\n$e")
      }

      // Introspect the device and update its deviceType.
      // TODO: Need to get capabilities information and store that in the devices repository.
      // (e.g on/off on which endpoint).
      Timber.d("onCommissionedDeviceNameCaptured 1")
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
              "The device has more than one endpoint. We're simply using the first one to define the device type."
            )
            return@forEach
          }
          if (deviceMatterInfo.types.size > 1) {
            // TODO: Handle this properly once we have specific examples to learn from.
            Timber.w(
              "The endpoint has more than one type. We're simply using the first one to define the device type."
            )
          }
          devicesRepository.updateDeviceType(
            deviceId,
            convertToAppDeviceType(deviceMatterInfo.types.first()),
          )
          gotDeviceType = true
        }
      }

      // update device name
      try {
        clustersHelper.writeBasicClusterNodeLabelAttribute(deviceId, deviceName)
      } catch (ex: Exception) {
        val title = "Failed to write NodeLabel"
        Timber.e(title, ex)
        showMsgDialog(title, "$ex")
      }
    }
  }

  // Called in Step 5 of the Device Commissioning flow when the GPS activity for
  // commissioning the device has failed.
  fun commissionDeviceFailed(resultCode: Int) {
    if (resultCode == 0) {
      // User simply wilfully exited from GPS commissioning.
      return
    }
    val title = "Commissioning the device failed"
    Timber.e(title)
    showMsgDialog(title, "result code: $resultCode")
  }

  fun updateDeviceStateOn(deviceId: Long, isOn: Boolean) {
    Timber.d("updateDeviceStateOn: Device [${deviceId}]  isOn [${isOn}]")
    viewModelScope.launch {
      Timber.d("Handling real device")
      clustersHelper.setOnOffDeviceStateOnOffCluster(deviceId, isOn, 1)
      devicesStateRepository.updateDeviceState(deviceId, true, isOn)
    }
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
                  device.deviceId,
                  isOnline = true,
                  isOn = onOffState,
                )
              }
            }
          }

        try {
          val connectedDevicePointer = chipClient.getConnectedDevicePointer(device.deviceId)
          subscriptionHelper.awaitSubscribeToPeriodicUpdates(
            connectedDevicePointer,
            SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(device.deviceId),
            SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(device.deviceId),
            reportCallback,
          )
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
      "${LocalDateTime.now()} startDevicesPeriodicPing every $PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS seconds"
    )
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
            device.deviceId,
            isOnline = isOnline,
            isOn = isOn,
          )
        }
        delay(PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS * 1000L)
      }
    }
  }

  private fun stopDevicesPeriodicPing() {
    devicesPeriodicPingEnabled = false
  }

  // -----------------------------------------------------------------------------------------------
  // Device Attestation

  fun setDeviceAttestationDelegate(
    failureTimeoutSeconds: Int = DEVICE_ATTESTATION_FAILED_TIMEOUT_SECONDS
  ) {
    Timber.d("setDeviceAttestationDelegate")
    chipClient.chipDeviceController.setDeviceAttestationDelegate(failureTimeoutSeconds) {
      devicePtr,
      _,
      errorCode ->
      Timber.d(
        "Device attestation errorCode: $errorCode, " +
          "Look at 'src/credentials/attestation_verifier/DeviceAttestationVerifier.h' " +
          "AttestationVerificationResult enum to understand the errors"
      )

      if (errorCode == STATUS_PAIRING_SUCCESS) {
        Timber.d("DeviceAttestationDelegate: Success on device attestation.")
        viewModelScope.launch {
          chipClient.chipDeviceController.continueCommissioning(devicePtr, true)
        }
      } else {
        Timber.d("DeviceAttestationDelegate: Error on device attestation [$errorCode].")
        // Ideally, we'd want to show a Dialog and ask the user whether the attestation
        // failure should be ignored or not.
        // Unfortunately, the GPS commissioning API is in control at this point, and the
        // Dialog will only show up after GPS gives us back control.
        // So, we simply ignore the attestation failure for now.
        // TODO: Add a new setting to control that behavior.
        _deviceAttestationFailureIgnored.value = true
        Timber.w("Ignoring attestation failure.")
        viewModelScope.launch {
          chipClient.chipDeviceController.continueCommissioning(devicePtr, true)
        }
      }
    }
  }

  fun resetDeviceAttestationDelegate() {
    Timber.d("resetDeviceAttestationDelegate")
    chipClient.chipDeviceController.setDeviceAttestationDelegate(0, EmptyAttestationDelegate())
  }

  private class EmptyAttestationDelegate : DeviceAttestationDelegate {
    override fun onDeviceAttestationCompleted(
      devicePtr: Long,
      attestationInfo: AttestationInfo,
      errorCode: Int,
    ) {}
  }

  // -----------------------------------------------------------------------------------------------
  // UI State update

  fun showMsgDialog(title: String, msg: String) {
    _msgDialogInfo.value = DialogInfo(title, msg)
  }

  // Called after user dismisses the Info dialog. If we don't consume, a config change redisplays
  // the
  // alert dialog.
  fun dismissMsgDialog() {
    _msgDialogInfo.value = null
  }

  fun setMultiadminCommissioningTaskStatus(taskStatus: TaskStatus) {
    _multiadminCommissionDeviceTaskStatus.value = taskStatus
  }

  // ---------------------------------------------------------------------------
  // Companion object

  companion object {
    private const val STATUS_PAIRING_SUCCESS = 0

    /** Set for the fail-safe timer before onDeviceAttestationFailed is invoked. */
    private const val DEVICE_ATTESTATION_FAILED_TIMEOUT_SECONDS = 60
  }
}
