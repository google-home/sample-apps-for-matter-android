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

package com.google.homesampleapp.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.homesampleapp.DevicesState
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object DevicesStateSerializer : Serializer<DevicesState> {

  override val defaultValue: DevicesState = DevicesState.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): DevicesState {
    try {
      return DevicesState.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
      throw CorruptionException("Cannot read proto.", exception)
    }
  }

  override suspend fun writeTo(t: DevicesState, output: OutputStream) = t.writeTo(output)
}

val Context.devicesStateDataStore: DataStore<DevicesState> by
    dataStore(fileName = "devices_state__store.proto", serializer = DevicesStateSerializer)
