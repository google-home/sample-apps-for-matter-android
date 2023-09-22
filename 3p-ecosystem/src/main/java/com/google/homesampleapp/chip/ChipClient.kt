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
import chip.devicecontroller.*
import chip.devicecontroller.GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback
import chip.devicecontroller.model.*
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

  private val DEFAULT_TIMEOUT = 1000

  // Lazily instantiate [ChipDeviceController] and hold a reference to it.
  val chipDeviceController: ChipDeviceController by lazy {
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
              val errorMessage = "Unable to get connected device with nodeId $nodeId."
              Timber.e(errorMessage, error)
              continuation.resumeWithException(IllegalStateException(errorMessage))
            }
          })
    }
  }

  /**
   * Removes the app's fabric from the device.
   *
   * @param nodeId node identifier
   */
  suspend fun awaitUnpairDevice(nodeId: Long) {
    return suspendCoroutine { continuation ->
      Timber.d("Calling chipDeviceController.unpair")
      val callback: UnpairDeviceCallback =
          object : UnpairDeviceCallback {
            override fun onError(status: Int, nodeId: Long) {
              continuation.resumeWithException(
                  java.lang.IllegalStateException(
                      "Failed unpairing device [$nodeId] with status [$status]"))
            }

            override fun onSuccess(nodeId: Long) {
              Timber.d("awaitUnpairDevice.onSuccess: deviceId [$nodeId]")
              continuation.resume(Unit)
            }
          }
      chipDeviceController.unpairDeviceCallback(nodeId, callback)
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

            // Note that an error in processing is not necessarily communicated via onError().
            // onCommissioningComplete with a "code != 0" also denotes an error in processing.
            override fun onPairingComplete(code: Int) {
              super.onPairingComplete(code)
              if (code != 0) {
                continuation.resumeWithException(
                    IllegalStateException("Pairing failed with error code [${code}]"))
              } else {
                continuation.resume(Unit)
              }
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
            // Note that an error in processing is not necessarily communicated via onError().
            // onCommissioningComplete with an "errorCode != 0" also denotes an error in processing.
            override fun onCommissioningComplete(nodeId: Long, errorCode: Int) {
              super.onCommissioningComplete(nodeId, errorCode)
              if (errorCode != 0) {
                continuation.resumeWithException(
                    IllegalStateException("Commissioning failed with error code [${errorCode}]"))
              } else {
                continuation.resume(Unit)
              }
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
      Timber.d("Calling chipDeviceController.openPairingWindowWithPIN")
      val callback: OpenCommissioningCallback =
          object : OpenCommissioningCallback {
            override fun onError(status: Int, deviceId: Long) {
              Timber.e(
                  "ShareDevice: awaitOpenPairingWindowWithPIN.onError: status [${status}] device [${deviceId}]")
              continuation.resumeWithException(
                  java.lang.IllegalStateException(
                      "Failed opening the pairing window with status [${status}]"))
            }

            override fun onSuccess(deviceId: Long, manualPairingCode: String?, qrCode: String?) {
              Timber.d(
                  "ShareDevice: awaitOpenPairingWindowWithPIN.onSuccess: deviceId [${deviceId}]")
              continuation.resume(Unit)
            }
          }
      chipDeviceController.openPairingWindowWithPINCallback(
          connectedDevicePointer, duration, iteration, discriminator, setupPinCode, callback)
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

  // ---------------------------------------------------------------------------
  // We use our own mDNS discovery code, but interesting to note that
  // ChipDeviceController also offers that feature.

  fun getCommissionableNodes() {
    chipDeviceController.discoverCommissionableNodes()
  }

  fun getDiscoveredDevice(index: Int): DiscoveredDevice? {
    Timber.d("getDiscoveredDevice(${index})")
    return chipDeviceController.getDiscoveredDevice(index)
  }

  // ---------------------------------------------------------------------------
  // Access clusters via numeric ids. Useful to access manufacturer specific clusters.

  suspend fun writeAttribute(
      devicePtr: Long,
      attributePath: ChipAttributePath,
      tlv: ByteArray,
      timedRequestTimeoutMs: Int = DEFAULT_TIMEOUT,
      imTimeoutMs: Int = DEFAULT_TIMEOUT
  ) {
    return writeAttributes(
        devicePtr, mapOf(attributePath to tlv), timedRequestTimeoutMs, imTimeoutMs)
  }

  /** Wrapper around [ChipDeviceController.write] */
  suspend fun writeAttributes(
      devicePtr: Long,
      attributes: Map<ChipAttributePath, ByteArray>,
      timedRequestTimeoutMs: Int = DEFAULT_TIMEOUT,
      imTimeoutMs: Int = DEFAULT_TIMEOUT
  ) {
    return suspendCoroutine { continuation ->
      val requests: List<AttributeWriteRequest> =
          attributes.toList().map {
            AttributeWriteRequest.newInstance(
                it.first.endpointId, it.first.clusterId, it.first.attributeId, it.second)
          }
      val callback: WriteAttributesCallback =
          object : WriteAttributesCallback {
            override fun onError(attributePath: ChipAttributePath?, e: java.lang.Exception?) {
              continuation.resumeWithException(IllegalStateException("writeAttributes failed", e))
            }

            override fun onResponse(attributePath: ChipAttributePath?) {
              if (attributePath!! ==
                  ChipAttributePath.newInstance(
                      requests.last().endpointId,
                      requests.last().clusterId,
                      requests.last().attributeId)) {
                continuation.resume(Unit)
              }
            }
          }

      chipDeviceController.write(callback, devicePtr, requests, timedRequestTimeoutMs, imTimeoutMs)
    }
  }

  suspend fun readAttribute(devicePtr: Long, attributePath: ChipAttributePath): AttributeState? {
    return readAttributes(devicePtr, listOf(attributePath))[attributePath]
  }

  /** Wrapper around [ChipDeviceController.readAttributePath] */
  suspend fun readAttributes(
      devicePtr: Long,
      attributePaths: List<ChipAttributePath>
  ): Map<ChipAttributePath, AttributeState> {
    return suspendCoroutine { continuation ->
      val callback: ReportCallback =
          object : ReportCallback {
            override fun onError(
                attributePath: ChipAttributePath?,
                eventPath: ChipEventPath?,
                e: Exception?
            ) {
              continuation.resumeWithException(IllegalStateException("readAttributes failed", e))
            }

            override fun onReport(nodeState: NodeState?) {
              val states: HashMap<ChipAttributePath, AttributeState> = HashMap()
              for (path in attributePaths) {
                var endpoint: Int = path.endpointId.id.toInt()
                states[path] =
                    nodeState!!
                        .getEndpointState(endpoint)!!
                        .getClusterState(path.clusterId.id)!!
                        .getAttributeState(path.attributeId.id)!!
              }
              continuation.resume(states)
            }

            override fun onDone() {
              super.onDone()
            }
          }
      chipDeviceController.readAttributePath(callback, devicePtr, attributePaths)
    }
  }

  /** Wrapper around [ChipDeviceController.subscribeToAttributePath] */
  suspend fun subscribeToAttribute(
      devicePtr: Long,
      attributePath: ChipAttributePath,
      minInterval: Int,
      maxInterval: Int,
      callback: ReportCallback
  ) {
    return suspendCoroutine { continuation ->
      chipDeviceController.subscribeToAttributePath(
          { continuation.resume(Unit) },
          callback,
          devicePtr,
          listOf(attributePath),
          minInterval,
          maxInterval)
    }
  }

  /** Wrapper around [ChipDeviceController.invoke] */
  suspend fun invoke(
      devicePtr: Long,
      invokeElement: InvokeElement,
      timedRequestTimeoutMs: Int = DEFAULT_TIMEOUT,
      imTimeoutMs: Int = DEFAULT_TIMEOUT
  ): Long {
    return suspendCoroutine { continuation ->
      val invokeCallback: InvokeCallback =
          object : InvokeCallback {
            override fun onError(e: java.lang.Exception?) {
              continuation.resumeWithException(IllegalStateException("invoke failed", e))
            }

            override fun onResponse(invokeElement: InvokeElement?, successCode: Long) {
              continuation.resume(successCode)
            }
          }
      chipDeviceController.invoke(
          invokeCallback, devicePtr, invokeElement, timedRequestTimeoutMs, imTimeoutMs)
    }
  }
}
