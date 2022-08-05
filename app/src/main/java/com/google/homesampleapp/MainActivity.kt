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

package com.google.homesampleapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil.setContentView
import com.google.homesampleapp.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

/** Main Activity for the "Google Home Sample App for Matter" (GHSAFM). */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

  private lateinit var binding: ActivityMainBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = setContentView(this, R.layout.activity_main)

    // Useful to see which preferences are set under the hood by Matter libraries.
    displayPreferences(this)

    initContextDependentConstants()
  }

  /**
   * Constants we access from Utils, but that depend on the Activity context to be set to their
   * values.
   */
  fun initContextDependentConstants() {
    // versionName is set in build.gradle.
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    VERSION_NAME = packageInfo.versionName

    // Strings associated with DeviceTypes
    setDeviceTypeStrings(
        unspecified = getString(R.string.device_type_unspecified),
        light = getString(R.string.device_type_light),
        outlet = getString(R.string.device_type_outlet),
        unknown = getString(R.string.device_type_unknown))
  }
}
