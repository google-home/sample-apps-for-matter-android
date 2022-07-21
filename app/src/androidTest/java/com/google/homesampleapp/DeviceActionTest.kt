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
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@LargeTest
class DeviceActionTest {

    private val TEN_SECONDS = Duration.ofSeconds(10).toMillis()
    private val SCAN_QR_CODE_TITLE = By.text("Scan the QR code")
    private val TRY_WITH_SETUP_CODE_BUTTON = By.text("Try with setup code")
    private val ENTER_SETUP_CODE_TITLE = By.textContains("Enter setup code")
    private val SETUP_CODE_TEXTBOX = UiSelector().className("android.widget.EditText").instance(0)
    private val SETUP_CODE = "749701123365521327687"
    private val NEXT_BUTTON = By.text("Next")
    private val CONNECT_ACCOUNT_TITLE = By.text("Connect .* your Google Account".toPattern())
    private val AGREE_BUTTON = By.text("I Agree")
    private lateinit var device: UiDevice

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun init() {
        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    fun triggerScanForQRCode() {
        // Verify Add Device button is displayed.
        onView(withId(R.id.addDeviceButton)).check(matches(isDisplayed()))
        // Click the Add Device button.
        onView(withId(R.id.addDeviceButton)).perform(click())
        // Verify the Google play services screen is displayed
        // to scan the QR code.
        assertNotNull(device.wait(Until.hasObject(SCAN_QR_CODE_TITLE), TEN_SECONDS))
        assertNotNull(device.wait(Until.hasObject(TRY_WITH_SETUP_CODE_BUTTON), TEN_SECONDS))
        // Click on Try with setup code option.
        device.findObject(TRY_WITH_SETUP_CODE_BUTTON).click()
    }

    fun enterSetupCode() {
        // Verify the enter setup code screen.
        assertNotNull(device.wait(Until.hasObject(ENTER_SETUP_CODE_TITLE), TEN_SECONDS))
        // Enter the setup code.
        device.findObject(SETUP_CODE_TEXTBOX).setText(SETUP_CODE)
        // Click the next button
        val nextButton: UiObject2 = device.findObject(NEXT_BUTTON)
        assertNotNull(nextButton.wait(Until.clickable(true), TEN_SECONDS))
        nextButton.click()
    }

    fun clickIAgree() {
        // Verify the connect to your Google Account screen.
        assertNotNull(device.wait(Until.hasObject(CONNECT_ACCOUNT_TITLE), TEN_SECONDS))
        // Click on "I agree"
        device.wait(Until.findObject(AGREE_BUTTON), TEN_SECONDS).click()
    }

    @Test
    fun addDevice() {
        triggerScanForQRCode()
        enterSetupCode()
        clickIAgree()
    }
}