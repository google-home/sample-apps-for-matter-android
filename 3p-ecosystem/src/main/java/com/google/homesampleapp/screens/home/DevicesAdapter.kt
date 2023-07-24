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

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.homesampleapp.R

/**
 * Adapter for a device item in the devices list, which is backed by RecyclerView. The Adapter
 * creates ViewHolder objects as needed, and also sets the data for those views. The process of
 * associating views to their data is called binding. See DeviceViewHolder.
 *
 * ListAdapter is RecyclerView.Adapter base class for presenting List data in a RecyclerView,
 * including computing diffs between Lists on a background thread.
 */
class DevicesAdapter(
    private val itemClickListener: (DeviceUiModel) -> Unit,
    private val switchClickListener: (View, DeviceUiModel) -> Unit
) : ListAdapter<DeviceUiModel, DeviceViewHolder>(DEVICES_COMPARATOR) {

  /**
   * RecyclerView calls this method whenever it needs to create a new ViewHolder. The method creates
   * and initializes the ViewHolder and its associated View, but does not fill in the view's
   * contents — the ViewHolder has not yet been bound to specific data.
   */
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
    return DeviceViewHolder.create(parent)
  }

  /**
   * RecyclerView calls this method to associate a ViewHolder with data. The method fetches the
   * appropriate data and uses the data to fill in the view holder's layout. For example, if the
   * RecyclerView displays a list of names, the method might find the appropriate name in the list
   * and fill in the view holder's TextView widget.
   */
  override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
    val repoItem = getItem(position)
    if (repoItem != null) {
      holder.bind(repoItem)
      // TODO: this should be handled in onCreateViewHolder().
      // Set onClickListener for the item in the list.
      // See https://www.youtube.com/watch?v=GvLgWjPigmQ
      holder.itemView.setOnClickListener { itemClickListener(repoItem) }
      val onOffSwitch = holder.itemView.findViewById<SwitchMaterial>(R.id.onoff_switch)
      onOffSwitch.setOnClickListener { switchClickListener(holder.itemView, repoItem) }
    }
  }

  /**
   * DiffUtil is a utility class that calculates the difference between two lists and outputs a list
   * of update operations that converts the first list into the second one. It is used here to
   * calculate updates for the RecyclerView Adapter.
   */
  companion object {
    private val DEVICES_COMPARATOR =
        object : DiffUtil.ItemCallback<DeviceUiModel>() {
          override fun areItemsTheSame(oldItem: DeviceUiModel, newItem: DeviceUiModel): Boolean =
              oldItem == newItem

          override fun areContentsTheSame(oldItem: DeviceUiModel, newItem: DeviceUiModel): Boolean =
              oldItem == newItem
        }
  }
}
