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
import com.google.homesampleapp.Device
import com.google.homesampleapp.chip.ChipClient
import com.google.homesampleapp.chip.ClustersHelper
import com.google.homesampleapp.convertToAppDeviceType
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.getTimestampForNow
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
  @Inject internal lateinit var clustersHelper: ClustersHelper

  private val serviceJob = Job()
  private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

  private lateinit var commissioningServiceDelegate: CommissioningService

  override fun onCreate() {
    super.onCreate()
    Timber.d("onCreate()")
    commissioningServiceDelegate = CommissioningService.Builder(this).setCallback(this).build()
  }

  override fun onBind(intent: Intent): IBinder {
    Timber.d("onBind()")
    return commissioningServiceDelegate.asBinder()
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
      val deviceId = devicesRepository.incrementAndReturnLastDeviceId()
      Timber.d("Commissioning Step 1: ChipClient.establishPaseConnection(): deviceId [${deviceId}]")
      chipClient.awaitEstablishPaseConnection(
          deviceId,
          metadata.networkLocation.ipAddress.hostAddress!!,
          metadata.networkLocation.port,
          metadata.passcode)
      Timber.d("Commissioning Step 2: ChipClient.commissionDevice(): deviceId [${deviceId}]")
      chipClient.awaitCommissionDevice(deviceId, null)

      Timber.d("Commissioning Step 3: Adding device to repository")
      devicesRepository.addDevice(
          Device.newBuilder()
              .setName("REAL-$deviceId") // default name that can be overridden by user in next step
              .setDeviceId(deviceId)
              .setDateCommissioned(getTimestampForNow())
              .setVendorId(metadata.deviceDescriptor.vendorId.toString())
              .setProductId(metadata.deviceDescriptor.productId.toString())
              .setDeviceType(convertToAppDeviceType(metadata.deviceDescriptor.deviceType))
              .build())

      Timber.d("Commissioning Step 4: Adding device state to repository: isOnline:true isOn:false")
      devicesStateRepository.addDeviceState(deviceId, isOnline = true, isOn = false)

      Timber.d(
          "Commissioning Step 5: Calling commissioningServiceDelegate.sendCommissioningComplete()")
      commissioningServiceDelegate
          .sendCommissioningComplete(
              CommissioningCompleteMetadata.builder().setToken(deviceId.toString()).build())
          .addOnSuccessListener {
            Timber.d("OnSuccess for commissioningServiceDelegate.sendCommissioningComplete()")
          }
          .addOnFailureListener { ex -> Timber.w(ex, "Failed to send commissioning complete.") }
    }
    // CODELAB SECTION END
  }
}
