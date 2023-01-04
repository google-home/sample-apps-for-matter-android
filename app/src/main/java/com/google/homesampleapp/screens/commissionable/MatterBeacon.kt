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

package com.google.homesampleapp.screens.commissionable

import java.util.*

/**
 * Representation of a single Matter device beacon.
 *
 * @param name a name used to represent the beaconing device
 * @param vendorId the vendor ID of the beaconing device, or zero if not indicated
 * @param productId the product ID unique to the vendor ID, if present, or zero if not indicated
 * @param discriminator the semi-unique discriminator used to identify this beaconing device
 * @param transport the transport information on which this device was discovered
 */
data class MatterBeacon(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val discriminator: Int,
    val transport: Transport,
) {
  override fun toString(): String {
    return String.format(
        Locale.ROOT,
        "MatterBeacon([%s] VID=%04X, PID=%04X, Discriminator=%03X, Transport=%s",
        name,
        vendorId,
        productId,
        discriminator,
        transport)
  }
}

/** Sealed enumeration of supported transports for Matter beacon discovery. */
sealed class Transport {
  /**
   * Bluetooth LE transport.
   *
   * @param address the Bluetooth address of the device which was discovered
   */
  data class Ble(val address: String) : Transport()

  /**
   * Wi-Fi hotspot transport.
   *
   * @param ssid the Wi-Fi SSID of the device which was discovered
   */
  data class Hotspot(val ssid: String) : Transport()

  /**
   * mDNS transport.
   *
   * @param ipAddress the IP address of the device which was discovered
   * @param port the port at which the service can be reached
   * @param port whether the service is active or not
   */
  data class Mdns(val ipAddress: String, val port: Int, val active: Boolean) : Transport()
}
