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

package com.google.homesampleapp.screens.thread

import android.app.Activity.RESULT_OK
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials
import com.google.common.io.BaseEncoding
import com.google.homesampleapp.R
import com.google.homesampleapp.databinding.FragmentThreadBinding
import com.google.homesampleapp.intentSenderToString
import com.google.homesampleapp.lifeCycleEvent
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/** The Thread Fragment */
@AndroidEntryPoint
class ThreadFragment : Fragment() {

  // Fragment binding
  private lateinit var binding: FragmentThreadBinding
  private lateinit var threadClientLauncher: ActivityResultLauncher<IntentSenderRequest>

  // The fragment's ViewModel.
  private val viewModel: ThreadViewModel by viewModels()

  /** Lifecycle functions */
  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    super.onCreateView(inflater, container, savedInstanceState)

    Timber.d(lifeCycleEvent("onCreateView()"))

    // Setup the binding with the fragment.
    binding = DataBindingUtil.inflate(inflater, R.layout.fragment_thread, container, false)

    // Setup UI elements and livedata observers.
    setupUiElements()
    setupObservers()

    return binding.root
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
  }

  override fun onResume() {
    super.onResume()
    Timber.d("onResume(): Starting Service Discovery")
    viewModel.startServiceDiscovery(requireContext())
  }

  override fun onPause() {
    super.onPause()
    Timber.d("onPause(): Stopping Service Discovery")
    viewModel.stopServiceDiscovery()
  }

  /** Setup UI elements */
  private fun setupUiElements() {
    binding.getGPSButton.setOnClickListener {
      viewModel.getGPSThreadPreferredCredentials(requireActivity())
    }

    binding.setGPSButton.setOnClickListener { viewModel.setGPSThreadCredentials(requireActivity()) }

    binding.clearGPSButton.setOnClickListener {
      viewModel.clearGPSPreferredCredentials(requireActivity(), requireContext())
    }

    binding.setOTBRButton.setOnClickListener {
      viewModel.setOTBRPendingThreadCredentials(requireActivity())
    }

    binding.getOTBRButton.setOnClickListener {
      viewModel.getOTBRActiveThreadCredentials(requireActivity())
    }

    binding.readQRCode.setOnClickListener { viewModel.readQRCodeWorkingSet(requireActivity()) }

    binding.showQRCode.setOnClickListener { viewModel.showWorkingSetQRCode(requireActivity()) }

    binding.doGPSPreferredCredsExistButton.setOnClickListener {
      viewModel.doGPSPreferredCredsExist(requireActivity())
    }

    setupMenu()
  }

  private fun setupMenu() {
    // Navigate back
    binding.topAppBar.setOnClickListener {
      findNavController().navigate(R.id.action_threadFragment_to_homeFragment)
    }

    binding.topAppBar.setOnMenuItemClickListener {
      // Navigate to Settings
      findNavController().navigate(R.id.action_threadFragment_to_settingsFragment)
      true
    }
  }

  /** Setup Observers */
  private fun setupObservers() {
    /** Registers for activity result from Google Play Services */
    threadClientLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
          if (result.resultCode == RESULT_OK) {
            val threadNetworkCredentials =
                ThreadNetworkCredentials.fromIntentSenderResultData(result.data!!)
            viewModel.threadPreferredCredentialsOperationalDataset.postValue(
                threadNetworkCredentials)
          } else {
            val error = "User denied request."
            Timber.d(error)
            updateThreadInfo(null, "")
          }
        }

    viewModel.threadClientIntentSender.observe(viewLifecycleOwner) { sender ->
      Timber.d("threadClient: intent observe is called with [${intentSenderToString(sender)}]")
      if (sender != null) {
        Timber.d("threadClient: Launch GPS activity to get ThreadClient")
        threadClientLauncher.launch(IntentSenderRequest.Builder(sender).build())
        viewModel.consumeThreadClientIntentSender()
      }
    }

    viewModel.threadPreferredCredentialsOperationalDataset.observe(viewLifecycleOwner) {
      updateThreadInfo(it, "")
    }
  }

  /** UI update functions */
  private fun updateThreadInfo(credentials: ThreadNetworkCredentials?, title: String = "") {

    var textBox = ""
    var tlv = ""

    if (credentials != null) {
      textBox =
          "NetworkName: " +
              credentials.networkName +
              "\nChannel: " +
              credentials.channel +
              "\nPanID: " +
              credentials.panId +
              "\nExtendedPanID: " +
              BaseEncoding.base16().encode(credentials.extendedPanId) +
              "\nNetworkKey: " +
              BaseEncoding.base16().encode(credentials.networkKey) +
              "\nPskc:" +
              BaseEncoding.base16().encode(credentials.pskc) +
              "\nMesh Local Prefix: " +
              BaseEncoding.base16().encode(credentials.meshLocalPrefix)

      tlv = BaseEncoding.base16().encode(credentials.activeOperationalDataset)
    } else {
      textBox = "Error"
    }

    Timber.d(textBox)

    binding.threadNetworkInformationTextView.text = textBox
    binding.threadTLVTextView.text = tlv
  }
}
