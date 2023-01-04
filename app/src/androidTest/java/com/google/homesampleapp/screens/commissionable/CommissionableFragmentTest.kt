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
import androidx.test.espresso.matcher.ViewMatchers.withId
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

  fun navigateToDiscoverCommissionableDevices() {
    // Click the Settings icon.
    onView(withId(R.id.settings)).perform(click())
    onView(withId(R.id.nested_settings_fragment)).perform(click())
    // Click on "Developer utilities"
    onView(ViewMatchers.withText(DEVELOPER_UTILITIES)).perform(click())
    // Click on "Commissionable devices"
    onView(ViewMatchers.withText(COMMISSIONABLE_DEVICES)).perform(click())
  }

  // ---------------------------------------------------------------------------
  // Tests

  @Test
  fun testFragmentBehavior() {
    navigateToDiscoverCommissionableDevices()
    // Let the producers run for 10 seconds, giving the user a chance to verify that all beacons
    // are properly shown on screen.
    Thread.sleep(60000)
  }
}
