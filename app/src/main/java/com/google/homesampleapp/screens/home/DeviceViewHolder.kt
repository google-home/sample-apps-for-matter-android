/*
 * Copyright 2020 The Android Open Source Project
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

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.homesampleapp.*
import com.google.homesampleapp.databinding.DeviceViewItemBinding
import timber.log.Timber

/**
 * ViewHolder for a device item in the devices list, which is backed by RecyclerView. The ViewHolder
 * is a wrapper around a View that contains the layout for an individual item in the list.
 *
 * When the view holder is created, it doesn't have any data associated with it. After the view
 * holder is created, the RecyclerView binds it to its data. The RecyclerView requests those views,
 * and binds the views to their data, by calling methods in the adapter (see DevicesAdapter).
 */
class DeviceViewHolder(private val binding: DeviceViewItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

  // TODO: Can't this be cached somewhere?
  private val lightBulbIcon = getResourceId("quantum_gm_ic_lights_gha_vd_theme_24")
  private val outletIcon = getResourceId("ic_baseline_outlet_24")
  private val unknownDeviceIcon = getResourceId("ic_baseline_device_unknown_24")
  private val shapeOffDrawable = getDrawable("device_item_shape_off")
  private val shapeOnDrawable = getDrawable("device_item_shape_on")

  /**
   * Binds the Device (DeviceUiModel, the model class) to the UI element (device_view_item.xml) that
   * holds the Device view.
   *
   * TODO: change icon
   * https://stackoverflow.com/questions/16906528/change-image-of-imageview-programmatically-in-android
   */
  fun bind(deviceUiModel: DeviceUiModel) {
    Timber.d("binding device [${deviceUiModel}]")

    val iconResourceId =
        when (deviceUiModel.device.deviceType) {
          Device.DeviceType.TYPE_LIGHT -> lightBulbIcon
          Device.DeviceType.TYPE_OUTLET -> outletIcon
          else -> unknownDeviceIcon
        }
    val shapeDrawable =
        if (deviceUiModel.isOnline && deviceUiModel.isOn) shapeOnDrawable else shapeOffDrawable
    binding.zeicon.setImageResource(iconResourceId)
    binding.name.text = deviceUiModel.device.name
    binding.zestate.text = stateDisplayString(deviceUiModel.isOnline, deviceUiModel.isOn)
    binding.rootLayout.background = shapeDrawable
    if (ON_OFF_SWITCH_DISABLED_WHEN_DEVICE_OFFLINE) {
      binding.onoffSwitch.isEnabled = deviceUiModel.isOnline
    } else {
      binding.onoffSwitch.isEnabled = true
    }
    binding.onoffSwitch.isChecked = deviceUiModel.isOn
  }

  private fun getDrawable(name: String): Drawable? {
    val resources: Resources = itemView.context.resources
    val resourceId = resources.getIdentifier(name, "drawable", itemView.context.packageName)
    return resources.getDrawable(resourceId)
  }

  private fun getResourceId(name: String): Int {
    val resources: Resources = itemView.context.resources
    return resources.getIdentifier(name, "drawable", itemView.context.packageName)
  }

  companion object {
    fun create(parent: ViewGroup): DeviceViewHolder {
      val view =
          LayoutInflater.from(parent.context).inflate(R.layout.device_view_item, parent, false)
      val binding = DeviceViewItemBinding.bind(view)
      return DeviceViewHolder(binding)
    }
  }
}
