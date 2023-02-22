/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.homesampleapp.data

import android.content.Context
import androidx.lifecycle.asLiveData
import com.google.homesampleapp.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import timber.log.Timber

/** Singleton repository that updates and persists settings and user preferences. */
@Singleton
class UserPreferencesRepository @Inject constructor(@ApplicationContext context: Context) {
  // The datastore managed by UserPreferencesRepository.
  private val userPreferencesDataStore = context.userPreferencesDataStore

  // The Flow to read data from the DataStore.
  val userPreferencesFlow: Flow<UserPreferences> =
      userPreferencesDataStore.data.catch { exception ->
        // dataStore.data throws an IOException when an error is encountered when reading data
        if (exception is IOException) {
          Timber.e(exception, "Error reading user preferences.")
          emit(UserPreferences.getDefaultInstance())
        } else {
          throw exception
        }
      }

  val userPreferencesLiveData = userPreferencesFlow.asLiveData()

  suspend fun updateHideCodelabInfo(hide: Boolean) {
    Timber.d("updateHideCodelabInfo [$hide]")
    userPreferencesDataStore.updateData { prefs ->
      prefs.toBuilder().setHideCodelabInfo(hide).build()
    }
  }

  suspend fun updateHideOfflineDevices(hide: Boolean) {
    Timber.d("updateHideOfflineDevices [$hide]")
    userPreferencesDataStore.updateData { prefs ->
      prefs.toBuilder().setHideOfflineDevices(hide).build()
    }
  }

  suspend fun shouldShowHalfsheetNotification(): Boolean {
    Timber.d("shouldShowHalfsheetNotification")
    return userPreferencesFlow.first().showHalfsheetNotification
  }

  suspend fun updateShowHalfsheetNotification(show: Boolean) {
    Timber.d("updateShowHalfsheetNotification [$show]")
    userPreferencesDataStore.updateData { prefs ->
      prefs.toBuilder().setShowHalfsheetNotification(show).build()
    }
  }

  suspend fun isHideCodelabInfo(): Boolean {
    return userPreferencesFlow.first().hideCodelabInfo
  }

  suspend fun getData(): UserPreferences {
    return userPreferencesFlow.first()
  }
}
