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

package com.google.homesampleapp.mdns

import chip.platform.ChipMdnsCallback
import chip.platform.ServiceResolver
import com.google.android.gms.home.matter.discovery.DiscoveryClient
import com.google.android.gms.home.matter.discovery.ResolveServiceRequest
import javax.inject.Inject
import timber.log.Timber

/** mDNS service resolver that leverages the GMSCore mDNS discovery API. */
class GmsCoreServiceResolver @Inject constructor(private val client: DiscoveryClient) :
    ServiceResolver {
  override fun resolve(
      instanceName: String,
      serviceType: String,
      callbackHandle: Long,
      contextHandle: Long,
      callback: ChipMdnsCallback
  ) {
    client
        .resolveService(ResolveServiceRequest.create(serviceType, instanceName))
        .addOnSuccessListener { result ->
          val serviceInfo = result.serviceInfo
          Timber.d("GmsCoreServiceResolver:resolveService:success [${serviceInfo}]")
          Timber.d("txtRecords:")
          serviceInfo.txtRecords.forEach { txtRecord -> Timber.d(txtRecord.toString()) }

          callback.handleServiceResolve(
              serviceInfo.instanceName,
              serviceInfo.serviceType,
              "fixme-hostName", // TODO
              serviceInfo.primaryNetworkLocation.ipAddress.hostAddress,
              serviceInfo.primaryNetworkLocation.port,
              /* serviceInfo.txtRecords */ null, // TODO
              callbackHandle,
              contextHandle,
          )
        }
        .addOnFailureListener { ex ->
          Timber.d("GmsCoreServiceResolver:resolveService:failure [${ex}]")
          callback.handleServiceResolve(
              instanceName,
              serviceType,
              /* hostName */ null,
              /* ipAddress= */ null,
              /* port= */ 0,
              /* txtRecords */ null,
              callbackHandle,
              contextHandle,
          )
        }
  }

  override fun publish(
      p0: String?,
      p1: String?,
      p2: String?,
      p3: Int,
      p4: Array<out String>?,
      p5: Array<out ByteArray>?,
      p6: Array<out String>?
  ) {
    throw NotImplementedError("Our usage does not involve publishing services.")
  }

  override fun removeServices() {
    throw NotImplementedError("Our usage does not involve removing services.")
  }
}
