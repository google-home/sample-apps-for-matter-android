package com.google.homesampleapp.chip

object MatterConstants {
  val DeviceTypesMap =
      mapOf<Long, String>(
          256L to "On/Off Light",
      )

  val ClustersMap =
      mapOf<Long, String>(
          3L to "Identify",
          4L to "Groups",
          5L to "Scenes",
          6L to "On/Off",
          8L to "Level Control",
          30L to "Binding",
          1030L to "Occupancy Sensing",
      )
}
