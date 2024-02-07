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

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.method.LinkMovementMethod
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.DeviceInfo
import com.google.android.gms.home.matter.commissioning.SharedDeviceData
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_COMMISSIONING_WINDOW_EXPIRATION
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_DEVICE_NAME
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_MANUAL_PAIRING_CODE
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_PRODUCT_ID
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_VENDOR_ID
import com.google.android.material.textview.MaterialTextView
import com.google.homesampleapp.AppViewModel
import com.google.homesampleapp.Device
import com.google.homesampleapp.MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS
import com.google.homesampleapp.R
import com.google.homesampleapp.TaskStatus
import com.google.homesampleapp.commissioning.AppCommissioningService
import com.google.homesampleapp.getDeviceTypeIconId
import com.google.homesampleapp.isMultiAdminCommissioning
import com.google.homesampleapp.screens.common.DialogInfo
import com.google.homesampleapp.screens.common.MsgAlertDialog
import com.google.homesampleapp.screens.shared.UserPreferencesViewModel
import com.google.homesampleapp.screens.thread.getActivity
import com.google.homesampleapp.stateDisplayString
import com.google.protobuf.Timestamp
import timber.log.Timber

/**
 * Home screen for the application.
 *
 * The Home screen features four sections:
 * 1. The list of devices currently commissioned into the app's fabric. When the user clicks on a
 *    device, app flow moves to the Device screen where one can get additional details on the device
 *    and perform actions on it. Devices are persisted in the DevicesRepository, a Proto Datastore.
 *    It's possible to hide the devices that are currently offline via a setting in the Settings
 *    screen.
 * 2. Top App Bar. Settings icon to navigate to the Settings screen.
 * 3. "Add Device" button. Triggers the commissioning of a new device.
 * 4. Codelab information. When the app is first launched, a Dialog is shown to provide information
 *    about the app's companion codelab. This can be dismissed via a checkbox and the setting is
 *    persisted in the UserPreferences proto datastore.
 *
 * Note:
 * - The app currently only supports Matter devices with server attribute "ON/OFF".
 *
 * TODO:
 * - Finding out that a device is offline is not working very well. Much work needed there.
 */
@Composable
internal fun HomeRoute(
  innerPadding: PaddingValues,
  updateTitle: (title: String) -> Unit,
  navigateToDevice: (deviceId: Long) -> Unit,
  userPreferencesViewModel: UserPreferencesViewModel = hiltViewModel(),
  homeViewModel: HomeViewModel = hiltViewModel(),
) {
  // Launching GPS commissioning requires Activity.
  val activity = LocalContext.current.getActivity()

  // UI Model for all the devices shown on the screen.
  val devicesUiModel by homeViewModel.devicesUiModelLiveData.observeAsState()
  val devices = devicesUiModel?.devices
  val devicesList = devices ?: emptyList()

  // Tells whether a device attestation failure was ignored.
  // This is used in the "Device information" screen to warn the user about that fact.
  // We're doing it this way as we cannot ask permission to the user while the
  // decision has to be made because UI is fully controlled by GPS at that point.
  val deviceAttestationFailureIgnored by
    homeViewModel.deviceAttestationFailureIgnored.collectAsState()

  // Controls whether the codelab alert dialog should be shown.
  val showCodelabAlertDialog by userPreferencesViewModel.showCodelabAlertDialog.collectAsState()
  val onCodelabCheckboxChange: (checked: Boolean) -> Unit = remember {
    {
      userPreferencesViewModel.updateHideCodelabInfo(it)
    }
  }

  // Controls when the "New Device" alert dialog is shown.
  // When that alert dialog completes, control needs to go back to the ViewModel to complete
  // the commissioning flow.
  val showNewDeviceAlertDialog by homeViewModel.showNewDeviceNameAlertDialog.collectAsState()
  val onCommissionedDeviceNameCaptured: (name: String) -> Unit = remember {
    {
      homeViewModel.onCommissionedDeviceNameCaptured(it)
    }
  }

  // Controls the Msg AlertDialog.
  // When the user dismisses the Msg AlertDialog, we "consume" the dialog.
  val msgDialogInfo by homeViewModel.msgDialogInfo.collectAsState()
  val onDismissMsgDialog: () -> Unit = remember {
    { homeViewModel.dismissMsgDialog() }
  }

  // Status of multiadmin commissioning.
  val multiadminCommissionDeviceTaskStatus by
    homeViewModel.multiadminCommissionDeviceTaskStatus.collectAsState()

  // Functions invoked when UI controls are clicked on a specific device in the list.
  val onDeviceClick: (deviceUiModel: DeviceUiModel) -> Unit = remember {
    {
      navigateToDevice(it.device.deviceId)
    }
  }
  val onOnOffClick: (deviceId: Long, value: Boolean) -> Unit = remember {
    { deviceId, value ->
      homeViewModel.updateDeviceStateOn(deviceId, value)
    }
  }

  // The device commissioning flow involves multiple steps as it is based on an Activity
  // that is launched on the Google Play Services (GPS).
  // Step 1 (here) is where An activity launcher is registered.
  // At step 2, the user triggers the "Commission Device" action by clicking on the
  // "Add device" button on this screen. This creates the proper IntentSender that is then
  // used in step 3 to call commissionDevicelauncher.launch().
  // Step 4 is when GPS takes over the commissioning flow.
  // Step 5 is when the GPS activity completes and the result is handled here.
  // CODELAB: commissionDeviceLauncher definition
  val commissionDeviceLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
      // Commission Device Step 5.
      // The Commission Device activity in GPS (step 4) has completed.
      val resultCode = result.resultCode
      if (resultCode == Activity.RESULT_OK) {
        Timber.d("CommissionDevice: Success")
        // We let the ViewModel know that GPS commissioning has completed successfully.
        // The ViewModel knows that we still need to capture the device name and will\
        // update UI state to trigger the NewDeviceAlertDialog.
        homeViewModel.gpsCommissioningDeviceSucceeded(result)
      } else {
        homeViewModel.commissionDeviceFailed(resultCode)
      }
    }
  // CODELAB SECTION END

  val onCommissionDevice: () -> Unit = remember {
    {
      Timber.d("onAddDeviceClick")
      // fixme deviceAttestationFailureIgnored = false
      homeViewModel.stopMonitoringStateChanges()
      commissionDevice(activity!!.applicationContext, commissionDeviceLauncher)
    }
  }

  LaunchedEffect(Unit) {
    updateTitle("Home")
  }

  LifecycleResumeEffect {
    Timber.d("HomeScreen: LifecycleResumeEffect")
    val intent = activity!!.intent
    Timber.d("intent [${intent}]")
    if (isMultiAdminCommissioning(intent)) {
      Timber.d("Invocation: MultiAdminCommissioning")
      if (multiadminCommissionDeviceTaskStatus == TaskStatus.NotStarted) {
        Timber.d("TaskStatus.NotStarted so starting multiadmin commissioning")
        homeViewModel.setMultiadminCommissioningTaskStatus(TaskStatus.InProgress)
        multiAdminCommissionDevice(
          activity.applicationContext,
          intent,
          homeViewModel,
          commissionDeviceLauncher,
        )
      } else {
        Timber.d("TaskStatus is *not* NotStarted: $multiadminCommissionDeviceTaskStatus")
      }
    } else {
      Timber.d("Invocation: Main")
      homeViewModel.startMonitoringStateChanges()
    }
    // FIXME[TJ]: I had this on fragment's create(). Anything similar to that for composables?
    // We need our own device attestation delegate as we currently only support attestation
    // of test Matter devices. This DeviceAttestationDelegate makes it possible to ignore device
    // attestation failures, which happen if commissioning production devices.
    // TODO: Look into supporting different Root CAs.
    // FIXME: This currently breaks commissioning. Removed for now.
    // homeViewModel.setDeviceAttestationDelegate()
    onPauseOrDispose {
      // do any needed clean up here
      Timber.d("LifecycleResumeEffect:onPauseOrDispose stopMonitoringStateChanges()")
      homeViewModel.stopMonitoringStateChanges()
      // FIXME[TJ]: I had this on fragment's destroy(). Anything similar to that for composables?
      // FIXME: This currently breaks commissioning. Removed for now.
      // homeViewModel.resetDeviceAttestationDelegate()
    }
  }

  HomeScreen(
    innerPadding,
    devicesList,
    showCodelabAlertDialog,
    onCodelabCheckboxChange,
    msgDialogInfo,
    onDismissMsgDialog,
    showNewDeviceAlertDialog,
    deviceAttestationFailureIgnored,
    onCommissionedDeviceNameCaptured,
    onCommissionDevice,
    onDeviceClick,
    onOnOffClick,
  )
}

@Composable
private fun HomeScreen(
  innerPadding: PaddingValues,
  devicesList: List<DeviceUiModel>,
  showCodelabUserPrefs: Boolean,
  onCodelabCheckboxChange: (Boolean) -> Unit,
  msgDialogInfo: DialogInfo?,
  onConsumeMsgDialog: () -> Unit,
  showNewDeviceAlertDialog: Boolean,
  deviceAttestationFailureIgnored: Boolean,
  onCommissionedDeviceNameCaptured: (name: String) -> Unit,
  onCommissionDevice: () -> Unit,
  onDeviceClick: (deviceUiModel: DeviceUiModel) -> Unit,
  onOnOffClick: (deviceId: Long, value: Boolean) -> Unit,
) {
  // Alert Dialog taling about the Codelab when the app is first launched.
  CodelabAlertDialog(showCodelabUserPrefs, onCodelabCheckboxChange)

  // Alert Dialog for messages to be shown to the user.
  MsgAlertDialog(msgDialogInfo, onConsumeMsgDialog)

  // Alert Dialog shown when the name of the device must be captured in the commissioning flow.
  NewDeviceAlertDialog(
    showNewDeviceAlertDialog,
    onCommissionedDeviceNameCaptured,
    deviceAttestationFailureIgnored,
  )

  // Content for the screen.
  Box {
    if (devicesList.isEmpty()) {
      NoDevices()
    } else {
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
          // verticalArrangement = Arrangement.spacedBy(1.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(innerPadding)
        ) {
          this.items(devicesList) { device ->
            val onDeviceItemClick: () -> Unit = { onDeviceClick(device) }
            DeviceItem(
              device.device.deviceId,
              device.device.deviceType,
              device.device.name,
              device.isOnline,
              device.isOn,
              onOnOffClick,
              onDeviceItemClick,
            )
          }
        }
      }
    }
    FloatingActionButton(
      onClick = onCommissionDevice,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(16.dp),
    ) {
      Icon(Icons.Filled.Add, contentDescription = "Add")
    }
  }
  LaunchedEffect(devicesList) { Timber.d("HomeRoute [$devicesList]") }
}

@Composable
private fun DeviceItem(
  deviceId: Long,
  deviceType: Device.DeviceType,
  name: String,
  isOnline: Boolean,
  isOn: Boolean,
  onOnOffClick: (deviceId: Long, value: Boolean) -> Unit,
  onDeviceClick: (() -> Unit),
) {
  val bgColor =
    if (isOnline && isOn) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface
  val contentColor =
    if (isOnline && isOn) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface
  val text = stateDisplayString(isOnline, isOn)
  val iconId = getDeviceTypeIconId(deviceType)
  val onCheckedChange: (value: Boolean) -> Unit = { onOnOffClick(deviceId, it) }

  Surface(
    modifier = Modifier
      .padding(top = 12.dp)
      .padding(PaddingValues(horizontal = 12.dp)),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    contentColor = contentColor,
    color = bgColor,
    shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
    onClick = onDeviceClick,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(dimensionResource(R.dimen.padding_surface_content)),
    ) {
      Icon(
        painter = painterResource(id = iconId),
        contentDescription = null, // decorative element
      )
      Column {
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
      }
      Spacer(Modifier.weight(1f))
      Switch(checked = isOn, onCheckedChange = onCheckedChange)
    }
  }
}

@Composable
private fun NewDeviceAlertDialog(
  showNewDeviceAlertDialog: Boolean,
  onCommissionedDeviceNameCaptured: (name: String) -> Unit,
  deviceAttestationFailureIgnored: Boolean,
) {
  if (!showNewDeviceAlertDialog) {
    return
  }

  var inputText by remember { mutableStateOf("") }

  AlertDialog(
    title = { Text(text = "Specify device name") },
    text = {
      Column {
        TextField(
          value = inputText,
          onValueChange = { inputText = it },
          label = { Text("Device name") },
          modifier = Modifier.fillMaxWidth(),
        )
        if (deviceAttestationFailureIgnored) {
          val htmlText =
            HtmlCompat.fromHtml(
                stringResource(R.string.device_attestation_warning),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
              )
              .toString()
          AndroidView(
            modifier = Modifier.padding(top = 20.dp),
            update = { it.text = htmlText },
            factory = {
              MaterialTextView(it).apply { movementMethod = LinkMovementMethod.getInstance() }
            },
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          // Process inputText
          onCommissionedDeviceNameCaptured(inputText)
        },
        enabled = inputText.isNotEmpty(),
      ) {
        Text("OK")
      }
    },
    onDismissRequest = {},
    dismissButton = {},
  )
}

@Composable
private fun CodelabAlertDialog(
  showCodelabAlertDialog: Boolean,
  onCodelabCheckboxChange: (Boolean) -> Unit,
) {
  // The user preference dictates whether this dialog should show or not.
  if (!showCodelabAlertDialog) return

  // Internal state, since the user may click on OK without checking to hide the
  // dialog. Dialog won't be shown anymore until the next app launch.
  var showDialog by remember { mutableStateOf(true) }
  if (!showDialog) return

  val htmlText =
    HtmlCompat.fromHtml(
      stringResource(R.string.showCodelabMessage),
      HtmlCompat.FROM_HTML_MODE_LEGACY,
    )
  var isChecked by remember { mutableStateOf(false) }

  AlertDialog(
    title = { Text(stringResource(id = R.string.codelab)) },
    text = {
      // See https://developer.android.com/codelabs/jetpack-compose-migration
      Column {
        AndroidView(
          update = { it.text = htmlText },
          factory = {
            MaterialTextView(it).apply { movementMethod = LinkMovementMethod.getInstance() }
          },
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = isChecked,
            onCheckedChange = {
              isChecked = !isChecked
              onCodelabCheckboxChange(isChecked)
            },
          )
          Text(stringResource(id = R.string.do_not_show_again))
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          Timber.d("confirmButton: onClick")
          showDialog = false
        }
      ) {
        Text("OK")
      }
    },
    onDismissRequest = {},
    dismissButton = {},
  )
}

@Composable
private fun NoDevices() {
  Column(
    modifier = Modifier.fillMaxSize(), // Make the Column occupy the whole screen
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Image(
      painter = painterResource(R.drawable.emptystate_missing_content),
      contentDescription = stringResource(R.string.no_devices_image),
      modifier = Modifier
        .fillMaxWidth()
        .height(200.dp),
    )
    Text(
      text = stringResource(R.string.no_devices_yet),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally),
    )
    Text(
      text = stringResource(R.string.add_your_first),
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally),
    )
  }
}

// ---------------------------------------------------------------------------
// Launch GPS Activity

fun commissionDevice(
  context: Context,
  commissionDeviceLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
) {
  Timber.d("CommissionDevice: starting")

  // CODELAB: commissionDevice
  val commissionDeviceRequest =
    CommissioningRequest.builder()
      .setCommissioningService(ComponentName(context, AppCommissioningService::class.java))
      .build()

  // The call to commissionDevice() creates the IntentSender that will eventually be launched
  // in the fragment to trigger the commissioning activity in GPS.
  Matter.getCommissioningClient(context)
    .commissionDevice(commissionDeviceRequest)
    .addOnSuccessListener { result ->
      Timber.d("CommissionDevice: Success getting the IntentSender: result [${result}]")
      commissionDeviceLauncher.launch(IntentSenderRequest.Builder(result).build())
    }
    .addOnFailureListener { error ->
      Timber.e(error)
      //      _commissionDeviceStatus.postValue(
      //        TaskStatus.Failed("Setting up the IntentSender failed", error))
    }
  // CODELAB SECTION END
}

fun multiAdminCommissionDevice(
  context: Context,
  intent: Intent,
  homeViewModel: HomeViewModel,
  commissionDeviceLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
) {
  Timber.d("CommissionDevice: starting")

  val sharedDeviceData = SharedDeviceData.fromIntent(intent)
  Timber.d("multiadminCommissioning: sharedDeviceData [${sharedDeviceData}]")
  Timber.d("multiadminCommissioning: manualPairingCode [${sharedDeviceData.manualPairingCode}]")

  val commissionRequestBuilder =
    CommissioningRequest.builder()
      .setCommissioningService(ComponentName(context, AppCommissioningService::class.java))

  // Fill in the commissioning request...

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
    homeViewModel.showMsgDialog(
      title = "Commissioning Window Expiration",
      msg =
        "The commissioning window will " +
          "expire in $timeLeftSeconds seconds, not long enough to complete the commissioning.\n\n" +
          "In the future, please select the target commissioning application faster to avoid this situation.",
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

  Matter.getCommissioningClient(context)
    .commissionDevice(commissioningRequest)
    .addOnSuccessListener { result ->
      Timber.d("Success getting the IntentSender: result [${result}]")
      commissionDeviceLauncher.launch(IntentSenderRequest.Builder(result).build())
    }
    .addOnFailureListener { error ->
      Timber.e(error)
      homeViewModel.showMsgDialog(
        title = "Failed to to get the IntentSender",
        msg = error.toString(),
      )
    }
}

// -----------------------------------------------------------------------------
// Composable previews

@Preview(showSystemUi = true, showBackground = true)
@Composable
private fun HomeScreenNoDevicesPreview() {
  val bogus: (a: Long, b: Boolean) -> Unit = { _, _ -> }
  MaterialTheme {
    HomeScreen(
      PaddingValues(8.dp),
      emptyList(),
      false,
      {},
      null,
      {},
      false,
      false,
      {},
      {},
      {},
      bogus,
    )
  }
}

@Preview
@Composable
private fun HomeScreenWithDevicesPreview() {
  val bogus: (a: Long, b: Boolean) -> Unit = { _, _ -> }
  val devicesList =
    listOf(
      DeviceUiModel(createDevice(), true, true),
      DeviceUiModel(createDevice(name = "Smart Outlet"), true, false),
      DeviceUiModel(createDevice(name = "My living room lamp"), false, true),
    )
  MaterialTheme {
    HomeScreen(
      PaddingValues(8.dp),
      devicesList,
      false,
      {},
      null,
      {},
      false,
      false,
      {},
      {},
      {},
      bogus,
    )
  }
}

@Preview
@Composable
private fun NoDevicesPreview() {
  MaterialTheme { NoDevices() }
}

@Preview
@Composable
private fun CodelabAlertDialogPreview() {
  MaterialTheme { CodelabAlertDialog(true, {}) }
}

@Preview
@Composable
private fun NewDeviceAlertDialogPreview() {
  MaterialTheme { NewDeviceAlertDialog(true, {}, false) }
}

@Preview
@Composable
private fun NewDeviceAlertDialogAttestationFailureIgnoredPreview() {
  MaterialTheme { NewDeviceAlertDialog(true, {}, true) }
}

private fun createDevice(
  deviceId: Long = 1L,
  deviceType: Device.DeviceType = Device.DeviceType.TYPE_OUTLET,
  dateCommissioned: Timestamp = Timestamp.getDefaultInstance(),
  name: String = "My Matter Device",
  productId: String = "8785",
  vendorId: String = "6006",
  room: String = "Living Room",
): Device {
  return Device.newBuilder()
    .setDeviceId(deviceId)
    .setDeviceType(deviceType)
    .setDateCommissioned(dateCommissioned)
    .setName(name)
    .setProductId(productId)
    .setVendorId(vendorId)
    .setRoom(room)
    .build()
}
