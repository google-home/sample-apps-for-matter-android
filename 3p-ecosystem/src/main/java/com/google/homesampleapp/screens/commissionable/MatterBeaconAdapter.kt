/*
 * Copyright 2023 Google LLC
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

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.google.homesampleapp.R

/**
 * Adapter for a MatterBeacon item in the discovery list, which is backed by RecyclerView. The
 * Adapter creates ViewHolder objects as needed, and also sets the data for those views. The process
 * of associating views to their data is called binding. See MatterBeaconViewHolder.
 *
 * ListAdapter is a RecyclerView.Adapter base class for presenting List data in a RecyclerView,
 * including computing diffs between Lists on a background thread.
 */
class MatterBeaconAdapter() :
    ListAdapter<MatterBeacon, MatterBeaconViewHolder>(MATTER_BEACON_COMPARATOR) {

  private val delegatingListener =
      View.OnClickListener {
        onBeaconClickedListener?.onBeaconClicked(it.getTag(R.id.beacon_tag) as MatterBeacon)
      }

  /** A listener to be notified when a user clicks a beacon in the list. */
  var onBeaconClickedListener: OnBeaconClickedListener? = null

  /** Listener interface for list item clicks. */
  fun interface OnBeaconClickedListener {
    /** Invoked when the user clicks on a beacon in the list. */
    fun onBeaconClicked(beacon: MatterBeacon)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatterBeaconViewHolder {
    return MatterBeaconViewHolder.create(parent).apply {
      itemView.setOnClickListener(delegatingListener)
    }
  }

  override fun onBindViewHolder(holder: MatterBeaconViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  companion object {
    private val MATTER_BEACON_COMPARATOR =
        object : DiffUtil.ItemCallback<MatterBeacon>() {
          override fun areItemsTheSame(oldItem: MatterBeacon, newItem: MatterBeacon): Boolean {
            val retVal = oldItem.name == newItem.name
            return retVal
          }

          override fun areContentsTheSame(oldItem: MatterBeacon, newItem: MatterBeacon): Boolean {
            val retVal = oldItem == newItem
            return retVal
          }
        }
  }
}
