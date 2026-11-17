package com.trefilsoftware.fileConventions

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.*

class PatternPreviewTest {
  @Test
  fun `preview uses the same whole name matching and reports invalid regex`() {
    val results = LinkedBlockingQueue<PreviewResult>()
    PatternPreview().use { preview ->
      preview.request("report-????.pdf", PatternLanguage.Wildcard, "report-2026.pdf") {
        results.add(it)
      }
      assertEquals(true, assertNotNull(results.poll(3, TimeUnit.SECONDS)).matches)
      preview.request("report-????.pdf", PatternLanguage.Wildcard, "Report-2026.pdf") {
        results.add(it)
      }
      assertEquals(false, assertNotNull(results.poll(3, TimeUnit.SECONDS)).matches)
      preview.request("[", PatternLanguage.Regex, "sample") { results.add(it) }
      assertNotNull(assertNotNull(results.poll(3, TimeUnit.SECONDS)).error)
    }
  }

  @Test
  fun `typing replaces a pending expensive preview with the latest sample`() {
    val results = LinkedBlockingQueue<PreviewResult>()
    PatternPreview().use { preview ->
      preview.request("(a+)+$", PatternLanguage.Regex, "a".repeat(30000) + "!") { results.add(it) }
      preview.request("*.pdf", PatternLanguage.Wildcard, "report.pdf") { results.add(it) }
      assertEquals(PreviewResult(matches = true), assertNotNull(results.poll(3, TimeUnit.SECONDS)))
      assertNull(results.poll(400, TimeUnit.MILLISECONDS))
    }
  }
}
