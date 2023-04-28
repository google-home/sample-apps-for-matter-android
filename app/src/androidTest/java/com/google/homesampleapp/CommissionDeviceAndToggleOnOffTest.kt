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

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-To-End (E2E) test to verify the commissioning of a Matter device and toggle its on/off state.
 *
 * Preconditions:
 * - A Matter device should be in a commissionable state
 * - If reusing the same device over and over to test, make sure to do the following
 *
 *   <pre>
 *     Remove all devices on the local Android fabrics:
 *       Settings > Google > Devices & Sharing > Matter Devices
 *       Select "This Android device"
 *       For each of the devices in "Linked Matter devices"
 *         - select the device
 *         - click on "Remove device"
 *         - If device was still online
 *             - click on Unlink
 *           Otherwise
 *             - takes a while, but eventually a screen is shown and click on "remove"
 *   </pre>
 * - If GHSAFM has been fully reset
 *
 *   <pre>
 *     App info > Storage and cache > Clear storage
 *     Then do the following:
 *     - Launch the app
 *     - codelab dialog: click on "Don't show this again", and then OK
 *     - Settings > Developer Utilities > Commissionable devices
 *     - Click on "while using the app"
 *     - Click on "Allow"
 *     - exit the app
 *   </pre>
 * - TODO: test with devices that advertise in a variety of ways: ble, mdns, wifi
 * - TODO: Find a way to handle programmatically the cleanup that's described above.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class CommissionDeviceAndToggleOnOffTest {

  // Need to use UI Automator because we yield control to GPS activity for commissioning.
  private lateinit var device: UiDevice

  // The SETUP_CODE should match the one used for the Matter device
  private val SETUP_CODE_11 = "34970112332" // TODO: set as test parameter
  private val DEVICE_NAME = "Test Device"

  @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
  @get:Rule var hiltRule = HiltAndroidRule(this)

  @Inject lateinit var devicesRepository: DevicesRepository
  @Inject lateinit var devicesStateRepository: DevicesStateRepository

  private val scope = CoroutineScope(Dispatchers.Main)

  // ---------------------------------------------------------------------------
  // Initializations

  @Before
  fun init() {
    // Hilt injection.
    hiltRule.inject()

    // Initialize UiDevice instance.
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    // Reset the repositories
    scope.launch {
      devicesRepository.clearAllData()
      devicesStateRepository.clearAllData()
    }
  }

  // ---------------------------------------------------------------------------
  // Test

  @Test
  fun commissionDeviceAndVerifyOnOffToggle() {
    // Handle codelab dialog.
    HomeScreen.ensureCodelabDialogNotShown()

    // Verify that at least one device is commissionable.
    HomeScreen.navigateToSettingsScreen()
    SettingsScreen.selectDeveloperUtilities()
    DeveloperUtilitiesScreen.selectCommissionableDevices()
    CommissionableDevicesScreen.verifyAtLeastOneDeviceCommissionable()
    CommissionableDevicesScreen.navigateToHomeScreen()

    // Commission the device.
    HomeScreen.addDevice()
    GpsCommissioningQrCodeScreen.selectSetupWithoutQrCode(device)
    GpsCommissioningSetupCodeScreen.enterSetupCode(device, SETUP_CODE_11)
    // Commissioning is happening...
    GpsCommissioningDeviceConnectedScreen.waitUntilShown(device, 40)
    GpsCommissioningDeviceConnectedScreen.selectDone(device)
    HomeScreen.enterDeviceName(device, DEVICE_NAME)
    HomeScreen.verifyDeviceShown(DEVICE_NAME)

    // Toggle the on/off state of the device.
    // Good idea to visually check that the device's state is indeed toggled.
    Thread.sleep(2000)
    HomeScreen.toggleOnOffState()
    Thread.sleep(2000)
    HomeScreen.toggleOnOffState()
    Thread.sleep(2000)
    HomeScreen.toggleOnOffState()
    Thread.sleep(5000)
  }
}
