/*
 * Copyright 2024 Google LLC
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel which provides a unified view into nearby [MatterBeacon]s as provided by all bound
 * [MatterBeaconProducer]s in the dependency injection graph.
 */
@HiltViewModel
class CommissionableViewModel
@Inject
constructor(producers: Set<@JvmSuppressWildcards MatterBeaconProducer>) : ViewModel() {
  /**
   * Provides a [Flow] representing a live [Set] of nearby [MatterBeacon]s. The set of items will be
   * amended as more beacons are detected, so can be observed to see the most recently discovered
   * view.
   */
  private val beaconsFlow: Flow<Set<MatterBeacon>> =
    merge(*producers.map { it.getBeaconsFlow() }.toTypedArray())
      .runningFold(setOf<MatterBeacon>()) { set, item -> set + item }
      .stateIn(scope = viewModelScope, started = WhileSubscribed(2000), initialValue = emptySet())

  val beaconsLiveData = beaconsFlow.asLiveData()
}
