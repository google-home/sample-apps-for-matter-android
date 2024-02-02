package com.google.homesampleapp

import androidx.lifecycle.ViewModel
import com.google.homesampleapp.screens.common.DialogInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
) : ViewModel() {
  // The title in the TopAppBar
  private var _topAppBarTitle = MutableStateFlow<String>("Sample App")
  val topAppBarTitle: StateFlow<String> = _topAppBarTitle.asStateFlow()

  fun setAppBarTitle(title: String) {
    _topAppBarTitle.value = title
  }
}