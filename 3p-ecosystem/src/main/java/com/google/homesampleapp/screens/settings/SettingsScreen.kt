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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.google.homesampleapp.R
import com.google.homesampleapp.VERSION_NAME
import com.google.homesampleapp.screens.common.HtmlInfoDialog
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.rememberPreferenceState
import me.zhanghai.compose.preference.switchPreference

@Composable
internal fun SettingsRoute(
  innerPadding: PaddingValues,
  updateTitle: (title: String) -> Unit,
  navigateToDeveloperUtilities: () -> Unit,
) {
  LaunchedEffect(Unit) {
    updateTitle("Settings")
  }

  SettingsScreen(innerPadding, navigateToDeveloperUtilities)
}

@Composable
private fun SettingsScreen(innerPadding: PaddingValues, navigateToDeveloperUtilities: () -> Unit) {
  var showHelpAndFeedbackDialog by remember { mutableStateOf(false) }
  var showAboutDialog by remember { mutableStateOf(false) }
  var showHalfsheetDialog by remember { mutableStateOf(false) }
  // Cannot use extension function for Halfsheet Preference, onValueChange needed.
  val showHalfsheetPref = rememberPreferenceState("halfsheet_preference", false)

  LazyColumn(modifier = Modifier.fillMaxSize()) {
    switchPreference(
      key = "codelab_preference",
      defaultValue = false,
      icon = {
        Icon(
          painter = painterResource(id = R.drawable.ic_baseline_code_24),
          contentDescription = null, // decorative element
        )
      },
      title = { Text(text = "Codelab") },
      summary = {
        Text(
          text = if (it) "Show codelab info at startup" else "Do not show codelab info at startup"
        )
      },
    )
    switchPreference(
      key = "offline_devices_preference",
      defaultValue = true,
      icon = {
        Icon(
          painter = painterResource(id = R.drawable.ic_baseline_signal_wifi_off_24),
          contentDescription = null, // decorative element
        )
      },
      title = { Text(text = "Offline devices") },
      summary = { Text(text = if (it) "Show offline devices" else "Do not show offline devices") },
    )
    item {
      // Need to use this form as we must have access to onValueChange.
      var value by showHalfsheetPref
      SwitchPreference(
        value = value,
        icon = {
          Icon(
            painter = painterResource(id = R.drawable.baseline_notifications_24),
            contentDescription = null, // decorative element
          )
        },
        title = { Text(text = "Halfsheet notification") },
        summary = {
          Text(
            text =
              if (showHalfsheetPref.value)
                "Show proactive commissionable discovery notifications for Matter devices"
              else "Do not show proactive commissionable discovery notifications for Matter devices"
          )
        },
        onValueChange = {
          value = it
          showHalfsheetDialog = true
        },
      )
    }
    preference(
      key = "developer_utilities_preference",
      icon = {
        Icon(
          painter = painterResource(id = R.drawable.ic_baseline_developer_mode_24),
          contentDescription = null, // decorative element
        )
      },
      title = { Text(text = "Developer utilities") },
      summary = { Text(text = "Various utility functions for developers who want to dig deeper!") },
      onClick = navigateToDeveloperUtilities,
    )
    preference(
      key = "help_feedback_preference",
      icon = {
        Icon(
          painter = painterResource(id = R.drawable.ic_baseline_help_24),
          contentDescription = null, // decorative element
        )
      },
      title = { Text(text = "Help and Feedback") },
      summary = { Text(text = "Learn how to use this sample app and/or give us feedback") },
      onClick = { showHelpAndFeedbackDialog = true },
    )
    preference(
      key = "about_preference",
      icon = {
        Icon(
          painter = painterResource(id = R.drawable.ic_baseline_help_24),
          contentDescription = null, // decorative element
        )
      },
      title = { Text(text = "About this app") },
      summary = { Text(text = "More information about this application") },
      onClick = { showAboutDialog = true },
    )
  }
  if (showHelpAndFeedbackDialog) {
    HtmlInfoDialog(
      "Help and Feedback",
      stringResource(R.string.help_and_feedback),
      onClick = { showHelpAndFeedbackDialog = false },
    )
  }
  if (showAboutDialog) {
    HtmlInfoDialog(
      "About this app",
      stringResource(R.string.about_app, VERSION_NAME),
      onClick = { showAboutDialog = false },
    )
  }
  if (showHalfsheetDialog) {
    HtmlInfoDialog(
      "Halfsheet Notification",
      stringResource(R.string.halfsheet_notification_alert),
      onClick = { showHalfsheetDialog = false },
    )
  }
}
