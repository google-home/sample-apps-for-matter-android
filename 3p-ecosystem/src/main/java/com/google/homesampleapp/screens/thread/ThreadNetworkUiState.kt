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

import android.net.nsd.NsdServiceInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Stable
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes.getStatusCodeString
import com.google.android.gms.threadnetwork.ThreadBorderAgent
import com.google.android.gms.threadnetwork.ThreadNetwork
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials
import com.google.android.gms.threadnetwork.ThreadNetworkStatusCodes
import com.google.common.io.BaseEncoding
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import timber.log.Timber

/**
 * Implements UI logic associated with ThreadFragment.
 * That UI logic is mainly associated with API calls that require Activity.
 * Actions performed by the user in the Fragment trigger calls to processAction(),
 * and the impact of theses actions are communicated back to the UI via StateFlows.
 *
 * Note: Adding the @Stable annotation to this class helps the compose compiler understand
 * that it doesn't need to eagerly recompose where this class is used and it explicitly
 * tells it we've taken care to make sure that variables that are read in composition
 * will tell the composition when it has changed.
 */
@Stable
class ThreadNetworkUiState(
  private val activity: ComponentActivity,
  private val viewModel: ThreadViewModel
) {

  private lateinit var sd: ServiceDiscovery

  // -----------------------------------------------------------------------------------------------
  // Process Action triggered from the UI

  fun processAction(actionRequest: ActionRequest) {
    Timber.d("processAction [${actionRequest.type}] [${actionRequest.task}] [${actionRequest.serviceInfo}]")

    // Dispatch processing according to action type.
    when (actionRequest.type) {
      ActionType.None -> {
        // No currently active action. No Action Dialog
        viewModel.setActionDialogInfo(ActionType.None, ActionState.None)
      }

      ActionType.doGpsPreferredCredentialsExist -> {
        doGPSPreferredCredsExist(actionRequest)
      }

      ActionType.getGpsPreferredCredentials -> {
        getGPSPreferredCreds(actionRequest)
      }

      ActionType.setGpsPreferredCredentials -> {
        setGPSThreadCredentials(actionRequest)
      }

      ActionType.clearGpsPreferredCredentials -> {
        viewModel.clearGPSPreferredCreds(actionRequest)
      }

      ActionType.getOtbrActiveThreadCredentials -> {
        viewModel.getOTBRActiveThreadCredentials(actionRequest, getThreadBorderRoutersList())
      }

      ActionType.setOtbrPendingThreadCredentials -> {
        viewModel.setOTBRPendingThreadCredentials(actionRequest, getThreadBorderRoutersList())
      }

      ActionType.readQrCodeCredentials -> {
        readQRCodeWorkingSet(actionRequest)
      }

      ActionType.showQrCodeCredentials -> {
        viewModel.showWorkingSetQRCode(actionRequest)
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Actions for GPS Preferred Credentials

  /**
   * Checks whether credentials exist in storage or not. Consent from user is not necessary.
   * Requires Activity.
   */
  private fun doGPSPreferredCredsExist(actionRequest: ActionRequest) {
    viewModel.setActionDialogInfo(
      actionRequest.type,
      ActionState.Processing
    )
    try {
      ThreadNetwork.getClient(activity)
        .preferredCredentials
        .addOnSuccessListener { intentSenderResult ->
          // Don't post the intent on `threadClientIntentSender` as we do when
          // we really want to know what the credentials are as this would prompt a
          // user consent. In this case we just want to know whether the credentials
          // exist.
          if (intentSenderResult.intentSender == null) {
            viewModel.setActionDialogInfoWithError(
              actionRequest.type,
              "No preferred credentials found."
            )
          } else {
            viewModel.setActionDialogInfo(
              actionRequest.type,
              ActionState.Completed
            )
          }
        }
        .addOnFailureListener { e: Exception ->
          viewModel.setActionDialogInfoWithError(
            actionRequest.type,
            ThreadNetworkStatusCodes.getStatusCodeString((e as ApiException).statusCode)
          )
        }
    } catch (e: Exception) {
      viewModel.setActionDialogInfoWithError(actionRequest.type, "Error: $e")
    }
  }

  /**
   * Gets preferred thread network credentials from Google Play Services.
   * Requires Activity.
   */
  private fun getGPSPreferredCreds(actionRequest: ActionRequest) {
    viewModel.setActionDialogInfo(actionRequest.type, ActionState.Processing)
    try {
      ThreadNetwork.getClient(activity)
        .preferredCredentials
        .addOnSuccessListener { intentSenderResult ->
          if (intentSenderResult.intentSender == null) {
            viewModel.setActionDialogInfoWithError(
              actionRequest.type,
              "No preferred credentials found."
            )
          } else {
            intentSenderResult.intentSender?.let { intentSender ->
              Timber.d("threadClient: intent returned result")
              viewModel.setThreadClientIntentSender(intentSender)
            }
          }
        }
        .addOnFailureListener { e: Exception ->
          viewModel.setActionDialogInfoWithError(
            actionRequest.type,
            ThreadNetworkStatusCodes.getStatusCodeString((e as ApiException).statusCode)
          )
        }
    } catch (e: Exception) {
      viewModel.setActionDialogInfoWithError(actionRequest.type, "Error: $e")
    }
  }

  /**
   * Sets the thread network credentials into Google Play Services, pertaining a specific BR.
   * User must have used the "RPi OTBR Credentials - Get" action first to set the
   * Thread Credentials Working Set.
   *
   * The first credentials set become the preferred credentials. Thus, whenever installing a new
   * border router, always follow the procedure
   * 1. Check if a set of preferred credentials exists in GPS (use isPreferredCredentials to a
   *    random set)
   * 2. If preferred credentials already exist, set those to your TBR, and update GPS credentials
   *    with that information
   * 3. If preferred credentials don't exist, create a set (E.g. use the random credentials shown
   *    below), set those to your TBR and update the GPS credentials with that information. Your set
   *    of credentials will thus become the preferred credentials
   */
  private fun setGPSThreadCredentials(actionRequest: ActionRequest) {
    if (viewModel.threadCredentialsExist()) {
      if (!viewModel.selectedThreadBorderRouterExists()) {
        viewModel.setActionDialogInfoWithError(
          actionRequest.type, "Thread Credentials exist in the Working Dataset, " +
              "but a selected Border Router ID is also required to set the GPS " +
              "Thread credentials.\n\n" +
              "Use action \"RPI OTBR Credentials - Get\" to setup " +
              "a Thread Credentials Working Dataset that also includes a Border Router ID."
        )
        return
      }
    } else {
      viewModel.setActionDialogInfoWithError(
        actionRequest.type, "No Thread Credentials Working Dataset currently exists.\n\n" +
            "Use action \"RPI OTBR Credentials - Get\" to setup a Thread Credentials " +
            "Working Dataset that also includes a Border Router ID.\""
      )
      return
    }

    val threadBorderAgent =
      ThreadBorderAgent.newBuilder(viewModel.getSelectedBorderRouterId()!!).build()
    val credentials = viewModel.getThreadNetworkCredentials()
    credentials?.let {
      ThreadNetwork.getClient(activity)
        .addCredentials(threadBorderAgent, credentials)
        .addOnSuccessListener {
          viewModel.setActionDialogInfoWithMessage(
            actionRequest.type, "Thread credentials set successfully!"
          )
        }
        .addOnFailureListener { e: Exception ->
          viewModel.setActionDialogInfoWithError(
            actionRequest.type, "Error adding the new credentials:\n" +
                getStatusCodeString((e as ApiException).statusCode)
          )
        }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Actions for QR Code of Thread credentials

  // Requires Activity.
  private fun readQRCodeWorkingSet(actionRequest: ActionRequest) {
    val options =
      GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC)
        .build()
    val scanner = GmsBarcodeScanning.getClient(activity, options)
    scanner
      .startScan()
      .addOnSuccessListener { barcode ->
        try {
          val qrCodeDataset =
            ThreadNetworkCredentials.fromActiveOperationalDataset(
              BaseEncoding.base16().decode(barcode.displayValue?.substringAfter(":"))
            )
          viewModel.setThreadCredentialsInfo(null, qrCodeDataset)
        } catch (e: Exception) {
          viewModel.setActionDialogInfoWithError(
            actionRequest.type, e.toString()
          )
        }
      }
      .addOnCanceledListener {
        viewModel.setActionDialogInfoWithError(
          actionRequest.type, "QR Code scanning cancelled."
        )
      }
      .addOnFailureListener {
        viewModel.setActionDialogInfoWithError(
          actionRequest.type, it.toString()
        )
      }
  }

  // -----------------------------------------------------------------------------------------------
  // Service Discovery

  /** Scans for border routers in the network */
  fun startServiceDiscovery() {
    // FIXME: are we using the proper activity artifacts here?
    sd = ServiceDiscovery(activity.applicationContext, activity.lifecycleScope)
    sd.start()
  }

  /** Stops scanning for border routers in the network */
  fun stopServiceDiscovery() {
    // FIXME: causes a crash. Commented out for now.
    //   launch app > settings > developer utilities > thread > home
    //   java.lang.IllegalArgumentException: listener not registered
    // sd.stop()
  }

  private fun getThreadBorderRoutersList(): List<NsdServiceInfo> {
    // creates a local immutable list Border Routers. Prevents an update of the list
    // mid operation and a possible invalid reference to a [sd.resolvedDevices] item
    return sd.resolvedDevices.toList()
    //return borderRouterLocalList.map.toList()
  }
}