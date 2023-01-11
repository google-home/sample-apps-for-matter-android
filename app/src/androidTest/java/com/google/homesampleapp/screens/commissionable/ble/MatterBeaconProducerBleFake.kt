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

package com.google.homesampleapp.screens.commissionable.ble

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

/** [MatterBeaconProducer] which emits BLE beacons as they are discovered. */
class MatterBeaconProducerBleFake
@Inject
constructor(@ApplicationContext private val context: Context) : MatterBeaconProducer {

  private val EMIT_DELAY_MS = 1000L

  override fun getBeaconsFlow(): Flow<MatterBeacon> = callbackFlow {
    Timber.d("Starting BLE discovery -- NATIVE")

    var count = 0
    while (true) {
      val beacon =
          MatterBeacon(
              name = "BLE-test-${count}",
              vendorId = 1,
              productId = 1,
              discriminator = 1,
              Transport.Ble("1.1.1.1"))

      trySend(beacon)

      delay(EMIT_DELAY_MS)
      count++
    }

    awaitClose { Timber.d("awaitClose: Stop discovery.") }
  }
}
