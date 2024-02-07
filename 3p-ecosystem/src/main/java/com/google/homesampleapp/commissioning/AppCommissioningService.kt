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

package com.google.homesampleapp.commissioning

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.android.gms.home.matter.commissioning.CommissioningCompleteMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningRequestMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningService
import com.google.android.gms.home.matter.commissioning.CommissioningService.CommissioningError
import com.google.homesampleapp.APP_NAME
import com.google.homesampleapp.DeviceIdGenerator
import com.google.homesampleapp.R
import com.google.homesampleapp.chip.ChipClient
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.generateNextDeviceId
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The CommissioningService that's responsible for commissioning the device on the app's custom
 * fabric. AppCommissioningService is specified when building the
 * [com.google.android.gms.home.matter.commissioning.CommissioningRequest] in
 * [../screens.home.HomeViewModel].
 */
@AndroidEntryPoint
class AppCommissioningService : Service(), CommissioningService.Callback {

  @Inject internal lateinit var devicesRepository: DevicesRepository
  @Inject internal lateinit var devicesStateRepository: DevicesStateRepository
  @Inject internal lateinit var chipClient: ChipClient

  private val serviceJob = Job()
  private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

  private lateinit var commissioningServiceDelegate: CommissioningService

  override fun onCreate() {
    super.onCreate()
    // May be invoked without MainActivity being called to initialize APP_NAME.
    // So do it here as well.
    APP_NAME = getString(R.string.app_name)
    Timber.d("onCreate()")
    commissioningServiceDelegate = CommissioningService.Builder(this).setCallback(this).build()
  }

  override fun onBind(intent: Intent): IBinder {
    Timber.d("onBind(): intent [${intent}]")
    return commissioningServiceDelegate.asBinder()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Timber.d("onStartCommand(): intent [${intent}] flags [${flags}] startId [${startId}]")
    return super.onStartCommand(intent, flags, startId)
  }

  override fun onDestroy() {
    super.onDestroy()
    Timber.d("onDestroy()")
    serviceJob.cancel()
  }

  override fun onCommissioningRequested(metadata: CommissioningRequestMetadata) {
    Timber.d(
        "*** onCommissioningRequested ***:\n" +
            "\tdeviceDescriptor: " +
            "deviceType [${metadata.deviceDescriptor.deviceType}] " +
            "vendorId [${metadata.deviceDescriptor.vendorId}] " +
            "productId [${metadata.deviceDescriptor.productId}]\n" +
            "\tnetworkLocation: " +
            "IP address toString() [${metadata.networkLocation.ipAddress}] " +
            "IP address hostAddress [${metadata.networkLocation.ipAddress.hostAddress}] " +
            "port [${metadata.networkLocation.port}]\n" +
            "\tpassCode [${metadata.passcode}]")

    // CODELAB: onCommissioningRequested()
    // Perform commissioning on custom fabric for the sample app.
    serviceScope.launch {
      val deviceId = getNextDeviceId(DeviceIdGenerator.Random)
      try {
        Timber.d(
            "Commissioning: App fabric -> ChipClient.establishPaseConnection(): deviceId [${deviceId}]")
        chipClient.awaitEstablishPaseConnection(
            deviceId,
            metadata.networkLocation.ipAddress.hostAddress!!,
            metadata.networkLocation.port,
            metadata.passcode)

        Timber.d(
            "Commissioning: App fabric -> ChipClient.commissionDevice(): deviceId [${deviceId}]")
        chipClient.awaitCommissionDevice(deviceId, null)
      } catch (e: Exception) {
        Timber.e(e, "onCommissioningRequested() failed")
        // No way to determine whether this was ATTESTATION_FAILED or DEVICE_UNREACHABLE.
        commissioningServiceDelegate
            .sendCommissioningError(CommissioningError.OTHER)
            .addOnSuccessListener {
              Timber.d(
                  "Commissioning: commissioningServiceDelegate.sendCommissioningError() succeeded")
            }
            .addOnFailureListener { e2 ->
              Timber.e(
                  e2, "Commissioning: commissioningServiceDelegate.sendCommissioningError() failed")
            }
        return@launch
      }

      Timber.d("Commissioning: Calling commissioningServiceDelegate.sendCommissioningComplete()")
      commissioningServiceDelegate
          .sendCommissioningComplete(
              CommissioningCompleteMetadata.builder().setToken(deviceId.toString()).build())
          .addOnSuccessListener {
            Timber.d(
                "Commissioning: commissioningServiceDelegate.sendCommissioningComplete() succeeded")
          }
          .addOnFailureListener { e ->
            Timber.e(
                e, "Commissioning: commissioningServiceDelegate.sendCommissioningComplete() failed")
          }
    }
    // CODELAB SECTION END
  }

  /**
   * Generates the device id for the device being commissioned ToDo() move this function into an
   * appropriate class to make it visible in HomeFragmentRecyclerViewTest
   *
   * @param generator the method used to generate the device id
   */
  private suspend fun getNextDeviceId(generator: DeviceIdGenerator): Long {
    return when (generator) {
      DeviceIdGenerator.Incremental -> {
        devicesRepository.incrementAndReturnLastDeviceId()
      }
      DeviceIdGenerator.Random -> {
        generateNextDeviceId()
      }
    }
  }
}
