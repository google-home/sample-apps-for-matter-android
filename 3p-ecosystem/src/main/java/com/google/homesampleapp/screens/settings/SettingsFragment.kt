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

package com.google.homesampleapp.screens.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.homesampleapp.R
import com.google.homesampleapp.databinding.FragmentSettingsBinding
import timber.log.Timber

/** Shows settings and user preferences. */
class SettingsFragment : Fragment() {

  private lateinit var binding: FragmentSettingsBinding

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {

    binding = DataBindingUtil.inflate(inflater, R.layout.fragment_settings, container, false)
    setupUiElements()
    return binding.root
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    childFragmentManager
        .beginTransaction()
        .replace(R.id.nested_settings_fragment, SettingsNestedFragment())
        .commit()
  }

  private fun setupUiElements() {
    binding.topAppBar.setNavigationOnClickListener {
      // navigate back.
      Timber.d("topAppBar.setNavigationOnClickListener")
      findNavController().popBackStack()
    }
  }
}
