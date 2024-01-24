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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.homesampleapp.R
import com.google.homesampleapp.databinding.FragmentCommissionableBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Fragment used to display a list of nearby discovered Matter devices (discoverable over BLE,
 * Wi-Fi, or mDNS).
 */
@AndroidEntryPoint
class CommissionableFragment : Fragment() {

  // Fragment binding.
  private lateinit var binding: FragmentCommissionableBinding

  // The Fragment's ViewModel
  private val viewModel: CommissionableViewModel by viewModels()

  // ---------------------------------------------------------------------------
  // Lifecycle functions

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.d("onCreate()")

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel.beaconsFlow.distinctUntilChanged().collect { beacons ->
          Timber.d("In onCreate, new beacons:\n${beacons}")
        }
      }
    }
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    Timber.d("onCreateView()")

    // Setup the binding with the fragment.
    binding = DataBindingUtil.inflate<FragmentCommissionableBinding>(
      inflater,
      R.layout.fragment_commissionable,
      container,
      false
    ).apply {
      composeView.apply {
        // Dispose the Composition when the view's LifecycleOwner is destroyed
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
          MaterialTheme {
            CommissionableRoute(viewModel)
          }
        }
      }
    }

    // Setup the UI elements
    setupUiElements()

    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    Timber.d("onViewCreated()")
  }

  override fun onResume() {
    super.onResume()
    Timber.d("onResume()")
  }

  // ---------------------------------------------------------------------------
  // UI Functions

  private fun setupUiElements() {
    setupMenu()
  }

  private fun setupMenu() {
    // Navigate back
    binding.topAppBar.setOnClickListener {
      findNavController().navigate(R.id.action_commissionableFragment_to_homeFragment)
    }

    binding.topAppBar.setOnMenuItemClickListener {
      // Navigate to Settings
      findNavController().navigate(R.id.action_commissionableFragment_to_settingsFragment)
      true
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Composables

  @Composable
  private fun CommissionableRoute(commissionableViewModel: CommissionableViewModel) {
    val beacons by commissionableViewModel.beaconsLiveData.observeAsState()
    val beaconsList = beacons!!.toList()
    CommissionableScreen(beaconsList)
  }

  @Composable
  private fun CommissionableScreen(beaconsList: List<MatterBeacon>) {
    LazyColumn(
      Modifier.fillMaxSize()
      // FIXME: How can we get autoscroll with LazyColumn?
      // Statement below causes an error:
      // "Vertically scrollable component was measured with an infinity maximum height constraints, which is disallowed."
      //.verticalScroll(rememberScrollState())
    ) {
      this.items(beaconsList) {
        MatterBeaconItem(it)
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
    Row(modifier = Modifier
      .clickable {
        // [TODO] Selecting an item in this list could display a screen with detailed information
        //  about the device, and allow actions on it such as "commissioning".
        Timber.d("beacon item clicked")
      }
    ) {
      Image(
        painter = painterResource(icon),
        contentDescription = stringResource(R.string.transport_icon),
        modifier = Modifier
          .padding(4.dp)
          .align(Alignment.CenterVertically)
      )
      Column(modifier = Modifier
        .padding(8.dp)
        .align(Alignment.CenterVertically)) {
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
        Text(
          text = text,
          color = color,
          style = MaterialTheme.typography.titleMedium
        )
        Text(
          text = stringResource(
            R.string.beacon_detail_text, beacon.vendorId, beacon.productId, beacon.discriminator
          ),
          style = MaterialTheme.typography.bodyMedium
        )
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Composables Previews

  @Preview
  @Composable
  private fun CommissionableScreenPreview() {
    val beaconsList = listOf(
      MatterBeacon("Acme LightBulb", 1, 2, 3, Transport.Ble("address")),
      MatterBeacon("Acme Plug", 1, 2, 3, Transport.Mdns("address", 5480, false)),
      MatterBeacon("0AFE867DE", 1, 2, 3, Transport.Hotspot("onhub"))
    )
    MaterialTheme {
      CommissionableScreen(beaconsList)
    }
  }
}
