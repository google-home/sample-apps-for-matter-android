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
import chip.devicecontroller.model.NodeState
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
import com.google.homesampleapp.PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS
import com.google.homesampleapp.SETUP_PIN_CODE
import com.google.homesampleapp.STATE_CHANGES_MONITORING_MODE
import com.google.homesampleapp.StateChangesMonitoringMode
import com.google.homesampleapp.TaskStatus
import com.google.homesampleapp.UiAction
import com.google.homesampleapp.chip.ChipClient
import com.google.homesampleapp.chip.ClustersHelper
import com.google.homesampleapp.chip.MatterConstants.OnOffAttribute
import com.google.homesampleapp.chip.SubscriptionHelper
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
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
    private val clustersHelper: ClustersHelper,
    private val subscriptionHelper: SubscriptionHelper
) : ViewModel() {

  // The deviceId being shown by the Fragment.
  // Initialized in Fragment onResume().
  lateinit var deviceUiModel: DeviceUiModel

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

  /** Let the fragment know about a UI action to handle. */
  private val _uiActionLiveData = MutableLiveData<UiAction?>()
  val uiActionLiveData: LiveData<UiAction?>
    get() = _uiActionLiveData

  fun consumeUiActionLiveData() {
    _uiActionLiveData.postValue(null)
  }

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
   *    Google Play Services "Share Device" activity (step 3).
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
  fun shareDeviceSucceeded() {
    _shareDeviceStatus.postValue(TaskStatus.Completed("Device sharing completed successfully"))
    startDevicePeriodicPing()
  }

  // Called by the fragment in Step 5 of the Device Sharing flow when the GPS activity for
  // Device Sharing has failed.
  fun shareDeviceFailed(deviceUiModel: DeviceUiModel, resultCode: Int) {
    Timber.d("ShareDevice: Failed with errorCode [${resultCode}]")
    _shareDeviceStatus.postValue(TaskStatus.Failed("Device sharing failed [${resultCode}]", null))
    startDevicePeriodicPing()
  }

  // Called after we dismiss an error dialog. If we don't consume, a config change redisplays the
  // alert dialog.
  fun consumeShareDeviceStatus() {
    _shareDeviceStatus.postValue(TaskStatus.NotStarted)
  }

  // -----------------------------------------------------------------------------------------------
  // Operations on device

  // Removes the device. First we remove the fabric from the device, and then we remove the
  // device from the app's devices repository.
  // If removing the fabric from the device fails (e.g. device is offline), a dialog is shown so
  // the user has the option to force remove the device without unlinking the fabric at the
  // device. If a forced removal is selected, then function removeDeviceWithoutUnlink is called.
  // TODO: The device will still be linked to the local Android fabric. We should remove all the
  //  fabrics at the device.
  fun removeDevice(deviceId: Long) {
    Timber.d("Removing device [${deviceId}]")
    viewModelScope.launch {
      try {
        _backgroundWorkAlertDialogAction.postValue(
            BackgroundWorkAlertDialogAction.Show(
                "Unlinking the device",
                "Calling the device to remove the sample app's fabric. " +
                    "If the device is offline, this will fail when the call times out, " +
                    "and this may take a while."))
        chipClient.awaitUnpairDevice(deviceId)
      } catch (e: Exception) {
        Timber.e(e, "Unlinking the device failed.")
        _backgroundWorkAlertDialogAction.postValue(BackgroundWorkAlertDialogAction.Hide)
        // Show a dialog so the user has the option to force remove without unlinking the device.
        _uiActionLiveData.postValue(
            UiAction(id = DEVICE_REMOVAL_CONFIRM, data = deviceId.toString()))
        return@launch
      }
      // Remove device from the app's devices repository.
      _backgroundWorkAlertDialogAction.postValue(BackgroundWorkAlertDialogAction.Hide)
      devicesRepository.removeDevice(deviceId)
      _uiActionLiveData.postValue(UiAction(id = DEVICE_REMOVAL_COMPLETED))
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
      _uiActionLiveData.postValue(UiAction(id = DEVICE_REMOVAL_COMPLETED))
    }
  }

  fun updateDeviceStateOn(deviceUiModel: DeviceUiModel, isOn: Boolean) {
    Timber.d("updateDeviceStateOn: isOn [${isOn}]")
    val deviceId = deviceUiModel.device.deviceId
    viewModelScope.launch {

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
            "setupPinCode [${SETUP_PIN_CODE}]")
    chipClient.awaitOpenPairingWindowWithPIN(
        connectedDevicePointer, duration, ITERATION, DISCRIMINATOR, SETUP_PIN_CODE)
    Timber.d("ShareDevice: After chipClient.awaitOpenPairingWindowWithPIN")
  }

  // TODO: Was not working when tested. Use openCommissioningWindowUsingOpenPairingWindowWithPin
  // for now.
  private suspend fun openCommissioningWindowWithAdministratorCommissioningCluster(deviceId: Long) {
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
        object : SubscriptionHelper.ReportCallbackForDevice(deviceUiModel.device.deviceId) {
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
                  deviceUiModel.device.deviceId, isOnline = true, isOn = onOffState)
            }
          }
        }
    viewModelScope.launch {
      try {
        val connectedDevicePointer =
            chipClient.getConnectedDevicePointer(deviceUiModel.device.deviceId)
        subscriptionHelper.awaitSubscribeToPeriodicUpdates(
            connectedDevicePointer,
            SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(
                deviceUiModel.device.deviceId),
            SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(
                deviceUiModel.device.deviceId),
            reportCallback)
      } catch (e: IllegalStateException) {
        Timber.e("Can't get connectedDevicePointer for ${deviceUiModel.device.deviceId}.")
        return@launch
      }
    }
  }

  private fun unsubscribeToPeriodicUpdates() {
    Timber.d("unsubscribeToPeriodicUpdates()")
    viewModelScope.launch {
      try {
        val connectedDevicePtr = chipClient.getConnectedDevicePointer(deviceUiModel.device.deviceId)
        subscriptionHelper.awaitUnsubscribeToPeriodicUpdates(connectedDevicePtr)
      } catch (e: IllegalStateException) {
        Timber.e("Can't get connectedDevicePointer for ${deviceUiModel.device.deviceId}.")
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
        "${LocalDateTime.now()} startDevicePeriodicPing every $PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS seconds")
    devicePeriodicPingEnabled = true
    runDevicePeriodicUpdate(deviceUiModel)
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
            deviceUiModel.device.deviceId, isOnline = isOnline, isOn = isOn == true)
        delay(PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS * 1000L)
      }
    }
  }

  private fun stopDevicePeriodicPing() {
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

  // ---------------------------------------------------------------------------
  // Companion object

  companion object {
    public const val DEVICE_REMOVAL_CONFIRM = "DEVICE_REMOVAL_CONFIRM"
    public const val DEVICE_REMOVAL_COMPLETED = "DEVICE_REMOVAL_COMPLETED"
  }
}
