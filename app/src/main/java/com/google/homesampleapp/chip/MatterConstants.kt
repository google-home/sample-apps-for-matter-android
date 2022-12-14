package com.google.homesampleapp.chip

object MatterConstants {
  val DeviceTypesMap =
      mapOf<Long, String>(
          22L to "Root Node",
          256L to "On/Off Light",
          266L to "Outlet",
      )

  val ClustersMap =
      mapOf<Long, String>(
          3L to "Identify",
          4L to "Groups",
          5L to "Scenes",
          6L to "On/Off",
          8L to "Level Control",
          29L to "Descriptor",
          30L to "Binding",
          31L to "Access Control",
          40L to "Basic Information",
          41L to "OTA Software Update Provider",
          42L to "OTA Software Update Requestor",
          43L to "Localization Configuration",
          44L to "Time Format Localization",
          44L to "Time Format Localization",
          44L to "Unit Localization",
          48L to "General Commissioning",
          49L to "Network Commissioning",
          50L to "Diagnostics Logs",
          51L to "General Diagnostics",
          52L to "Software Diagnostics",
          53L to "Thread Network Diagnostics",
          54L to "Wi-Fi Network Diagnostics",
          55L to "Ethernet Network Diagnostics",
          56L to "Time Synchronization",
          59L to "Switch",
          60L to "Administrator Commissioning",
          62L to "Node Operational Credentials",
          63L to "Group Key Management",
          64L to "Fixed label",
          65L to "User label",
          1030L to "Occupancy Sensing",
      )
}
