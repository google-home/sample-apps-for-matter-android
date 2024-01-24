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
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.tooling.preview.Preview
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.fragment.findNavController
import com.google.homesampleapp.Device
import com.google.homesampleapp.R
import com.google.homesampleapp.chip.DeviceMatterInfo
import com.google.homesampleapp.chip.MatterConstants
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.databinding.FragmentInspectBinding
import com.google.homesampleapp.lifeCycleEvent
import com.google.homesampleapp.screens.shared.SelectedDeviceViewModel
import com.google.protobuf.Timestamp
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

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
    binding = DataBindingUtil.inflate<FragmentInspectBinding>(
      inflater,
      R.layout.fragment_inspect,
      container,
      false
    ).apply {
      composeView.apply {
        // Dispose the Composition when the view's LifecycleOwner is destroyed
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
          MaterialTheme {
            InspectRoute()
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
    Timber.d("onResume")
    super.onResume()
  }

  // -----------------------------------------------------------------------------------------------
  // Setup UI elements

  private fun setupUiElements() {
    // Navigate back
    binding.topAppBar.setOnClickListener { findNavController().popBackStack() }
  }

  private fun setupObservers() {
    selectedDeviceViewModel.selectedDeviceLiveData.observe(viewLifecycleOwner) {
      binding.topAppBar.title = it?.device?.name
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Composables

  @Composable
  private fun InspectRoute() {
    // Observes values needed by the InspectScreen.
    val selectedDeviceId by selectedDeviceViewModel.selectedDeviceIdLiveData.observeAsState()
    val instrospectionInfo by viewModel.instrospectionInfo.observeAsState()

    LifecycleResumeEffect {
      Timber.d("LifecycleResumeEffect: selectedDeviceId [$selectedDeviceId]")
      viewModel.inspectDevice(selectedDeviceViewModel.selectedDeviceLiveData.value!!.device.deviceId)
      onPauseOrDispose {
        // do any needed clean up here
        Timber.d("LifecycleResumeEffect:onPauseOrDispose")
      }
    }

    InspectScreen(selectedDeviceId, instrospectionInfo)
  }

  @Composable
  private fun InspectScreen(
    selectedDeviceId: Long?,
    deviceMatterInfoList: List<DeviceMatterInfo>?
  ) {
    if (selectedDeviceId == -1L) {
      // Device was just removed, nothing to do. We'll move to HomeFragment.
      return
    }
    Column (
    // FIXME: Triggers  java.lang.IllegalStateException: Vertically scrollable component
    // was measured with an infinity maximum height constraints, which is disallowed.
    // modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
      if (deviceMatterInfoList == null) {
        // The viewModel.inspectDevice() call has not yet completed.
        return
      }
      if (deviceMatterInfoList.isEmpty()) {
        Text(
          text = "Oops... We could not retrieve any information from the Descriptor Cluster. " +
              "This is probably because the device just recently turned \"offline\".",
          style = MaterialTheme.typography.bodyMedium)
        return
      }
      // Add the Descriptor Cluster Title
      Text(
        text = "Descriptor Cluster",
        style = MaterialTheme.typography.titleLarge)
      // For each endpoint
      for (deviceMatterInfo in deviceMatterInfoList) {
        // Endpoint ID
        Text(
          text = "<<< Endpoint ${deviceMatterInfo.endpoint} >>>",
          style = MaterialTheme.typography.titleMedium)
        // Device Types
        Text(text = "Device Types", style = MaterialTheme.typography.titleSmall)
        for (deviceType in deviceMatterInfo.types) {
          val hex = String.format("0x%04X", deviceType)
          val typeString = MatterConstants.DeviceTypesMap.getOrDefault(deviceType, "Unknown")
          Text(
            text = "[${hex}] $typeString",
            style = MaterialTheme.typography.bodySmall)
        }
        // Server Clusters
        Text(
          text = "Server Clusters",
          style = MaterialTheme.typography.titleSmall)
        if (deviceMatterInfo.serverClusters.isEmpty()) {
          Text(text = "None", style = MaterialTheme.typography.bodySmall)
        } else {
          for (serverCluster in deviceMatterInfo.serverClusters) {
            val hex = String.format("0x%04X", serverCluster)
            val serverClusterString =
              MatterConstants.ClustersMap.getOrDefault(serverCluster, "Unknown")
            Text(
              text = "[${hex}] $serverClusterString",
              style = MaterialTheme.typography.bodySmall)
          }
        }
        // Client Clusters
        Text(
          text = "Client Clusters",
          style = MaterialTheme.typography.titleSmall)
        if (deviceMatterInfo.clientClusters.isEmpty()) {
          Text(text = "None", style = MaterialTheme.typography.bodySmall)
        } else {
          for (clientCluster in deviceMatterInfo.clientClusters) {
            val hex = String.format("0x%04X", clientCluster)
            val clientClusterString =
              MatterConstants.ClustersMap.getOrDefault(clientCluster, "Unknown")
            Text(
              text = "[${hex}] $clientClusterString",
              style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Composable Previews

  @Preview(widthDp = 300)
  @Composable
  private fun InspectScreenOfflinePreview() {
    MaterialTheme {
      InspectScreen(1L, emptyList())
    }
  }

  @Preview(widthDp = 300)
  @Composable
  private fun InspectScreenOnlineNoClustersPreview() {
    MaterialTheme {
      InspectScreen(1L,
        listOf(
          DeviceMatterInfo(1, listOf(15L, 22L), emptyList(), emptyList())
        ))
    }
  }

  @Preview(widthDp = 300)
  @Composable
  private fun InspectScreenOnlineWithClustersPreview() {
    MaterialTheme {
      InspectScreen(1L,
        listOf(
          DeviceMatterInfo(0, listOf(15L, 22L),
            listOf(3L),
            listOf(43L, 48L)),
          DeviceMatterInfo(1, listOf(15L, 22L),
            listOf(3L, 4L, 5L),
            listOf(43L, 44L, 45L, 48L))
        ))
    }
  }

  private val DeviceTest = Device.newBuilder()
    .setDeviceId(1L)
    .setDeviceType(Device.DeviceType.TYPE_OUTLET)
    .setDateCommissioned(Timestamp.getDefaultInstance())
    .setName("MyOutlet")
    .setProductId("8785")
    .setVendorId("6006")
    .setRoom("Office")
    .build()
}
