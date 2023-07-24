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

  // The adapter used by the RecyclerView (where we show the list of devices).
  private val adapter = MatterBeaconAdapter()

  // ---------------------------------------------------------------------------
  // Lifecycle functions

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.d("onCreate()")

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel.beaconsFlow.distinctUntilChanged().collect { beacon ->
          adapter.submitList(beacon.toList())
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
    binding = DataBindingUtil.inflate(inflater, R.layout.fragment_commissionable, container, false)

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
    // Click listener when an item is selected in the beacons list.
    adapter.onBeaconClickedListener =
        MatterBeaconAdapter.OnBeaconClickedListener { beacon ->
          // [TODO] Selecting an item in this list could display a screen with detailed information
          //  about the device, and allow actions on it such as "commissioning".
          Timber.d("onBeaconClickedListener: beacon [${beacon}]")
        }

    // RecyclerView
    binding.listRecyclerView.adapter = adapter

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
}
