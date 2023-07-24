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
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.homesampleapp.BackgroundWorkAlertDialogAction
import com.google.homesampleapp.DeviceState
import com.google.homesampleapp.ON_OFF_SWITCH_DISABLED_WHEN_DEVICE_OFFLINE
import com.google.homesampleapp.R
import com.google.homesampleapp.TaskStatus
import com.google.homesampleapp.TaskStatus.InProgress
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.databinding.FragmentDeviceBinding
import com.google.homesampleapp.displayString
import com.google.homesampleapp.formatTimestamp
import com.google.homesampleapp.intentSenderToString
import com.google.homesampleapp.lifeCycleEvent
import com.google.homesampleapp.screens.device.DeviceViewModel.Companion.DEVICE_REMOVAL_COMPLETED
import com.google.homesampleapp.screens.device.DeviceViewModel.Companion.DEVICE_REMOVAL_CONFIRM
import com.google.homesampleapp.screens.shared.SelectedDeviceViewModel
import com.google.homesampleapp.showAlertDialog
import com.google.homesampleapp.stateDisplayString
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

/**
 * The Device Fragment shows all the information about the device that was selected in the Home
 * screen and supports the following actions:
 * ```
 * - toggle the on/off state of the device
 * - share the device with another Matter commissioner app
 * - inspect the device (get all info we can from the clusters supported by the device)
 * ```
 *
 * When the Fragment is viewable, a periodic ping is sent to the device to get its latest
 * information. Main use case is to update the device's online status dynamically.
 */
@AndroidEntryPoint
class DeviceFragment : Fragment() {

  @Inject internal lateinit var devicesStateRepository: DevicesStateRepository

  // Fragment binding.
  private lateinit var binding: FragmentDeviceBinding

  // The ViewModel for the currently selected device.
  private val selectedDeviceViewModel: SelectedDeviceViewModel by activityViewModels()

  // The fragment's ViewModel.
  private val viewModel: DeviceViewModel by viewModels()

  // The ActivityResultLauncher that launches the "shareDevice" activity in Google Play Services.
  private lateinit var shareDeviceLauncher: ActivityResultLauncher<IntentSenderRequest>

  // Background work dialog.
  private lateinit var backgroundWorkAlertDialog: AlertDialog

  // Error alert dialog.
  private lateinit var errorAlertDialog: AlertDialog

  // The current state of the device.
  var isOnline = false
  var isOn = false

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
    shareDeviceLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
          // Share Device Step 5.
          // The Share Device activity in GPS (step 4) has completed.
          val resultCode = result.resultCode
          if (resultCode == RESULT_OK) {
            Timber.d("ShareDevice: Success")
            viewModel.shareDeviceSucceeded()
          } else {
            viewModel.shareDeviceFailed(
                selectedDeviceViewModel.selectedDeviceLiveData.value!!, resultCode)
          }
        }
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
    binding = DataBindingUtil.inflate(inflater, R.layout.fragment_device, container, false)

    // Setup UI elements and livedata observers.
    setupUiElements()
    setupObservers()

    return binding.root
  }

  override fun onResume() {
    super.onResume()
    Timber.d("onResume(): Starting monitoring state changes on device")
    viewModel.deviceUiModel = selectedDeviceViewModel.selectedDeviceLiveData.value!!
    viewModel.startMonitoringStateChanges()
  }

  override fun onPause() {
    super.onPause()
    Timber.d("onPause(): Stopping periodic ping on device")
    viewModel.stopMonitoringStateChanges()
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
    // Background Work AlertDialog.
    backgroundWorkAlertDialog = MaterialAlertDialogBuilder(requireContext()).create()
    // Prevents the ability to remove the dialog.
    backgroundWorkAlertDialog.setCancelable(false)
    backgroundWorkAlertDialog.setCanceledOnTouchOutside(false)

    // Error AlertDialog
    errorAlertDialog =
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(resources.getString(R.string.ok)) { _, _ ->
              // Consume the status so the error panel does not show up again
              // on a config change.
              viewModel.consumeShareDeviceStatus()
            }
            .create()

    // Navigate back
    binding.topAppBar.setOnClickListener {
      Timber.d("topAppBar.setOnClickListener")
      findNavController().popBackStack()
    }

    binding.topAppBar.setOnMenuItemClickListener {
      processInpectDeviceInfo()
      true
    }

    // Share Device Button
    binding.shareButton.setOnClickListener {
      val deviceName = selectedDeviceViewModel.selectedDeviceLiveData.value?.device?.name!!
      val deviceId = selectedDeviceViewModel.selectedDeviceLiveData.value?.device?.deviceId
      // Trigger the processing for sharing the device
      viewModel.shareDevice(requireActivity(), deviceId!!)
    }

    // CODELAB FEATURED BEGIN
    // Change the on/off state of the device
    binding.onoffSwitch.setOnClickListener {
      val isOn = binding.onoffSwitch.isChecked
      viewModel.updateDeviceStateOn(selectedDeviceViewModel.selectedDeviceLiveData.value!!, isOn)
    }
    // CODELAB FEATURED END

    // Remove Device
    binding.removeButton.setOnClickListener {
      val deviceId = selectedDeviceViewModel.selectedDeviceIdLiveData.value
      MaterialAlertDialogBuilder(requireContext())
          .setTitle("Remove this device?")
          .setMessage(
              "This device will be removed and unlinked from this sample app. Other services and connection-types may still have access.")
          .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
            // nothing to do
          }
          .setPositiveButton(resources.getString(R.string.yes_remove_it)) { _, _ ->
            viewModel.removeDevice(deviceId!!)
          }
          .show()
    }
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

  private fun updateShareDeviceButton(enable: Boolean) {
    binding.shareButton.isEnabled = enable
  }

  private fun processInpectDeviceInfo() {
    if (!isOnline) {
      MaterialAlertDialogBuilder(requireContext())
          .setTitle("Inspect Device")
          .setMessage("Device is offline, cannot be inspected.")
          .setPositiveButton(resources.getString(R.string.ok)) { _, _ -> }
          .show()
    } else {
      findNavController().navigate(R.id.action_deviceFragment_to_inspectFragment)
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Setup Observers

  private fun setupObservers() {
    // Generic status about actions processed in this screen.
    devicesStateRepository.lastUpdatedDeviceState.observe(viewLifecycleOwner) {
      Timber.d(
          "devicesStateRepository.lastUpdatedDeviceState.observe: [${devicesStateRepository.lastUpdatedDeviceState.value}]")
      updateDeviceInfo(devicesStateRepository.lastUpdatedDeviceState.value)
    }

    // CODELAB FEATURED BEGIN
    // The current status of the share device action.
    viewModel.shareDeviceStatus.observe(viewLifecycleOwner) { status ->
      val isButtonEnabled = status !is InProgress
      updateShareDeviceButton(isButtonEnabled)
      if (status is TaskStatus.Failed) {
        showAlertDialog(errorAlertDialog, status.message, status.cause!!.toString())
      }
    }
    // CODELAB FEATURED END

    // Background work alert dialog actions.
    viewModel.backgroundWorkAlertDialogAction.observe(viewLifecycleOwner) { action ->
      if (action is BackgroundWorkAlertDialogAction.Show) {
        showBackgroundWorkAlertDialog(action.title, action.message)
      } else if (action is BackgroundWorkAlertDialogAction.Hide) {
        hideBackgroundWorkAlertDialog()
      }
    }

    // In the DeviceSharing flow step 2, the ViewModel calls the GPS shareDevice() API to get the
    // IntentSender to be used with the Android Activity Result API. Once the ViewModel has
    // the IntentSender, it posts it via LiveData so the Fragment can use that value to launch the
    // activity (step 3).
    // Note that when the IntentSender has been processed, it must be consumed to avoid a
    // configuration change that resends the observed values and re-triggers the device sharing.
    // CODELAB FEATURED BEGIN
    viewModel.shareDeviceIntentSender.observe(viewLifecycleOwner) { sender ->
      Timber.d("shareDeviceIntentSender.observe is called with [${intentSenderToString(sender)}]")
      if (sender != null) {
        // Share Device Step 4: Launch the activity described in the IntentSender that
        // was returned in Step 3 (where the viewModel calls the GPS API to share
        // the device).
        Timber.d("ShareDevice: Launch GPS activity to share device")
        shareDeviceLauncher.launch(IntentSenderRequest.Builder(sender).build())
        viewModel.consumeShareDeviceIntentSender()
      }
    }
    // CODELAB FEATURED END

    // Observer on the currently selected device
    selectedDeviceViewModel.selectedDeviceIdLiveData.observe(viewLifecycleOwner) { deviceId ->
      Timber.d(
          "selectedDeviceViewModel.selectedDeviceIdLiveData.observe is called with deviceId [${deviceId}]")
      // After a device is removed, this is called with deviceId set to -1. Needs to be ignored.
      if (deviceId != -1L) {
        viewModel.deviceUiModel = selectedDeviceViewModel.selectedDeviceLiveData.value!!
        updateDeviceInfo(null)
      }
    }

    viewModel.uiActionLiveData.observe(viewLifecycleOwner) { uiAction ->
      Timber.d("uiActionLiveData.observe is called with [${uiAction}]")
      if (uiAction != null) {
        when (uiAction.id) {
          DEVICE_REMOVAL_CONFIRM -> confirmDeviceRemoval(uiAction.data!!.toLong())
          DEVICE_REMOVAL_COMPLETED -> deviceRemovalCompleted()
          else -> Timber.e("Invalid ID in errorInfo: [${uiAction.id}]")
        }
        viewModel.consumeUiActionLiveData()
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // UI update functions

  private fun updateDeviceInfo(deviceState: DeviceState?) {
    // The DeviceUiModel is not updated whenever we observe changes in the state of the device.
    // This is an issue for the "Inspect Device" onClick listener which relies on the device
    // state to decide whether to show a dialog stating that the device is offline and therefore
    // the inspect screen cannot be shown, or go show the inspect information (when device is
    // online).
    // This is why the state of the device is cached in local variables.
    if (selectedDeviceViewModel.selectedDeviceIdLiveData.value == -1L) {
      // Device was just removed, nothing to do. We'll move to HomeFragment.
      isOnline = false
      return
    }
    val deviceUiModel = selectedDeviceViewModel.selectedDeviceLiveData.value

    // Device state
    deviceUiModel?.let {
      isOnline =
          when (deviceState) {
            null -> deviceUiModel.isOnline
            else -> deviceState.online
          }
      isOn =
          when (deviceState) {
            null -> deviceUiModel.isOn
            else -> deviceState.on
          }

      binding.topAppBar.title = deviceUiModel.device.name

      val shapeOffDrawable = getDrawable("device_item_shape_off")
      val shapeOnDrawable = getDrawable("device_item_shape_on")
      val shapeDrawable = if (isOnline && isOn) shapeOnDrawable else shapeOffDrawable

      binding.shareLine1TextView.text =
          getString(R.string.share_device_name, deviceUiModel.device.name)
      binding.onOffTextView.text = stateDisplayString(isOnline, isOn)
      binding.stateLayout.background = shapeDrawable
      if (ON_OFF_SWITCH_DISABLED_WHEN_DEVICE_OFFLINE) {
        binding.onoffSwitch.isEnabled = isOnline
      } else {
        binding.onoffSwitch.isEnabled = true
      }
      binding.onoffSwitch.isChecked = isOn
      binding.techInfoDetailsTextView.text =
          getString(
              R.string.share_device_info,
              formatTimestamp(deviceUiModel.device.dateCommissioned!!, null),
              deviceUiModel.device.deviceId.toString(),
              deviceUiModel.device.vendorName,
              deviceUiModel.device.vendorId,
              deviceUiModel.device.productName,
              deviceUiModel.device.productId,
              deviceUiModel.device.deviceType.displayString())
    }
  }

  private fun getDrawable(name: String): Drawable? {
    val resources: Resources = requireContext().resources
    val resourceId = resources.getIdentifier(name, "drawable", requireContext().packageName)
    return resources.getDrawable(resourceId)
  }

  // -----------------------------------------------------------------------------------------------
  // UIAction handling

  private fun confirmDeviceRemoval(deviceId: Long) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle("Error removing the fabric from the device")
        .setMessage(
            "Removing the fabric from the device failed. " +
                "Do you still want to remove the device from the application?")
        .setPositiveButton(resources.getString(R.string.yes)) { _, _ ->
          viewModel.removeDeviceWithoutUnlink(deviceId)
        }
        .setNegativeButton(resources.getString(R.string.no)) { _, _ -> }
        .show()
  }

  private fun deviceRemovalCompleted() {
    requireView().findNavController().navigate(R.id.action_deviceFragment_to_homeFragment)
  }
}
