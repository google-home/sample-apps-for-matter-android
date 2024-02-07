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

package com.google.homesampleapp.screens.home

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.homesampleapp.Device
import com.google.homesampleapp.DeviceScreen
import com.google.homesampleapp.HomeScreen
import com.google.homesampleapp.MainActivity
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.getTimestampForNow
import com.google.homesampleapp.navigateBack
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
 * Test to verify that devices are properly shown in HomeFragment.
 *
 * The test navigates to the "Home" screen, and then a variety of devices are added. Simply visually
 * inspect that all these devices are properly shown on the screen (e.g. proper icon, state).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class HomeFragmentRecyclerViewTest {

  @Inject lateinit var devicesRepository: DevicesRepository
  @Inject lateinit var devicesStateRepository: DevicesStateRepository

  @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
  @get:Rule var hiltRule = HiltAndroidRule(this)

  private val scope = CoroutineScope(Dispatchers.Main)

  // Test device creation

  @Before
  fun init() {
    // Hilt injection.
    hiltRule.inject()
    // Reset the repositories
    scope.launch {
      devicesRepository.clearAllData()
      devicesStateRepository.clearAllData()
    }
  }

  // ---------------------------------------------------------------------------
  // Test

  data class TestDevice(
      val vendorId: String,
      val productId: String,
      val deviceType: String,
      val isOnline: Boolean,
      val isOn: Boolean
  ) {
    fun getName(deviceId: Long): String {
      val testDeviceNamePrefix = "[Test-"
      val testDeviceNameSuffix = "]"
      return testDeviceNamePrefix + deviceId + testDeviceNameSuffix
    }

    fun getRoom(deviceId: Long): String {
      val testDeviceRoomPrefix = "Room-"
      return testDeviceRoomPrefix + deviceId
    }
  }

  private fun addDevice(testDevice: TestDevice) {
    val timestamp = getTimestampForNow()
    val deviceType =
        when (testDevice.deviceType) {
          "Light" -> Device.DeviceType.TYPE_LIGHT
          "Outlet" -> Device.DeviceType.TYPE_OUTLET
          else -> Device.DeviceType.TYPE_UNSPECIFIED
        }
    scope.launch {
      val deviceId = devicesRepository.incrementAndReturnLastDeviceId()
      val device =
          Device.newBuilder()
              .setDateCommissioned(timestamp)
              .setVendorId(testDevice.vendorId)
              .setProductId(testDevice.productId)
              .setDeviceType(deviceType)
              .setDeviceId(deviceId)
              .setName(testDevice.getName(deviceId))
              .setRoom(testDevice.getRoom(deviceId))
              .build()
      val deviceUiModel = DeviceUiModel(device, testDevice.isOnline, testDevice.isOn)
      // Add the device to the repository.
      devicesRepository.addDevice(deviceUiModel.device)
      devicesStateRepository.addDeviceState(
          deviceUiModel.device.deviceId, deviceUiModel.isOnline, deviceUiModel.isOn)
    }
  }

  private val TEST_DEVICES =
      listOf(
          TestDevice("1", "11", "Light", false, false),
          TestDevice("2", "22", "Light", false, true),
          TestDevice("3", "33", "Light", true, false),
          TestDevice("4", "44", "Light", true, true),
          TestDevice("5", "55", "Outlet", false, false),
          TestDevice("6", "66", "Outlet", false, true),
          TestDevice("7", "77", "Outlet", true, false),
          TestDevice("8", "88", "Outlet", true, true),
          TestDevice("9", "99", "Fan", false, false),
          TestDevice("10", "1010", "Fan", false, true),
          TestDevice("11", "1111", "Fan", true, false),
          TestDevice("12", "1212", "Fan", true, true),
      )

  @Test
  fun testRecyclerView() {
    // Handle codelab dialog.
    HomeScreen.ensureCodelabDialogNotShown()

    var count = 0
    TEST_DEVICES.forEach { device ->
      count++
      addDevice(device)
      Thread.sleep(1000)
      HomeScreen.selectDevice(count)
      DeviceScreen.verifyDevice(count, device)
      Thread.sleep(1000)
      navigateBack()
    }
    // Give 30s to review the screen and play with it.
    Thread.sleep(10000)
  }
}
