/*
 * Copyright 2023 Google LLC
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

package com.google.homesampleapp.screens.commissionable.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.core.content.ContextCompat.getSystemService
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.discovery.DnsSdServiceInfo
import com.google.android.gms.home.matter.discovery.ResolveServiceRequest
import com.google.android.gms.home.matter.discovery.ResolveServiceRequest.SERVICE_TYPE_COMMISSIONABLE
import com.google.homesampleapp.chip.ChipClient
import com.google.homesampleapp.screens.commissionable.MatterBeacon
import com.google.homesampleapp.screens.commissionable.MatterBeaconProducer
import com.google.homesampleapp.screens.commissionable.Transport
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/** [MatterBeaconProducer] which emits mDNS beacons as they are discovered. */
class MatterBeaconProducerMdns
@Inject
constructor(private val chipClient: ChipClient, @ApplicationContext private val context: Context) :
    MatterBeaconProducer {

  // Android's NSD Manager. Used to scan mDNS advertisements.
  private val nsdManager = getSystemService(context, NsdManager::class.java) as NsdManager

  // Google Home Discovery API. See
  // https://developers.home.google.com/reference/com/google/android/gms/home/matter/discovery/package-summary.
  private val discoveryClient = Matter.getDiscoveryClient(context)

  // The scanned mDNS beacons list that's currently active.
  private var beaconsList = mutableListOf<MatterBeacon>()

  // The producer that is set by the flow.
  private lateinit var producer: ProducerScope<MatterBeacon>

  // ---------------------------------------------------------------------------
  // DiscoveryListener for NsdManager.discoverServices().

  private val discoveryListener =
      object : NsdManager.DiscoveryListener {

        // Called as soon as service discovery begins.
        override fun onDiscoveryStarted(regType: String) {
          Timber.d("onDiscoveryStarted: regType [${regType}]")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
          Timber.d("onServiceFound: service [${service}]")
          if (service.serviceType != SERVICE_TYPE_ANDROID) {
            Timber.d(
                "Discarded Service: Type [${service.serviceType}] Name [${service.serviceName}]")
          } else {
            // Resolve the service.
            val resolveServiceRequest =
                ResolveServiceRequest.create(service.serviceName, SERVICE_TYPE_COMMISSIONABLE)
            discoveryClient
                .resolveService(resolveServiceRequest)
                .addOnSuccessListener { result ->
                  Timber.d("resolveService success: [${result.serviceInfo}]")
                  resolvedDnsSdServiceInfo(result.serviceInfo)
                }
                .addOnFailureListener { error ->
                  Timber.e(error, "resolveService failure: [${error}]")
                }
          }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
          // When the network service is no longer available.
          Timber.d("onServiceLost service [${service}]")
          lostNsdServiceInfo(service)
        }

        override fun onDiscoveryStopped(serviceType: String) {
          Timber.d("onDiscoveryStopped serviceType [${serviceType}]")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
          Timber.d("onStartDiscoveryFailed serviceType [${serviceType}] errorCode [${errorCode}]")
          nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
          Timber.d("onStopDiscoveryFailed serviceType [${serviceType}] errorCode [${errorCode}]")
          nsdManager.stopServiceDiscovery(this)
        }
      }

  // ---------------------------------------------------------------------------
  // Utility methods to manage the discovered mDNS services.

  /**
   * The mDNS service has been resolved. We convert the service information into a MatterBeacon and
   * emit that beacon to the producer flow.
   */
  private fun resolvedDnsSdServiceInfo(dnsSdServiceInfo: DnsSdServiceInfo) {
    val discriminator =
        dnsSdServiceInfo
            .getTxtAttributeValue("D")
            ?.takeIf { it.length <= 4 }
            ?.takeIf { it.all { char -> char.isDigit() } }
            ?.toIntOrNull()
            ?: null

    val vidPid =
        dnsSdServiceInfo
            .getTxtAttributeValue("VP")
            .orEmpty()
            .split("+")
            .takeUnless { it.size > 2 }
            ?.takeIf { it.all { value -> value.all { char -> char.isDigit() } } }
            ?: null

    val address = dnsSdServiceInfo.networkLocations.get(0).formattedIpAddress
    val port = dnsSdServiceInfo.networkLocations.get(0).port

    val beacon =
        MatterBeacon(
            name = dnsSdServiceInfo.instanceName,
            vendorId = vidPid?.getOrNull(0)?.toIntOrNull() ?: 0,
            productId = vidPid?.getOrNull(1)?.toIntOrNull() ?: 0,
            discriminator = discriminator!!,
            transport = Transport.Mdns(address, port, true))
    Timber.d("resolvedDnsSdServiceInfo: [${beacon}]")
    producer.trySend(beacon)
  }

  /** The mDNS service is no longer advertising. */
  private fun lostNsdServiceInfo(nsdServiceInfo: NsdServiceInfo) {
    Timber.d("lostNsdServiceInfo: [${nsdServiceInfo.serviceName}]")
    val beacon =
        MatterBeacon(
            name = nsdServiceInfo.serviceName,
            vendorId = 0,
            productId = 0,
            discriminator = 0,
            Transport.Mdns("0.0.0.0", 0 /* fixme */, false))
    producer.trySend(beacon)
  }

  // ---------------------------------------------------------------------------
  // MatterBeaconProducer interface functions

  override fun getBeaconsFlow(): Flow<MatterBeacon> = callbackFlow {
    Timber.d("Starting mDNS discovery -- NATIVE")
    producer = this
    nsdManager.discoverServices(SERVICE_TYPE_ANDROID, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

    awaitClose {
      Timber.d("awaitClose: Stop discovery.")
      nsdManager.stopServiceDiscovery(discoveryListener)
    }
  }

  // ---------------------------------------------------------------------------
  // Companion

  companion object {
    private const val SERVICE_TYPE_GMSCORE = SERVICE_TYPE_COMMISSIONABLE
    private const val SERVICE_TYPE_ANDROID = SERVICE_TYPE_GMSCORE + "."
  }
}
