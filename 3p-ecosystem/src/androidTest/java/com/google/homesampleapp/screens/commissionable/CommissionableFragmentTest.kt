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

import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.homesampleapp.DeveloperUtilitiesScreen
import com.google.homesampleapp.HomeScreen
import com.google.homesampleapp.MainActivity
import com.google.homesampleapp.SettingsScreen
import com.google.homesampleapp.screens.commissionable.ble.MatterBeaconProducerBleFake
import com.google.homesampleapp.screens.commissionable.ble.ModuleBle
import com.google.homesampleapp.screens.commissionable.mdns.MatterBeaconProducerMdnsFake
import com.google.homesampleapp.screens.commissionable.wifi.MatterBeaconProducerWifiFake
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test to verify that Matter beacons are properly shown in CommissionableFragment.
 *
 * The test navigates to the "Commissionable devices" screen, and then each beacon producer (BLE,
 * mDNS, Wi-Fi) emits a beacon at a specific interval.
 *
 * Simply visually inspect that all these beacons are properly shown on the screen (e.g. proper
 * icon, inactive mDNS services shows with different color, etc).
 */

// See https://developer.android.com/training/dependency-injection/hilt-testing
@UninstallModules(ModuleBle::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class CommissionableFragmentTest {
  @Module
  @InstallIn(SingletonComponent::class)
  internal abstract class ModuleBleTest {
    @Singleton
    @Binds
    @IntoSet
    abstract fun bindsMatterBeaconProducer(
        fakeBeacon: MatterBeaconProducerBleFake
    ): MatterBeaconProducer
  }

  @Module
  @InstallIn(SingletonComponent::class)
  internal abstract class ModuleMdnsTest {
    @Singleton
    @Binds
    @IntoSet
    abstract fun bindsMatterBeaconProducer(
        fakeBeacon: MatterBeaconProducerMdnsFake
    ): MatterBeaconProducer
  }

  @Module
  @InstallIn(SingletonComponent::class)
  internal abstract class ModuleWifiTest {
    @Singleton
    @Binds
    @IntoSet
    abstract fun bindsMatterBeaconProducer(
        fakeBeacon: MatterBeaconProducerWifiFake
    ): MatterBeaconProducer
  }

  @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
  @get:Rule var hiltRule = HiltAndroidRule(this)

  @Before
  fun init() {
    // Hilt injection.
    hiltRule.inject()
  }

  // ---------------------------------------------------------------------------
  // Test

  @Test
  fun testFragmentBehavior() {
    // Handle codelab dialog.
    HomeScreen.ensureCodelabDialogNotShown()

    // Navigate to "Commissionable devices" screen.
    HomeScreen.navigateToSettingsScreen()
    SettingsScreen.selectDeveloperUtilities()
    DeveloperUtilitiesScreen.selectCommissionableDevices()

    // Let the producers run for 60 seconds, giving the user a chance to verify that all beacons
    // are properly shown on screen.
    Thread.sleep(60000)
  }
}
