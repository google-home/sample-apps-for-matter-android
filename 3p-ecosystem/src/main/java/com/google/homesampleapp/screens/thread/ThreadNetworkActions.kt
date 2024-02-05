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

package com.google.homesampleapp.screens.thread

import android.graphics.Bitmap
import android.net.nsd.NsdServiceInfo
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials

enum class ActionType(val title: String) {
  None(""),
  // GPS Preferred Credentials
  doGpsPreferredCredentialsExist("Do Preferred Credentials Exist?"),
  getGpsPreferredCredentials("Get Preferred Credentials"),
  setGpsPreferredCredentials("Set Preferred Credentials"),
  clearGpsPreferredCredentials("Clear Preferred Credentials"),
  // Rapsberry Pi OTBR Credentials
  getOtbrActiveThreadCredentials("Get OTBR Credentials"),
  setOtbrPendingThreadCredentials("Set OTBR Credentials"),
  // QR Code Credentials
  readQrCodeCredentials("Read QR Code Credentials"),
  showQrCodeCredentials("Show QR Code Credentials"),
}

enum class ActionTask {
  None,
  Init,
  Process,
  Complete
}

enum class ActionState { None,
  Processing,
  BorderRoutersProvided,
  Completed,
  Error,
}

data class ActionDialogInfo(
  val type: ActionType = ActionType.None,
  val state: ActionState = ActionState.None,
  val data: String = "",
  val borderRoutersList: List<NsdServiceInfo> = emptyList(),
  val qrCodeBitmap: Bitmap? = null
)

data class ActionRequest(
  val type: ActionType = ActionType.None,
  val task: ActionTask = ActionTask.None,
  val serviceInfo: NsdServiceInfo? = null
)

data class ThreadCredentialsInfo(
  val selectedThreadBorderRouterId: ByteArray? = null,
  val credentials: ThreadNetworkCredentials? = null,
)