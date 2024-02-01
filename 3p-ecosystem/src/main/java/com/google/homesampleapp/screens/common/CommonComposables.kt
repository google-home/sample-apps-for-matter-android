package com.google.homesampleapp.screens.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import timber.log.Timber

data class DialogInfo(val title: String?, val message: String?, val showConfirmButton: Boolean = true)

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
        TextButton(
          onClick = onDismissMsgAlertDialog
        ) {
          Text("OK")
        }
      }
    },
    onDismissRequest = {},
    dismissButton = {}
  )
}
