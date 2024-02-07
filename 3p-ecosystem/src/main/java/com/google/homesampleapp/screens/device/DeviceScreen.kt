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

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningWindow
import com.google.android.gms.home.matter.commissioning.ShareDeviceRequest
import com.google.android.gms.home.matter.common.DeviceDescriptor
import com.google.android.gms.home.matter.common.Discriminator
import com.google.homesampleapp.DISCRIMINATOR
import com.google.homesampleapp.Device
import com.google.homesampleapp.DeviceState
import com.google.homesampleapp.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
import com.google.homesampleapp.R
import com.google.homesampleapp.SETUP_PIN_CODE
import com.google.homesampleapp.formatTimestamp
import com.google.homesampleapp.screens.common.DialogInfo
import com.google.homesampleapp.screens.common.MsgAlertDialog
import com.google.homesampleapp.screens.home.DeviceUiModel
import com.google.homesampleapp.screens.thread.getActivity
import com.google.homesampleapp.stateDisplayString
import com.google.protobuf.Timestamp
import timber.log.Timber

/**
 * The Device Screen shows all the information about the device that was selected in the Home
 * screen. It supports the following actions:
 * ```
 * - toggle the on/off state of the device
 * - share the device with another Matter commissioner app
 * - remove the device
 * - inspect the device (get all info we can from the clusters supported by the device)
 * ```
 *
 * When the screen is shown, state monitoring is activated to get the device's latest state. This
 * makes it possible to update the device's online status dynamically.
 */
@Composable
internal fun DeviceRoute(
  innerPadding: PaddingValues,
  updateTitle: (title: String) -> Unit,
  navigateToHome: () -> Unit,
  navigateToInspect: (deviceId: Long) -> Unit,
  deviceId: Long,
  deviceViewModel: DeviceViewModel = hiltViewModel(),
) {
  Timber.d("DeviceRoute deviceId [$deviceId]")

  // Launching GPS commissioning requires Activity.
  val activity = LocalContext.current.getActivity()

  // Observes values needed by the DeviceScreen.
  val deviceUiModel by deviceViewModel.deviceUiModel.collectAsState()
  Timber.d("DeviceRoute deviceUiModel [${deviceUiModel?.device?.deviceId}]")

  // When the device has been removed by the ViewModel, navigate back to the Home screen.
  val deviceRemovalCompleted by deviceViewModel.deviceRemovalCompleted.collectAsState()
  if (deviceRemovalCompleted) {
    navigateToHome()
    deviceViewModel.resetDeviceRemovalCompleted()
  }

  // Controls the Msg AlertDialog.
  // When the user dismisses the Msg AlertDialog, we "consume" the dialog.
  val msgDialogInfo by deviceViewModel.msgDialogInfo.collectAsState()
  val onDismissMsgDialog: () -> Unit = remember {
    { deviceViewModel.dismissMsgDialog() }
  }

  // Controls whether the "remove device" alert dialog should be shown.
  val showRemoveDeviceAlertDialog by deviceViewModel.showRemoveDeviceAlertDialog.collectAsState()
  val onRemoveDeviceClick: () -> Unit = remember {
    { deviceViewModel.showRemoveDeviceAlertDialog() }
  }
  val onRemoveDeviceOutcome: (doIt: Boolean) -> Unit = remember {
    { doIt ->
      deviceViewModel.dismissRemoveDeviceDialog()
      if (doIt) {
        deviceViewModel.removeDevice(deviceUiModel!!.device.deviceId)
      }
    }
  }

  // Controls whether the "confirm device removal" alert dialog should be shown.
  val showConfirmDeviceRemovalAlertDialog by
    deviceViewModel.showConfirmDeviceRemovalAlertDialog.collectAsState()
  val onConfirmDeviceRemovalOutcome: (doIt: Boolean) -> Unit = remember {
    { doIt ->
      deviceViewModel.dismissConfirmDeviceRemovalDialog()
      if (doIt) {
        deviceViewModel.removeDeviceWithoutUnlink(deviceUiModel!!.device.deviceId)
      }
    }
  }

  val lastUpdatedDeviceState by
    deviceViewModel.devicesStateRepository.lastUpdatedDeviceState.observeAsState()

  // On/Off Switch click.
  val onOnOffClick: (value: Boolean) -> Unit = remember {
    { value ->
      deviceViewModel.updateDeviceStateOn(deviceUiModel!!, value)
    }
  }

  // Inspect button click handler.
  // isOnline must be provided in InspectScreen because it is updated there.
  val onInspect: (isOnline: Boolean) -> Unit = remember {
    { isOnline ->
      if (isOnline) {
        navigateToInspect(deviceUiModel!!.device.deviceId)
      } else {
        deviceViewModel.showMsgDialog(
          "Inspect Device",
          "Device is offline, so it cannot be inspected.",
        )
      }
    }
  }

  // The device sharing flow involves multiple steps as it is based on an Activity
  // that is launched on the Google Play Services (GPS).
  // Step 1 (here) is where an activity launcher is registered.
  // At step 2, the user triggers the "Share Device" action by clicking on the
  // "Share" button on this screen. This creates the proper IntentSender that is then
  // used in step 3 to call shareDeviceLauncher.launch().
  // Step 4 is when GPS takes over the sharing flow.
  // Step 5 is when the GPS activity completes and the result is handled here.
  // CODELAB: shareDeviceLauncher definition
  val shareDeviceLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
      // Commission Device Step 5.
      // The Share Device activity in GPS (step 4) has completed.
      val resultCode = result.resultCode
      if (resultCode == Activity.RESULT_OK) {
        deviceViewModel.shareDeviceSucceeded()
      } else {
        deviceViewModel.shareDeviceFailed(resultCode)
      }
    }
  // CODELAB SECTION END

  // When the pairing window has been open for device sharing.
  val pairingWindowOpenForDeviceSharing by
    deviceViewModel.pairingWindowOpenForDeviceSharing.collectAsState()
  if (pairingWindowOpenForDeviceSharing) {
    deviceViewModel.resetPairingWindowOpenForDeviceSharing()
    shareDevice(activity!!.applicationContext, shareDeviceLauncher, deviceViewModel)
  }

  // Share Device button click.
  val onShareDevice: () -> Unit = remember {
    {
      deviceViewModel.openPairingWindow(deviceUiModel!!.device.deviceId)
    }
  }

  // When app is sent to the background, and pulled back, this kicks in.
  LifecycleResumeEffect {
    Timber.d("LifecycleResumeEffect: deviceUiModel [${deviceUiModel?.device?.deviceId}]")
    deviceViewModel.loadDevice(deviceId)
    deviceViewModel.startMonitoringStateChanges()
    onPauseOrDispose {
      // do any needed clean up here
      Timber.d(
        "LifecycleResumeEffect:onPauseOrDispose deviceUiModel [${deviceUiModel?.device?.deviceId}]"
      )
      deviceViewModel.stopMonitoringStateChanges()
    }
  }

  LaunchedEffect(Unit) {
    updateTitle("Device")
  }

  DeviceScreen(
    innerPadding,
    deviceUiModel,
    lastUpdatedDeviceState,
    onOnOffClick,
    onRemoveDeviceClick,
    onShareDevice,
    onInspect,
    msgDialogInfo,
    onDismissMsgDialog,
    showRemoveDeviceAlertDialog,
    onRemoveDeviceOutcome,
    showConfirmDeviceRemovalAlertDialog,
    onConfirmDeviceRemovalOutcome,
  )
}

@Composable
private fun DeviceScreen(
  innerPadding: PaddingValues,
  deviceUiModel: DeviceUiModel?,
  deviceState: DeviceState?,
  onOnOffClick: (value: Boolean) -> Unit,
  onRemoveDeviceClick: () -> Unit,
  onShareDevice: () -> Unit,
  onInspect: (isOnline: Boolean) -> Unit,
  msgDialogInfo: DialogInfo?,
  onDismissMsgDialog: () -> Unit,
  showRemoveDeviceAlertDialog: Boolean,
  onRemoveDeviceOutcome: (Boolean) -> Unit,
  showConfirmDeviceRemovalAlertDialog: Boolean,
  onConfirmDeviceRemovalOutcome: (Boolean) -> Unit,
) {
  // The current state of the device.
  // The DeviceUiModel is not updated whenever we observe changes in the state of the device.
  // This is an issue for the "Inspect Device" onClick listener which relies on the device
  // state to decide whether to show a dialog stating that the device is offline and therefore
  // the inspect screen cannot be shown, or go show the inspect information (when device is
  // online).
  // This is why the state of the device is cached in local variables.
  var isOnline by remember { mutableStateOf(false) }
  var isOn by remember { mutableStateOf(false) }

  if (deviceUiModel == null) {
    Text("Still loading the device information")
    return
  }

  LaunchedEffect(deviceUiModel, deviceState) {
    if (deviceUiModel == null) {
      // Device was just removed, nothing to do. We'll move to HomeFragment.
      isOnline = false
      return@LaunchedEffect
    }

    // Device state
    deviceUiModel.let { model ->
      isOnline =
        when (deviceState) {
          null -> model.isOnline
          else -> deviceState.online
        }
      isOn =
        when (deviceState) {
          null -> model.isOn
          else -> deviceState.on
        }
    }
    Timber.d("deviceState: isOnline [$isOnline] isOn[$isOn]")
  }

  // The various AlertDialog's that may pop up to inform the user of important information.
  MsgAlertDialog(msgDialogInfo, onDismissMsgDialog)
  RemoveDeviceAlertDialog(showRemoveDeviceAlertDialog, onRemoveDeviceOutcome)
  ConfirmDeviceRemovalAlertDialog(
    showConfirmDeviceRemovalAlertDialog,
    onConfirmDeviceRemovalOutcome,
  )

  deviceUiModel.let { model ->
    Column(modifier = Modifier.fillMaxWidth().padding(innerPadding)) {
      OnOffStateSection(isOnline, isOn) { onOnOffClick(it) }
      ShareSection(name = model.device.name, onShareDevice)
      // TODO: Use HorizontalDivider when it becomes part of the stable Compose BOM.
      Spacer(modifier = Modifier)
      TechnicalInfoSection(model.device, onInspect, isOnline)
      RemoveDeviceSection(onRemoveDeviceClick)
    }
  }
}

@Composable
private fun OnOffStateSection(
  isOnline: Boolean,
  isOn: Boolean,
  onStateChange: ((Boolean) -> Unit)?,
) {
  val bgColor =
    if (isOnline && isOn) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface
  val contentColor =
    if (isOnline && isOn) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface
  val text = stateDisplayString(isOnline, isOn)
  Surface(
    modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal)),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    contentColor = contentColor,
    color = bgColor,
    shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(dimensionResource(R.dimen.padding_surface_content)),
    ) {
      Text(text = text, style = MaterialTheme.typography.bodyLarge)
      Spacer(Modifier.weight(1f))
      Switch(checked = isOn, onCheckedChange = onStateChange)
    }
  }
}

@Composable
private fun ShareSection(name: String, onShareDevice: () -> Unit) {
  Surface(
    modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal)),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
  ) {
    Column(modifier = Modifier.padding(8.dp)) {
      Text(
        text = stringResource(R.string.share_device_name, name),
        style = MaterialTheme.typography.bodyLarge,
      )
      Text(
        text = stringResource(R.string.share_device_body),
        style = MaterialTheme.typography.bodySmall,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onShareDevice) { Text(stringResource(R.string.share)) }
      }
    }
  }
}

@Composable
private fun TechnicalInfoSection(
  device: Device,
  onInspect: (isOnline: Boolean) -> Unit,
  isOnline: Boolean,
) {
  Surface(
    modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal)),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
  ) {
    Column(modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal))) {
      Text(
        text = stringResource(R.string.technical_information),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier,
      )
      Text(
        text =
          stringResource(
            R.string.share_device_info,
            formatTimestamp(device.dateCommissioned, null),
            device.deviceId.toString(),
            device.vendorName,
            device.vendorId,
            device.productName,
            device.productId,
            device.deviceType,
          ),
        style = MaterialTheme.typography.bodySmall,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { onInspect(isOnline) }) { Text(stringResource(R.string.inspect)) }
      }
    }
  }
}

@Composable
private fun RemoveDeviceSection(onClick: () -> Unit) {
  Row {
    TextButton(onClick = onClick) {
      Icon(Icons.Outlined.Delete, contentDescription = "Localized description")
      Text(stringResource(R.string.remove_device).uppercase())
    }
  }
}

@Composable
private fun RemoveDeviceAlertDialog(
  showRemoveDeviceAlertDialog: Boolean,
  onRemoveDeviceOutcome: (doIt: Boolean) -> Unit,
) {
  Timber.d("RemoveDeviceAlertDialog [$showRemoveDeviceAlertDialog]")
  if (!showRemoveDeviceAlertDialog) {
    return
  }

  AlertDialog(
    title = { Text(text = "Remove this device?") },
    text = {
      Text(
        "This device will be removed and unlinked from this sample app. " +
          "Other services and connection-types may still have access."
      )
    },
    confirmButton = {
      Button(onClick = { onRemoveDeviceOutcome(true) }) {
        Text(stringResource(R.string.yes_remove_it))
      }
    },
    onDismissRequest = {},
    dismissButton = {
      Button(onClick = { onRemoveDeviceOutcome(false) }) { Text(stringResource(R.string.cancel)) }
    },
  )
}

@Composable
private fun ConfirmDeviceRemovalAlertDialog(
  showConfirmDeviceRemovalAlertDialog: Boolean,
  onConfirmDeviceRemovalOutcome: (doIt: Boolean) -> Unit,
) {
  if (!showConfirmDeviceRemovalAlertDialog) {
    return
  }

  var showDialog by remember { mutableStateOf(false) }

  AlertDialog(
    title = { Text(text = "Error removing the fabric from the device") },
    text = {
      Text(
        "Removing the fabric from the device failed. " +
          "Do you still want to remove the device from the application?"
      )
    },
    confirmButton = {
      Button(
        onClick = {
          showDialog = false
          onConfirmDeviceRemovalOutcome(true)
        }
      ) {
        Text(stringResource(R.string.yes_remove_it))
      }
    },
    onDismissRequest = {},
    dismissButton = {
      Button(
        onClick = {
          showDialog = false
          onConfirmDeviceRemovalOutcome(false)
        }
      ) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

// ---------------------------------------------------------------------------
// Share Device

fun shareDevice(
  context: Context,
  shareDeviceLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
  deviceViewModel: DeviceViewModel,
) {
  // CODELAB: shareDevice
  Timber.d("ShareDevice: starting")

  val shareDeviceRequest =
    ShareDeviceRequest.builder()
      .setDeviceDescriptor(DeviceDescriptor.builder().build())
      .setDeviceName("GHSAFM temp device name")
      .setCommissioningWindow(
        CommissioningWindow.builder()
          .setDiscriminator(Discriminator.forLongValue(DISCRIMINATOR))
          .setPasscode(SETUP_PIN_CODE)
          .setWindowOpenMillis(SystemClock.elapsedRealtime())
          .setDurationSeconds(OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS.toLong())
          .build()
      )
      .build()
  Timber.d(
    "ShareDevice: shareDeviceRequest " +
      "onboardingPayload [${shareDeviceRequest.commissioningWindow.passcode}] " +
      "discriminator [${shareDeviceRequest.commissioningWindow.discriminator}]"
  )

  // The call to shareDevice() creates the IntentSender that will eventually be launched
  // in the fragment to trigger the multi-admin activity in GPS (step 3).
  Matter.getCommissioningClient(context)
    .shareDevice(shareDeviceRequest)
    .addOnSuccessListener { result ->
      Timber.d("ShareDevice: Success getting the IntentSender: result [${result}]")
      shareDeviceLauncher.launch(IntentSenderRequest.Builder(result).build())
    }
    .addOnFailureListener { error ->
      Timber.e(error)
      deviceViewModel.showMsgDialog("Share device failed", error.toString())
    }
  // CODELAB SECTION END
}

// -----------------------------------------------------------------------------------------------
// Compose Previews

@Preview(widthDp = 300)
@Composable
private fun OnOffStateSection_OnlineOn() {
  MaterialTheme { OnOffStateSection(isOnline = true, isOn = true)
    { Timber.d("OnOff state changed to $it") }
  }
}

@Preview(widthDp = 300)
@Composable
private fun OnOffStateSection_Offline() {
  MaterialTheme { OnOffStateSection(false, true, { Timber.d("OnOff state changed to $it") }) }
}

@Preview(widthDp = 300)
@Composable
private fun ShareSectionPreview() {
  MaterialTheme { ShareSection("Lightbulb", {}) }
}

@Preview(widthDp = 300)
@Composable
private fun TechnicalInfoSectionPreview() {
  MaterialTheme { TechnicalInfoSection(DeviceTest, {}, true) }
}

@Preview
@Composable
private fun RemoveDeviceSectionPreview() {
  MaterialTheme { RemoveDeviceSection({ Timber.d("preview", "button clicked") }) }
}

@Preview(widthDp = 300)
@Composable
private fun DeviceScreenOnlineOnPreview() {
  val deviceState = DeviceState_OnlineOn
  val device = DeviceTest
  val deviceUiModel = DeviceUiModel(device, true, true)
  val onOnOffClick: (value: Boolean) -> Unit =
    { value ->
      Timber.d("deviceUiModel [$deviceUiModel] value [$value]")
    }
  MaterialTheme {
    DeviceScreen(
      PaddingValues(),
      deviceUiModel,
      deviceState,
      onOnOffClick,
      {},
      {},
      {},
      null,
      {},
      false,
      {},
      false,
      {},
    )
  }
}

// -----------------------------------------------------------------------------------------------
// Constant objects used in Compose Preview

// DeviceState -- Online and On
private val DeviceState_OnlineOn =
  DeviceState.newBuilder()
    .setDateCaptured(Timestamp.getDefaultInstance())
    .setDeviceId(1L)
    .setOn(true)
    .setOnline(true)
    .build()

// DeviceState -- Offline
private val DeviceState_Offline =
  DeviceState.newBuilder()
    .setDateCaptured(Timestamp.getDefaultInstance())
    .setDeviceId(1L)
    .setOn(false)
    .setOnline(false)
    .build()

private val DeviceTest =
  Device.newBuilder()
    .setDeviceId(1L)
    .setDeviceType(Device.DeviceType.TYPE_OUTLET)
    .setDateCommissioned(Timestamp.getDefaultInstance())
    .setName("MyOutlet")
    .setProductId("8785")
    .setVendorId("6006")
    .setRoom("Office")
    .build()
