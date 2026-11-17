package com.trefilsoftware.fileConventions

import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.jupiter.api.io.TempDir

class MonitoringTest {
  @Test
  fun `watch events detect a violation and resolution before periodic interval`() {
    val results = LinkedBlockingQueue<CheckResult>()
    DirectoryMonitor { result, _ -> results.add(result) }
        .use { monitor ->
          monitor.updateRules(listOf(rule(true).copy(notificationDelaySeconds = 0)))
          assertNotNull(results.poll(5, TimeUnit.SECONDS))
          val sub = Files.createDirectory(directory.resolve("new-subfolder"))
          val bad = Files.writeString(sub.resolve("bad.txt"), "")
          val detected = results.poll(8, TimeUnit.SECONDS)
          assertNotNull(detected)
          assertEquals(
              listOf(bad.toAbsolutePath().toString()), detected.issues.map { it.sourceFilePath })
          Files.delete(bad)
          val resolved = results.poll(8, TimeUnit.SECONDS)
          assertNotNull(resolved)
          assertTrue(resolved.issues.isEmpty())
        }
  }

  @Test
  fun `settling delay ignores short lived invalid names`() {
    val notifications = LinkedBlockingQueue<Int>()
    DirectoryMonitor { _, fresh -> notifications.add(fresh) }
        .use { monitor ->
          monitor.updateRules(listOf(rule().copy(notificationDelaySeconds = 2)))
          assertEquals(0, notifications.poll(5, TimeUnit.SECONDS))
          val temporary = Files.writeString(directory.resolve("download.tmp"), "")
          assertNull(notifications.poll(1200, TimeUnit.MILLISECONDS))
          Files.move(temporary, directory.resolve("valid.pdf"))
          assertEquals(0, notifications.poll(7, TimeUnit.SECONDS))
        }
  }

  @Test
  fun `periodic only mode does not react to folder events`() {
    val results = LinkedBlockingQueue<CheckResult>()
    DirectoryMonitor { result, _ -> results.add(result) }
        .use { monitor ->
          monitor.updateRules(
              listOf(rule().copy(watchChanges = false, notificationDelaySeconds = 0)))
          assertNotNull(results.poll(5, TimeUnit.SECONDS))
          Files.writeString(directory.resolve("bad.txt"), "")
          assertNull(results.poll(2200, TimeUnit.MILLISECONDS))
        }
  }

  @Test
  fun `older settings default to watching with seven second delay`() {
    val store = RuleStore(directory.toFile())
    store.save(listOf(rule().copy(watchChanges = false, notificationDelaySeconds = 19)))
    assertFalse(store.load().single().watchChanges)
    assertEquals(19, store.load().single().notificationDelaySeconds)
    val file = directory.resolve("rules.properties")
    Files.write(
        file,
        Files.readAllLines(file).filterNot {
          it.contains("watchChanges=") || it.contains("notificationDelaySeconds=")
        })
    assertTrue(store.load().single().watchChanges)
    assertEquals(7, store.load().single().notificationDelaySeconds)
    assertFailsWith<IllegalArgumentException> { rule().copy(notificationDelaySeconds = -1) }
  }

  @Test
  fun `notification preference defaults on and survives changes`() {
    val store = SettingsStore(directory.toFile())
    assertTrue(store.loadNotificationsEnabled())
    store.saveNotificationsEnabled(false)
    assertFalse(SettingsStore(directory.toFile()).loadNotificationsEnabled())
    store.saveNotificationsEnabled(true)
    assertTrue(SettingsStore(directory.toFile()).loadNotificationsEnabled())
  }

  @TempDir lateinit var directory: java.nio.file.Path

  private fun rule(recursive: Boolean = false) =
      Rule(
          "PDF files",
          "[a-z]+\\.pdf",
          PatternLanguage.Regex,
          true,
          1,
          directory.toFile(),
          recursive)

  @Test
  fun `matches whole file names and optionally includes subfolders`() {
    Files.writeString(directory.resolve("valid.pdf"), "")
    Files.writeString(directory.resolve("Invalid.pdf"), "")
    Files.createDirectory(directory.resolve("sub"))
    Files.writeString(directory.resolve("sub/bad.txt"), "")
    assertEquals(1, DirectoryMonitor.scan(rule()).issues.size)
    assertEquals(2, DirectoryMonitor.scan(rule(true)).issues.size)
  }

  @Test
  fun `wildcards escape regex punctuation`() {
    val wildcard =
        rule()
            .copy(pattern = "report[?]*.pdf", patternLanguage = PatternLanguage.Wildcard)
            .matcher()
    assertTrue(wildcard.matches("report[1]final.pdf"))
    assertFalse(wildcard.matches("report1finalXpdf"))
  }

  @Test
  fun `invalid rules fail before scheduling`() {
    assertFailsWith<IllegalArgumentException> { rule().copy(timeBetweenChecksInMinutes = 0) }
    assertFailsWith<IllegalArgumentException> { rule().copy(pattern = "[") }
  }

  @Test
  fun `rules survive a storage round trip`() {
    val store = RuleStore(directory.toFile())
    val rules = listOf(rule().copy(name = "Unicode žluťoučký", recursive = true))
    store.save(rules)
    assertEquals(rules, store.load())
    store.save(emptyList())
    assertEquals(emptyList(), store.load())
  }

  @Test
  fun `missing folders produce errors rather than clean reports`() {
    assertNotNull(
        DirectoryMonitor.scan(rule().copy(pathToMonitor = directory.resolve("missing").toFile()))
            .error)
  }

  @Test
  fun `notifications repeat only after a violation resolves and returns`() {
    val counts = LinkedBlockingQueue<Int>()
    val bad = directory.resolve("bad.txt")
    Files.writeString(bad, "")
    DirectoryMonitor { _, fresh -> counts.add(fresh) }
        .use { monitor ->
          monitor.checkNow(rule())
          assertEquals(1, counts.poll(5, TimeUnit.SECONDS))
          val r = rule()
          // A stable rule ID is required across successive checks.
          monitor.checkNow(r)
          assertEquals(1, counts.poll(5, TimeUnit.SECONDS))
          monitor.checkNow(r)
          assertEquals(0, counts.poll(5, TimeUnit.SECONDS))
          Files.delete(bad)
          monitor.checkNow(r)
          assertEquals(0, counts.poll(5, TimeUnit.SECONDS))
          Files.writeString(bad, "")
          monitor.checkNow(r)
          assertEquals(1, counts.poll(5, TimeUnit.SECONDS))
        }
  }
}
