package com.trefilsoftware.fileConventions

import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class CheckResult(
    val rule: Rule,
    val issues: List<Issue>,
    val checkedAt: Instant = Instant.now(),
    val error: String? = null
)

class DirectoryMonitor(private val onResult: (CheckResult, Int) -> Unit) : AutoCloseable {
  private val worker =
      Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "folder-checker").apply { isDaemon = true }
      }
  @Volatile private var rules: List<Rule> = emptyList()
  private val nextCheck = mutableMapOf<String, Instant>()
  private val known = mutableMapOf<String, Set<String>>()

  private data class Subscription(
      val service: WatchService,
      val directories: MutableMap<WatchKey, Path>
  )

  private val subscriptions = mutableMapOf<String, Subscription>()
  private val pending = mutableMapOf<String, Instant>()

  init {
    worker.scheduleWithFixedDelay({ tick() }, 0, 1, TimeUnit.SECONDS)
  }

  fun updateRules(value: List<Rule>) {
    worker.execute {
      val previous = rules.associateBy { it.id }
      rules = value.toList()
      val ids = value.filter { it.isEnabled }.map { it.id }.toSet()
      nextCheck.keys.retainAll(ids)
      known.keys.retainAll(value.map { it.id }.toSet())
      subscriptions.keys
          .toList()
          .filter { id ->
            value.none { it.id == id && it.isEnabled && it.watchChanges && previous[id] == it }
          }
          .forEach {
            subscriptions.remove(it)?.service?.close()
            pending.remove(it)
          }
      value
          .filter { previous[it.id] != it }
          .forEach {
            nextCheck.remove(it.id)
            if (previous[it.id]?.copy(isEnabled = it.isEnabled) != it) known.remove(it.id)
          }
    }
  }

  fun checkNow(rule: Rule) {
    worker.execute { check(rule) }
  }

  private fun tick() {
    for (rule in rules.filter { it.isEnabled }) {
      try {
        pollEvents(rule)
        val now = Instant.now()
        val due = pending[rule.id]
        if (due != null) {
          if (!now.isBefore(due) || !now.isBefore(nextCheck[rule.id] ?: Instant.MIN)) {
            pending.remove(rule.id)
            check(rule)
          }
        } else if (!now.isBefore(nextCheck[rule.id] ?: Instant.MIN)) check(rule)
      } catch (e: Exception) {
        subscriptions.remove(rule.id)?.service?.close()
        pending.remove(rule.id)
        check(rule)
      }
    }
  }

  // One nonblocking poll per second; no thread or timer per watched directory.
  private fun pollEvents(rule: Rule) {
    val subscription = subscriptions[rule.id] ?: return
    var changed = false
    while (true) {
      val key = subscription.service.poll() ?: break
      for (event in key.pollEvents()) {
        changed = true
        // Register new subtrees now so changes during the settling delay are observed.
        if (rule.recursive && event.kind() == ENTRY_CREATE) {
          val path = subscription.directories[key]?.resolve(event.context() as Path)
          if (path != null && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
              registerTree(subscription, path, true)
        }
      }
      if (!key.reset()) subscription.directories.remove(key)
    }
    if (changed)
        pending[rule.id] = Instant.now().plusSeconds(rule.notificationDelaySeconds.toLong())
  }

  private fun registerTree(subscription: Subscription, root: Path, recursive: Boolean) {
    val registered = subscription.directories.values.toSet()
    Files.walk(root, if (recursive) Int.MAX_VALUE else 0).use { paths ->
      paths
          .filter { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) && it !in registered }
          .forEach {
            val key = it.register(subscription.service, ENTRY_CREATE, ENTRY_DELETE)
            subscription.directories[key] = it
          }
    }
  }

  private fun ensureWatching(rule: Rule) {
    if (!rule.watchChanges || !rule.isEnabled) return
    var subscription = subscriptions[rule.id]
    try {
      if (subscription == null) {
        subscription = Subscription(FileSystems.getDefault().newWatchService(), mutableMapOf())
        subscriptions[rule.id] = subscription
      }
      registerTree(subscription, rule.pathToMonitor.toPath(), rule.recursive)
    } catch (e: Exception) {
      subscriptions.remove(rule.id)?.service?.close()
      System.err.println(
          "Watching unavailable for ${rule.name}; periodic checks remain active: ${e.message}")
    }
  }

  private fun check(rule: Rule) {
    ensureWatching(rule)
    val result = scan(rule)
    nextCheck[rule.id] = Instant.now().plusSeconds(rule.timeBetweenChecksInMinutes.toLong() * 60)
    val paths = result.issues.map { it.sourceFilePath }.toSet()
    val fresh = if (result.error == null) (paths - (known[rule.id] ?: emptySet())).size else 0
    if (result.error == null) known[rule.id] = paths
    try {
      onResult(result, fresh)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  override fun close() {
    worker.execute {
      subscriptions.values.forEach { it.service.close() }
      subscriptions.clear()
    }
    worker.shutdown()
    if (!worker.awaitTermination(2, TimeUnit.SECONDS)) worker.shutdownNow()
  }

  companion object {
    fun scan(rule: Rule): CheckResult =
        try {
          val root = rule.pathToMonitor.toPath()
          if (!Files.isDirectory(root)) throw IOException("Folder is unavailable: $root")
          val matcher = rule.matcher()
          val issues = mutableListOf<Issue>()
          Files.walk(root, if (rule.recursive) Int.MAX_VALUE else 1).use { paths ->
            paths
                .filter { it != root && Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .forEach {
                  if (Thread.currentThread().isInterrupted) throw InterruptedException()
                  if (!matcher.matches(it.fileName.toString()))
                      issues.add(Issue(rule, it.toAbsolutePath().toString()))
                }
          }
          CheckResult(rule, issues)
        } catch (e: Exception) {
          CheckResult(rule, emptyList(), error = e.message ?: e.javaClass.simpleName)
        }
  }
}
