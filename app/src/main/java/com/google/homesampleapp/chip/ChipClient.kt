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

package com.google.homesampleapp.chip

import android.content.Context
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.ControllerParams
import chip.devicecontroller.GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback
import chip.devicecontroller.NetworkCredentials
import chip.devicecontroller.PaseVerifierParams
import chip.platform.AndroidBleManager
import chip.platform.AndroidChipPlatform
import chip.platform.ChipMdnsCallbackImpl
import chip.platform.DiagnosticDataProviderImpl
import chip.platform.NsdManagerServiceBrowser
import chip.platform.NsdManagerServiceResolver
import chip.platform.PreferencesConfigurationManager
import chip.platform.PreferencesKeyValueStoreManager
import com.google.homesampleapp.stripLinkLocalInIpAddress
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import timber.log.Timber

/** Singleton to interact with the CHIP APIs. */
@Singleton
class ChipClient @Inject constructor(@ApplicationContext context: Context) {

  /* 0xFFF4 is a test vendor ID, replace with your assigned company ID */
  private val VENDOR_ID = 0xFFF4

  // Lazily instantiate [ChipDeviceController] and hold a reference to it.
  private val chipDeviceController: ChipDeviceController by lazy {
    ChipDeviceController.loadJni()
    AndroidChipPlatform(
        AndroidBleManager(),
        PreferencesKeyValueStoreManager(context),
        PreferencesConfigurationManager(context),
        NsdManagerServiceResolver(context),
        NsdManagerServiceBrowser(context),
        ChipMdnsCallbackImpl(),
        DiagnosticDataProviderImpl(context))
    ChipDeviceController(
        ControllerParams.newBuilder().setUdpListenPort(0).setControllerVendorId(VENDOR_ID).build())
  }

  /**
   * Wrapper around [ChipDeviceController.getConnectedDevicePointer] to return the value directly.
   */
  suspend fun getConnectedDevicePointer(nodeId: Long): Long {
    return suspendCoroutine { continuation ->
      chipDeviceController.getConnectedDevicePointer(
          nodeId,
          object : GetConnectedDeviceCallback {
            override fun onDeviceConnected(devicePointer: Long) {
              Timber.d("Got connected device pointer")
              continuation.resume(devicePointer)
            }

            override fun onConnectionFailure(nodeId: Long, error: Exception) {
              val errorMessage =
                  "Unable to get connected device with nodeId $nodeId. mDNS flakiness???"
              Timber.e(errorMessage, error)
              continuation.resumeWithException(IllegalStateException(errorMessage))
            }
          })
    }
  }

  fun computePaseVerifier(
      devicePtr: Long,
      pinCode: Long,
      iterations: Long,
      salt: ByteArray
  ): PaseVerifierParams {
    Timber.d(
        "computePaseVerifier: devicePtr [${devicePtr}] pinCode [${pinCode}] iterations [${iterations}] salt [${salt}]")
    return chipDeviceController.computePaseVerifier(devicePtr, pinCode, iterations, salt)
  }

  suspend fun awaitEstablishPaseConnection(
      deviceId: Long,
      ipAddress: String,
      port: Int,
      setupPinCode: Long
  ) {
    return suspendCoroutine { continuation ->
      chipDeviceController.setCompletionListener(
          object : BaseCompletionListener() {
            override fun onConnectDeviceComplete() {
              super.onConnectDeviceComplete()
              continuation.resume(Unit)
            }
            override fun onPairingComplete(code: Int) {
              super.onPairingComplete(code)
              continuation.resume(Unit)
            }

            override fun onError(error: Throwable) {
              super.onError(error)
              continuation.resumeWithException(error)
            }

            override fun onReadCommissioningInfo(
                vendorId: Int,
                productId: Int,
                wifiEndpointId: Int,
                threadEndpointId: Int
            ) {
              super.onReadCommissioningInfo(vendorId, productId, wifiEndpointId, threadEndpointId)
              continuation.resume(Unit)
            }

            override fun onCommissioningStatusUpdate(nodeId: Long, stage: String?, errorCode: Int) {
              super.onCommissioningStatusUpdate(nodeId, stage, errorCode)
              continuation.resume(Unit)
            }
          })

      // Temporary workaround to remove interface indexes from ipAddress
      // due to https://github.com/project-chip/connectedhomeip/pull/19394/files
      chipDeviceController.establishPaseConnection(
          deviceId, stripLinkLocalInIpAddress(ipAddress), port, setupPinCode)
    }
  }

  suspend fun awaitCommissionDevice(deviceId: Long, networkCredentials: NetworkCredentials?) {
    return suspendCoroutine { continuation ->
      chipDeviceController.setCompletionListener(
          object : BaseCompletionListener() {
            override fun onCommissioningComplete(nodeId: Long, errorCode: Int) {
              super.onCommissioningComplete(nodeId, errorCode)
              continuation.resume(Unit)
            }
            override fun onError(error: Throwable) {
              super.onError(error)
              continuation.resumeWithException(error)
            }
          })
      chipDeviceController.commissionDevice(deviceId, networkCredentials)
    }
  }

  suspend fun awaitOpenPairingWindowWithPIN(
      connectedDevicePointer: Long,
      duration: Int,
      iteration: Long,
      discriminator: Int,
      setupPinCode: Long
  ) {
    return suspendCoroutine { continuation ->
      chipDeviceController.setCompletionListener(
          object : BaseCompletionListener() {
            override fun onCommissioningComplete(nodeId: Long, errorCode: Int) {
              Timber.d("awaitOpenPairingWindowWithPIN.onCommissioningComplete: nodeId [${nodeId}]")
              continuation.resume(Unit)
            }
            override fun onError(error: Throwable) {
              Timber.e("awaitOpenPairingWindowWithPIN.onError: nodeId [${error}]")
              continuation.resumeWithException(error)
            }
          })
      Timber.d("Calling chipDeviceController.openPairingWindowWithPIN")
      chipDeviceController.openPairingWindowWithPIN(
          connectedDevicePointer, duration, iteration, discriminator, setupPinCode)
      Timber.d("AFTER Calling chipDeviceController.openPairingWindowWithPIN")
    }
  }

  /**
   * Wrapper around [ChipDeviceController.getConnectedDevicePointer] to return the value directly.
   */
  suspend fun awaitGetConnectedDevicePointer(nodeId: Long): Long {
    return suspendCoroutine { continuation ->
      chipDeviceController.getConnectedDevicePointer(
          nodeId,
          object : GetConnectedDeviceCallback {
            override fun onDeviceConnected(devicePointer: Long) {
              Timber.d("Got connected device pointer")
              continuation.resume(devicePointer)
            }

            override fun onConnectionFailure(nodeId: Long, error: Exception) {
              val errorMessage = "Unable to get connected device with nodeId $nodeId"
              Timber.e(errorMessage, error)
              continuation.resumeWithException(IllegalStateException(errorMessage))
            }
          })
    }
  }
}
