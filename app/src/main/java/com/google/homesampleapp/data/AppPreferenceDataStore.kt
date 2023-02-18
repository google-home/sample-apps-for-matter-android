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

package com.google.homesampleapp.data

import androidx.preference.PreferenceDataStore
import com.google.homesampleapp.stringToBoolean
import java.security.InvalidParameterException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * A custom datastore for Settings data.
 * https://developer.android.com/guide/topics/ui/settings/use-saved-values#custom-data-store
 *
 * Everything is saved via the UserPreferencesRepository (a Proto DataStore).
 */
@Singleton
class AppPreferenceDataStore @Inject constructor() : PreferenceDataStore() {

  @Inject internal lateinit var userPreferencesRepository: UserPreferencesRepository

  override fun putString(key: String?, value: String?) {
    Timber.d("putString [$key] [$value]")
    val boolValue = stringToBoolean(key!!)
    runBlocking {
      when (key) {
        // codelab represents "showing" the codelab which is the inverse of the "hide" proto value.
        "codelab" -> {
          userPreferencesRepository.updateHideCodelabInfo(!boolValue)
        }
        // offline_devices represents "showing" the offline devices which is the inverse of the
        // "hide" proto value.
        "offline_devices" -> {
          userPreferencesRepository.updateHideOfflineDevices(!boolValue)
        }
        // halfsheet_notification represents "showing" the offline devices which is the
        // same as the "show" proto value.
        "halfsheet_notification" -> {
          userPreferencesRepository.updateShowHalfsheetNotification(boolValue)
        }
        else -> {
          throw InvalidParameterException()
        }
      }
    }
  }

  override fun getString(key: String?, defValue: String?): String {
    Timber.d("getString [$key]")
    var value: String
    runBlocking {
      val job = async { userPreferencesRepository.getData() }
      val userPreferences = job.await()
      Timber.d("userPreferences: [$userPreferences]")
      value =
          when (key) {
            // codelab represents "showing" the codelab which is the inverse of the "hide" proto
            // value.
            "codelab" -> (!userPreferences.hideCodelabInfo).toString()
            // offline_devices represents "showing" the offline devices which is the inverse of the
            // "hide" proto value.
            "offline_devices" -> (!userPreferences.hideOfflineDevices).toString()
            // halfsheet_notification represents "showing" the halfsheetnotification which is the
            // same as the "show" proto value.
            "halfsheet_notification" -> (userPreferences.showHalfsheetNotification).toString()
            else -> throw InvalidParameterException()
          }
    }
    Timber.d("getString returns [$value]")
    return value
  }

  override fun putBoolean(key: String?, value: Boolean) {
    Timber.d("putBoolean [$key] -> [$value]")
    runBlocking {
      when (key) {
        // codelab represents "showing" the codelab which is the inverse of the "hide" proto value.
        "codelab" -> {
          userPreferencesRepository.updateHideCodelabInfo(!value)
        }
        // offline_devices represents "showing" the offline devices which is the inverse of the
        // "hide" proto value.
        "offline_devices" -> {
          userPreferencesRepository.updateHideOfflineDevices(!value)
        }
        // halfsheet_notification represents "showing" the halfsheet notifications which is
        // the same as the "show" proto value.
        "halfsheet_notification" -> {
          userPreferencesRepository.updateShowHalfsheetNotification(value)
        }
        else -> {
          throw InvalidParameterException()
        }
      }
    }
  }

  override fun getBoolean(key: String?, defValue: Boolean): Boolean {
    var value: Boolean
    runBlocking {
      val job = async { userPreferencesRepository.getData() }
      val userPreferences = job.await()
      value =
          when (key) {
            // codelab represents "showing" the codelab which is the inverse of the "hide" proto
            // value.
            "codelab" -> !userPreferences.hideCodelabInfo
            // offline_devices represents "showing" the offline devices which is the inverse of the
            // "hide" proto value.
            "offline_devices" -> !userPreferences.hideOfflineDevices
            // halfsheet_notification represents "showing" the halfsheet notification
            // which is the same as the "show" proto value.
            "halfsheet_notification" -> userPreferences.showHalfsheetNotification
            // Not supported.
            else -> throw InvalidParameterException()
          }
    }
    Timber.d("getBoolean [$key] -> [$value]")
    return value
  }
}
