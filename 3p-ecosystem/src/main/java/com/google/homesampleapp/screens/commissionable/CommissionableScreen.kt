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

package com.google.homesampleapp.screens.commissionable

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.homesampleapp.R
import timber.log.Timber

/**
 * Fragment used to display a list of nearby discovered Matter devices (discoverable over BLE,
 * Wi-Fi, or mDNS).
 */
@Composable
internal fun CommissionableRoute(
  innerPadding: PaddingValues,
  updateTitle: (title: String) -> Unit,
  commissionableViewModel: CommissionableViewModel = hiltViewModel(),
) {
  val beacons by commissionableViewModel.beaconsLiveData.observeAsState()
  val beaconsList = beacons?.toList() ?: emptyList()

  LaunchedEffect(Unit) {
    updateTitle("Commissionable Devices")
  }

  CommissionableScreen(innerPadding, beaconsList)
}

@Composable
private fun CommissionableScreen(innerPadding: PaddingValues, beaconsList: List<MatterBeacon>) {
  Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    LazyColumn(modifier = Modifier.padding(dimensionResource(R.dimen.padding_surface_content))) {
      this.items(beaconsList) { MatterBeaconItem(it) }
    }
  }
}

@Composable
fun MatterBeaconItem(beacon: MatterBeacon) {
  val icon =
    when (beacon.transport) {
      is Transport.Ble -> R.drawable.quantum_gm_ic_bluetooth_vd_theme_24
      is Transport.Hotspot -> R.drawable.quantum_gm_ic_wifi_vd_theme_24
      is Transport.Mdns -> R.drawable.quantum_gm_ic_router_vd_theme_24
    }
  Row(
    modifier =
      Modifier.clickable {
        // [TODO] Selecting an item in this list could display a screen with detailed information
        //  about the device, and allow actions on it such as "commissioning".
        Timber.d("beacon item clicked")
      }
  ) {
    Image(
      painter = painterResource(icon),
      contentDescription = stringResource(R.string.transport_icon),
      modifier = Modifier.padding(4.dp).align(Alignment.CenterVertically),
    )
    Column(modifier = Modifier.padding(8.dp).align(Alignment.CenterVertically)) {
      val text: String
      val color: androidx.compose.ui.graphics.Color
      if (beacon.transport is Transport.Mdns) {
        val active = beacon.transport.active
        if (!active) {
          text = beacon.name + " [off]"
          color = androidx.compose.ui.graphics.Color.Red
        } else {
          text = beacon.name
          color = androidx.compose.ui.graphics.Color.Black
        }
      } else {
        text = beacon.name
        color = androidx.compose.ui.graphics.Color.Black
      }
      Text(text = text, color = color, style = MaterialTheme.typography.titleMedium)
      Text(
        text =
          stringResource(
            R.string.beacon_detail_text,
            beacon.vendorId,
            beacon.productId,
            beacon.discriminator,
          ),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

// -----------------------------------------------------------------------------------------------
// Composables Previews

@Preview
@Composable
private fun CommissionableScreenPreview() {
  val beaconsList =
    listOf(
      MatterBeacon("Acme LightBulb", 1, 2, 3, Transport.Ble("address")),
      MatterBeacon("Acme Plug", 1, 2, 3, Transport.Mdns("address", 5480, false)),
      MatterBeacon("0AFE867DE", 1, 2, 3, Transport.Hotspot("onhub")),
    )
  MaterialTheme { CommissionableScreen(PaddingValues(), beaconsList) }
}
