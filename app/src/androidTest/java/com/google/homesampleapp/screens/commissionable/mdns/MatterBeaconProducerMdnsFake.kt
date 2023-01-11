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
import com.google.homesampleapp.screens.commissionable.MatterBeacon
import com.google.homesampleapp.screens.commissionable.MatterBeaconProducer
import com.google.homesampleapp.screens.commissionable.Transport
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/** [MatterBeaconProducer] which emits mDNS beacons as they are discovered. */
class MatterBeaconProducerMdnsFake
@Inject
constructor(@ApplicationContext private val context: Context) : MatterBeaconProducer {

  private val EMIT_DELAY_MS = 2000L

  override fun getBeaconsFlow(): Flow<MatterBeacon> = callbackFlow {
    Timber.d("Starting mDNS discovery -- NATIVE")

    var count = 0
    while (true) {
      // Every 4th beacon is for inactive service.
      val active = (count % 4 != 3)
      val beacon =
          MatterBeacon(
              name = "mDNS-test-${count}",
              vendorId = 2,
              productId = 22,
              discriminator = 222,
              Transport.Mdns("2.2.2.2", 2, active))

      trySend(beacon)

      delay(EMIT_DELAY_MS)
      count++
    }

    awaitClose { Timber.d("awaitClose: Stop discovery.") }
  }
}
