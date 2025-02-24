package com.trefilsoftware.fileConventions

import jakarta.persistence.*
import java.io.File
import java.util.*

@Entity
@Table(name = "Rule")
class Rule {
  @Id @Column(name = "id") @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Int

  @Column(name = "name") val name: String
  @Column(name = "pattern") val pattern: String
  @Column(name = "patternLanguage") val patternLanguage: PatternLanguage
  @Column(name = "isEnabled") val isEnabled: Boolean
  @Column(name = "timeBetweenChecksInMinutes") val timeBetweenChecksInMinutes: Int
  @Column(name = "pathToMonitor") val pathToMonitor: File
  @Column(name = "lastCheckAt") val lastCheckAt: Date?

  constructor() {
    this.id = 0
    this.name = ""
    this.pattern = ""
    this.patternLanguage = PatternLanguage.Regex
    this.isEnabled = false
    this.timeBetweenChecksInMinutes = 0
    this.pathToMonitor = File("")
    this.lastCheckAt = null
  }

  constructor(
    name: String,
    pattern: String,
    patternLanguage: PatternLanguage,
    isEnabled: Boolean,
    timeBetweenChecksInMinutes: Int,
    pathToMonitor: File
  ) {
    this.id = 0
    this.name = name
    this.pattern = pattern
    this.patternLanguage = patternLanguage
    this.isEnabled = isEnabled
    this.timeBetweenChecksInMinutes = timeBetweenChecksInMinutes
    this.pathToMonitor = pathToMonitor
    this.lastCheckAt = null
  }
  // TODO: when the instance is created - start periodic checks of rule (possibly using coroutines)
  // TODO: when the rule is edited - rerun the check immediately
}
