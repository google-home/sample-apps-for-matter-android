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

package com.google.homesampleapp.screens.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chip.devicecontroller.model.NodeState
import com.google.homesampleapp.DISCRIMINATOR
import com.google.homesampleapp.ITERATION
import com.google.homesampleapp.OPEN_COMMISSIONING_WINDOW_API
import com.google.homesampleapp.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
import com.google.homesampleapp.OpenCommissioningWindowApi
import com.google.homesampleapp.PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS
import com.google.homesampleapp.SETUP_PIN_CODE
import com.google.homesampleapp.STATE_CHANGES_MONITORING_MODE
import com.google.homesampleapp.StateChangesMonitoringMode
import com.google.homesampleapp.chip.ChipClient
import com.google.homesampleapp.chip.ClustersHelper
import com.google.homesampleapp.chip.MatterConstants.OnOffAttribute
import com.google.homesampleapp.chip.SubscriptionHelper
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.screens.common.DialogInfo
import com.google.homesampleapp.screens.home.DeviceUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** The ViewModel for the Device Screen. */
@HiltViewModel
class DeviceViewModel
@Inject
constructor(
  private val devicesRepository: DevicesRepository,
  val devicesStateRepository: DevicesStateRepository,
  private val chipClient: ChipClient,
  private val clustersHelper: ClustersHelper,
  private val subscriptionHelper: SubscriptionHelper,
) : ViewModel() {

  // The UI model for device shown on the Device screen.
  private var _deviceUiModel = MutableStateFlow<DeviceUiModel?>(null)
  val deviceUiModel: StateFlow<DeviceUiModel?> = _deviceUiModel.asStateFlow()

  // Controls whether a periodic ping to the device is enabled or not.
  private var devicePeriodicPingEnabled: Boolean = true

  // Controls whether the "Message" AlertDialog should be shown in the UI.
  private var _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  val msgDialogInfo: StateFlow<DialogInfo?> = _msgDialogInfo.asStateFlow()

  // Controls whether the "Remove Device" AlertDialog should be shown in the UI.
  private var _showRemoveDeviceAlertDialog = MutableStateFlow(false)
  val showRemoveDeviceAlertDialog: StateFlow<Boolean> = _showRemoveDeviceAlertDialog.asStateFlow()

  // Controls whether the "Confirm Device Removal" AlertDialog should be shown in the UI.
  private var _showConfirmDeviceRemovalAlertDialog = MutableStateFlow(false)
  val showConfirmDeviceRemovalAlertDialog: StateFlow<Boolean> =
    _showConfirmDeviceRemovalAlertDialog.asStateFlow()

  // Communicates to the UI that removal of the device has completed successfully.
  // See resetDeviceRemovalCompleted() to reset this state after being handled by the UI.
  private var _deviceRemovalCompleted = MutableStateFlow(false)
  val deviceRemovalCompleted: StateFlow<Boolean> = _deviceRemovalCompleted.asStateFlow()

  // Communicates to the UI that the pairing window is open for device sharing.
  // See resetPairingWindowOpenForDeviceSharing() to reset this state after being handled by the UI.
  private var _pairingWindowOpenForDeviceSharing = MutableStateFlow(false)
  val pairingWindowOpenForDeviceSharing: StateFlow<Boolean> =
    _pairingWindowOpenForDeviceSharing.asStateFlow()

  // -----------------------------------------------------------------------------------------------
  // Load device

  fun loadDevice(deviceId: Long) {
    if (deviceId == deviceUiModel.value?.device?.deviceId) {
      Timber.d("loadDevice: [${deviceId}] was already loaded")
      return
    } else {
      Timber.d("loadDevice: loading [${deviceId}]")
      viewModelScope.launch {
        val device = devicesRepository.getDevice(deviceId)
        val deviceState = devicesStateRepository.loadDeviceState(deviceId)
        var isOnline = false
        var isOn = false
        if (deviceState != null) {
          isOnline = deviceState.online
          isOn = deviceState.on
        }
        _deviceUiModel.value = DeviceUiModel(device, isOnline, isOn)
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Share Device (aka Multi-Admin)

  fun openPairingWindow(deviceId: Long) {
    stopMonitoringStateChanges()
    showMsgDialog("Opening pairing window", "This may take a few seconds...", false)
    viewModelScope.launch {
      // First we need to open a commissioning window.
      try {
        when (OPEN_COMMISSIONING_WINDOW_API) {
          OpenCommissioningWindowApi.ChipDeviceController ->
            openCommissioningWindowUsingOpenPairingWindowWithPin(deviceId)
          OpenCommissioningWindowApi.AdministratorCommissioningCluster ->
            openCommissioningWindowWithAdministratorCommissioningCluster(deviceId)
        }
        dismissMsgDialog()
        // Communicate to the UI that the pairing window is open.
        // UI can then launch the GPS activity for device sharing.
        _pairingWindowOpenForDeviceSharing.value = true
      } catch (e: Throwable) {
        dismissMsgDialog()
        val msg = "Failed to open the commissioning window"
        Timber.d("ShareDevice: $msg [$e]")
        showMsgDialog(msg, e.toString())
      }
    }
  }

  // Called by the fragment in Step 5 of the Device Sharing flow when the GPS activity for
  // Device Sharing has succeeded.
  fun shareDeviceSucceeded() {
    showMsgDialog("Device sharing completed successfully", null)
    startDevicePeriodicPing()
  }

  // Called by the fragment in Step 5 of the Device Sharing flow when the GPS activity for
  // Device Sharing has failed.
  fun shareDeviceFailed(resultCode: Int) {
    Timber.d("ShareDevice: Failed with errorCode [${resultCode}]")
    showMsgDialog("Device sharing failed", "error code: [$resultCode]")
    startDevicePeriodicPing()
  }

  private suspend fun openCommissioningWindowUsingOpenPairingWindowWithPin(deviceId: Long) {
    // TODO: Should generate random 64 bit value for SETUP_PIN_CODE (taking into account
    // spec constraints)
    Timber.d("ShareDevice: chipClient.awaitGetConnectedDevicePointer(${deviceId})")
    val connectedDevicePointer = chipClient.awaitGetConnectedDevicePointer(deviceId)

    try {
      // Check if there is a commission window that's already open.
      // See [CommissioningWindowStatus] for complete details.
      val isOpen = clustersHelper.isCommissioningWindowOpen(connectedDevicePointer)
      Timber.d("ShareDevice: isCommissioningWindowOpen [$isOpen]")
      if (isOpen) {
        // close commission window
        clustersHelper.closeCommissioningWindow(connectedDevicePointer)
      }
    } catch (ex: Exception) {
      val errorMsg = "Failed to setup Administrator Commissioning Cluster"
      Timber.d("$errorMsg. Cause: ${ex.localizedMessage}")
      // ToDo() decide whether to terminate the OCW task if we fail to configure the window status.
    }

    val duration = OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
    Timber.d(
      "ShareDevice: chipClient.chipClient.awaitOpenPairingWindowWithPIN " +
        "duration [${duration}] iteration [${ITERATION}] discriminator [${DISCRIMINATOR}] " +
        "setupPinCode [${SETUP_PIN_CODE}]"
    )
    chipClient.awaitOpenPairingWindowWithPIN(
      connectedDevicePointer,
      duration,
      ITERATION,
      DISCRIMINATOR,
      SETUP_PIN_CODE,
    )
    Timber.d("ShareDevice: After chipClient.awaitOpenPairingWindowWithPIN")
  }

  // TODO: Was not working when tested. Use openCommissioningWindowUsingOpenPairingWindowWithPin
  // for now.
  private suspend fun openCommissioningWindowWithAdministratorCommissioningCluster(deviceId: Long) {
    Timber.d(
      "ShareDevice: openCommissioningWindowWithAdministratorCommissioningCluster [${deviceId}]"
    )
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
      timedInvokeTimeoutMs,
    )
  }

  // -----------------------------------------------------------------------------------------------
  // Remove device

  // Removes the device. First we remove the fabric from the device, and then we remove the
  // device from the app's devices repository.
  // Note that unlinking the device may take a while if the device is offline. Because of that,
  // a MsgAlertDIalog is shown, without any confirm button, to let the user know that unlinking
  // may take a while. That way the user is not left hanging wondering what is going on.
  // If removing the fabric from the device fails (e.g. device is offline), then another dialog
  // is shown so the user has the option to force remove the device without unlinking
  // the fabric at the device. If a forced removal is selected, then function
  // removeDeviceWithoutUnlink is called.
  // TODO: The device will still be linked to the local Android fabric. We should remove all the
  // fabrics at the device.
  fun removeDevice(deviceId: Long) {
    Timber.d("Removing device [${deviceId}]")
    showMsgDialog(
      "Unlinking the device",
      "Calling the device to remove the sample app's fabric. " +
        "If the device is offline, this will fail when the call times out, " +
        "and this may take a while.\n\n" +
        "Unlinking the device...",
      false,
    )
    viewModelScope.launch {
      try {
        chipClient.awaitUnpairDevice(deviceId)
      } catch (e: Exception) {
        Timber.e(e, "Unlinking the device failed.")
        dismissMsgDialog()
        // Show a dialog so the user has the option to force remove without unlinking the device.
        _showConfirmDeviceRemovalAlertDialog.value = true
        return@launch
      }
      // Remove device from the app's devices repository.
      Timber.d("removeDevice succeeded! [$deviceId]")
      dismissMsgDialog()
      devicesRepository.removeDevice(deviceId)
      // Notify UI so we navigate back to Home screen.
      _deviceRemovalCompleted.value = true
    }
  }

  // Removes the device from the app's devices repository, and does not unlink the fabric
  // from the device.
  // This function is called after removeDevice() has failed trying to unlink the device
  // and the user has confirmed that the device should still be removed from the app's device
  // repository.
  fun removeDeviceWithoutUnlink(deviceId: Long) {
    Timber.d("removeDeviceWithoutUnlink: [${deviceId}]")
    viewModelScope.launch {
      // Remove device from the app's devices repository.
      devicesRepository.removeDevice(deviceId)
      _deviceRemovalCompleted.value = true
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Device state (On/Off)

  fun updateDeviceStateOn(deviceUiModel: DeviceUiModel, isOn: Boolean) {
    Timber.d("updateDeviceStateOn: isOn [${isOn}]")
    viewModelScope.launch {

      // CODELAB: toggle
      Timber.d("Handling real device")
      try {
        clustersHelper.setOnOffDeviceStateOnOffCluster(deviceUiModel.device.deviceId, isOn, 1)
        // We observe state changes there, so we'll get these updates
        devicesStateRepository.updateDeviceState(deviceUiModel.device.deviceId, true, isOn)
      } catch (e: Throwable) {
        Timber.e("Failed setting on/off state")
      }
      // CODELAB SECTION END
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Inspect device

  fun inspectDescriptorCluster(deviceUiModel: DeviceUiModel) {
    val nodeId = deviceUiModel.device.deviceId
    val name = deviceUiModel.device.name
    val divider = "-".repeat(20)

    Timber.d("\n${divider} Inspect Device [${name}] [${nodeId}] $divider")
    viewModelScope.launch {
      val partsListAttribute =
        clustersHelper.readDescriptorClusterPartsListAttribute(
          chipClient.getConnectedDevicePointer(nodeId),
          0,
        )
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
            chipClient.getConnectedDevicePointer(nodeId),
            endpoint,
          )
        deviceListAttribute.forEach { Timber.d("device attribute: [${it}]") }

        val serverListAttribute =
          clustersHelper.readDescriptorClusterServerListAttribute(
            chipClient.getConnectedDevicePointer(nodeId),
            endpoint,
          )
        serverListAttribute.forEach { Timber.d("server attribute: [${it}]") }
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
  // State Changes Monitoring

  /**
   * The way we monitor state changes is defined by constant [StateChangesMonitoringMode].
   * [StateChangesMonitoringMode.Subscription] is the preferred mode.
   * [StateChangesMonitoringMode.PeriodicRead] was used initially because of issues with
   * subscriptions. We left its associated code as it could be useful to some developers.
   */
  fun startMonitoringStateChanges() {
    Timber.d("startMonitoringStateChanges(): mode [$STATE_CHANGES_MONITORING_MODE]")
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> subscribeToPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> startDevicePeriodicPing()
    }
  }

  fun stopMonitoringStateChanges() {
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> unsubscribeToPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> stopDevicePeriodicPing()
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Subscription to periodic device updates.
  // See:
  //   - Spec section "8.5 Subscribe Interaction"
  //   - Matter primer:
  // https://developers.home.google.com/matter/primer/interaction-model-reading#subscription_transaction
  //
  // TODO:
  //   - Properly implement unsubscribe behavior
  //   - Implement algorithm for online/offline detection.
  //     (Issue is that not clear how to register a callback for messages coming at maxInterval.

  /*
    Sample message coming at maxInterval.
  ```
  01-06 05:27:53.736 16814 16850 D EM      : >>> [E:59135r M:51879653] (S) Msg RX from 1:0000000000000001 [171D] --- Type 0001:05 (IM:ReportData)
  01-06 05:27:53.736 16814 16850 D EM      : Handling via exchange: 59135r, Delegate: 0x76767a7668
  01-06 05:27:53.736 16814 16850 D DMG     : ReportDataMessage =
  01-06 05:27:53.737 16814 16850 D DMG     : {
  01-06 05:27:53.737 16814 16850 D DMG     : 	SubscriptionId = 0x7e169ca8,
  01-06 05:27:53.737 16814 16850 D DMG     : 	InteractionModelRevision = 1
  01-06 05:27:53.737 16814 16850 D DMG     : }
  01-06 05:27:53.737 16814 16850 D DMG     : Refresh LivenessCheckTime for 35000 milliseconds with SubscriptionId = 0x7e169ca8 Peer = 01:0000000000000001
  01-06 05:27:53.737 16814 16850 D EM      : <<< [E:59135r M:213699489 (Ack:51879653)] (S) Msg TX to 1:0000000000000001 [171D] --- Type 0001:01 (IM:StatusResponse)
  01-06 05:27:53.737 16814 16850 D IN      : (S) Sending msg 213699489 on secure session with LSID: 25418
  01-06 05:27:53.838 16814 16850 D EM      : >>> [E:59135r M:51879654 (Ack:213699489)] (S) Msg RX from 1:0000000000000001 [171D] --- Type 0000:10 (SecureChannel:StandaloneAck)
  01-06 05:27:53.839 16814 16850 D EM      : Found matching exchange: 59135r, Delegate: 0x0
  01-06 05:27:53.839 16814 16850 D EM      : Rxd Ack; Removing MessageCounter:213699489 from Retrans Table on exchange 59135r
  ```
  */
  private fun subscribeToPeriodicUpdates() {
    Timber.d("subscribeToPeriodicUpdates()")
    val reportCallback =
      object : SubscriptionHelper.ReportCallbackForDevice(deviceUiModel.value!!.device.deviceId) {
        override fun onReport(nodeState: NodeState) {
          super.onReport(nodeState)
          val onOffState =
            subscriptionHelper.extractAttribute(nodeState, 1, OnOffAttribute) as Boolean?
          Timber.d("onOffState [${onOffState}]")
          if (onOffState == null) {
            Timber.e("onReport(): WARNING -> onOffState is NULL. Ignoring.")
            return
          }
          viewModelScope.launch {
            devicesStateRepository.updateDeviceState(
              deviceUiModel.value!!.device.deviceId,
              isOnline = true,
              isOn = onOffState,
            )
          }
        }
      }
    viewModelScope.launch {
      try {
        val connectedDevicePointer =
          chipClient.getConnectedDevicePointer(deviceUiModel.value!!.device.deviceId)
        subscriptionHelper.awaitSubscribeToPeriodicUpdates(
          connectedDevicePointer,
          SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(
            deviceUiModel.value!!.device.deviceId
          ),
          SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(
            deviceUiModel.value!!.device.deviceId
          ),
          reportCallback,
        )
      } catch (e: IllegalStateException) {
        Timber.e("Can't get connectedDevicePointer for ${deviceUiModel.value!!.device.deviceId}.")
        return@launch
      }
    }
  }

  private fun unsubscribeToPeriodicUpdates() {
    Timber.d("unsubscribeToPeriodicUpdates()")
    viewModelScope.launch {
      try {
        val connectedDevicePtr =
          chipClient.getConnectedDevicePointer(deviceUiModel.value!!.device.deviceId)
        subscriptionHelper.awaitUnsubscribeToPeriodicUpdates(connectedDevicePtr)
      } catch (e: IllegalStateException) {
        Timber.e("Can't get connectedDevicePointer for ${deviceUiModel.value!!.device.deviceId}.")
        return@launch
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Task that runs periodically to get and update the device state.
  // Periodic monitoring of a device state should be done with Subscription mode.
  // This code is left here in case it might be useful to some developers.

  private fun startDevicePeriodicPing() {
    Timber.d(
      "${LocalDateTime.now()} startDevicePeriodicPing every $PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS seconds"
    )
    devicePeriodicPingEnabled = true
    runDevicePeriodicUpdate(deviceUiModel.value!!)
  }

  private fun runDevicePeriodicUpdate(deviceUiModel: DeviceUiModel) {
    if (PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS == -1) {
      return
    }
    viewModelScope.launch {
      while (devicePeriodicPingEnabled) {
        // Do something here on the main thread
        var isOn: Boolean?
        var isOnline: Boolean
        // TODO: See HomeViewModel:CommissionDeviceSucceeded for device capabilities
        isOn = clustersHelper.getDeviceStateOnOffCluster(deviceUiModel.device.deviceId, 1)
        if (isOn == null) {
          Timber.e("[device ping] failed")
          isOn = false
          isOnline = false
        } else {
          Timber.d("[device ping] success [${isOn}]")
          isOnline = true
        }
        devicesStateRepository.updateDeviceState(
          deviceUiModel.device.deviceId,
          isOnline = isOnline,
          isOn = isOn == true,
        )
        delay(PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS * 1000L)
      }
    }
  }

  private fun stopDevicePeriodicPing() {
    devicePeriodicPingEnabled = false
  }

  // -----------------------------------------------------------------------------------------------
  // UI State update

  fun showMsgDialog(title: String?, msg: String?, showConfirmButton: Boolean = true) {
    Timber.d("showMsgDialog [$title]")
    _msgDialogInfo.value = DialogInfo(title, msg, showConfirmButton)
  }

  // Called after user dismisss the Info dialog. If we don't consume, a config change redisplays the
  // alert dialog.
  fun dismissMsgDialog() {
    Timber.d("dismissMsgDialog()")
    _msgDialogInfo.value = null
  }

  fun showRemoveDeviceAlertDialog() {
    Timber.d("showRemoveDeviceAlertDialog")
    _showRemoveDeviceAlertDialog.value = true
  }

  fun dismissRemoveDeviceDialog() {
    Timber.d("dismissRemoveDeviceDialog")
    _showRemoveDeviceAlertDialog.value = false
  }

  fun dismissConfirmDeviceRemovalDialog() {
    Timber.d("dismissConfirmDeviceRemovalDialog")
    _showConfirmDeviceRemovalAlertDialog.value = false
  }

  fun resetDeviceRemovalCompleted() {
    _deviceRemovalCompleted.value = false
  }

  fun resetPairingWindowOpenForDeviceSharing() {
    _pairingWindowOpenForDeviceSharing.value = false
  }
}
