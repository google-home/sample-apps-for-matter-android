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

import android.app.Activity.RESULT_OK
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.homesampleapp.BackgroundWorkAlertDialogAction
import com.google.homesampleapp.Device
import com.google.homesampleapp.DeviceState
import com.google.homesampleapp.R
import com.google.homesampleapp.TaskStatus
import com.google.homesampleapp.TaskStatus.InProgress
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.databinding.FragmentDeviceBinding
import com.google.homesampleapp.formatTimestamp
import com.google.homesampleapp.intentSenderToString
import com.google.homesampleapp.lifeCycleEvent
import com.google.homesampleapp.screens.device.DeviceViewModel.Companion.DEVICE_REMOVAL_COMPLETED
import com.google.homesampleapp.screens.device.DeviceViewModel.Companion.DEVICE_REMOVAL_CONFIRM
import com.google.homesampleapp.screens.home.DeviceUiModel
import com.google.homesampleapp.screens.home.HomeViewModel
import com.google.homesampleapp.showAlertDialog
import com.google.homesampleapp.stateDisplayString
import com.google.protobuf.Timestamp
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/*
FIXME: TODO
- subscription
- wire back: remove device
- wire back: share device
- cleanup
 */

/**
 * The Device Fragment shows all the information about the device that was selected in the Home
 * screen. It supports the following actions:
 * ```
 * - toggle the on/off state of the device
 * - share the device with another Matter commissioner app
 * - remove the device
 * - inspect the device (get all info we can from the clusters supported by the device)
 * ```
 *
 * When the Fragment is viewable, a periodic ping is sent to the device to get its latest
 * information. Main use case is to update the device's online status dynamically.
 */
@AndroidEntryPoint
class DeviceFragment : Fragment() {

  // We get the dynamic state of the device (DeviceState) from this repository.
  @Inject
  internal lateinit var devicesStateRepository: DevicesStateRepository

  // Fragment binding.
  private lateinit var binding: FragmentDeviceBinding

  // Tells whether user asked to show the Inspect information dialog.
  // Note that this is required for now because the "Inspect" icon is in the top appbar
  // which has not yet been migrated to Compose. This will probably be handled differently
  // once that migration is done.
  private val _showInspectInfoLiveData = MutableLiveData(false)
  val showInspectInfoLiveData: LiveData<Boolean> = _showInspectInfoLiveData

  // The ViewModel for the currently selected device.
  //private val selectedDeviceViewModel: SelectedDeviceViewModel by activityViewModels()

  // The fragment's ViewModel.
  //private val viewModel: DeviceViewModel by viewModels()

  // The ActivityResultLauncher that launches the "shareDevice" activity in Google Play Services.
//  private lateinit var shareDeviceLauncher: ActivityResultLauncher<IntentSenderRequest>

  // Background work dialog.
  private lateinit var backgroundWorkAlertDialog: AlertDialog

  // Error alert dialog.
  private lateinit var errorAlertDialog: AlertDialog

  // -----------------------------------------------------------------------------------------------
  // Lifecycle functions

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Share Device Step 1, where an activity launcher is registered.
    // At step 2 of the "Share Device" flow, the user triggers the "Share Device"
    // action and the ViewModel calls the Google Play Services (GPS) API
    // (commissioningClient.shareDevice()).
    // This returns an  IntentSender that is then used in step 3 to call
    // shareDevicelauncher.launch().
    // CODELAB: shareDeviceLauncher definition
    // FIXME
//    shareDeviceLauncher =
//      registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
//        // Share Device Step 5.
//        // The Share Device activity in GPS (step 4) has completed.
//        val resultCode = result.resultCode
//        if (resultCode == RESULT_OK) {
//          Timber.d("ShareDevice: Success")
//          viewModel.shareDeviceSucceeded()
//        } else {
//          viewModel.shareDeviceFailed(
//            selectedDeviceViewModel.selectedDeviceLiveData.value!!, resultCode
//          )
//        }
//      }
    // CODELAB SECTION END
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    super.onCreateView(inflater, container, savedInstanceState)

    Timber.d(lifeCycleEvent("onCreateView()"))

    // Setup the binding with the fragment.
    binding = DataBindingUtil.inflate<FragmentDeviceBinding>(
      inflater,
      R.layout.fragment_device,
      container,
      false
    ).apply {
      composeView.apply {
        // Dispose the Composition when the view's LifecycleOwner is destroyed
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
          MaterialTheme {
//            DeviceRoute()
          }
        }
      }
    }

    // Setup UI elements and livedata observers.
    setupUiElements()
    setupObservers()

    return binding.root
  }

  override fun onResume() {
    super.onResume()
    Timber.d("onResume(): Starting monitoring state changes on device")
//    viewModel.deviceUiModel = selectedDeviceViewModel.selectedDeviceLiveData.value!!
//    viewModel.startMonitoringStateChanges()
  }

  override fun onPause() {
    super.onPause()
    Timber.d("onPause(): Stopping monitoring state changes on device")
//    viewModel.stopMonitoringStateChanges()
  }

  override fun onDestroy() {
    super.onDestroy()
    // Destroy alert dialogs to avoid leaks
    errorAlertDialog.dismiss()
    backgroundWorkAlertDialog.dismiss()
  }

  // -----------------------------------------------------------------------------------------------
  // Setup UI elements

  private fun setupUiElements() {
//    // Background Work AlertDialog.
//    backgroundWorkAlertDialog = MaterialAlertDialogBuilder(requireContext()).create()
//    // Prevents the ability to remove the dialog.
//    backgroundWorkAlertDialog.setCancelable(false)
//    backgroundWorkAlertDialog.setCanceledOnTouchOutside(false)
//
//    // Error AlertDialog
//    errorAlertDialog =
//      MaterialAlertDialogBuilder(requireContext())
//        .setPositiveButton(resources.getString(R.string.ok)) { _, _ ->
//          // Consume the status so the error panel does not show up again
//          // on a config change.
//          viewModel.consumeShareDeviceStatus()
//        }
//        .create()
//
//    // Navigate back
//    // TODO -> currently not working. Fix when finalizing Jetpack Compose migration.
//    binding.topAppBar.setOnClickListener {
//      Timber.d("topAppBar.setOnClickListener")
//      findNavController().popBackStack()
//    }
//
//    binding.topAppBar.setOnMenuItemClickListener {
//      // Now that we've moved isOnline and isOn into the composable,
//      // the handler for clicking on the "Inspect" icon on the top appbar
//      // does not have access to isOnline anymore.
//      // We now have LiveData to keep track of when the Inspect icon is clicked
//      // on the top appbar. When we migrate the top appbar to Compose, this should
//      // then be cleaner and fully handled within the composables.
//      _showInspectInfoLiveData.value = true
//      true
//    }
  }

  private fun removeDevice() {
//    val deviceId = selectedDeviceViewModel.selectedDeviceIdLiveData.value
//    MaterialAlertDialogBuilder(requireContext())
//      .setTitle("Remove this device?")
//      .setMessage(
//        "This device will be removed and unlinked from this sample app. Other services and connection-types may still have access."
//      )
//      .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
//        // nothing to do
//      }
//      .setPositiveButton(resources.getString(R.string.yes_remove_it)) { _, _ ->
//        viewModel.removeDevice(deviceId!!)
//      }
//      .show()
  }

  private fun showBackgroundWorkAlertDialog(title: String?, message: String?) {
    if (title != null) {
      backgroundWorkAlertDialog.setTitle(title)
    }
    if (message != null) {
      backgroundWorkAlertDialog.setMessage(message)
    }
    backgroundWorkAlertDialog.show()
  }

  private fun hideBackgroundWorkAlertDialog() {
    backgroundWorkAlertDialog.hide()
  }

  // -----------------------------------------------------------------------------------------------
  // Setup Observers

  private fun setupObservers() {
//    // Background work alert dialog actions.
//    viewModel.backgroundWorkAlertDialogAction.observe(viewLifecycleOwner) { action ->
//      if (action is BackgroundWorkAlertDialogAction.Show) {
//        showBackgroundWorkAlertDialog(action.title, action.message)
//      } else if (action is BackgroundWorkAlertDialogAction.Hide) {
//        hideBackgroundWorkAlertDialog()
//      }
//    }
//
//    // In the DeviceSharing flow step 2, the ViewModel calls the GPS shareDevice() API to get the
//    // IntentSender to be used with the Android Activity Result API. Once the ViewModel has
//    // the IntentSender, it posts it via LiveData so the Fragment can use that value to launch the
//    // activity (step 3).
//    // Note that when the IntentSender has been processed, it must be consumed to avoid a
//    // configuration change that resends the observed values and re-triggers the device sharing.
//    // CODELAB FEATURED BEGIN
//    viewModel.shareDeviceIntentSender.observe(viewLifecycleOwner) { sender ->
//      Timber.d("shareDeviceIntentSender.observe is called with [${intentSenderToString(sender)}]")
//      if (sender != null) {
//        // Share Device Step 4: Launch the activity described in the IntentSender that
//        // was returned in Step 3 (where the viewModel calls the GPS API to share
//        // the device).
//        Timber.d("ShareDevice: Launch GPS activity to share device")
//        shareDeviceLauncher.launch(IntentSenderRequest.Builder(sender).build())
//        viewModel.consumeShareDeviceIntentSender()
//      }
//    }
//    // CODELAB FEATURED END
//
//    // Generic status about actions processed in this screen.
//    viewModel.uiActionLiveData.observe(viewLifecycleOwner) { uiAction ->
//      Timber.d("uiActionLiveData.observe is called with [${uiAction}]")
//      if (uiAction != null) {
//        when (uiAction.id) {
//          DEVICE_REMOVAL_CONFIRM -> confirmDeviceRemoval(uiAction.data!!.toLong())
//          DEVICE_REMOVAL_COMPLETED -> deviceRemovalCompleted()
//          else -> Timber.e("Invalid ID in errorInfo: [${uiAction.id}]")
//        }
//        viewModel.consumeUiActionLiveData()
//      }
//    }
//  }
  }
}

  // -----------------------------------------------------------------------------------------------
  // Composables

  @Composable
  fun DeviceRoute(
    navController: NavController,
    innerPadding: PaddingValues,
    deviceId: Long
//    viewModel: DeviceViewModel,
//    selectedDeviceViewModel: SelectedDeviceViewModel
  ) {
    Timber.d("DeviceRoute deviceId [$deviceId]")

    val viewModel: DeviceViewModel = hiltViewModel()

    // Observes values needed by the DeviceScreen.
    val deviceUiModel by viewModel.deviceUiModel.collectAsState()
    Timber.d("DeviceRoute deviceUiModel [${deviceUiModel?.device?.deviceId}]")

    // As soon as the route is invoked, need to load the device info.


//    val selectedDeviceId by selectedDeviceViewModel.selectedDeviceIdLiveData.observeAsState()
//    val selectedDeviceUiModel by selectedDeviceViewModel.selectedDeviceLiveData.observeAsState()
    val lastUpdatedDeviceState by viewModel.devicesStateRepository.lastUpdatedDeviceState.observeAsState()
    val shareDeviceStatus by viewModel.shareDeviceStatus.observeAsState()
    // fixme  val showInspectInfo by showInspectInfoLiveData.observeAsState()
    val showInspectInfo = false // false

    // FIXME
//    binding.topAppBar.title = selectedDevice?.device?.name

    val onOnOffClick: (deviceUiModel: DeviceUiModel, value: Boolean) -> Unit =
      { deviceUiModel, value ->
        viewModel.updateDeviceStateOn(deviceUiModel, value)
      }

    val onShareDeviceClick: (deviceId: Long) -> Unit = { deviceId ->
      viewModel.shareDevice(deviceId)
    }

    // Share Device Step 1, where an activity launcher is registered.
    // At step 2 of the "Share Device" flow, the user triggers the "Share Device"
    // action and the ViewModel calls the Google Play Services (GPS) API
    // (commissioningClient.shareDevice()).
    // This returns an  IntentSender that is then used in step 3 to call
    // shareDevicelauncher.launch().
    // CODELAB: shareDeviceLauncher definition
    val shareDeviceLauncher =
      rememberLauncherForActivityResult(contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // Share Device Step 5.
        // The Share Device activity in GPS (step 4) has completed.
        val resultCode = result.resultCode
        if (resultCode == RESULT_OK) {
          Timber.d("ShareDevice: Success")
          viewModel.shareDeviceSucceeded()
        } else {
//          viewModel.shareDeviceFailed(
            // FIXME
            //selectedDeviceViewModel.selectedDeviceLiveData.value!!, resultCode
//          )
        }
      }

    // FIXME: Understand when I come from the constructor and when I come from here.
    // When app is sent to the background, and pulled back, this kicks in.
    LifecycleResumeEffect {
      Timber.d("LifecycleResumeEffect: deviceUiModel [${deviceUiModel?.device?.deviceId}]")
      viewModel.loadDevice(deviceId)
      onPauseOrDispose {
        // do any needed clean up here
        Timber.d("LifecycleResumeEffect:onPauseOrDispose deviceUiModel [${deviceUiModel?.device?.deviceId}]")
      }
    }


    DeviceScreen(
      innerPadding,
      deviceUiModel,
      lastUpdatedDeviceState,
      shareDeviceStatus,
      showInspectInfo,
      onOnOffClick
    )
  }

  @Composable
  private fun DeviceScreen(
    innerPadding: PaddingValues,
    deviceUiModel: DeviceUiModel?,
    deviceState: DeviceState?,
    shareDeviceStatus: TaskStatus?,
    showInspectInfo: Boolean?,
    onOnOffClick: (deviceUiModel: DeviceUiModel, value: Boolean) -> Unit
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
      deviceUiModel?.let { model ->
        isOnline = when (deviceState) {
          null -> model.isOnline
          else -> deviceState.online
        }
        isOn = when (deviceState) {
          null -> model.isOn
          else -> deviceState.on
        }
      }
    }

    deviceUiModel.let { model ->
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(innerPadding)
      ) {
        OnOffStateSection(isOnline, isOn) {
          onOnOffClick(deviceUiModel, it)
        }
        ShareSection(
          id = model.device.deviceId,
          name = model.device.name,
          shareDeviceStatus
        )
        // TODO: Use HorizontalDivider when it becomes part of the stable Compose BOM.
        Spacer(
          modifier = Modifier,
        )
        TechnicalInfoSection(model.device)
        RemoveDeviceSection({ /* fixme removeDevice() */ })
      }
    }

//    if (showInspectInfo!!) {
//      if (isOnline) {
////        findNavController().navigate(R.id.action_deviceFragment_to_inspectFragment)
//      } else {
//        MaterialAlertDialogBuilder(requireContext())
//          .setTitle("Inspect Device")
//          .setMessage("Device is offline, cannot be inspected.")
//          .setPositiveButton(resources.getString(R.string.ok)) { _, _ -> }
//          .show()
//      }
////      _showInspectInfoLiveData.value = false
//    }
  }

  @Composable
  private fun OnOffStateSection(
    isOnline: Boolean,
    isOn: Boolean,
    onStateChange: ((Boolean) -> Unit)?
  ) {
    val bgColor =
      if (isOnline && isOn) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val contentColor =
      if (isOnline && isOn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val text = stateDisplayString(isOnline, isOn)
    Surface(
      modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal)),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
      contentColor = contentColor,
      color = bgColor,
      shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner))
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .padding(dimensionResource(R.dimen.padding_surface_content))
      ) {
        Text(
          text = text,
          style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.weight(1f))
        Switch(
          checked = isOn,
          onCheckedChange = onStateChange
        )
      }
    }
  }

  @Composable
  private fun ShareSection(id: Long, name: String, shareDeviceStatus: TaskStatus?) {
    val shareButtonEnabled = shareDeviceStatus !is InProgress
    if (shareDeviceStatus is TaskStatus.Failed) {
      // fixme showAlertDialog(errorAlertDialog, shareDeviceStatus.message, shareDeviceStatus.cause!!.toString())
    }
    Surface(
      modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal)),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner))
    ) {
      Column(
        modifier = Modifier
          .padding(8.dp),
      ) {
        Text(
          text = stringResource(R.string.share_device_name, name),
          style = MaterialTheme.typography.bodyLarge,
        )
        Text(
          text = stringResource(R.string.share_device_body),
          style = MaterialTheme.typography.bodySmall,
        )
        Row(
          modifier = Modifier
            .fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          Button(
            onClick = {
//              val deviceId = selectedDeviceViewModel.selectedDeviceLiveData.value?.device?.deviceId
//              // Trigger the processing for sharing the device
//              viewModel.shareDevice(requireActivity(), id)
            },
            enabled = shareButtonEnabled,
          ) {
            Text(stringResource(R.string.share))
          }
        }
      }
    }
  }

  @Composable
  private fun TechnicalInfoSection(device: Device) {
    Column(
      modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal)),

      ) {
      Text(
        text = stringResource(R.string.technical_information),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
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
          device.deviceType
        ),
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }

  @Composable
  private fun RemoveDeviceSection(onClick: () -> Unit) {
    Row(
    ) {
      TextButton(
        onClick = onClick,
      ) {
        Icon(
          Icons.Outlined.Delete,
          contentDescription = "Localized description"
        )
        Text(stringResource(R.string.remove_device).toUpperCase())
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // UIAction handling

//  private fun confirmDeviceRemoval(deviceId: Long) {
//    MaterialAlertDialogBuilder(requireContext())
//        .setTitle("Error removing the fabric from the device")
//        .setMessage(
//            "Removing the fabric from the device failed. " +
//                "Do you still want to remove the device from the application?")
//        .setPositiveButton(resources.getString(R.string.yes)) { _, _ ->
//          viewModel.removeDeviceWithoutUnlink(deviceId)
//        }
//        .setNegativeButton(resources.getString(R.string.no)) { _, _ -> }
//        .show()
//  }

//  private fun deviceRemovalCompleted() {
//    requireView().findNavController().navigate(R.id.action_deviceFragment_to_homeFragment)
//  }

  // -----------------------------------------------------------------------------------------------
  // Compose Preview

  @Preview(widthDp = 300)
  @Composable
  private fun OnOffStateSection_OnlineOn() {
    MaterialTheme {
      OnOffStateSection(true, true, { Timber.d("OnOff state changed to $it") })
    }
  }

  @Preview(widthDp = 300)
  @Composable
  private fun OnOffStateSection_Offline() {
    MaterialTheme {
      OnOffStateSection(false, true, { Timber.d("OnOff state changed to $it") })
    }
  }

  @Preview(widthDp = 300)
  @Composable
  private fun ShareSectionPreview() {
    MaterialTheme {
      ShareSection(1L, "Lightbulb", TaskStatus.NotStarted)
    }
  }

  @Preview(widthDp = 300)
  @Composable
  private fun TechnicalInfoSectionPreview() {
    MaterialTheme {
      TechnicalInfoSection(DeviceTest)
    }
  }

  @Preview()
  @Composable
  private fun RemoveDeviceSectionPreview() {
    MaterialTheme {
      RemoveDeviceSection(
        { Timber.d("preview", "button clicked") }
      )
    }
  }

  @Preview(widthDp = 300)
  @Composable
  private fun DeviceScreenOnlineOnPreview() {
    val deviceState = DeviceState_OnlineOn
    val device = DeviceTest
    val deviceUiModel = DeviceUiModel(device, true, true)
    val onOnOffClick: (deviceUiModel: DeviceUiModel, value: Boolean) -> Unit =
      { deviceUiModel, value ->
        Timber.d("deviceUiModel [$deviceUiModel] value [$value]")
      }
    MaterialTheme {
      DeviceScreen(PaddingValues(), deviceUiModel, deviceState, TaskStatus.NotStarted, false, onOnOffClick)
    }
  }

// -----------------------------------------------------------------------------------------------
// Constant objects used in Compose Preview

  // DeviceState -- Online and On
  private val DeviceState_OnlineOn = DeviceState.newBuilder()
    .setDateCaptured(Timestamp.getDefaultInstance())
    .setDeviceId(1L)
    .setOn(true)
    .setOnline(true)
    .build()

  // DeviceState -- Offline
  private val DeviceState_Offline = DeviceState.newBuilder()
    .setDateCaptured(Timestamp.getDefaultInstance())
    .setDeviceId(1L)
    .setOn(false)
    .setOnline(false)
    .build()

  private val DeviceTest = Device.newBuilder()
    .setDeviceId(1L)
    .setDeviceType(Device.DeviceType.TYPE_OUTLET)
    .setDateCommissioned(Timestamp.getDefaultInstance())
    .setName("MyOutlet")
    .setProductId("8785")
    .setVendorId("6006")
    .setRoom("Office")
    .build()

