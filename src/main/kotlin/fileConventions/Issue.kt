package com.trefilsoftware.fileConventions

import java.time.Instant

data class Issue(
    val source: Rule,
    val sourceFilePath: String,
    val detectedAt: Instant = Instant.now()
)
