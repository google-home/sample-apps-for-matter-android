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

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.google.homesampleapp.Device.DeviceType
import com.google.protobuf.Timestamp
import java.io.File
import java.lang.Long.max
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import timber.log.Timber

// -------------------------------------------------------------------------------------------------
// Various constants

// Not using lateinit so Timber can be used with Compose previews
var VERSION_NAME = "v?.?"
var APP_NAME = "APP_NAME"

// -------------------------------------------------------------------------------------------------
// Display helper functions

/** Enumeration of statuses for an asynchronous [com.google.android.gms.tasks.Task]. */
sealed class TaskStatus {
  /** The task has not been started. */
  object NotStarted : TaskStatus()

  /** The task has been started, and has not yet completed with a result. */
  object InProgress : TaskStatus()

  /**
   * The task completed with an exception.
   *
   * @param cause the cause of the failure
   */
  class Failed(val message: String, val cause: Throwable?) : TaskStatus()

  /**
   * The task completed successfully.
   *
   * @param statusMessage a message to be displayed in the UI
   */
  class Completed(val statusMessage: String) : TaskStatus()
}

/** Enumeration of actions to take a background work alert dialog. */
sealed class BackgroundWorkAlertDialogAction {
  /** Background work has started, show the dialog. */
  class Show(val title: String, val message: String) : BackgroundWorkAlertDialogAction()

  /** Background work has completed, hide the dialog. */
  object Hide : BackgroundWorkAlertDialogAction()
}

/** Useful when investigating lifecycle events in logcat. */
fun lifeCycleEvent(event: String): String {
  return "[*** LifeCycle ***] $event"
}

// -----------------------------------------------------------------------------
// Matter Device Type Display String

/** Set the strings for DeviceType. */
lateinit var DeviceTypeStrings: MutableMap<DeviceType, String>

fun setDeviceTypeStrings(unspecified: String, light: String, outlet: String, unknown: String) {
  DeviceTypeStrings =
    mutableMapOf(
      DeviceType.TYPE_UNSPECIFIED to unspecified,
      DeviceType.TYPE_LIGHT to light,
      DeviceType.TYPE_OUTLET to outlet,
      DeviceType.TYPE_UNKNOWN to unknown,
    )
}

/** Converts the Device.DeviceType enum to a string used in the UI. */
fun DeviceType.displayString(): String {
  return DeviceTypeStrings[this]!!
}

// -----------------------------------------------------------------------------
// Matter Device Type Display Icon


fun getDeviceTypeIconId(deviceType: DeviceType) : Int {
  return when (deviceType) {
    DeviceType.TYPE_UNSPECIFIED -> R.drawable.ic_baseline_device_unknown_24
    DeviceType.TYPE_UNKNOWN -> R.drawable.ic_baseline_device_unknown_24
    DeviceType.TYPE_LIGHT -> R.drawable.quantum_gm_ic_lights_gha_vd_theme_24
    DeviceType.TYPE_OUTLET -> R.drawable.ic_baseline_outlet_24
    DeviceType.TYPE_DIMMABLE_LIGHT -> R.drawable.quantum_gm_ic_lights_gha_vd_theme_24
    DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT -> R.drawable.quantum_gm_ic_lights_gha_vd_theme_24
    DeviceType.TYPE_EXTENDED_COLOR_LIGHT -> R.drawable.quantum_gm_ic_lights_gha_vd_theme_24
    DeviceType.TYPE_LIGHT_SWITCH -> R.drawable.quantum_gm_ic_lights_gha_vd_theme_24
    DeviceType.UNRECOGNIZED -> R.drawable.ic_baseline_device_unknown_24
  }
}

fun getDeviceTypeDisplayStringId(deviceType: DeviceType) : Int {
  return when (deviceType) {
    DeviceType.TYPE_UNSPECIFIED -> R.string.device_type_unspecified
    DeviceType.TYPE_UNKNOWN -> R.string.device_type_unknown
    DeviceType.TYPE_LIGHT -> R.string.device_type_light
    DeviceType.TYPE_OUTLET -> R.string.device_type_outlet
    DeviceType.TYPE_DIMMABLE_LIGHT -> R.string.device_type_dimmable_light
    DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT -> R.string.device_type_color_temperature_light
    DeviceType.TYPE_EXTENDED_COLOR_LIGHT -> R.string.device_type_extended_color_light
    DeviceType.TYPE_LIGHT_SWITCH -> R.string.device_type_light_switch
    DeviceType.UNRECOGNIZED -> R.string.device_type_unrecognized
  }
}

// -----------------------------------------------------------------------------
// Misc

fun convertToAppDeviceType(matterDeviceType: Long): DeviceType {
  return when (matterDeviceType) {
    256L -> DeviceType.TYPE_LIGHT // 0x0100 On/Off Light
    257L -> DeviceType.TYPE_DIMMABLE_LIGHT // 0x0101 Dimmable Light
    259L -> DeviceType.TYPE_LIGHT_SWITCH // 0x0103 On/Off Light Switch
    266L -> DeviceType.TYPE_OUTLET // 0x010A (On/Off Plug-in Unit)
    268L -> DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT // 0x010C Color Temperature Light
    269L -> DeviceType.TYPE_EXTENDED_COLOR_LIGHT // 0x010D Extended Color Light
    else -> DeviceType.TYPE_UNKNOWN
  }
}

/** Converts the "isOnline" boolean into a proper string for the UI. */
fun isOnlineDisplayString(isOnline: Boolean): String {
  return if (isOnline) "Online" else "Offline"
}

/** Converts the "isOn" boolean into a proper string for the UI. */
fun isOnDisplayString(isOn: Boolean): String {
  return if (isOn) "ON" else "OFF"
}

/** Converts the combo of "isOnline" and "isOn" into a proper string for the UI. */
fun stateDisplayString(isOnline: Boolean, isOn: Boolean): String {
  return if (!isOnline) {
    "OFFLINE"
  } else {
    if (isOn) "ON" else "OFF"
  }
}

fun stringToBoolean(s: String): Boolean {
  val boolValue =
    when (s) {
      "true",
      "True",
      "TRUE" -> true
      else -> false
    }
  return boolValue
}

fun intentSenderToString(intentSender: IntentSender?): String {
  if (intentSender == null) {
    return "null"
  }
  return "creatorPackage [${intentSender.creatorPackage}]"
}

// -------------------------------------------------------------------------------------------------
// System helper functions

fun isMultiAdminCommissioning(intent: Intent): Boolean {
  return intent.action == "com.google.android.gms.home.matter.ACTION_COMMISSION_DEVICE"
}

/**
 * The Matter APIs make use of SharedPreferences. Useful to print what they are when the app starts.
 */
fun displayPreferences(context: Context) {
  val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
  if (prefsDir.exists() && prefsDir.isDirectory) {
    Timber.d("*** Preference Files ***")
    val list: Array<String> = prefsDir.list()
    for (element in list) {
      Timber.d("*** [${element}] ***")
      val sharedPreferencesFileKey = element.substringBefore(".xml")
      Timber.d("*** FileKey: [${sharedPreferencesFileKey}] ***")
      val sharedPreferences =
        context.getSharedPreferences(sharedPreferencesFileKey, Context.MODE_PRIVATE)
      val allPreferences = sharedPreferences.all
      for ((key, value) in allPreferences.entries) Timber.d("$key [$value]")
    }
    return
  } else {
    Timber.d("prefsDir does not exist: $prefsDir")
    return
  }
}

/** Returns a com.google.protobuf.Timestamp for the current time. */
fun getTimestampForNow(): Timestamp {
  val now = Instant.now()
  return Timestamp.newBuilder().setSeconds(now.epochSecond).setNanos(now.nano).build()
}

/**
 * Formats a com.google.protobuf.Timestamp according to the specified pattern. If _pattern is null,
 * then the default is "MM.dd.yy HH:mm:ss".
 */
private const val TIMESTAMP_DEFAULT_FORMAT_PATTERN = "MM.dd.yy HH:mm:ss"

fun formatTimestamp(timestamp: Timestamp, _pattern: String?): String {
  val pattern = _pattern ?: TIMESTAMP_DEFAULT_FORMAT_PATTERN
  val timestampFormatter = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.of("UTC"))
  return timestampFormatter.format(Instant.ofEpochSecond(timestamp.seconds))
}

/**
 * Used in the context of StateFlow with MutableLists to ensure changes to the mutable lists trigger
 * data changes for observers. See
 * https://stackoverflow.com/questions/70905480/mutablestateflow-not-working-with-mutablelist
 */
fun <T> MutableList<T>.mapButReplace(targetItem: T, newItem: T) = map {
  Timber.d("mapButReplace targetItem [${targetItem}] newItem [${newItem}]")
  if (it == targetItem) {
    Timber.d("setting newItem for [${it}] to [${newItem}]")
    newItem
  } else {
    Timber.d("setting newItem")
    it
  }
}

/** Generates a random number to be used as a device identifier during device commissioning */
fun generateNextDeviceId(): Long {
  val secureRandom =
    try {
      SecureRandom.getInstance("SHA1PRNG")
    } catch (ex: Exception) {
      Timber.w(ex, "Failed to instantiate SecureRandom with SHA1PRNG")
      // instantiate with the default algorithm
      SecureRandom()
    }

  return max(abs(secureRandom.nextLong()), 1)
}

/**
 * Strip the link-local portion of an IP Address. Was needed to handle
 * https://github.com/google-home/sample-app-for-matter-android/issues/15. For example:
 * ```
 *    "fe80::84b1:c2f6:b1b7:67d4%wlan0"
 * ```
 *
 * becomes
 *
 * ```
 *    ""fe80::84b1:c2f6:b1b7:67d4"
 * ```
 *
 * The "%wlan0" at the end of the link-local ip address is stripped.
 */
fun stripLinkLocalInIpAddress(ipAddress: String): String {
  return ipAddress.replace("%.*".toRegex(), "")
}

// -------------------------------------------------------------------------------------------------
// Constants

// -------------------------------------------------------------------------------------------------
// Constants used when creating devices on the app's fabric.

// Shared device creation
const val SHARED_DEVICE_NAME_PREFIX = "Shared-"
const val SHARED_DEVICE_NAME_SUFFIX = ""
const val SHARED_DEVICE_ROOM_PREFIX = "Room-"

// Temporary device name used when commissioning the device to the 3P fabric.
const val REAL_DEVICE_NAME_PREFIX = "Real-"

// -------------------------------------------------------------------------------------------------
// Dialogs

fun showAlertDialog(alertDialog: AlertDialog, title: String?, message: String?) {
  if (title != null) {
    alertDialog.setTitle(title)
  }
  if (message != null) {
    alertDialog.setMessage(message)
  }
  alertDialog.show()
}

// -------------------------------------------------------------------------------------------------
// Device Sharing constants

// How long a commissioning window for Device Sharing should be open.
const val OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS = 180

// Discriminator
const val DISCRIMINATOR = 123

// Iteration
const val ITERATION = 10000L

// Iteration
const val SETUP_PIN_CODE = 11223344L

// Minimum time required to handle the multi-admin commissioning
// intent just received.
const val MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS = 20

// -------------------------------------------------------------------------------------------------
// Constants to modify the behavior of the app.

// Whether the on/off switch is disabled when the device is offline.
const val ON_OFF_SWITCH_DISABLED_WHEN_DEVICE_OFFLINE = false

// ----- Periodic monitoring of device state changes -----

// Modes supported for monitoring state changes.
enum class StateChangesMonitoringMode {
  // Subscription is what should normally be used.
  Subscription,
  // Left for historical reasons when we had issues with Subscription.
  PeriodicRead
}

val STATE_CHANGES_MONITORING_MODE = StateChangesMonitoringMode.Subscription

// Intervals for PeriodicRead mode.
const val PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS = 10
const val PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS = 2

// ----- Device Sharing -----

// Whether DeviceSharing does commissioning with GPS.
// Alternative is using DNS-SD to discover the device and get its IP address, and then
// do the standard 3P commissioning.
const val DEVICE_SHARING_WITH_GPS = true

// Which API should be used for opening the commissioning window for DeviceSharing.
enum class OpenCommissioningWindowApi {
  ChipDeviceController,
  AdministratorCommissioningCluster
}

// Which method should be used to generate identifiers for devices being commissioned
enum class DeviceIdGenerator {
  Random,
  Incremental
}

/**
 * Indicates the status of a node's commissioning window. Useful in the context of "multi-admin"
 * when a temporary commissioning window must be open for a target commissioner. That's because
 * sometimes multi-admin may fail with the target commissioner (especially in a testing environment)
 * and the temporary commissioning window can then stay open for a substantial amount of time (e.g.
 * 3 minutes) preventing a new "multi-admin" to fail until that temporary commissioning window is
 * closed. Checking on the status of the commissioning window beforehand makes it possible to close
 * the currently open temporary commissioning window before trying to open a new one. [status] is
 * the enum value returned by reading the WindowStatusAttribute of the "Administrator Commissioning
 * Cluster". (See spec section "11.18.6.1. CommissioningWindowStatus enum").
 */
enum class CommissioningWindowStatus(val status: Int) {
  /** Commissioning window not open */
  WindowNotOpen(0),

  /** An Enhanced Commissioning Method window is open */
  EnhancedWindowOpen(1),

  /** A Basic Commissioning Method window is open */
  BasicWindowOpen(2)
}

val OPEN_COMMISSIONING_WINDOW_API = OpenCommissioningWindowApi.ChipDeviceController

/**
 * ToastTimber logs the same message on both Timber and Toast, thus giving some feedback to the user
 * that doesn't have ADB connected
 */
object ToastTimber {
  fun d(msg: String, activity: FragmentActivity) {
    Timber.d(msg)
    checkLooper()
    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
  }

  fun e(msg: String, activity: FragmentActivity) {
    Timber.e(msg)
    checkLooper()
    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
  }

  /**
   * Asserts Looper is running in the current thread. Important when using Timber in coroutine
   * Threads that don't have a Looper running
   */
  private fun checkLooper() {
    if (Looper.myLooper() == null) Looper.prepare()
  }
}