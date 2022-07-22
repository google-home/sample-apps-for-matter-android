package com.google.homesampleapp

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class DeviceActionTest {

    private lateinit var device: UiDevice

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun init() {
        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun addDeviceButton_shouldTriggerScanForQRCode() {
        // Verify Add Device button is displayed.
        onView(withId(R.id.addDeviceButton)).check(matches(isDisplayed()))
        // Click the Add Device button.
        onView(withId(R.id.addDeviceButton)).perform(click())
        // Verify the Google play services screen is displayed
        // to scan the QR code.
        val scanQRCodeItem: UiObject = device.findObject(
            UiSelector().text("Scan the QR code")
        )
        assertEquals(true, scanQRCodeItem.exists());
    }
}