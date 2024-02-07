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

package com.google.homesampleapp.screens.settings

import android.bluetooth.BluetoothAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.homesampleapp.R
import com.google.homesampleapp.screens.common.DialogInfo
import com.google.homesampleapp.screens.common.HtmlInfoDialog
import com.google.homesampleapp.screens.common.MsgAlertDialog
import com.google.homesampleapp.screens.thread.getActivity
import me.zhanghai.compose.preference.preference
import timber.log.Timber

/** Shows Developer Utilities . */
@Composable
internal fun SettingsDeveloperUtilitiesRoute(
  innerPadding: PaddingValues,
  updateTitle: (title: String) -> Unit,
  navigateToCommissionables: () -> Unit,
  navigateToThread: () -> Unit,
  developerUtilitiesViewModel: DeveloperUtilitiesViewModel = hiltViewModel(),
) {

  // Permissions require Activity.
  val activity = LocalContext.current.getActivity()

  // Controls the Msg AlertDialog.
  // When the user dismisses the Msg AlertDialog, we "consume" the dialog.
  val msgDialogInfo by developerUtilitiesViewModel.msgDialogInfo.collectAsState()
  val onDismissMsgDialog: () -> Unit = remember {
    { developerUtilitiesViewModel.dismissMsgDialog() }
  }

  // Log Repos
  val showLogReposDialog by developerUtilitiesViewModel.showLogReposDialog.collectAsState()
  val onShowLogReposDialog: () -> Unit = remember {
    { developerUtilitiesViewModel.printRepositories() }
  }
  val onDismissLogReposDialog: () -> Unit = remember {
    {
      developerUtilitiesViewModel.dismissLogRepositoriesDialog()
    }
  }

  // Showing the commissionable devices requires scanning permissions.
  // This defines a launcher for the activity that requests the user to
  // allow the required permissions.
  val scanningPermissionsLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
      // Process the "RequestMultiplePermissions" activity results
      if (results.values.any { granted -> !granted }) {
        // At least one permission is not granted.
        Timber.d("scanningPermissionsLauncher: All scanning permissions needed were not granted.")
        developerUtilitiesViewModel.showMsgDialog(
          "Scanning Permissions",
          "Scanning permissions were not granted, so unfortunately " +
            "the \"Commissionable Devices\" feature is not available.",
        )
      } else {
        Timber.d("scanningPermissionsLauncher: Permissions were OK!")
        navigateToCommissionables()
      }
    }

  val onCommissionableDevicesClick: () -> Unit = remember {
    {
      developerUtilitiesViewModel.logScanningPermissions(activity!!.applicationContext)
      if (!developerUtilitiesViewModel.allScanningPermissionsGranted(activity.applicationContext)) {
        Timber.d("All scanning permissions NOT granted. Asking for them.")
        scanningPermissionsLauncher.launch(
          developerUtilitiesViewModel.getRequiredScanningPermissions()
        )
      } else {
        // Check if Bluetooth is enabled.
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter.isEnabled) {
          navigateToCommissionables()
        } else {
          Timber.d("Bluetooth is not enabled") // FIXME
          developerUtilitiesViewModel.showMsgDialog(
            "Bluetooth is not enabled",
            "Bluetooth must be enabled on your phone to allow discovery of matter devices",
          )
        }
      }
    }
  }

  val onThreadClick: () -> Unit = remember {
    { navigateToThread() }
  }

  LaunchedEffect(Unit) {
    updateTitle("Developer Utilities")
  }

  SettingsDeveloperUtilitiesScreen(
    innerPadding,
    msgDialogInfo,
    onDismissMsgDialog,
    showLogReposDialog,
    onShowLogReposDialog,
    onDismissLogReposDialog,
    onCommissionableDevicesClick,
    onThreadClick,
  )
}

@Composable
private fun SettingsDeveloperUtilitiesScreen(
  innerPadding: PaddingValues,
  msgDialogInfo: DialogInfo?,
  onDismissMsgDialog: () -> Unit,
  showLogReposDialog: Boolean,
  onShowLogReposDialog: () -> Unit,
  onDismissLogReposDialog: () -> Unit,
  onCommissionableDevicesClick: () -> Unit,
  onThreadClick: () -> Unit,
) {
  // Alert Dialog for messages to be shown to the user.
  MsgAlertDialog(msgDialogInfo, onDismissMsgDialog)

  LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    preference(
      key = "commissionable_devices_preference",
      icon = {
        Icon(
          painter = painterResource(id = R.drawable.ic_baseline_search_24),
          contentDescription = null, // decorative element
        )
      },
      title = { Text(text = "Commissionable devices") },
      summary = { Text(text = "Discover commissionable Matter devices") },
      onClick = onCommissionableDevicesClick,
    )
    preference(
      key = "thread_preference",
      icon = {
        Icon(
          painter = painterResource(id = R.drawable.baseline_device_hub_24),
          contentDescription = null, // decorative element
        )
      },
      title = { Text(text = "Thread network") },
      summary = { Text(text = "Information about the thread network") },
      onClick = onThreadClick,
    )
    preference(
      key = "logrepos_preference",
      icon = {
        Icon(
          painter = painterResource(id = R.drawable.ic_outline_storage_24),
          contentDescription = null, // decorative element
        )
      },
      title = { Text(text = "Log repositories content") },
      summary = { Text(text = "View in the logs the content of repositories used in the app") },
      onClick = onShowLogReposDialog,
    )
  }
  if (showLogReposDialog) {
    HtmlInfoDialog(
      "Repositories logged",
      stringResource(R.string.log_repos_info),
      onClick = onDismissLogReposDialog,
    )
  }
}
