# Usage and troubleshooting

## Use

1. Launch **File Conventions**.
2. Select **Add your first rule** (or **+ Add rule**) and choose a descriptive name and folder. Start with **Wildcard (simple)**, enter a valid-name pattern, and use **Try a file name** to check a sample before saving. **How it works** offers a short introduction at any time.
3. Choose an interval from 1 to 525,600 minutes, optionally enable **Include files in subfolders**, and select **Save rule**. Enabled rules start their first check automatically.
4. Select a rule and open **Current violations** to see its violating files, with file names and full paths. Click **Check now** to refresh the list immediately.

The list reflects the latest successful scan of the selected rule. Resolved violations disappear after the next scan. If a scan fails, the previous successful results remain visible with a warning that they may be stale.

Rules default to **Also check after file names change** in **Monitoring options**. Creation, deletion, and rename events trigger a background scan after a quiet period of **7 seconds**. Change **Wait after changes** in the rule editor to any value from 0 to 3,600 seconds. Further name changes restart that delay, grouping bursts into one scan and avoiding alerts for short-lived temporary names. Content edits alone do not trigger scans. Initial, manual, and scheduled verification scans run immediately; scheduled verification also limits how long a continually busy folder can defer a scan.

Uncheck **Also check after file names change** in **Monitoring options** for periodic checks only. The checking interval remains the verification/fallback interval in either mode. Recursive rules also watch newly created subfolders. Event queues are checked approximately once per second, so delivery is near-real-time rather than an exact timer guarantee. Each settled batch rescans the rule's folder scope; large recursive trees can cost more than small folders.

Network shares may not deliver all filesystem events, particularly changes made from other machines. Periodic scans remain enabled even when watching succeeds. Failed watcher registration falls back to scheduled scans and is retried on later checks; disconnected folders are reported as scan errors and checked again at the configured interval. Missed or overflowed events are repaired by rescanning. Network-share behavior still requires testing against your particular server and desktop.

For Google Drive, choose a folder exposed on your computer by the desktop sync client. The app checks regular files visible in that filesystem folder; it does not use the Google Drive website or API. Changes made elsewhere become detectable when the sync client exposes them locally, and may wait until the next periodic scan. Availability of cloud-only items and change events depends on your sync client and OS. Keep the drive mounted and the client running when needed for access. Compatibility with your particular network share or sync setup should be checked with **Check now**.

All naming checks run locally. File Conventions does not upload file names, contents, rules, or results, and requires no app account or cloud service. Access to a network drive or Google Drive still uses your existing network connection or sync software.

Use **System notifications** above the rules to turn OS notifications on or off. The setting is saved across restarts; checks and the violations list continue working while notifications are off. Turning notifications back on applies to newly detected violations on subsequent checks.

**History** lists each check with its rule, timestamp, and outcome. **Total violations** counts all violations found in that check; **new** counts violations newly detected for that rule during the session, including previously resolved violations that return. The first successful check counts existing violations as new. These counts are recorded even when system notifications are off. Failed checks show **Counts unavailable** rather than a misleading zero. Select a check to see both counts and the full list of files that violated the rule at that time. The latest 100 checks are kept for the current session.

When a system tray is available, closing the window leaves the application monitoring in the background. Use the tray menu to reopen it or quit. If your desktop has no supported tray, keep the application window open. Monitoring stops when the application quits or you log out; installation does not automatically configure login startup.

Use **Edit rule**, **Pause** / **Resume**, or **Remove…** to manage a rule. Removing a rule never removes files. **Check now** also works while paused. **Copy selected path** copies a violating file path for use in your file manager. A new violation triggers a notification; unchanged violations do not repeat alerts during the same app session. If a violation disappears and later returns, it triggers a new alert. Starting the app checks again and may notify about existing violations.

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
