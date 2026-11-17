# Project goals

File Conventions is a small, cross-platform desktop utility that periodically checks names in user-selected folders and sends a system notification when a naming rule is violated.

## Product requirements

- Keep Kotlin/JVM and JavaFX viable on Windows, macOS, and Linux. Prefer fixing the shared implementation to introducing a Windows-only rewrite.
- Let users choose folders, regular expressions or wildcard patterns, and a positive checking interval in minutes.
- Keep checks off the JavaFX application thread. Closing the window should leave monitoring active when a system tray is available; quitting must release resources.
- Persist rules across restarts. Report storage and scan failures clearly rather than silently discarding user settings.
- Show current violations and offer a manual check. Suppress repeated notifications for unchanged violations; notify again when a resolved violation returns.
- Never rename, delete, or change monitored files. Match names rather than file contents.
- Provide an identifiable application/tray icon and native installers with a bundled Java runtime.
- Run automated checks on all three platforms. A `vMAJOR.MINOR.PATCH` tag must build native installers and publish a GitHub release only after all platform builds succeed.

## Working conventions

- Keep scanning, scheduling, persistence, and UI responsibilities separate where practical.
- Validate patterns and intervals before saving. Document matching scope and recursion behavior in the README.
- Test meaningful behavior: matching, rescheduling, violation lifecycle, persistence, and error handling. Avoid tests that merely repeat implementation details.
- Use the Gradle wrapper and JDK 17. Run `./gradlew test` (Windows: `.\gradlew.bat test`) and relevant packaging checks before handing off changes.
- Build installers on their target OS using `jpackage`. Do not claim platform-specific installation or notification behavior was verified unless it actually was.
- Keep the README aligned with implemented functionality and explain OS notification/tray prerequisites and release steps.
