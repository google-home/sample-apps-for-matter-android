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
package com.google.homesampleapp.screens.thread

import android.app.AlertDialog
import android.content.Context
import android.content.IntentSender
import android.net.nsd.NsdServiceInfo
import android.widget.EditText
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.threadnetwork.ThreadBorderAgent
import com.google.android.gms.threadnetwork.ThreadNetwork
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials
import com.google.android.gms.threadnetwork.ThreadNetworkStatusCodes.*
import com.google.common.io.BaseEncoding
import com.google.homesampleapp.ToastTimber
import com.google.homesampleapp.screens.thread.servicediscovery.ServiceDiscovery
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/** The ViewModel for the Thread Fragment. See [ThreadFragment] for additional information. */
@HiltViewModel
class ThreadViewModel @Inject constructor() : ViewModel() {
  /** Thread network info. */
  val threadPreferredCredentialsOperationalDataset = MutableLiveData<ThreadNetworkCredentials?>()
  private lateinit var sd: ServiceDiscovery
  private val otbrPort = "8081"
  private val otbrDatasetPendingEndpoint = "/node/dataset/pending"
  private val otbrDatasetActiveEndpoint = "/node/dataset/active"
  private val threadCredentialsQRCodePrefix = "TD:"
  /** IntentSender LiveData triggered by getting thread client information. */
  private val _threadClientIntentSender = MutableLiveData<IntentSender?>()
  val threadClientIntentSender: LiveData<IntentSender?>
    get() = _threadClientIntentSender

  /** Scans for border routers in the network */
  fun startServiceDiscovery(context: Context) {
    sd = ServiceDiscovery(context, this.viewModelScope)
    sd.start()
  }

  /** Stops scanning for border routers in the network */
  fun stopServiceDiscovery() {
    sd.stop()
  }

  /** Gets preferred thread network credentials from Google Play Services */
  fun getGPSThreadPreferredCredentials(activity: FragmentActivity) {
    Timber.d("threadClient: getPreferredCredentials intent sent")
    ThreadNetwork.getClient(activity)
    ThreadNetwork.getClient(activity)
        .preferredCredentials
        .addOnSuccessListener { intentSenderResult ->
          intentSenderResult.intentSender?.let { intentSender ->
            Timber.d("threadClient: intent returned result")
            _threadClientIntentSender.postValue(intentSender)
          }
              ?: ToastTimber.d("threadClient: no preferred credentials found", activity)
        }
        .addOnFailureListener { e: Exception ->
          Timber.d("threadClient: " + getStatusCodeString((e as ApiException).statusCode))
        }
  }

  /**
   * Sets the thread network credentials into Google Play Services, pertaining a specific BR
   *
   * The first credentials set become the preferred credentials. Thus, whenever installing a new
   * border router, always follow the procedure
   * 1. Check if a set of preferred credentials exists in GPS (use [isPreferredCredentials] to a
   *    random set)
   * 2. If preferred credentials already exist, set those to your TBR, and update GPS credentials
   *    with that information
   * 3. If preferred credentials don't exist, create a set (E.g. use the random credentials shown
   *    below), set those to your TBR and update the GPS credentials with that information. Your set
   *    of credentials will thus become the preferred credentials
   */
  fun setGPSThreadCredentials(activity: FragmentActivity) {
    actionOnOTBRDialog(activity, Dispatchers.Main) { serviceInfo ->
      val selectedThreadBorderRouterId = serviceInfo.attributes["id"]
      if (selectedThreadBorderRouterId == null) {
        ToastTimber.e("Could not determine the Border Router ID from its TXT record", activity)
      } else {
        /** Dialog to enter the thread credentials */
        val enterTBRCredentialsDialog = AlertDialog.Builder(activity)
        enterTBRCredentialsDialog.setTitle("Thread Credentials")
        enterTBRCredentialsDialog.setMessage(
            "Use the preferred credentials (default), create random or copy your own Base16-encoded credentials")
        val enterTBRCredentialsEditText = EditText(activity)
        val credentials = threadPreferredCredentialsOperationalDataset.value
        var base16Credentials = ""
        if (credentials != null) {
          base16Credentials =
              BaseEncoding.base16()
                  .encode((credentials as ThreadNetworkCredentials).activeOperationalDataset)
        }
        enterTBRCredentialsEditText.setText(base16Credentials)
        enterTBRCredentialsDialog.setView(enterTBRCredentialsEditText)
        /** Ok button: assigns to GPS credentials from text edit box */
        enterTBRCredentialsDialog.setPositiveButton("OK") { _, _ ->
          setGPSThreadCredentials(
              selectedThreadBorderRouterId, enterTBRCredentialsEditText.text.toString(), activity)
        }
        /** Random button: creates new random set of credentials and assigns to GPS */
        enterTBRCredentialsDialog.setNeutralButton("Create Random") { _, _ ->
          setGMSThreadRandomCredentials(selectedThreadBorderRouterId, activity)
        }
        /** Cancel button */
        enterTBRCredentialsDialog.setNegativeButton("Cancel", null)
        enterTBRCredentialsDialog.show()
      }
    }
  }

  /** Sets the GPS thread credentials from new random credentials */
  private fun setGMSThreadRandomCredentials(borderRouterId: ByteArray, activity: FragmentActivity) {
    // Dialog for network name
    val enterNetworkName = AlertDialog.Builder(activity)
    enterNetworkName.setTitle("Network Name")
    enterNetworkName.setMessage("Enter the network name")
    val enterNetworkNameEditText = EditText(activity)
    enterNetworkName.setView(enterNetworkNameEditText)
    enterNetworkName.setPositiveButton("OK") { _, _ ->
      val credentials =
          ThreadNetworkCredentials.newRandomizedBuilder()
              .setNetworkName(enterNetworkNameEditText.text.toString())
              .build()
      val threadBorderAgent = ThreadBorderAgent.newBuilder(borderRouterId).build()
      associateGPSThreadCredentialsToThreadBorderRouterAgent(
          credentials, activity, threadBorderAgent)
    }
    enterNetworkName.show()
  }

  /** Sets the GPS thread credentials based on base16 credentials and the TBR id */
  private fun setGPSThreadCredentials(
      borderRouterId: ByteArray,
      base16Credentials: String,
      activity: FragmentActivity,
  ) {
    val threadBorderAgent = ThreadBorderAgent.newBuilder(borderRouterId).build()
    Timber.d("threadClient: using BR $borderRouterId and credentials $base16Credentials")
    var credentials: ThreadNetworkCredentials? = null
    try {
      credentials =
          ThreadNetworkCredentials.fromActiveOperationalDataset(
              BaseEncoding.base16().decode(base16Credentials))
    } catch (e: Exception) {
      ToastTimber.e("threadClient: error $e", activity)
    }
    associateGPSThreadCredentialsToThreadBorderRouterAgent(credentials, activity, threadBorderAgent)
  }

  /** Last step in setting the GPS thread credentials of a TBR */
  private fun associateGPSThreadCredentialsToThreadBorderRouterAgent(
      credentials: ThreadNetworkCredentials?,
      activity: FragmentActivity,
      threadBorderAgent: ThreadBorderAgent,
  ) {
    credentials?.let {
      ThreadNetwork.getClient(activity)
          .addCredentials(threadBorderAgent, credentials)
          .addOnSuccessListener { ToastTimber.d("threadClient: Credentials added", activity) }
          .addOnFailureListener { e: Exception ->
            ToastTimber.e(
                "threadClient: Error adding the new credentials: " +
                    getStatusCodeString((e as ApiException).statusCode),
                activity)
          }
    }
  }

  /** Sets the credentials of a sample RPi running a Thread Border Router */
  fun setOTBRPendingThreadCredentials(activity: FragmentActivity) {
    val credentials = threadPreferredCredentialsOperationalDataset.value
    if (credentials != null) {
      actionOnOTBRDialog(activity) { serviceInfo ->
        val ipAddress = serviceInfo.host.hostAddress
        val jsonQuery = OtbrHttpClient.createJsonCredentialsObject(credentials)
        var response =
            OtbrHttpClient.createJsonHttpRequest(
                URL("http://$ipAddress:$otbrPort$otbrDatasetPendingEndpoint"),
                activity,
                OtbrHttpClient.Verbs.PUT,
                jsonQuery.toString())
        if (response.first.responseCode in OtbrHttpClient.okResponses) {
          ToastTimber.d("Success", activity)
        }
      }
    } else {
      ToastTimber.e("You must set the working dataset", activity)
    }
  }

  /** Gets the credentials of a sample RPi running a Thread Border Router */
  fun getOTBRActiveThreadCredentials(activity: FragmentActivity) {
    actionOnOTBRDialog(activity) { serviceInfo ->
      val ipAddress = serviceInfo.host.hostAddress
      var response =
          OtbrHttpClient.createJsonHttpRequest(
              URL("http://$ipAddress:$otbrPort$otbrDatasetActiveEndpoint"),
              activity,
              OtbrHttpClient.Verbs.GET,
              acceptMimeType = "text/plain")
      if (response.first.responseCode in OtbrHttpClient.okResponses) {
        val otbrDataset =
            ThreadNetworkCredentials.fromActiveOperationalDataset(
                BaseEncoding.base16().decode(response.second))
        threadPreferredCredentialsOperationalDataset.postValue(otbrDataset)
        ToastTimber.d("Success", activity)
        Timber.d("${response.second}")
      }
    }
  }

  /** Shows the QR Code of the credentials in the working set */
  fun showWorkingSetQRCode(activity: FragmentActivity) {
    val mWriter = MultiFormatWriter()
    try {
      // BitMatrix class to encode entered text and set Width & Height
      val credentials = threadPreferredCredentialsOperationalDataset.value
      if (credentials != null) {
        val qrCodeContent =
            threadCredentialsQRCodePrefix +
                BaseEncoding.base16().encode(credentials.activeOperationalDataset)
        Timber.d("Showing QRCode of $qrCodeContent")
        val mMatrix = mWriter.encode(qrCodeContent, BarcodeFormat.QR_CODE, 600, 600)
        val mEncoder = BarcodeEncoder()
        val mBitmap = mEncoder.createBitmap(mMatrix)
        val qrView = ImageView(activity)
        qrView.setImageBitmap(mBitmap)
        val dialogBuilder = AlertDialog.Builder(activity).setView(qrView)
        dialogBuilder.show()
      } else {
        ToastTimber.e("You must set the working dataset", activity)
      }
    } catch (e: Exception) {
      ToastTimber.e("Error $e", activity)
    }
  }

  /** Prompts whether credentials exist in storage or now. Consent from user is not necessary */
  fun doGPSPreferredCredsExist(activity: FragmentActivity) {
    try {
      Timber.d("threadClient: getPreferredCredentials intent sent")
      ThreadNetwork.getClient(activity)
          .preferredCredentials
          .addOnSuccessListener { intentSenderResult ->
            intentSenderResult.intentSender?.let { intentSender ->
              ToastTimber.d("threadClient: preferred credentials exist", activity)
              // don't post the intent on `threadClientIntentSender` as we do when
              // we really want to know which are the credentials. That will prompt a
              // user consent. In this case we just want to know whether they exist
            }
                ?: ToastTimber.d("threadClient: no preferred credentials found", activity)
          }
          .addOnFailureListener { e: Exception ->
            ToastTimber.e(
                "threadClient: Error adding the new credentials: " +
                    getStatusCodeString((e as ApiException).statusCode),
                activity)
          }
    } catch (e: Exception) {
      ToastTimber.e("Error $e", activity)
    }
  }

  /** Reads the QR Code of Thread credentials into the working set */
  fun readQRCodeWorkingSet(activity: FragmentActivity) {
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
                    BaseEncoding.base16().decode(barcode.displayValue?.substringAfter(":")))
            threadPreferredCredentialsOperationalDataset.postValue(qrCodeDataset)
            ToastTimber.d("Working set updated with QR Code content", activity)
          } catch (e: Exception) {
            ToastTimber.e("Error reading QR Code content: $e", activity)
          }
        }
        .addOnCanceledListener { ToastTimber.d("QR Code scanning cancelled", activity) }
        .addOnFailureListener { e -> ToastTimber.e("Error reading QR Code: $e", activity) }
  }

  /**
   * Creates a dialog where user may pick a Border Router from the ones found on the local network
   * via mDNS/Service Discovery. The caller must provide a lambda function that will be called with
   * the [NsdServiceInfo] of the selected border router
   */
  private fun actionOnOTBRDialog(
      activity: FragmentActivity,
      dispatcher: CoroutineDispatcher = Dispatchers.IO,
      block: suspend (serviceInfo: NsdServiceInfo) -> Unit,
  ) {
    // Get the ips of the Border Routers in the network and prompts the user to select them
    val selectTBRDialog = AlertDialog.Builder(activity)
    selectTBRDialog.setTitle("Select the Border Router. Ensure the BR has the REST API enabled")
    // creates a local immutable list Border Routers. Prevents an update of the list
    // mid operation and a possible invalid reference to a [sd.resolvedDevices] item
    val borderRouterLocalList: List<NsdServiceInfo> = sd.resolvedDevices.toList()
    val borderRouterStringList =
        borderRouterLocalList.map { serviceInfo -> serviceInfo.serviceName }.toTypedArray()
    selectTBRDialog.setItems(borderRouterStringList) { _, selectedThreadBorderRouter ->
      viewModelScope.launch(dispatcher) {
        // We need to catch this again for java.net.ConnectException
        try {
          block(borderRouterLocalList[selectedThreadBorderRouter])
        } catch (e: Exception) {
          ToastTimber.e("$e", activity)
        }
      }
    }
    selectTBRDialog.show()
  }

  /**
   * There is no API to clear the preferred credential's storage. This method displays a message on
   * how to perform it in development phones via adb
   */
  fun clearGPSPreferredCredentials(activity: FragmentActivity, context: Context) {
    val input = EditText(context)
    val dialogBuilder = AlertDialog.Builder(activity).setView(input)
    dialogBuilder
        .setMessage(
            "You can't clear credentials programmatically.\n\n" +
                "If you would like to alter your GPS Preferred Thread credentials " +
                "after they have been set, you must:\n" +
                "1. Factory Reset all your Google Border Routers\n" +
                "2. Clear all GPS information via `adb -d shell pm clear com.google.android.gms`\n" +
                "3a. get credentials from your OTBR or\n" +
                "3b. create a new set\n" +
                "4. Set the credentials to a BR in GPS. First credential set will become preferred\n\n" +
                "Clearing all the GPS data might have unforeseen side effects on other Google " +
                "Services. Use cautiously on a phone and on an user dedicated to development purposes.")
        .setCancelable(false)
        .setPositiveButton("Ok") { dialog, id -> dialog.dismiss() }
    val alert = dialogBuilder.create()
    alert.setTitle("Warning")
    alert.show()
  }

  /**
   * Consumes the value in [_threadClientIntentSender] and sets it back to null. Needs to be called
   * to avoid re-processing the IntentSender after a configuration change (where the LiveData is
   * re-posted.
   */
  fun consumeThreadClientIntentSender() {
    _threadClientIntentSender.postValue(null)
  }
}
