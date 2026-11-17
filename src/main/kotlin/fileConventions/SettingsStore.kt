package com.trefilsoftware.fileConventions

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

class SettingsStore(private val directory: File) {
  private val file = File(directory, "settings.properties")

  fun loadNotificationsEnabled(): Boolean {
    if (!file.exists()) return true
    val properties = Properties().apply { file.inputStream().use { load(it) } }
    return properties.getProperty("notificationsEnabled", "true").toBooleanStrict()
  }

  fun saveNotificationsEnabled(enabled: Boolean) {
    Files.createDirectories(directory.toPath())
    val temporary = File(directory, "settings.tmp").toPath()
    Files.newOutputStream(temporary).use {
      Properties()
          .apply { setProperty("notificationsEnabled", enabled.toString()) }
          .store(it, "File Conventions settings")
    }
    try {
      Files.move(
          temporary,
          file.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE)
    } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
      Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
  }
}
