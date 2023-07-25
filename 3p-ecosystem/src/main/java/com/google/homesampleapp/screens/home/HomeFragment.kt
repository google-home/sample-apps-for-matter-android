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

import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.text.Html.FROM_HTML_MODE_LEGACY
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import chip.devicecontroller.AttestationInfo
import chip.devicecontroller.DeviceAttestationDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.homesampleapp.R
import com.google.homesampleapp.TaskStatus
import com.google.homesampleapp.chip.ChipClient
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.data.UserPreferencesRepository
import com.google.homesampleapp.databinding.FragmentCodelabInfoCheckboxBinding
import com.google.homesampleapp.databinding.FragmentHomeBinding
import com.google.homesampleapp.databinding.FragmentNewDeviceBinding
import com.google.homesampleapp.intentSenderToString
import com.google.homesampleapp.isMultiAdminCommissioning
import com.google.homesampleapp.screens.shared.SelectedDeviceViewModel
import com.google.homesampleapp.screens.shared.UserPreferencesViewModel
import com.google.homesampleapp.showAlertDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Home screen for the application.
 *
 * The fragment features four sections:
 * 1. The list of devices currently commissioned into the app's fabric. When the user clicks on a
 *    device, control moves to the Device fragment where one can get additional details on the
 *    device and perform actions on it. Implemented via a RecyclerView. Devices are persisted in the
 *    DevicesRepository, a Proto Datastore. It's possible to hide the devices that are currently
 *    offline via a setting in the Settings screen.
 * 2. Top App Bar. Settings icon to navigate to the Settings screen.
 * 3. "Add Device" button. Triggers the commissioning of a new device.
 * 4. Codelab information. When the fragment view is created, a Dialog is shown to provide
 *    information about the app's companion codelab. This can be dismissed via a checkbox and the
 *    setting is persisted in the UserPreferences proto datastore.
 *
 * Note:
 * - The app currently only supports Matter devices with server attribute "ON/OFF". An icon
 *   representing the on/off device only exists for device type light. Any other device type is
 *   shown with a generic matter device icon.
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

  @Inject internal lateinit var devicesRepository: DevicesRepository
  @Inject internal lateinit var devicesStateRepository: DevicesStateRepository
  @Inject internal lateinit var userPreferencesRepository: UserPreferencesRepository
  @Inject internal lateinit var chipClient: ChipClient

  // Fragment binding.
  private lateinit var binding: FragmentHomeBinding

  private val selectedDeviceViewModel: SelectedDeviceViewModel by activityViewModels()

  // The shared ViewModel for the UserPreferences.
  private val userPreferencesViewModel: UserPreferencesViewModel by activityViewModels()

  // The fragment's ViewModel.
  private val viewModel: HomeViewModel by viewModels()

  // Codelab information dialog.
  private lateinit var codelabInfoAlertDialog: AlertDialog
  private lateinit var codelabInfoCheckboxBinding: FragmentCodelabInfoCheckboxBinding

  // New device information dialog
  private lateinit var newDeviceAlertDialog: AlertDialog
  private lateinit var newDeviceAlertDialogBinding: FragmentNewDeviceBinding

  // Error alert dialog.
  private lateinit var errorAlertDialog: AlertDialog

  // Tells whether a device attestation failure was ignored.
  // This is used in the "Device information" screen to warn the user about that fact.
  // We're doing it this way as we cannot ask permission to the user while the
  // decision has to be made because UI is fully controlled by GPS at that point.
  private var deviceAttestationFailureIgnored = false

  // The adapter used by the RecyclerView (where we show the list of devices).
  private val adapter =
      DevicesAdapter(
          { deviceUiModel ->
            // The click listener.
            // We update the selectedDeviceViewModel which is shared with the Device fragment.
            Timber.d("DevicesAdapter clickListener invoked")
            selectedDeviceViewModel.setSelectedDevice(deviceUiModel)
            view?.findNavController()?.navigate(R.id.action_homeFragment_to_deviceFragment)
          },
          { view, deviceUiModel ->
            Timber.d("onOff switch onClickListener: view [$view]")
            val onOffSwitch = view.findViewById<SwitchMaterial>(R.id.onoff_switch)
            Timber.d("onOff switch state: [${onOffSwitch?.isChecked}]")
            viewModel.updateDeviceStateOn(deviceUiModel, onOffSwitch?.isChecked!!)
          })

  // CODELAB: commissionDeviceLauncher declaration
  // The ActivityResultLauncher that launches the "commissionDevice" activity in Google Play
  // Services.
  private lateinit var commissionDeviceLauncher: ActivityResultLauncher<IntentSenderRequest>
  // CODELAB SECTION END

  // -----------------------------------------------------------------------------------------------
  // Lifecycle functions

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.d("onCreate bundle is: ${savedInstanceState.toString()}")

    // We need our own device attestation delegate as we currently only support attestation
    // of test Matter devices. This DeviceAttestationDelegate makes it possible to ignore device
    // attestation failures, which happen if commissioning production devices.
    // TODO: Look into supporting different Root CAs.
    setDeviceAttestationDelegate()

    // Commission Device Step 1, where An activity launcher is registered.
    // At step 2 of the "Commission Device" flow, the user triggers the "Commission Device"
    // action and the ViewModel calls the Google Play Services (GPS) API
    // (commissioningClient.commissionDevice()).
    // This returns an  IntentSender that is then used in step 3 to call
    // commissionDevicelauncher.launch().
    // CODELAB: commissionDeviceLauncher definition
    commissionDeviceLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
          // Commission Device Step 5.
          // The Commission Device activity in GPS (step 4) has completed.
          val resultCode = result.resultCode
          if (resultCode == Activity.RESULT_OK) {
            Timber.d("CommissionDevice: Success")
            // We now need to capture the device information for the app's fabric.
            // Once this completes, a call is made to the viewModel to persist the information
            // about that device in the app.
            showNewDeviceAlertDialog(result)
          } else {
            viewModel.commissionDeviceFailed(resultCode)
          }
        }
    // CODELAB SECTION END
  }

  private fun showNewDeviceAlertDialog(activityResult: ActivityResult?) {
    newDeviceAlertDialog.setCanceledOnTouchOutside(false)

    // Set on click listener for positive button of the dialog.
    newDeviceAlertDialog.setButton(
        DialogInterface.BUTTON_POSITIVE, resources.getString(R.string.ok)) { _, _ ->
          // Extract the info entered by user and process it.
          val nameTextView: TextInputEditText = newDeviceAlertDialogBinding.nameTextView
          val deviceName = nameTextView.text.toString()
          viewModel.commissionDeviceSucceeded(activityResult!!, deviceName)
        }

    if (deviceAttestationFailureIgnored) {
      newDeviceAlertDialog.setMessage(
          Html.fromHtml(getString(R.string.device_attestation_warning), FROM_HTML_MODE_LEGACY))
    }

    // Clear previous device name before showing the dialog
    newDeviceAlertDialogBinding.nameTextView.setText("")
    newDeviceAlertDialog.show()

    // Make the hyperlink clickable. Must be set after show().
    val msgTextView: TextView? = newDeviceAlertDialog.findViewById(android.R.id.message)
    msgTextView?.movementMethod = LinkMovementMethod.getInstance()
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    Timber.d("onCreateView()")

    // Setup the binding with the fragment.
    binding = DataBindingUtil.inflate(inflater, R.layout.fragment_home, container, false)

    // Binding to the CodelabInfoCheckbox UI, which is part of the dialog providing
    // information about the companion codelab. That checkbox is used to prevent displaying
    // that dialog on subsequent app launches.
    codelabInfoCheckboxBinding =
        DataBindingUtil.inflate(inflater, R.layout.fragment_codelab_info_checkbox, container, false)

    // Binding to the NewDevice UI, which is part of the dialog where we
    // capture new device information.
    newDeviceAlertDialogBinding =
        DataBindingUtil.inflate(inflater, R.layout.fragment_new_device, container, false)

    // Setup the UI elements and livedata observers.
    setupUiElements()
    setupObservers()

    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    Timber.d("onViewCreated()")
    Timber.d("[${arguments}]")
    // TODO: enable this if we want to show the snackbar. To discuss with UX.
    //    val snackbarMsg = arguments?.get("snackbarMsg").toString()
    //    Timber.d("snackbarMsg: $snackbarMsg")
    //    Snackbar.make(view, snackbarMsg, Snackbar.LENGTH_LONG).show()
  }

  override fun onResume() {
    super.onResume()
    val intent = requireActivity().intent
    Timber.d("onResume(): intent [${intent}]")
    if (isMultiAdminCommissioning(intent)) {
      Timber.d("Invocation: MultiAdminCommissioning")
      if (viewModel.commissionDeviceStatus.value == TaskStatus.NotStarted) {
        Timber.d("TaskStatus.NotStarted so starting commissioning")
        viewModel.multiadminCommissioning(intent, requireContext())
      } else {
        Timber.d("TaskStatus is *not* NotStarted: $viewModel.commissionDeviceStatus.value")
      }
    } else {
      Timber.d("Invocation: Main")
      viewModel.startMonitoringStateChanges()
    }
  }

  override fun onPause() {
    super.onPause()
    Timber.d("onPause(): Stopping periodic ping on devices")
    viewModel.stopMonitoringStateChanges()
  }

  override fun onStart() {
    super.onStart()
    Timber.d("onStart()")
  }

  override fun onDestroy() {
    super.onDestroy()
    Timber.d("onDestroy()")
    chipClient.chipDeviceController.setDeviceAttestationDelegate(0, EmptyAttestationDelegate())
    // Destroy alert dialogs
    errorAlertDialog.dismiss()
    newDeviceAlertDialog.dismiss()
    codelabInfoAlertDialog.dismiss()
  }

  // -----------------------------------------------------------------------------------------------
  // Setup UI elements of the fragment

  private fun setupUiElements() {
    setupMenu()
    setupAddDeviceButton()
    setupRecyclerView()
    setupNewDeviceDialog()
    setupCodelabInfoDialog()
    setupErrorAlertDialog()
  }

  private fun setupMenu() {
    binding.topAppBar.setOnMenuItemClickListener {
      when (it.itemId) {
        R.id.settings -> {
          // Navigate to settings screen
          view?.findNavController()?.navigate(R.id.action_homeFragment_to_settingsFragment)
          true
        }
        else -> {
          false
        }
      }
    }
  }

  private fun setupAddDeviceButton() {
    // Add device button click listener. This triggers the commissioning of a Matter device.
    binding.addDeviceButton.setOnClickListener {
      Timber.d("addDeviceButton.setOnClickListener")
      deviceAttestationFailureIgnored = false
      viewModel.stopMonitoringStateChanges()
      viewModel.commissionDevice(requireContext())
    }
  }

  private fun setupRecyclerView() {
    binding.devicesListRecyclerView.adapter = adapter
  }

  private fun setupNewDeviceDialog() {
    newDeviceAlertDialog =
        MaterialAlertDialogBuilder(requireContext())
            .setView(newDeviceAlertDialogBinding.root)
            .setTitle("New device information")
            .setCancelable(false)
            .create()
  }

  private fun setupCodelabInfoDialog() {
    codelabInfoAlertDialog =
        MaterialAlertDialogBuilder(requireContext())
            .setView(codelabInfoCheckboxBinding.root)
            .setTitle(resources.getString(R.string.codelab))
            .setMessage(
                Html.fromHtml(
                    resources.getString(R.string.showCodelabMessage), FROM_HTML_MODE_LEGACY))
            .setPositiveButton(resources.getString(R.string.ok)) { _, _ ->
              // Nothing to do.
            }
            .create()

    // Setup the listener when the checkbox is clicked to hide the dialog.
    codelabInfoCheckboxBinding.doNotShowMsgCheckbox.setOnCheckedChangeListener { _, checked ->
      // Checkbox is shown only when the checkbox is false (not checked), so the only possible
      // value is to have it checked by the user in the dialog.
      userPreferencesViewModel.updateHideCodelabInfo(checked)
    }
  }

  private fun setupErrorAlertDialog() {
    errorAlertDialog =
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(resources.getString(R.string.ok)) { _, _ ->
              viewModel.consumeErrorLiveData()
            }
            .create()
  }

  // -----------------------------------------------------------------------------------------------
  // Setup observers on the ViewModel

  private fun setupObservers() {
    // Observe the devicesLiveData.
    viewModel.devicesUiModelLiveData.observe(viewLifecycleOwner) { devicesUiModel: DevicesUiModel ->
      adapter.submitList(devicesUiModel.devices)
      updateUi(devicesUiModel)
    }

    // CODELAB: commissionDeviceStatus
    // The current status of the share device action.
    viewModel.commissionDeviceStatus.observe(viewLifecycleOwner) { status ->
      Timber.d("commissionDeviceStatus.observe: status [${status}]")
      // TODO: disable the "add device button", update the result text view, etc.
    }
    // CODELAB SECTION END

    // In the CommissionDevice flow step 2, the ViewModel calls the GPS commissionDevice() API to
    // get the
    // IntentSender to be used with the Android Activity Result API. Once the ViewModel has
    // the IntentSender, it posts it via LiveData so the Fragment can use that value to launch the
    // activity (step 3).
    // Note that when the IntentSender has been processed, it must be consumed to avoid a
    // configuration change that resends the observed values and re-triggers the commissioning.
    // CODELAB: commissionDeviceIntentSender
    viewModel.commissionDeviceIntentSender.observe(viewLifecycleOwner) { sender ->
      Timber.d(
          "commissionDeviceIntentSender.observe is called with [${intentSenderToString(sender)}]")
      if (sender != null) {
        // Commission Device Step 4: Launch the activity described in the IntentSender that
        // was returned in Step 3 (where the viewModel calls the GPS API to commission
        // the device).
        Timber.d("CommissionDevice: Launch GPS activity to commission device")
        commissionDeviceLauncher.launch(IntentSenderRequest.Builder(sender).build())
        viewModel.consumeCommissionDeviceIntentSender()
      }
    }
    // CODELAB SECTION END

    viewModel.errorLiveData.observe(viewLifecycleOwner) { errorInfo ->
      Timber.d("errorLiveData.observe is called with [${errorInfo}]")
      if (errorInfo != null) {
        showAlertDialog(errorAlertDialog, errorInfo.title, errorInfo.message)
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // UI Updates

  private fun updateUi(devicesUiModel: DevicesUiModel) {
    Timber.d("updateUi [${devicesUiModel}]")
    if (devicesUiModel.devices.isEmpty()) {
      binding.noDevicesLayout.visibility = View.VISIBLE
      binding.devicesListRecyclerView.visibility = View.GONE
    } else {
      binding.noDevicesLayout.visibility = View.GONE
      binding.devicesListRecyclerView.visibility = View.VISIBLE
    }

    // Codelab Info alert dialog
    if (devicesUiModel.showCodelabInfo && !viewModel.codelabDialogHasBeenShown) {
      viewModel.codelabDialogHasBeenShown = true
      showCodelabAlertDialog()
    }
  }

  // Show the Codelab AlertDialog.
  private fun showCodelabAlertDialog() {
    codelabInfoAlertDialog.show()
    // Make the hyperlink clickable. Must be set after show().
    val msgTextView: TextView? = codelabInfoAlertDialog.findViewById(android.R.id.message)
    msgTextView?.movementMethod = LinkMovementMethod.getInstance()
  }

  // ---------------------------------------------------------------------------
  // Device Attestation Delegate

  private class EmptyAttestationDelegate : DeviceAttestationDelegate {
    override fun onDeviceAttestationCompleted(
        devicePtr: Long,
        attestationInfo: AttestationInfo,
        errorCode: Int
    ) {}
  }

  private fun setDeviceAttestationDelegate() {
    chipClient.chipDeviceController.setDeviceAttestationDelegate(
        DEVICE_ATTESTATION_FAILED_TIMEOUT_SECONDS) { devicePtr, attestationInfo, errorCode ->
          Timber.d(
              "Device attestation errorCode: $errorCode, " +
                  "Look at 'src/credentials/attestation_verifier/DeviceAttestationVerifier.h' " +
                  "AttestationVerificationResult enum to understand the errors")

          if (errorCode == STATUS_PAIRING_SUCCESS) {
            Timber.d("DeviceAttestationDelegate: Success on device attestation.")
            lifecycleScope.launch {
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
            deviceAttestationFailureIgnored = true
            Timber.w("Ignoring attestation failure.")
            lifecycleScope.launch {
              chipClient.chipDeviceController.continueCommissioning(devicePtr, true)
            }
          }
        }
  }

  // ---------------------------------------------------------------------------
  // Companion object

  companion object {
    private const val STATUS_PAIRING_SUCCESS = 0

    /** Set for the fail-safe timer before onDeviceAttestationFailed is invoked. */
    private const val DEVICE_ATTESTATION_FAILED_TIMEOUT_SECONDS = 60
  }
}
