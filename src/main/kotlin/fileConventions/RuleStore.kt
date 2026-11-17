package com.trefilsoftware.fileConventions

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

class RuleStore(val directory: File) {
  private val file = File(directory, "rules.properties")

  fun load(): List<Rule> {
    if (!file.exists()) return emptyList()
    val p = Properties().apply { file.inputStream().use { load(it) } }
    return (0 until p.getProperty("count").toInt()).map { i ->
      fun field(key: String): String =
          requireNotNull(p.getProperty("$i.$key")) { "Missing rule field $key" }
      Rule(
          field("name"),
          field("pattern"),
          PatternLanguage.valueOf(field("language")),
          field("enabled").toBooleanStrict(),
          field("interval").toInt(),
          File(field("folder")),
          field("recursive").toBooleanStrict(),
          field("id"),
          p.getProperty("$i.watchChanges", "true").toBooleanStrict(),
          p.getProperty("$i.notificationDelaySeconds", "7").toInt())
    }
  }

  fun save(rules: List<Rule>) {
    Files.createDirectories(directory.toPath())
    val p = Properties()
    p.setProperty("count", rules.size.toString())
    rules.forEachIndexed { i, r ->
      mapOf(
              "name" to r.name,
              "pattern" to r.pattern,
              "language" to r.patternLanguage.name,
              "enabled" to r.isEnabled.toString(),
              "interval" to r.timeBetweenChecksInMinutes.toString(),
              "folder" to r.pathToMonitor.absolutePath,
              "recursive" to r.recursive.toString(),
              "id" to r.id,
              "watchChanges" to r.watchChanges.toString(),
              "notificationDelaySeconds" to r.notificationDelaySeconds.toString())
          .forEach { (k, v) -> p.setProperty("$i.$k", v) }
    }
    val temp = File(directory, "rules.tmp").toPath()
    Files.newOutputStream(temp).use { p.store(it, "File Conventions rules") }
    try {
      Files.move(
          temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
      Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
  }
}
