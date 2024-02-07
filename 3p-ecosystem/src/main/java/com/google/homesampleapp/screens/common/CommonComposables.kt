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

package com.google.homesampleapp.screens.common

import android.text.method.LinkMovementMethod
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.google.android.material.textview.MaterialTextView
import timber.log.Timber

// Information used for [MsgAlertDialog].
data class DialogInfo(
  val title: String?,
  val message: String?,
  val showConfirmButton: Boolean = true,
)

// Useful dialog that can display title, message, and confirm button.
@Composable
fun MsgAlertDialog(dialogInfo: DialogInfo?, onDismissMsgAlertDialog: () -> Unit) {
  Timber.d("MsgAlertDialog [$dialogInfo]")
  if (dialogInfo == null) return

  AlertDialog(
    title = {
      if (!dialogInfo.title.isNullOrEmpty()) {
        Text(dialogInfo.title)
      }
    },
    text = {
      if (!dialogInfo.message.isNullOrEmpty()) {
        Text(dialogInfo.message)
      }
    },
    confirmButton = {
      if (dialogInfo.showConfirmButton) {
        TextButton(onClick = onDismissMsgAlertDialog) { Text("OK") }
      }
    },
    onDismissRequest = {},
    dismissButton = {},
  )
}

@Composable
fun HtmlInfoDialog(title: String, htmlInfo: String, onClick: () -> Unit) {
  val htmlText = HtmlCompat.fromHtml(htmlInfo, HtmlCompat.FROM_HTML_MODE_LEGACY)
  AlertDialog(
    title = { Text(text = title) },
    text = {
      // See https://developer.android.com/codelabs/jetpack-compose-migration
      AndroidView(
        update = { it.text = htmlText },
        factory = {
          MaterialTextView(it).apply { movementMethod = LinkMovementMethod.getInstance() }
        },
      )
    },
    confirmButton = { TextButton(onClick = onClick) { Text("OK") } },
    onDismissRequest = {},
    dismissButton = {},
  )
}
