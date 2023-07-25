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

package com.google.homesampleapp.screens.inspect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.homesampleapp.R
import com.google.homesampleapp.chip.DeviceMatterInfo
import com.google.homesampleapp.chip.MatterConstants
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.databinding.FragmentInspectBinding
import com.google.homesampleapp.lifeCycleEvent
import com.google.homesampleapp.screens.shared.SelectedDeviceViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

/**
 * The Inspect Fragment shows all the "cluster" information about the device that was selected in
 * the Home screen.
 */
@AndroidEntryPoint
class InspectFragment : Fragment() {

  @Inject internal lateinit var devicesStateRepository: DevicesStateRepository

  // Fragment binding.
  private lateinit var binding: FragmentInspectBinding

  // The ViewModel for the currently selected device.
  private val selectedDeviceViewModel: SelectedDeviceViewModel by activityViewModels()

  // The fragment's ViewModel.
  private val viewModel: InspectViewModel by viewModels()

  // -----------------------------------------------------------------------------------------------
  // Lifecycle functions

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    super.onCreateView(inflater, container, savedInstanceState)
    Timber.d(lifeCycleEvent("onCreateView()"))

    // Setup the binding with the fragment.
    binding = DataBindingUtil.inflate(inflater, R.layout.fragment_inspect, container, false)

    // Setup UI elements and livedata observers.
    setupUiElements()
    setupObservers()

    return binding.root
  }

  // -----------------------------------------------------------------------------------------------
  // Setup UI elements

  private fun setupUiElements() {
    // Navigate back
    binding.topAppBar.setOnClickListener { findNavController().popBackStack() }
  }

  // -----------------------------------------------------------------------------------------------
  // Setup Observers

  private fun setupObservers() {
    // Observer on the currently selected device
    selectedDeviceViewModel.selectedDeviceIdLiveData.observe(viewLifecycleOwner) { deviceId ->
      Timber.d(
          "selectedDeviceViewModel.selectedDeviceIdLiveData.observe is called with deviceId [${deviceId}]")
      updateDeviceInfo()
    }

    // Observer on introspection information
    viewModel.instrospectionInfo.observe(viewLifecycleOwner) { updateIntrospectionInfo(it) }
  }

  // -----------------------------------------------------------------------------------------------
  // UI update functions

  private fun updateDeviceInfo() {
    Timber.d("updateDeviceIfo")
    if (selectedDeviceViewModel.selectedDeviceIdLiveData.value == -1L) {
      // Device was just removed, nothing to do. We'll move to HomeFragment.
      return
    }
    val deviceUiModel = selectedDeviceViewModel.selectedDeviceLiveData.value
    Timber.d(
        "updateDeviceIfo with device [${deviceUiModel?.device?.deviceId}] [${deviceUiModel?.device?.name}]")

    binding.topAppBar.title = deviceUiModel?.device?.name
    // Fetch device introspection info, and then live data will be updated, which is observed
    // by the fragment.
    viewModel.inspectDevice(deviceUiModel?.device?.deviceId!!)
  }

  private fun updateIntrospectionInfo(deviceMatterInfoList: List<DeviceMatterInfo>) {
    val linearLayout = binding.inspectInfoLayout
    linearLayout.removeAllViews()
    if (deviceMatterInfoList.isEmpty()) {
      addTextView(
          "Oops... We could not retrieve any information from the Descriptor Cluster. " +
              "This is probably because the device just recently turned \"offline\".",
          com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
      return
    }
    // Add the Descriptor Cluster Title
    addTextView(
        "Descriptor Cluster",
        com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
    // For each endpoint
    for (deviceMatterInfo in deviceMatterInfoList) {
      // Endpoint ID
      addTextView(
          "Endpoint ${deviceMatterInfo.endpoint}",
          com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
      // Device Types
      addTextView(
          "Device Types", com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
      for (deviceType in deviceMatterInfo.types) {
        val hex = String.format("0x%04X", deviceType)
        val typeString = MatterConstants.DeviceTypesMap.getOrDefault(deviceType, "Unknown")
        addTextView(
            "[${hex}] ${typeString}",
            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
      }
      // Server Clusters
      addTextView(
          "Server Clusters",
          com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
      if (deviceMatterInfo.serverClusters.isEmpty()) {
        addTextView("None", com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
      } else {
        for (serverCluster in deviceMatterInfo.serverClusters) {
          val hex = String.format("0x%04X", serverCluster)
          val serverClusterString =
              MatterConstants.ClustersMap.getOrDefault(serverCluster, "Unknown")
          addTextView(
              "[${hex}] ${serverClusterString}",
              com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        }
      }
      // Client Clusters
      addTextView(
          "Client Clusters",
          com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
      if (deviceMatterInfo.clientClusters.isEmpty()) {
        addTextView("None", com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
      } else {
        for (clientCluster in deviceMatterInfo.clientClusters) {
          val hex = String.format("0x%04X", clientCluster)
          val clientClusterString =
              MatterConstants.ClustersMap.getOrDefault(clientCluster, "Unknown")
          addTextView(
              "[${hex}] ${clientClusterString}",
              com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        }
      }
    }
  }

  private fun addTextView(text: String, style: Int) {
    val textView = TextView(requireContext())
    textView.text = text
    textView.setTextAppearance(style)
    binding.inspectInfoLayout.addView(textView)
  }
}
