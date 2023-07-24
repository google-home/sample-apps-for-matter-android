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

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/** Google Home Sample Application for Matter (GHSAFM) */
@HiltAndroidApp
class GHSAFM3pEcoApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    // Setup Timber for logging.
    Timber.plant(
        object : Timber.DebugTree() {
          // Override [log] to add a "global prefix" prefix to the tag.
          override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            super.log(priority, "${APP_NAME}-$tag", message, t)
          }

          // Override [createStackElementTag] to include additional information to the tag.
          // (e.g. a "method name" to the tag).
          /**
           * Not enabled for now, but leaving here since it may be useful when debugging. override
           * fun createStackElementTag(element: StackTraceElement): String { return String.format(
           * "%s:%s", super.createStackElementTag(element), element.methodName, ) }
           */
        })
  }
}
