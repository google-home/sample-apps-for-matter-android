package com.google.homesampleapp.chip

import chip.devicecontroller.ChipIdLookup
import chip.devicecontroller.ReportCallback
import chip.devicecontroller.ResubscriptionAttemptCallback
import chip.devicecontroller.SubscriptionEstablishedCallback
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.ChipEventPath
import chip.devicecontroller.model.ChipPathId
import chip.devicecontroller.model.NodeState
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import timber.log.Timber

@Singleton
class SubscriptionHelper @Inject constructor(private val chipClient: ChipClient) {

  suspend fun awaitSubscribeToPeriodicUpdates(
      connectedDevicePtr: Long,
      subscriptionEstablishedCallback: SubscriptionEstablishedCallback,
      resubscriptionAttemptCallback: ResubscriptionAttemptCallback,
      reportCallback: ReportCallback
  ) {
    return suspendCoroutine { continuation ->
      Timber.d("subscribeToPeriodicUpdates()")
      val endpointId = ChipPathId.forWildcard()
      val clusterId = ChipPathId.forWildcard()
      val attributeId = ChipPathId.forWildcard()
      val minInterval = 1 // seconds
      val maxInterval = 10 // seconds
      val attributePath = ChipAttributePath.newInstance(endpointId, clusterId, attributeId)
      val eventPath = ChipEventPath.newInstance(endpointId, clusterId, attributeId)
      Timber.d("attributePath: [${attributePath}]")
      chipClient.chipDeviceController.subscribeToPath(
          subscriptionEstablishedCallback,
          resubscriptionAttemptCallback,
          reportCallback,
          connectedDevicePtr,
          listOf(attributePath),
          listOf(eventPath),
          minInterval,
          maxInterval,
          // keepSubscriptions
          // false: all existing or pending subscriptions on the publisher for this
          // subscriber SHALL be terminated.
          false,
          // isFabricFiltered
          // limits the data read within fabric-scoped lists to the accessing fabric.
          // Some things (such as the list of fabrics on the device) need to *not be*
          // fabric-scoped,
          // i.e. isFabricFiltered = false, to allow reading all values not just the one for the
          // current fabric
          false)
      continuation.resume(Unit)
    }
  }

  suspend fun awaitUnsubscribeToPeriodicUpdates(connectedDevicePtr: Long) {
    Timber.d("awaitUnsubscribeToPeriodicUpdates()")
    return suspendCoroutine { continuation ->
      chipClient.chipDeviceController.shutdownSubscriptions()
      continuation.resume(Unit)
    }
  }

  /** Endpoint [1] { Cluster [6] [OnOff] { [0] [OnOff] false } } */
  fun extractAttribute(
      nodeState: NodeState,
      endpointId: Int,
      clusterAttribute: MatterConstants.ClusterAttribute,
  ): Any? {
    nodeState.endpointStates.forEach { (_endpointId, endpointState) ->
      if (_endpointId != endpointId) return@forEach
      endpointState.clusterStates.forEach { (clusterId, clusterState) ->
        if (clusterId != clusterAttribute.clusterId) return@forEach
        clusterState.attributeStates.forEach { (attributeId, attributeState) ->
          if (attributeId != clusterAttribute.attributeId) return@forEach
          return attributeState.value
        }
      }
    }
    return null
  }

  open class ReportCallbackForDevice(val deviceId: Long) : ReportCallback {
    override fun onError(
        attributePath: ChipAttributePath?,
        eventPath: ChipEventPath?,
        ex: Exception
    ) {
      if (attributePath != null) {
        Timber.e(ex, "reportCallback: error on device [${deviceId}] for [${attributePath}]")
      }
      if (eventPath != null) {
        Timber.e(ex, "reportCallback: error on device [${deviceId}] for [${eventPath}]")
      }
    }

    override fun onReport(nodeState: NodeState) {
      //Timber.d("reportCallback: onReport")
      val debugString = nodeStateToDebugString(nodeState)
      //Timber.d("------- BEGIN REPORT -----")
      //Timber.d(debugString)
      //Timber.d("------- END REPORT -----")
    }

    override fun onDone() {
      Timber.d("reportCallback: onDone")
    }
  }

  open class SubscriptionEstablishedCallbackForDevice(val deviceId: Long) :
      SubscriptionEstablishedCallback {
    override fun onSubscriptionEstablished(subscriptionId: Long) {
      Timber.d("onSubscriptionEstablished(): subscriptionId [${subscriptionId}]")
    }
  }

  open class ResubscriptionAttemptCallbackForDevice(val deviceId: Long) :
      ResubscriptionAttemptCallback {
    override fun onResubscriptionAttempt(terminationCause: Int, nextResubscribeIntervalMsec: Int) {
      Timber.d(
          "onResubscriptionAttempt(): device [$deviceId] terminationCause [$terminationCause] nextResubscribeIntervalMsec [$nextResubscribeIntervalMsec]")
    }
  }
}

// TODO: If that function is in SubscriptionHelper, given that
// ReportCallbackBase calls it, it must be made an inner class.
// And if it is an inner class, then client code cannot inherit from it.
// Maybe there's a cleaner way to achieve what I want?
fun nodeStateToDebugString(nodeState: NodeState): String {
  val stringBuilder = StringBuilder()
  nodeState.endpointStates.forEach { (endpointId, endpointState) ->
    stringBuilder.append("\nEndpoint [${endpointId}] {\n")
    // Map<Long, ClusterState>
    endpointState.clusterStates.forEach { (clusterId, clusterState) ->
      stringBuilder.append(
          "\tCluster [${clusterId}] [${ChipIdLookup.clusterIdToName(clusterId)}] {\n")
      clusterState.attributeStates.forEach { (attributeId, attributeState) ->
        val attributeName = ChipIdLookup.attributeIdToName(clusterId, attributeId)
        stringBuilder.append("\t\t[${attributeId}] [${attributeName}] ${attributeState.value}\n")
      }
      // Map<Long, ArrayList<EventState>>
      clusterState.eventStates.forEach { (eventId, eventStates) ->
        eventStates.forEach { eventState ->
          stringBuilder.append("\t\teventNumber: ${eventState.eventNumber}\n")
          stringBuilder.append("\t\tpriorityLevel: ${eventState.priorityLevel}\n")
          stringBuilder.append("\t\tsystemTimeStamp: ${eventState.systemTimeStamp}\n")
          val eventName = ChipIdLookup.eventIdToName(clusterId, eventId)
          stringBuilder.append("\t\t[${eventId}] [${eventName}] ${eventState.value}\n")
        }
      }
      stringBuilder.append("\t}\n")
    }
    stringBuilder.append("}\n")
  }
  return stringBuilder.toString()
}
