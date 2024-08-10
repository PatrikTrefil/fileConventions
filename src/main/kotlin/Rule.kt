package com.patriktrefil

import jakarta.persistence.*
import java.io.File

@Entity
@Table(name = "Rule")
class Rule {
  @Id @Column(name = "id") @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Int

  @Column(name = "name") val name: String
  @Column(name = "regex") val regex: Regex
  @Column(name = "isEnabled") val isEnabled: Boolean
  @Column(name = "timeBetweenChecksInMinutes") val timeBetweenChecksInMinutes: Int
  @Column(name = "pathToMonitor") val pathToMonitor: File

  constructor() {
    this.id = 0
    this.name = ""
    this.regex = Regex("")
    this.isEnabled = false
    this.timeBetweenChecksInMinutes = 0
    this.pathToMonitor = File("")
  }

  constructor(
      name: String,
      regex: Regex,
      isEnabled: Boolean,
      timeBetweenChecksInMinutes: Int,
      pathToMonitor: File
  ) {
    this.id = 0
    this.name = name
    this.regex = regex
    this.isEnabled = isEnabled
    this.timeBetweenChecksInMinutes = timeBetweenChecksInMinutes
    this.pathToMonitor = pathToMonitor
  }
  // TODO: when the instance is created - start periodic checks of rule (possibly using coroutines)
  // TODO: when the rule is edited - rerun the check immediately
}
