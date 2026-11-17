<div align="center">
  <img src="src/main/resources/file-check-white.png" width="80" alt="File Conventions icon">
  <h1>File Conventions</h1>
  <p>Get notified when file names break your naming rules.</p>

  [Download](https://github.com/PatrikTrefil/fileConventions/releases/latest) · [Report a problem](https://github.com/PatrikTrefil/fileConventions/issues)

  [![Build and release](https://github.com/PatrikTrefil/fileConventions/actions/workflows/main.yml/badge.svg)](https://github.com/PatrikTrefil/fileConventions/actions/workflows/main.yml)
</div>

File Conventions is a small desktop app for **Windows, macOS, and Linux**. Choose a folder, define valid file names with a wildcard or regular expression, and get desktop alerts when a name does not match. **Your files are never renamed, deleted, or modified.**

![File Conventions showing two file names that break an invoice naming rule](docs/images/app.png)

## Get started

1. [Download the latest release](https://github.com/PatrikTrefil/fileConventions/releases/latest): `.exe` for Windows, `.dmg` for macOS, or `.deb` for Debian/Ubuntu. Java is included; choose the asset for your architecture.
2. Click **Add rule**, choose a folder and pattern, and set a checking interval in minutes.
3. View **Current violations**, or click **Check now** to scan immediately.

Installers are unsigned, so Windows or macOS may show an unfamiliar-publisher warning. Linux needs a graphical desktop. Desktop alerts require system tray support and notifications enabled in your OS.

## How it works

- Watches for file-name changes and checks periodically in the background. Rules and notification preferences are saved across restarts.
- Waits 7 seconds after changes by default to let temporary names settle; the delay is adjustable.
- Alerts once per new violation during a session. If the name is fixed and the violation returns, it alerts again.
- Keeps monitoring when you close the window if a system tray is available. Otherwise, keep the window open. Use **Quit** to stop.

Patterns describe **valid names** and match the entire file name, including the extension, case-sensitively:

| Pattern | Valid example |
| --- | --- |
| Wildcard: `report-????.pdf` | `report-2026.pdf` |
| Regex: `invoice-[0-9]{4}-[0-9]{3}\.pdf` | `invoice-2026-001.pdf` |

Only regular files are checked; folders and symbolic links are skipped. Enable **Include subfolders** for recursive checks.

[Usage and troubleshooting](docs/usage.md) · [Build and release instructions](docs/development.md)
