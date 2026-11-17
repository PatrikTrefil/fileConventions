package com.trefilsoftware.fileConventions

enum class PatternLanguage {
  Regex,
  Wildcard;

  fun matcher(pattern: String): kotlin.text.Regex =
      when (this) {
        Regex -> kotlin.text.Regex(pattern)
        Wildcard ->
            kotlin.text.Regex(
                pattern
                    .map {
                      when (it) {
                        '*' -> ".*"
                        '?' -> "."
                        else -> kotlin.text.Regex.escape(it.toString())
                      }
                    }
                    .joinToString(""))
      }
}
