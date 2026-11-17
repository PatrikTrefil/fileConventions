# Development and releases

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

For an isolated UI preview, launch the shadow JAR with `java -DfileConventions.dataDir=build/preview-settings -jar build/libs/fileConventions-1.0.0-all.jar` after `shadowJar` (adjust the version if needed). This keeps preview rules and the instance lock separate from your installed app. Omit the property for normal OS user-data storage.

The Windows installer's final page includes a **Run File Conventions** checkbox, checked by default. Leaving it checked launches the app when **Finish** is clicked after installation; uncheck it to skip launching. Silent installs, repair, and uninstall do not launch it. The build extends the running JDK 17's WiX template with `packaging/windows/launch-application.xml`; use JDK 17 to run Gradle as well as compile the app. Check the default selection, opt-out, and launch behavior interactively when accepting a Windows release.

## Publish a release

The GitHub Actions workflow tests and builds native installers on Windows, macOS, and Linux for pushes and pull requests. Installer artifacts are available from each successful workflow run.

To publish a version, commit the intended changes and push a version tag:

```sh
git tag v1.0.1
git push origin v1.0.1
```

Use `vMAJOR.MINOR.PATCH` tags, such as `v1.2.3`. The tag supplies the application's packaged version. Once **all three platform builds succeed**, the workflow creates a GitHub release with generated release notes, installers, and SHA-256 checksum files. The workflow uses the repository's `GITHUB_TOKEN`; no personal access token is required. Re-running the workflow replaces assets for the same release.

Signing and macOS notarization are not configured. The workflow is the automated packaging path; installing and exercising notifications on each target desktop remains a separate acceptance check.

## Contributing

See [AGENTS.md](../AGENTS.md) for project goals and development expectations. Keep the core cross-platform, add tests for monitoring behavior, and include the operating system and relevant error details when reporting a problem.
