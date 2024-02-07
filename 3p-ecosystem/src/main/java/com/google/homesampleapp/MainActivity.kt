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

package com.google.homesampleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.rememberNavController
import com.google.homesampleapp.lifecycle.AppLifecycleObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import timber.log.Timber

/** Main Activity for the "Google Home Sample App for Matter" (GHSAFM). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject internal lateinit var lifecycleObservers: Set<@JvmSuppressWildcards AppLifecycleObserver>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    initContextDependentConstants()
    Timber.d("onCreate()")

    // See package "com.google.homesampleapp.lifecycle" for all the lifecycle observers
    // defined for the application.
    Timber.d("lifecycleObservers [$lifecycleObservers]")
    lifecycleObservers.forEach { lifecycle.addObserver(it) }

    // Useful to see which preferences are set under the hood by Matter libraries.
    displayPreferences(this)

    setContent {
      MaterialTheme {
        ProvidePreferenceLocals {
          val navController = rememberNavController()
          AppLayout(navController)
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    Timber.d("onStart()")
  }

  /**
   * Constants we access from Utils, but that depend on the Activity context to be set to their
   * values.
   */
  private fun initContextDependentConstants() {
    // versionName is set in build.gradle.
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    VERSION_NAME = packageInfo.versionName
    APP_NAME = getString(R.string.app_name)
    packageInfo.packageName
    Timber.i(
      "====================================\n" +
        "Version ${VERSION_NAME}\n" +
        "App     ${APP_NAME}\n" +
        "===================================="
    )
  }
}
