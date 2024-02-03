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

package com.google.homesampleapp

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
) : ViewModel() {
  // TODO: Tried to support updating the shared Scaffold TopAppBar title
  // via a shared AppViewModel. Did not work. Revisit eventually, if this
  // makes the code cleaner to do it this way instead.
  private var _topAppBarTitle = MutableStateFlow("Sample App")
  val topAppBarTitle: StateFlow<String> = _topAppBarTitle.asStateFlow()

  fun setAppBarTitle(title: String) {
    _topAppBarTitle.value = title
  }
}