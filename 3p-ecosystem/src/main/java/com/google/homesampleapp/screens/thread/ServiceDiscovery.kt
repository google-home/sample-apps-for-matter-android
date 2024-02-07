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

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.google.common.io.BaseEncoding
import java.util.concurrent.Semaphore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Service discovery for Thread Border Routers
 *
 * This method starts the discovery of Thread Border Router services, identified by the
 * [threadBorderRouterServiceType]. Service discovery will *only* find devices in the same network
 * partition or subnet, such as we find on a home or SMB. Once there is a router in between
 * partitions on complex network topologies, such as corporate networks, the multicasts will not
 * traverse them, and you'll not be able to see devices beyond the router.
 *
 * The exception to the rule is the Thread Border Router, which will expose the Thread devices in
 * the Thread Network, which register using the Service Registration Protocol (SRP)
 *
 * Once the service (device) is found, it is resolved by the [ResolveListener], which will add the
 * device information to the list of [resolvedDevices]. A list is used here (and not a map) because
 * it makes the iteration easier when user selects a BR in the pop up dialog
 *
 * This set has rich service information, such as IP addresses and several TXT fields. See more
 * information about the [threadBorderRouterServiceType] on
 * https://developers.home.google.com/thread#border_agent_discovery
 *
 * You'll find similar mDNS/Service Discovery usage, but for Matter devices, on
 * screens/commissionable/mdns. Matter has its own specific libraries that encapsulate nsdManager
 * and Matter-specific mDNS/SD code
 */
class ServiceDiscovery(context: Context, val coroutineScope: CoroutineScope) {
  val resolvedDevices = mutableListOf<NsdServiceInfo>()
  private val threadBorderRouterServiceType = "_meshcop._udp."
  private val nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager)
  private val lock = Semaphore(1)
  private val discoveryListener: NsdManager.DiscoveryListener =
      DiscoveryListener(
          threadBorderRouterServiceType, nsdManager, resolvedDevices, coroutineScope, lock)

  fun start() {
    coroutineScope.launch(Dispatchers.IO) {
      nsdManager.discoverServices(
          threadBorderRouterServiceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }
  }

  fun stop() {
    // FIXME: how come it crashes when changing orientation?
    // Caused by: java.lang.IllegalArgumentException: listener not registered
    //   at android.net.nsd.NsdManager.getListenerKey(NsdManager.java:1102)
    //   at android.net.nsd.NsdManager.stopServiceDiscovery(NsdManager.java:1346)
    //   at com.google.homesampleapp.screens.thread.servicediscovery.ServiceDiscovery.stop(ServiceDiscovery.kt:56)
    nsdManager.stopServiceDiscovery(discoveryListener)
  }
}

/**
 * DiscoveryListener overrides several methods that are called throughout the lifecycle of the
 * service/device in Service Discovery.
 *
 * Whenever a service/device is found, we resolve the device. Please note that due to some
 * limitations of NsdManager on Android V and previous, we use a lock and a short delay to prevent
 * simultaneous resolving of services.
 */
class DiscoveryListener(
  private val serviceType: String,
  private val nsdManager: NsdManager,
  private val resolvedServices: MutableList<NsdServiceInfo>,
  private val coroutineScope: CoroutineScope,
  private val lock: Semaphore
) : NsdManager.DiscoveryListener {
  // Called as soon as service discovery begins.
  override fun onDiscoveryStarted(regType: String) {
    Timber.d("threadClient: Border Router Service discovery started")
  }

  override fun onServiceFound(service: NsdServiceInfo) {
    if (service.serviceType !=
        serviceType) { // Service type is the string containing the protocol and transport layer for
      // this service.
      Timber.d("Unknown Service discovered: ${service.serviceType}")
    } else {
      Timber.d("Service discovered $service")
      // Sending this coroutine to the Dispatcher.IO thread, so it doesn't block UI
      coroutineScope.launch(Dispatchers.IO) {
        // NsdManager doesn't like simultaneous resolve calls. Thus using a lock
        lock.acquire()
        nsdManager.resolveService(service, ResolveListener(resolvedServices))
        // NsdManager fails if several resolve requests are sent without delays between them
        delay(100)
        lock.release()
      }
    }
  }

  override fun onServiceLost(service: NsdServiceInfo) {
    if (resolvedServices.contains(service)) {
      resolvedServices.remove(service)
    }
    Timber.e("Service Lost: $service")
  }

  override fun onDiscoveryStopped(serviceType: String) {
    resolvedServices.clear()
    Timber.i("Discovery stopped: $serviceType")
  }

  override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
    resolvedServices.clear()
    Timber.e("Discovery failed: Error code: $errorCode")
    nsdManager.stopServiceDiscovery(this)
  }

  override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
    resolvedServices.clear()
    Timber.e("Discovery failed: Error code: $errorCode")
    nsdManager.stopServiceDiscovery(this)
  }
}

/**
 * ResolveListener overrides is the last step on acquiring information about a service/device.
 *
 * It will log part of the information found and add the data to [resolvedDevices] for future usage
 * when we want to show a list of known Border Routers in the local network.
 */
class ResolveListener(
    private val resolvedDevices: MutableList<NsdServiceInfo>,
) : NsdManager.ResolveListener {
  override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
    // Called when the resolve fails. Use the error code to debug.
    Timber.e("Resolve failed: $errorCode")
  }

  override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
    if ((serviceInfo.attributes["id"] != null && serviceInfo.attributes["id"]!!.isNotEmpty())) {
      Timber.d(
          "Resolve Succeeded\n" +
              "    Host Service Name: ${serviceInfo.serviceName}\n" +
              "                   ID: ${
          BaseEncoding.base16().encode(serviceInfo.attributes["id"]?.let { it })
        }\n" +
              "                   NN: ${serviceInfo.attributes["nn"]?.let { String(it) }}\n" +
              "                   IP: ${serviceInfo.host.hostAddress}\n")
      resolvedDevices.add(serviceInfo)
    } else {
      Timber.e("Resolve failed")
    }
  }
}
