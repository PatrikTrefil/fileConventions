package com.patriktrefil

class Rule(
    val name: String,
    val regex: Regex,
    val isEnabled: Boolean,
    val timeBetweenChecksInMinutes: Int
) {
  // TODO: when the instance is created - start periodic checks of rule (possibly using coroutines)
  // TODO: when the rule is edited - rerun the check immediately
}
