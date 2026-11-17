package com.trefilsoftware.fileConventions

import java.io.File
import java.util.UUID

data class Rule(
    val name: String,
    val pattern: String,
    val patternLanguage: PatternLanguage,
    val isEnabled: Boolean,
    val timeBetweenChecksInMinutes: Int,
    val pathToMonitor: File,
    val recursive: Boolean = false,
    val id: String = UUID.randomUUID().toString(),
    val watchChanges: Boolean = true,
    val notificationDelaySeconds: Int = 7
) {
  init {
    require(name.isNotBlank()) { "Enter a rule name." }
    require(pattern.isNotBlank()) { "Enter a pattern." }
    require(timeBetweenChecksInMinutes in 1..525600) { "Interval must be 1–525600 minutes." }
    require(notificationDelaySeconds in 0..3600) { "Notification delay must be 0–3600 seconds." }
    matcher()
  }

  fun matcher(): Regex = patternLanguage.matcher(pattern)
}
