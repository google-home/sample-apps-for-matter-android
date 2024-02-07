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

package com.google.homesampleapp.screens.commissionable.wifi

import android.content.Context
import android.net.wifi.WifiManager
import com.google.homesampleapp.screens.commissionable.MatterBeaconInject
import com.google.homesampleapp.screens.commissionable.MatterBeaconProducer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModuleWifi {
  @Binds
  @IntoSet
  abstract fun bindsMatterBeaconProducer(impl: MatterBeaconProducerWifi): MatterBeaconProducer

  companion object {
    @Provides
    @MatterBeaconInject
    fun providesWifiManager(@ApplicationContext context: Context): WifiManager {
      return context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
  }
}
