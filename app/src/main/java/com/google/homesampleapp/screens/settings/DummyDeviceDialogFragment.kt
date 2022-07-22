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

package com.google.homesampleapp.screens.settings

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.google.homesampleapp.DUMMY_PRODUCT_ID
import com.google.homesampleapp.DUMMY_VENDOR_ID
import com.google.homesampleapp.R
import timber.log.Timber

/**
 * DialogFragment used when adding a "dummy" device into the fabric. Useful to check how the UI
 * behaves. This device will obviously always be "offline" as there is no real device associated
 * with it.
 */
class DummyDeviceDialogFragment : DialogFragment() {

  // Use this instance of the interface to deliver action events.
  private lateinit var listener: DummyDeviceDialogListener

  /* The activity that creates an instance of this dialog fragment must
   * implement this interface in order to receive event callbacks.
   * Each method passes the DialogFragment in case the host needs to query it. */
  interface DummyDeviceDialogListener {
    fun onDialogPositiveClick(deviceType: String, isOnline: Boolean, isOn: Boolean)
  }

  // Override the Fragment.onAttach() method to instantiate the DummyDeviceDialogListener.
  // Note that context comes from MainActivity, so must use parentFragment.
  override fun onAttach(context: Context) {
    super.onAttach(context)
    listener =
        (parentFragment as? DummyDeviceDialogListener)
            ?: (context as? DummyDeviceDialogListener)
                ?: throw ClassCastException(
                "Container of DummyDeviceDialogFragment must implement DummyDeviceDialogListener")
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    // Inflate the DummyDeviceFragment that holds the content view of the dialog.
    val inflater = requireParentFragment().layoutInflater
    val view = inflater.inflate(R.layout.fragment_dummy_device, null)

    // TODO: Get last device id, increment, and use that in the display.
    // Name and Room
    val nameAndRoomTextView: TextView = view.findViewById(R.id.nameRoomTextView)
    nameAndRoomTextView.text = "[Test-?]  Room-?"

    // ID, VID, PID
    val idVidPidTextView: TextView = view.findViewById(R.id.idVidPidTextView)
    idVidPidTextView.text = "ID: ?  VID: $DUMMY_VENDOR_ID  PID: $DUMMY_PRODUCT_ID"

    // Device types currently supported.
    val items = listOf("Light", "Outlet")
    val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_list_item, items)
    val textField: TextInputLayout = view.findViewById(R.id.menu)
    (textField.editText as? AutoCompleteTextView)?.setAdapter(adapter)

    // Dynamic State
    val isOnlineSwitch: SwitchMaterial = view.findViewById(R.id.isOnline)
    val isOnSwitch: SwitchMaterial = view.findViewById(R.id.isOn)

    // Use the Builder class for convenient dialog construction
    val builder =
        AlertDialog.Builder(requireActivity())
            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            .setView(view)
            .setMessage(R.string.add_dummy_device)
            .setPositiveButton(R.string.create) { _, _ ->
              // Call host fragment with the data to add the device
              val deviceType = textField.editText?.text.toString()
              val isOnline = isOnlineSwitch.isChecked
              val isOn = isOnSwitch.isChecked
              Timber.d("deviceType [$deviceType] isOnline [$isOnline] isOn [$isOn]")
              listener.onDialogPositiveClick(deviceType, isOnline, isOn)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
              // User cancelled the dialog
              dialog?.cancel()
            }
    // Create the AlertDialog object and return it
    return builder.create()
  }
}
