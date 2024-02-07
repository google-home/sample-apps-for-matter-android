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

package com.google.homesampleapp.screens.commissionable.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import com.google.homesampleapp.screens.commissionable.MatterBeaconInject
import com.google.homesampleapp.screens.commissionable.MatterBeaconProducer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * If this is instantiated, then all permissions have been cleared and Bluetooth is enabled. See
 * [SettingsDeveloperUtilitiesNestedFragment].
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModuleBle {
  @Binds
  @IntoSet
  abstract fun bindsMatterBeaconProducer(impl: MatterBeaconProducerBle): MatterBeaconProducer

  companion object {
    @Provides
    @MatterBeaconInject
    fun providesBluetoothLeScanner(@ApplicationContext context: Context): BluetoothLeScanner? {
      return BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner
    }
  }
}
