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

package com.google.homesampleapp.lifecycle

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.home.matter.Matter
import com.google.homesampleapp.data.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class HalfSheetSuppressionObserver
@Inject
internal constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository
) : AppLifecycleObserver {

  private val scope = CoroutineScope(Dispatchers.Main)

  /**
   * Handle user's preference for suppressing HalfSheet Notifications (proactive commissionable
   * discovery notifications for Matter devices).
   * https://developers.home.google.com/reference/com/google/android/gms/home/matter/commissioning/CommissioningClient#suppressHalfSheetNotification().
   */
  override fun onStart(owner: LifecycleOwner) {
    Timber.d("onStart()")
    scope.launch {
      val suppressHalfSheetNotification = !preferencesRepository.shouldShowHalfsheetNotification()
      if (suppressHalfSheetNotification) {
        try {
          Matter.getCommissioningClient(context).suppressHalfSheetNotification().await()
          Timber.d("suppressHalfSheetNotification: Successful")
        } catch (e: Exception) {
          Timber.e(e, "Error on suppressHalfSheetNotification")
        }
      }
    }
  }

  override fun onStop(owner: LifecycleOwner) {
    scope.cancel()
  }
}
