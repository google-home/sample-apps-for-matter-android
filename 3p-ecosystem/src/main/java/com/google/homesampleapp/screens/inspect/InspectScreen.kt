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

package com.google.homesampleapp.screens.inspect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.google.homesampleapp.Device
import com.google.homesampleapp.R
import com.google.homesampleapp.chip.DeviceMatterInfo
import com.google.homesampleapp.chip.MatterConstants
import com.google.homesampleapp.screens.common.DialogInfo
import com.google.homesampleapp.screens.common.MsgAlertDialog
import com.google.protobuf.Timestamp
import timber.log.Timber

/**
 * The Inspect Screen shows all the "cluster" information about the currently selected device in the
 * Device screen.
 */
@Composable
fun InspectRoute(
  innerPadding: PaddingValues,
  updateTitle: (title: String) -> Unit,
  deviceId: Long,
  inspectViewModel: InspectViewModel = hiltViewModel(),
) {
  Timber.d("InspectRoute deviceId [$deviceId]")

  // Controls the Msg AlertDialog.
  // When the user dismisses the Msg AlertDialog, we "consume" the dialog.
  val msgDialogInfo by inspectViewModel.msgDialogInfo.collectAsState()
  val onDismissMsgDialog: () -> Unit = remember {
    { inspectViewModel.dismissMsgDialog() }
  }

  // Observes values needed by the InspectScreen.
  val deviceMatterInfoList by inspectViewModel.deviceMatterInfoList.collectAsState()

  LifecycleResumeEffect {
    Timber.d("LifecycleResumeEffect: selectedDeviceId [$deviceId]")
    inspectViewModel.inspectDevice(deviceId)
    onPauseOrDispose {
      // do any needed clean up here
      Timber.d("LifecycleResumeEffect:onPauseOrDispose")
    }
  }

  LaunchedEffect(Unit) {
    updateTitle("Inspect")
  }

  InspectScreen(innerPadding, deviceMatterInfoList, msgDialogInfo, onDismissMsgDialog)
}

@Composable
private fun InspectScreen(
  innerPadding: PaddingValues,
  deviceMatterInfoList: List<DeviceMatterInfo>?,
  msgDialogInfo: DialogInfo?,
  onDismissMsgDialog: () -> Unit,
) {
  // The various AlertDialog's that may pop up to inform the user of important information.
  MsgAlertDialog(msgDialogInfo, onDismissMsgDialog)

  Surface(modifier = Modifier.padding(innerPadding)) {
    Column(modifier = Modifier.fillMaxWidth().padding(dimensionResource(R.dimen.margin_normal))) {
      if (deviceMatterInfoList == null) {
        Text(
          text =
            "Fetching device information...\n" +
              "Note that this may take a while if the device is offline.",
          style = MaterialTheme.typography.bodyMedium,
        )
      } else {
        if (deviceMatterInfoList.isEmpty()) {
          Text(
            text =
              "Oops... We could not retrieve any information from the Descriptor Cluster. " +
                "This is probably because the device just recently turned \"offline\".",
            style = MaterialTheme.typography.bodyMedium,
          )
        } else {
          // Add the Descriptor Cluster Title
          Text(text = "Descriptor Cluster", style = MaterialTheme.typography.titleLarge)
          // For each endpoint
          for (deviceMatterInfo in deviceMatterInfoList) {
            // Endpoint ID
            Text(
              text = "<<< Endpoint ${deviceMatterInfo.endpoint} >>>",
              style = MaterialTheme.typography.titleMedium,
            )
            // Device Types
            Text(text = "Device Types", style = MaterialTheme.typography.titleSmall)
            for (deviceType in deviceMatterInfo.types) {
              val hex = String.format("0x%04X", deviceType)
              val typeString = MatterConstants.DeviceTypesMap.getOrDefault(deviceType, "Unknown")
              Text(text = "[${hex}] $typeString", style = MaterialTheme.typography.bodySmall)
            }
            // Server Clusters
            Text(text = "Server Clusters", style = MaterialTheme.typography.titleSmall)
            if (deviceMatterInfo.serverClusters.isEmpty()) {
              Text(text = "None", style = MaterialTheme.typography.bodySmall)
            } else {
              for (serverCluster in deviceMatterInfo.serverClusters) {
                val hex = String.format("0x%04X", serverCluster)
                val serverClusterString =
                  MatterConstants.ClustersMap.getOrDefault(serverCluster, "Unknown")
                Text(
                  text = "[${hex}] $serverClusterString",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
            // Client Clusters
            Text(text = "Client Clusters", style = MaterialTheme.typography.titleSmall)
            if (deviceMatterInfo.clientClusters.isEmpty()) {
              Text(text = "None", style = MaterialTheme.typography.bodySmall)
            } else {
              for (clientCluster in deviceMatterInfo.clientClusters) {
                val hex = String.format("0x%04X", clientCluster)
                val clientClusterString =
                  MatterConstants.ClustersMap.getOrDefault(clientCluster, "Unknown")
                Text(
                  text = "[${hex}] $clientClusterString",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }
      }
    }
  }
}

// -----------------------------------------------------------------------------------------------
// Composable Previews

@Preview(widthDp = 300)
@Composable
private fun InspectScreenLoadingPreview() {
  MaterialTheme { InspectScreen(PaddingValues(), null, null, {}) }
}

@Preview(widthDp = 300)
@Composable
private fun InspectScreenOfflinePreview() {
  MaterialTheme { InspectScreen(PaddingValues(), emptyList(), null, {}) }
}

@Preview(widthDp = 300)
@Composable
private fun InspectScreenOnlineNoClustersPreview() {
  MaterialTheme {
    InspectScreen(
      PaddingValues(),
      listOf(DeviceMatterInfo(1, listOf(15L, 22L), emptyList(), emptyList())),
      null,
      {},
    )
  }
}

@Preview(widthDp = 300)
@Composable
private fun InspectScreenOnlineWithClustersPreview() {
  MaterialTheme {
    InspectScreen(
      PaddingValues(),
      listOf(
        DeviceMatterInfo(0, listOf(15L, 22L), listOf(3L), listOf(43L, 48L)),
        DeviceMatterInfo(1, listOf(15L, 22L), listOf(3L, 4L, 5L), listOf(43L, 44L, 45L, 48L)),
      ),
      null,
      {},
    )
  }
}

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
