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

package com.google.homesampleapp.screens.commissionable

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.homesampleapp.MainActivity
import com.google.homesampleapp.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test to verify that Matter beacons are properly shown in CommissionableFragment.
 *
 * The test navigates to the "Commissionable devices" screen, and then each beacon producer (BLE,
 * mDNS, Wi-Fi) emits a beacon at a specific interval. Simply visually inspect that all these
 * beacons are properly shown on the screen (e.g. proper icon, inactive mDNS services shows with
 * different color, etc).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class CommissionableFragmentTest {

  private val DEVELOPER_UTILITIES = "Developer utilities"
  private val COMMISSIONABLE_DEVICES = "Commissionable devices"

  @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
  @get:Rule var hiltRule = HiltAndroidRule(this)

  @Before
  fun init() {
    // Hilt injection.
    hiltRule.inject()
  }

  // ---------------------------------------------------------------------------
  // Tests support functions

  /**
   * When the app starts, it may or may not display the codelab dialog depending on the state it was
   * in the last time it was launched. Was unable to find a way to force the preferences datastore
   * to be cleared prior to the test being run. Having the code below:
   * ```
   * @Before fun init() {
   *   scope.launch {
   *     userPreferencesRepository.clearData()
   *   }
   * }
   * ```
   *
   * does not complete prior to the start of the test.
   *
   * So for now, if we fail clicking on "OK" this means the codelab dialog is not shown and we
   * simply ignore the exception.
   *
   * TODO: If someone knows of a clean way to force a clear of the preferences datastore before the
   *   test runs, please submit a PR!
   */
  private fun clickOkOnCodelabDialog() {
    try {
      onView(withText("OK")).perform(click())
    } catch (e: Throwable) {
      // The Codelab dialog was not shown. Simply ignore the error.
      System.out.println("*** Codelab Dialog was not shown.")
    }
  }

  // ---------------------------------------------------------------------------
  // Tests

  @Test
  fun testFragmentBehavior() {
    // Click on "OK" of the codelab dialog.
    // In its own function because the dialog may, or may not be shown and lots of comments
    // associated with that :-).
    clickOkOnCodelabDialog()
    // Click on "Settings"
    onView(withId(R.id.settings)).perform(click())
    // Click on "Developer utilities"
    onView(ViewMatchers.withText(DEVELOPER_UTILITIES)).perform(click())
    // Click on "Commissionable devices"
    onView(ViewMatchers.withText(COMMISSIONABLE_DEVICES)).perform(click())
    // Let the producers run for 60 seconds, giving the user a chance to verify that all beacons
    // are properly shown on screen.
    Thread.sleep(60000)
  }
}
