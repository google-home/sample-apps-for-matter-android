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

package com.google.homesampleapp.screens.commissionable

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.homesampleapp.*
import com.google.homesampleapp.databinding.MatterBeaconViewItemBinding
import timber.log.Timber

/**
 * ViewHolder for a MatterBeacon item in the discovered commissionable devices list, which is backed
 * by RecyclerView. The ViewHolder is a wrapper around a View that contains the layout for an
 * individual item in the list.
 *
 * When the view holder is created, it doesn't have any data associated with it. After the view
 * holder is created, the RecyclerView binds it to its data. The RecyclerView requests those views,
 * and binds the views to their data, by calling methods in the adapter (see MatterBeaconsAdapter).
 */
class MatterBeaconViewHolder(private val binding: MatterBeaconViewItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

  private val addressText: TextView = binding.address
  private val detailsText: TextView = binding.detail
  private val iconView: ImageView = binding.icon

  /** Binds the MatterBeacon to the UI element (beacon_view_item.xml) that holds the beacon view. */
  fun bind(beacon: MatterBeacon) {
    Timber.d("binding beacon [${beacon}]")

    val icon =
        when (beacon.transport) {
          is Transport.Ble -> R.drawable.quantum_gm_ic_bluetooth_vd_theme_24
          is Transport.Hotspot -> R.drawable.quantum_gm_ic_wifi_vd_theme_24
          is Transport.Mdns -> R.drawable.quantum_gm_ic_router_vd_theme_24
        }

    if (beacon.transport is Transport.Mdns) {
      val active = beacon.transport.active
      if (!active) {
        addressText.setTextColor(Color.RED)
        addressText.text = "[OFF] " + beacon.name
      } else {
        addressText.setTextColor(Color.BLACK)
        addressText.text = beacon.name
      }
    } else {
      addressText.setTextColor(Color.BLACK)
      addressText.text = beacon.name
    }

    itemView.setTag(R.id.beacon_tag, beacon)
    iconView.setImageResource(icon)
    // addressText.text = beacon.name
    detailsText.text =
        detailsText.context.getString(
            R.string.beacon_detail_text, beacon.vendorId, beacon.productId, beacon.discriminator)
  }

  companion object {
    fun create(parent: ViewGroup): MatterBeaconViewHolder {
      val view =
          LayoutInflater.from(parent.context)
              .inflate(R.layout.matter_beacon_view_item, parent, false)
      val binding = MatterBeaconViewItemBinding.bind(view)
      return MatterBeaconViewHolder(binding)
    }
  }
}
