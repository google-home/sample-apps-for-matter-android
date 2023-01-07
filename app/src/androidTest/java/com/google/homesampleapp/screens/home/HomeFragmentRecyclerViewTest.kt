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

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.homesampleapp.Device
import com.google.homesampleapp.MainActivity
import com.google.homesampleapp.R
import com.google.homesampleapp.data.DevicesRepository
import com.google.homesampleapp.data.DevicesStateRepository
import com.google.homesampleapp.getTimestampForNow
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
 * The test navigates to the "Home" screen, and then a variety of devices is added. Simply visually
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

  val scope = CoroutineScope(Dispatchers.Main)

  // Test device creation
  val TEST_DEVICE_NAME_PREFIX = "[Test-"
  val TEST_DEVICE_NAME_SUFFIX = "]"
  val TEST_DEVICE_ROOM_PREFIX = "Room-"

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
  // Tests support functions

  fun navigateToHomeScreen() {
    // Nothing to do. When app is launched, we are initially at the Home screen.
  }

  fun navigateBack() {
    pressBack()
  }

  fun addDevice(testDevice: TestDevice) {
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
              .setName(TEST_DEVICE_NAME_PREFIX + deviceId + TEST_DEVICE_NAME_SUFFIX)
              .setRoom(TEST_DEVICE_ROOM_PREFIX + deviceId)
              .build()
      val deviceUiModel = DeviceUiModel(device, testDevice.isOnline, testDevice.isOn)
      // Add the device to the repository.
      devicesRepository.addDevice(deviceUiModel.device)
      devicesStateRepository.addDeviceState(
          deviceUiModel.device.deviceId, deviceUiModel.isOnline, deviceUiModel.isOn)
    }
  }

  private fun verifyListCount(count: Int) {
    // FIXME: not sure how to do that.
  }

  private fun clickOnDevice(count: Int, device: TestDevice) {
    onView(ViewMatchers.withId(R.id.devicesListRecyclerView))
        .perform(RecyclerViewActions.actionOnItemAtPosition<DeviceViewHolder>(count - 1, click()))
  }

  private fun verifyDeviceOnDeviceScreen(device: TestDevice) {}

  // ---------------------------------------------------------------------------
  // Tests

  data class TestDevice(
      val vendorId: String,
      val productId: String,
      val deviceType: String,
      val isOnline: Boolean,
      val isOn: Boolean
  )

  private val TEST_DEVICES =
      listOf<TestDevice>(
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
    var count = 0
    navigateToHomeScreen()
    Thread.sleep(3000)
    TEST_DEVICES.forEach { device ->
      count++
      addDevice(device)
      verifyListCount(count)
      Thread.sleep(3000)
      clickOnDevice(count, device)
      verifyDeviceOnDeviceScreen(device)
      Thread.sleep(2000)
      navigateBack()
    }
    // Give 30s to review the screen and play with it.
    Thread.sleep(30000)
  }
}
