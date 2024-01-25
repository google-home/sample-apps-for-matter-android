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

package com.google.homesampleapp.screens.settings

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.textview.MaterialTextView
import com.google.homesampleapp.R
import com.google.homesampleapp.VERSION_NAME
import com.google.homesampleapp.databinding.FragmentSettingsBinding
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.rememberPreferenceState
import me.zhanghai.compose.preference.switchPreference
import timber.log.Timber

/** Shows settings and user preferences. */
class SettingsFragment : Fragment() {

  private lateinit var binding: FragmentSettingsBinding

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {

    binding = DataBindingUtil.inflate<FragmentSettingsBinding>(
      inflater,
      R.layout.fragment_settings,
      container,
      false
    ).apply {
      composeView.apply {
        // Dispose the Composition when the view's LifecycleOwner is destroyed
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
          MaterialTheme {
            ProvidePreferenceLocals {
              SettingsScreen()
            }
          }
        }
      }
    }
    setupUiElements()
    return binding.root
  }

  private fun setupUiElements() {
    binding.topAppBar.setNavigationOnClickListener {
      // navigate back.
      Timber.d("topAppBar.setNavigationOnClickListener")
      findNavController().popBackStack()
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Composables

  @Composable
  private fun SettingsScreen(
  ) {
    val showHelpAndFeedbackDialog = remember { mutableStateOf(false) }
    val showAboutDialog = remember { mutableStateOf(false) }
    val showHalfsheetDialog = remember { mutableStateOf(false) }
    // Cannot use extension function for Halfsheet Preference, onValueChange needed.
    val showHalfsheetPref =
      rememberPreferenceState("halfsheet_preference", false)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
      switchPreference(
        key = "codelab_preference",
        defaultValue = false,
        icon = {
          Icon(
            painter = painterResource(id = R.drawable.ic_baseline_code_24),
            contentDescription = null // decorative element
          )
        },
        title = { Text(text = "Codelab") },
        summary = { Text(text = if (it) "Show codelab info at startup" else "Do not show codelab info at startup") }
      )
      switchPreference(
        key = "offline_devices_preference",
        defaultValue = true,
        icon = {
          Icon(
            painter = painterResource(id = R.drawable.ic_baseline_signal_wifi_off_24),
            contentDescription = null // decorative element
          )
        },
        title = { Text(text = "Offline devices") },
        summary = { Text(text = if (it) "Show offline devices" else "Do not show offline devices") }
      )
      item {
        // Need to use this form as we7 must have access to onValueChange.
        var value by showHalfsheetPref
        SwitchPreference(
          value = value,
          icon = {
            Icon(
              painter = painterResource(id = R.drawable.baseline_notifications_24),
              contentDescription = null // decorative element
            )
          },
          title = { Text(text = "Halfsheet notification") },
          summary = {
            Text(
              text = if (showHalfsheetPref.value)
                "Show proactive commissionable discovery notifications for Matter devices"
              else
                "Do not show proactive commissionable discovery notifications for Matter devices"
            )
          },
          onValueChange = {
            value = it
            showHalfsheetDialog.value = true
          })
      }
      preference(
        key = "developer_utilities_preference",
        icon = {
          Icon(
            painter = painterResource(id = R.drawable.ic_baseline_developer_mode_24),
            contentDescription = null // decorative element
          )
        },
        title = { Text(text = "Developer utilities") },
        summary = { Text(text = "Various utility functions for developers who want to dig deeper!") },
        onClick = {
          findNavController().navigate(R.id.action_settingsFragment_to_settingsDeveloperUtilitiesFragment)
        }
      )
      preference(
        key = "help_feedback_preference",
        icon = {
          Icon(
            painter = painterResource(id = R.drawable.ic_baseline_help_24),
            contentDescription = null // decorative element
          )
        },
        title = { Text(text = "Help and Feedback") },
        summary = { Text(text = "Learn how to use this sample app and/or give us feedback") },
        onClick = { showHelpAndFeedbackDialog.value = true }
      )
      preference(
        key = "about_preference",
        icon = {
          Icon(
            painter = painterResource(id = R.drawable.ic_baseline_help_24),
            contentDescription = null // decorative element
          )
        },
        title = { Text(text = "About this app") },
        summary = { Text(text = "More information about this application") },
        onClick = { showAboutDialog.value = true }
      )
    }
    if (showHelpAndFeedbackDialog.value) {
      HtmlInfoDialog(
        "Help and Feedback",
        getString(R.string.help_and_feedback),
        onClick = { showHelpAndFeedbackDialog.value = false })
    }
    if (showAboutDialog.value) {
      HtmlInfoDialog(
        "About this app",
        getString(R.string.about_app, VERSION_NAME),
        onClick = { showAboutDialog.value = false })
    }
    if (showHalfsheetDialog.value) {
      HtmlInfoDialog(
        "Halfsheet Notification",
        getString(R.string.halfsheet_notification_alert),
        onClick = { showHalfsheetDialog.value = false })
    }
  }
}

@Composable
fun HtmlInfoDialog(title: String, htmlInfo: String, onClick: () -> Unit) {
  val htmlText = HtmlCompat.fromHtml(htmlInfo, FROM_HTML_MODE_LEGACY)
  AlertDialog(
    title = { Text(text = title) },
    text = {
      // See https://developer.android.com/codelabs/jetpack-compose-migration
      AndroidView(
        update = { it.text = htmlText },
        factory = {
          MaterialTextView(it).apply {
            movementMethod = LinkMovementMethod.getInstance()
          }
        },
      )
    },
    confirmButton = {
      TextButton(
        onClick = { onClick() }
      ) {
        Text("OK")
      }
    },
    onDismissRequest = {},
    dismissButton = {}
  )
}

