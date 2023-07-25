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

package com.google.homesampleapp.screens.commissionable.wifi

import android.annotation.SuppressLint
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import com.google.homesampleapp.screens.commissionable.MatterBeacon
import com.google.homesampleapp.screens.commissionable.MatterBeaconInject
import com.google.homesampleapp.screens.commissionable.MatterBeaconProducer
import com.google.homesampleapp.screens.commissionable.Transport
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import timber.log.Timber

private const val SCAN_QUERY_DELAY_MILLIS = 30_000L
// See section 5.4.2.6 "Using Wi-Fi Temporary Access Points (Soft-AP)" of the Matter Specification.
private val MATTER_SSID_PATTERN =
    """MATTER-(\p{XDigit}{3})-(\p{XDigit}{4})-(\p{XDigit}{4})""".toRegex()

/**
 * [MatterBeaconProducer] that looks for Wi-Fi Soft AP advertisements matching a Matter
 * commissionable device.
 *
 * See these links for important details on Wi-Fi scanning.
 * - https://developer.android.com/guide/topics/connectivity/wifi-scan
 * - https://stackoverflow.com/questions/56401057/wifimanager-startscan-deprecated-alternative
 */
class MatterBeaconProducerWifi
@Inject
constructor(
    @MatterBeaconInject private val wifiManager: WifiManager,
) : MatterBeaconProducer {
  @SuppressLint("MissingPermission")
  override fun getBeaconsFlow(): Flow<MatterBeacon> = flow {
    while (coroutineContext.isActive) {
      val scanResults = wifiManager.scanResults
      Timber.d("${scanResults.size} results from the wifi scan.")
      scanResults
          .orEmpty()
          .mapNotNull { scanResult -> scanResult.toMatterBeaconOrNull() }
          .forEach { beacon ->
            Timber.d("Emitting Matter hotspot beacon: [${beacon}]")
            emit(beacon)
          }

      requestScan()
      delay(SCAN_QUERY_DELAY_MILLIS)
    }
  }

  @Suppress("DEPRECATION") // Currently the only option to refresh scan results.
  private fun requestScan() {
    // This may stop working in a future Android release, but for now this allows us to refresh the
    // nearby Wi-Fi SSIDs.
    wifiManager.startScan()
  }
}

private fun ScanResult.toMatterBeaconOrNull(): MatterBeacon? {
  val ssid = SSID.stripSurroundingQuotes()
  // TODO when minSdk is 33: val ssid = wifiSsid.toString().stripSurroundingQuotes()
  return MATTER_SSID_PATTERN.find(ssid)?.let { result ->
    val (discriminator, vid, pid) = result.destructured
    MatterBeacon(
        ssid, vid.toInt(16), pid.toInt(16), discriminator.toInt(16), Transport.Hotspot(ssid))
  }
}

private fun String.stripSurroundingQuotes(): String {
  return if (length > 1 && startsWith("\"") && endsWith("\"")) {
    substring(1, length - 1)
  } else {
    this
  }
}
