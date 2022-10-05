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
import android.os.Bundle
import android.text.Html
import android.text.Html.FROM_HTML_MODE_LEGACY
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.homesampleapp.PERIODIC_UPDATE_INTERVAL_HOME_SCREEN_SECONDS
import com.google.homesampleapp.R
import com.google.homesampleapp.TaskStatus
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.data.UserPreferencesRepository
import com.google.homesampleapp.databinding.FragmentCodelabInfoCheckboxBinding
import com.google.homesampleapp.databinding.FragmentHomeBinding
import com.google.homesampleapp.isMultiAdminCommissioning
import com.google.homesampleapp.screens.shared.SelectedDeviceViewModel
import com.google.homesampleapp.screens.shared.UserPreferencesViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

/**
 * Home screen for the application.
 *
 * The fragment features five sections:
 * 1. The list of devices currently commissioned into the app's fabric. When the user clicks on a
 * device, control moves to the Device fragment where one can get additional details on the device
 * and perform actions on it. Implemented via a RecyclerView. Devices are persisted in the
 * DevicesRepository, a Proto Datastore. It's possible to hide the devices that are currently
 * offline via a setting in the Settings screen.
 * 2. Top App Bar. Settings icon to navigate to the Settings screen.
 * 3. "Add Device" button. Triggers the commissioning of a new device.
 * 4. Codelab information. When the fragment view is created, a Dialog is shown to provide
 * information about the app's companion codelab. This can be dismissed via a checkbox and the
 * setting is persisted in the UserPreferences proto datastore.
 *
 * Note:
 * - The app currently only supports Matter devices with server attribute "ON/OFF". An icon
 * representing the on/off device only exists for device type light. Any other device type is shown
 * with a generic matter device icon.
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

  @Inject internal lateinit var devicesRepository: DevicesRepository
  @Inject internal lateinit var devicesStateRepository: DevicesStateRepository
  @Inject internal lateinit var userPreferencesRepository: UserPreferencesRepository

  // Fragment binding.
  private lateinit var binding: FragmentHomeBinding

  private val selectedDeviceViewModel: SelectedDeviceViewModel by activityViewModels()

  // The shared ViewModel for the UserPreferences.
  private val userPreferencesViewModel: UserPreferencesViewModel by activityViewModels()

  // The fragment's ViewModel.
  private val viewModel: HomeViewModel by viewModels()

  // Show codelab info Checkbox.
  private lateinit var codelabInfoCheckboxBinding: FragmentCodelabInfoCheckboxBinding

  // Codelab information dialog.
  private lateinit var codelabInfoAlertDialog: AlertDialog

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

  // The ActivityResult launcher that launches the "commissionDevice" activity in Google Play
  // Services.
  // CODELAB: commissionDeviceLauncher declaration
  private lateinit var commissionDeviceLauncher: ActivityResultLauncher<IntentSenderRequest>
  // CODELAB SECTION END

  // -----------------------------------------------------------------------------------------------
  // Lifecycle functions

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.d("onCreate bundle is: ${savedInstanceState.toString()}")

    // Commission Device Step 1.
    // An activity launcher is registered. It will be launched
    // at step 2 (in the viewModel) when the user triggers the "Add Device" action and the
    // Google Play Services (GPS) API (commissioningClient.commissionDevice()) returns the
    // IntentSender to be used to launch the proper activity in GPS.
    // CODELAB: commissionDeviceLauncher definition
    commissionDeviceLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
          // Commission Device Step 5.
          // The Commission Device activity in GPS has completed.
          val resultCode = result.resultCode
          Timber.d("GOT result for commissioningLauncher: resultCode [${resultCode}]")
          if (resultCode == Activity.RESULT_OK) {
            viewModel.commissionDeviceSucceeded(
                result, getString(R.string.commission_device_status_success))
          } else {
            viewModel.commissionDeviceFailed(getString(R.string.status_failed_with, resultCode))
          }
        }
    // CODELAB SECTION END
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
    Timber.d("onResume()")

    val intent = requireActivity().intent
    if (isMultiAdminCommissioning(intent)) {
      Timber.d("*** MultiAdminCommissioning ***")
      if (viewModel.commissionDeviceStatus.value == TaskStatus.NotStarted) {
        Timber.d("TaskStatus.NotStarted so starting commissioning")
        viewModel.commissionDevice(intent, requireContext())
      } else {
        Timber.d("TaskStatus is not NotStarted: $viewModel.commissionDeviceStatus.value")
      }
    } else {
      Timber.d("*** Main ***")
      if (PERIODIC_UPDATE_INTERVAL_HOME_SCREEN_SECONDS != -1) {
        Timber.d("Starting periodic ping on devices")
        viewModel.startDevicesPeriodicPing()
      }
    }
  }

  override fun onPause() {
    super.onPause()
    Timber.d("onPause(): Stopping periodic ping on devices")
    viewModel.stopDevicesPeriodicPing()
  }

  // -----------------------------------------------------------------------------------------------
  // Setup UI elements of the fragment

  private fun setupUiElements() {
    setupMenu()
    setupAddDeviceButton()
    setupRecyclerView()
    setupCodelabInfoDialog()
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
      viewModel.stopDevicesPeriodicPing()
      viewModel.commissionDevice(requireActivity().intent, requireContext())
    }
  }

  private fun setupRecyclerView() {
    binding.devicesListRecyclerView.adapter = adapter
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

  // -----------------------------------------------------------------------------------------------
  // Setup observers on the ViewModel

  private fun setupObservers() {
    // Observe the devicesLiveData.
    viewModel.devicesUiModelLiveData.observe(viewLifecycleOwner) { devicesUiModel: DevicesUiModel ->
      adapter.submitList(devicesUiModel.devices)
      updateUi(devicesUiModel)
    }

    // The current status of the share device action.
    // CODELAB: commissionDeviceStatus
    viewModel.commissionDeviceStatus.observe(viewLifecycleOwner) { status ->
      Timber.d("commissionDeviceStatus.observe: status [${status}]")
      // TODO: disable the "add device button", update the result text view, etc.
    }
    // CODELAB SECTION END

    // Commission Device Step 2.
    // The fragment observes the livedata for commissionDeviceIntentSender which
    // is updated in the ViewModel in step 3 of the Commission Device flow.
    // CODELAB: commissionDeviceIntentSender
    viewModel.commissionDeviceIntentSender.observe(viewLifecycleOwner) { sender ->
      Timber.d("commissionDeviceIntentSender.observe is called with sender [${sender}]")
      if (sender != null) {
        // Commission Device Step 4: Launch the activity described in the IntentSender that
        // was returned in Step 3 where the viewModel calls the GPS API to commission
        // the device.
        Timber.d("*** Calling commissionDeviceLauncher.launch")
        commissionDeviceLauncher.launch(IntentSenderRequest.Builder(sender).build())
      }
    }
    // CODELAB SECTION END
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
}
