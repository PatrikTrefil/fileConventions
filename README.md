<div align="center">
  <img src="src/main/resources/file-check-white.png" width="96" alt="File Conventions icon">
  <h1>File Conventions</h1>
  <p>Keep folder contents consistently named, with quiet background checks and desktop notifications.</p>

  [![Build and release](https://github.com/PatrikTrefil/fileConventions/actions/workflows/main.yml/badge.svg)](https://github.com/PatrikTrefil/fileConventions/actions/workflows/main.yml)
  [Download a release](https://github.com/PatrikTrefil/fileConventions/releases/latest) · [Report a problem](https://github.com/PatrikTrefil/fileConventions/issues)
</div>

---

Create a naming rule, choose a folder and a checking interval, and let File Conventions watch for names that do not match. Checks run in the background while the application is open. Your files are never renamed, deleted, or modified.

## Install

Download the installer for your operating system from [GitHub Releases](https://github.com/PatrikTrefil/fileConventions/releases):

| Platform | Installer | Installation |
| --- | --- | --- |
| Windows | `.exe` | Run the installer; installation is per user. |
| macOS | `.dmg` | Open the disk image and copy the app to Applications. |
| Linux (Debian/Ubuntu) | `.deb` | Run `sudo apt install ./File-Conventions-*.deb`. |

Installers include Java, so users do not need a separate Java installation. Choose the asset matching your machine's architecture (`X64` or `ARM64`), when available. Each build produces an installer for its runner's architecture; the release does not promise every OS/architecture combination.

Release installers are unsigned. Windows or macOS may show an unfamiliar-publisher warning. Only open builds from a source you trust. Linux requires a graphical desktop; tray and notification support depend on the desktop environment. Enable notifications for the application in your operating system if alerts are suppressed.

## Use

1. Launch **File Conventions**.
2. Add a rule with a descriptive name, a folder, and a pattern.
3. Choose an interval from 1 to 525,600 minutes, optionally enable recursive checking, and save the rule.
4. Select a rule and open **Current violations** to see its violating files, with file names and full paths. Click **Check now** to refresh the list immediately.

The list reflects the latest successful scan of the selected rule. Resolved violations disappear after the next scan. If a scan fails, the previous successful results remain visible with a warning that they may be stale.

Rules default to **Watch for changes + periodic verification**. Creation, deletion, and rename events trigger a background scan after a quiet period of **7 seconds**. Change **Wait after folder changes** in the rule editor to any value from 0 to 3,600 seconds. Further name changes restart that delay, grouping bursts into one scan and avoiding alerts for short-lived temporary names. Content edits alone do not trigger scans. Initial, manual, and scheduled verification scans run immediately; scheduled verification also limits how long a continually busy folder can defer a scan.

Uncheck **Watch for changes + periodic verification** for periodic checks only. The checking interval remains the verification/fallback interval in either mode. Recursive rules also watch newly created subfolders. Event queues are checked approximately once per second, so delivery is near-real-time rather than an exact timer guarantee. Each settled batch rescans the rule's folder scope; large recursive trees can cost more than small folders.

Network shares may not deliver all filesystem events, particularly changes made from other machines. Periodic scans remain enabled even when watching succeeds. Failed watcher registration falls back to scheduled scans and is retried on later checks; disconnected folders are reported as scan errors and checked again at the configured interval. Missed or overflowed events are repaired by rescanning. Network-share behavior still requires testing against your particular server and desktop.

Use **System notifications** in the toolbar to turn desktop alerts on or off. The setting is saved across restarts; checks and the violations list continue working while alerts are off. Turning alerts back on applies to newly detected violations on subsequent checks.

**Debug runs** is a separate troubleshooting view. Each check is listed as an individual run with its rule, timestamp, outcome, and details. The latest 100 runs are kept for the current session.

When a system tray is available, closing the window leaves the application monitoring in the background. Use the tray menu to reopen it or quit. If your desktop has no supported tray, keep the application window open. Monitoring stops when the application quits or you log out; installation does not automatically configure login startup.

Rules can be edited, disabled, or deleted. A new violation triggers a notification; unchanged violations do not repeat alerts during the same app session. If a violation disappears and later returns, it triggers a new alert. Starting the app checks again and may notify about existing violations.

### Naming patterns

Patterns describe **valid names**. Names that do not match are violations. Matching is case-sensitive and uses the complete name of each regular file, including its extension. Folder names and symbolic links are excluded. Recursive checking includes files in subfolders; without recursion, only the selected folder is checked.

| Pattern language | Pattern | Accepted names |
| --- | --- | --- |
| Regular expression | `[a-z0-9]+(?:-[a-z0-9]+)*\.pdf` | `invoice-2026.pdf`, `report.pdf` |
| Regular expression | `[0-9]{4}-[0-9]{2}-[0-9]{2}_.+\.txt` | `2026-11-17_notes.txt` |
| Wildcard | `*.txt` | `notes.txt`, `meeting notes.txt` |
| Wildcard | `report-????.pdf` | `report-2026.pdf` |

Enter patterns directly in the app: use a single backslash before a literal dot, as shown above. Regular expressions use Java's regular expression syntax; `*` and `?` in wildcard patterns mean any number of characters and one character respectively.

## Saved settings

Rules are stored in `rules.properties` under the operating system user-data directory selected by AppDirs for **File Conventions** (vendor **Trefil Software**). Notification preferences are stored alongside them in `settings.properties`. This location is independent of the application version. Current issues and scan history are held in memory and are rebuilt when the app runs. Earlier experimental Hibernate databases are not imported automatically; recreate those rules in the new app.

## Build from source

Install a **JDK 17** that includes `jpackage`. Dependencies are downloaded by the Gradle wrapper.

```powershell
# Windows
.\gradlew.bat test
.\gradlew.bat run
.\gradlew.bat jpackage
```

```sh
# macOS / Linux
bash ./gradlew test
bash ./gradlew run
bash ./gradlew jpackage
```

Native installers are written to `build/jpackage/`. Build on the target operating system: `jpackage` does not cross-compile installers. Windows packaging additionally requires **WiX Toolset 3** on `PATH`; Linux Debian packaging requires `fakeroot` and Debian packaging tools.

## Publish a release

The GitHub Actions workflow tests and builds native installers on Windows, macOS, and Linux for pushes and pull requests. Installer artifacts are available from each successful workflow run.

To publish a version, commit the intended changes and push a version tag:

```sh
git tag v1.0.0
git push origin v1.0.0
```

Use `vMAJOR.MINOR.PATCH` tags, such as `v1.2.3`. The tag supplies the application's packaged version. Once **all three platform builds succeed**, the workflow creates a GitHub release with generated release notes, installers, and SHA-256 checksum files. The workflow uses the repository's `GITHUB_TOKEN`; no personal access token is required. Re-running the workflow replaces assets for the same release.

Signing and macOS notarization are not configured. The workflow is the automated packaging path; installing and exercising notifications on each target desktop remains a separate acceptance check.

## Contributing

See [AGENTS.md](AGENTS.md) for project goals and development expectations. Keep the core cross-platform, add tests for monitoring behavior, and include the operating system and relevant error details when reporting a problem.

