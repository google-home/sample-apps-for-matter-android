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

package com.google.homesampleapp

import org.junit.Assert.*
import org.junit.Test

class UtilsTest {
  @Test
  fun stripLinkLocalInIpAddress_ok() {
    val ipAddress = "fe80::84b1:c2f6:b1b7:67d4"
    val linkLocalIpAddress = ipAddress + "%wlan"
    assertEquals(ipAddress, stripLinkLocalInIpAddress(ipAddress))
    assertEquals(ipAddress, stripLinkLocalInIpAddress(linkLocalIpAddress))
  }
}
