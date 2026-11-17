package com.trefilsoftware.fileConventions

import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

data class PreviewResult(val matches: Boolean? = null, val error: String? = null)

/** Debounces typing and cancels expensive regex matches when the input changes or editor closes. */
class PatternPreview : AutoCloseable {
  private val worker =
      ScheduledThreadPoolExecutor(1) { action ->
            Thread(action, "pattern-preview").apply { isDaemon = true }
          }
          .apply { removeOnCancelPolicy = true }
  private var pending: ScheduledFuture<*>? = null

  fun request(
      pattern: String,
      language: PatternLanguage,
      sample: String,
      onResult: (PreviewResult) -> Unit
  ) {
    pending?.cancel(true)
    pending =
        worker.schedule(
            {
              try {
                val matcher = language.matcher(pattern)
                val result =
                    PreviewResult(
                        if (sample.isEmpty()) null else matcher.matches(InterruptibleName(sample)))
                if (!Thread.currentThread().isInterrupted) onResult(result)
              } catch (_: CancellationException) {
                // A newer input or a closed editor owns the next result.
              } catch (e: IllegalArgumentException) {
                if (!Thread.currentThread().isInterrupted)
                    onResult(PreviewResult(error = e.message ?: "Invalid pattern"))
              }
            },
            250,
            TimeUnit.MILLISECONDS)
  }

  override fun close() {
    pending?.cancel(true)
    worker.shutdownNow()
  }

  private class InterruptibleName(private val value: String) : CharSequence {
    override val length
      get() = value.length

    override fun get(index: Int): Char {
      if (Thread.currentThread().isInterrupted) throw CancellationException()
      return value[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        InterruptibleName(value.substring(startIndex, endIndex))

    override fun toString() = value
  }
}
