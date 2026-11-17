package fileConventions

import com.trefilsoftware.fileConventions.*
import java.awt.SystemTray
import java.awt.Toolkit
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import javafx.application.Application
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import net.harawata.appdirs.AppDirsFactory

class MainPage : Application() {
  private val rules = FXCollections.observableArrayList<Rule>()
  private val list = ListView(rules)
  private val violations = FXCollections.observableArrayList<Issue>()
  private val issues = TableView(violations)
  private val summary = Label("Select a rule to see its current violations.")
  private val runs = FXCollections.observableArrayList<CheckResult>()
  private val runList = ListView(runs)
  private val runDetails =
      TextArea().apply {
        isEditable = false
        isWrapText = true
      }
  private val notifications = CheckBox("System notifications")
  private val status = Label("Ready")
  private val results = mutableMapOf<String, CheckResult>()
  private val store =
      RuleStore(
          File(
              AppDirsFactory.getInstance()
                  .getUserDataDir("File Conventions", null, "Trefil Software")))
  private lateinit var monitor: DirectoryMonitor
  private val settings by lazy { SettingsStore(store.directory) }
  private var tray: java.awt.TrayIcon? = null
  private var channel: FileChannel? = null
  private var lock: FileLock? = null

  override fun start(stage: Stage) {
    try {
      store.directory.mkdirs()
      channel =
          FileChannel.open(
              File(store.directory, "app.lock").toPath(),
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE)
      lock = channel!!.tryLock()
      if (lock == null) {
        error("File Conventions is already running. Open it from the system tray.")
        Platform.exit()
        return
      }
      rules.addAll(store.load())
      notifications.isSelected = settings.loadNotificationsEnabled()
    } catch (e: Exception) {
      error("Unable to load settings: ${e.message}\nSettings have been left untouched.")
      Platform.exit()
      return
    }
    monitor = DirectoryMonitor { result, fresh ->
      Platform.runLater {
        if (rules.none { it == result.rule }) return@runLater
        runs.add(0, result)
        if (runs.size > 100) runs.removeAt(runs.lastIndex)
        if (result.error == null) results[result.rule.id] = result
        renderResults()
        status.text =
            result.error
                ?: "Checked ${result.rule.name}: ${result.issues.size} violation(s) at ${result.checkedAt}"
        if (fresh > 0 && notifications.isSelected)
            tray?.displayMessage(
                "File Conventions",
                "${result.rule.name}: $fresh new naming violation(s). Open the app for details.",
                java.awt.TrayIcon.MessageType.WARNING)
      }
    }
    list.setCellFactory {
      object : ListCell<Rule>() {
        override fun updateItem(item: Rule?, empty: Boolean) {
          super.updateItem(item, empty)
          text =
              if (empty || item == null) null
              else
                  "${if (item.isEnabled) "Enabled:" else "Paused:"} ${item.name}\n${item.pathToMonitor} · every ${item.timeBetweenChecksInMinutes} min"
        }
      }
    }
    list.placeholder = Label("Add a rule to start monitoring folders")
    val add = Button("Add rule").apply { setOnAction { edit(stage, null) } }
    val edit =
        Button("Edit").apply {
          setOnAction { list.selectionModel.selectedItem?.let { edit(stage, it) } }
        }
    val delete =
        Button("Delete").apply {
          setOnAction {
            list.selectionModel.selectedItem?.let { selected ->
              if (commit(rules.filter { it.id != selected.id })) {
                results.remove(selected.id)
                renderResults()
              }
            }
          }
        }
    val check =
        Button("Check now").apply {
          setOnAction {
            list.selectionModel.selectedItem?.let {
              status.text = "Checking ${it.name}…"
              monitor.checkNow(it)
            }
          }
        }
    list.selectionModel.selectedItemProperty().addListener { _, _, value ->
      edit.isDisable = value == null
      delete.isDisable = value == null
      check.isDisable = value == null
      renderResults()
    }
    edit.isDisable = true
    delete.isDisable = true
    check.isDisable = true
    val quit = Button("Quit").apply { setOnAction { Platform.exit() } }
    notifications.setOnAction {
      try {
        settings.saveNotificationsEnabled(notifications.isSelected)
      } catch (e: Exception) {
        notifications.isSelected = !notifications.isSelected
        error("Unable to save notification setting: ${e.message}")
      }
    }
    val toolbar = HBox(8.0, add, edit, delete, check, notifications, quit)
    val heading = Label("File Conventions").apply { styleClass.add("heading") }
    val split = SplitPane(list, createResultsPane()).apply { setDividerPositions(0.35) }
    val root =
        VBox(
            12.0,
            heading,
            Label("Keep file names consistent. Checks continue while this window is hidden."),
            toolbar,
            split,
            status)
    root.padding = Insets(20.0)
    VBox.setVgrow(split, Priority.ALWAYS)
    stage.title = "File Conventions"
    stage.scene = Scene(root, 960.0, 600.0).apply { stylesheets.add("main.css") }
    stage.icons.add(Image(javaClass.getResourceAsStream("/file-check-white.png")))
    setupTray(stage)
    Platform.setImplicitExit(tray == null)
    stage.setOnCloseRequest {
      if (tray != null) {
        it.consume()
        stage.hide()
      }
    }
    stage.show()
    if (tray == null)
        status.text = "System tray unavailable. Keep this window open to monitor folders."
    monitor.updateRules(rules.toList())
  }

  private fun createResultsPane(): TabPane {
    val fileColumn =
        TableColumn<Issue, String>("File name").apply {
          setCellValueFactory {
            javafx.beans.property.SimpleStringProperty(File(it.value.sourceFilePath).name)
          }
          prefWidth = 180.0
        }
    val pathColumn =
        TableColumn<Issue, String>("Full path").apply {
          setCellValueFactory {
            javafx.beans.property.SimpleStringProperty(it.value.sourceFilePath)
          }
          prefWidth = 400.0
        }
    issues.columns.addAll(fileColumn, pathColumn)
    issues.placeholder = Label("No violations to display")
    val current = VBox(8.0, summary, issues).apply { VBox.setVgrow(issues, Priority.ALWAYS) }
    summary.isWrapText = true
    runList.placeholder = Label("No checks have run yet")
    runList.setCellFactory {
      object : ListCell<CheckResult>() {
        override fun updateItem(item: CheckResult?, empty: Boolean) {
          super.updateItem(item, empty)
          text =
              if (empty || item == null) null
              else
                  "${item.checkedAt} — ${item.rule.name}\n${if (item.error != null) "Failed" else "${item.issues.size} violation(s)"}"
        }
      }
    }
    runList.selectionModel.selectedItemProperty().addListener { _, _, run ->
      runDetails.text =
          if (run == null) "Select a run to inspect its details."
          else
              "Rule: ${run.rule.name}\nChecked at: ${run.checkedAt}\nFolder: ${run.rule.pathToMonitor}\nPattern: ${run.rule.pattern}\nRecursive: ${run.rule.recursive}\n\n" +
                  (run.error?.let { "Scan failed: $it" }
                      ?: "${run.issues.size} violation(s)\n" +
                          run.issues.joinToString("\n") { it.sourceFilePath })
    }
    runDetails.text = "Select a run to inspect its details."
    val history =
        SplitPane(runList, runDetails).apply { orientation = javafx.geometry.Orientation.VERTICAL }
    val debug =
        VBox(8.0, Label("Debug history — latest 100 runs, kept for this session"), history).apply {
          VBox.setVgrow(history, Priority.ALWAYS)
        }
    return TabPane(Tab("Current violations", current), Tab("Debug runs", debug)).apply {
      tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
    }
  }

  private fun setupTray(stage: Stage) {
    if (!SystemTray.isSupported()) return
    try {
      val icon =
          java.awt.TrayIcon(
              Toolkit.getDefaultToolkit().getImage(javaClass.getResource("/file-check-white.png")),
              "File Conventions")
      icon.isImageAutoSize = true
      val menu = java.awt.PopupMenu()
      menu.add(
          java.awt.MenuItem("Open File Conventions").apply {
            addActionListener {
              Platform.runLater {
                stage.show()
                stage.toFront()
              }
            }
          })
      menu.add(
          java.awt.MenuItem("Quit").apply {
            addActionListener { Platform.runLater { Platform.exit() } }
          })
      icon.popupMenu = menu
      icon.addActionListener {
        Platform.runLater {
          stage.show()
          stage.toFront()
        }
      }
      SystemTray.getSystemTray().add(icon)
      tray = icon
    } catch (e: Exception) {
      System.err.println("Tray unavailable: ${e.message}")
    }
  }

  private fun edit(stage: Stage, old: Rule?) {
    val dialog = Dialog<ButtonType>()
    dialog.initOwner(stage)
    dialog.title = if (old == null) "Add rule" else "Edit rule"
    dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
    val name = TextField(old?.name ?: "")
    val pattern = TextField(old?.pattern ?: "").apply { promptText = "[a-z0-9_-]+\\.pdf" }
    val language =
        ComboBox(FXCollections.observableArrayList(*PatternLanguage.values())).apply {
          value = old?.patternLanguage ?: PatternLanguage.Regex
        }
    val folder = TextField(old?.pathToMonitor?.absolutePath ?: "")
    val browse =
        Button("Browse…").apply {
          setOnAction {
            DirectoryChooser().showDialog(stage)?.let { folder.text = it.absolutePath }
          }
        }
    val interval = TextField(old?.timeBetweenChecksInMinutes?.toString() ?: "60")
    val watchChanges =
        CheckBox("Watch for changes + periodic verification").apply {
          isSelected = old?.watchChanges ?: true
        }
    val delay = TextField(old?.notificationDelaySeconds?.toString() ?: "7")
    val recursive = CheckBox("Include subfolders").apply { isSelected = old?.recursive ?: false }
    val enabled = CheckBox("Enabled").apply { isSelected = old?.isEnabled ?: true }
    val validation =
        Label().apply {
          isWrapText = true
          styleClass.add("error-label")
        }
    dialog.dialogPane.content =
        VBox(
            8.0,
            Label("Rule name"),
            name,
            Label("Pattern language"),
            language,
            Label("Pattern — must match the entire file name, including extension"),
            pattern,
            Label("Folder"),
            HBox(8.0, folder, browse),
            Label("Check interval (minutes)"),
            interval,
            watchChanges,
            Label("Wait after folder changes (seconds, 0–3600)"),
            delay,
            recursive,
            enabled,
            validation)
    dialog.dialogPane.prefWidth = 560.0
    dialog.dialogPane.lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION) {
        event ->
      try {
        val directory = File(folder.text)
        require(directory.isDirectory) { "Choose an existing folder." }
        val rule =
            Rule(
                name.text.trim(),
                pattern.text,
                language.value,
                enabled.isSelected,
                interval.text.toIntOrNull() ?: 0,
                directory,
                recursive.isSelected,
                old?.id ?: java.util.UUID.randomUUID().toString(),
                watchChanges.isSelected,
                delay.text.toIntOrNull() ?: -1)
        val updated = rules.toMutableList()
        if (old == null) updated.add(rule) else updated[updated.indexOf(old)] = rule
        if (!commit(updated)) event.consume()
        else {
          results.remove(rule.id)
          renderResults()
        }
      } catch (e: Exception) {
        validation.text = e.message
        event.consume()
      }
    }
    dialog.showAndWait()
  }

  private fun commit(updated: List<Rule>): Boolean =
      try {
        store.save(updated)
        rules.setAll(updated)
        monitor.updateRules(updated)
        true
      } catch (e: Exception) {
        error("Unable to save rules: ${e.message}")
        false
      }

  private fun renderResults() {
    val rule = list.selectionModel.selectedItem
    val result = rule?.let { results[it.id] }
    violations.setAll(result?.issues ?: emptyList())
    val latest = rule?.let { selected -> runs.firstOrNull { it.rule == selected } }
    summary.text =
        when {
          rule == null -> "Select a rule to see its current violations."
          latest?.error != null ->
              "Check failed: ${latest.error}. " +
                  (if (result == null) "No successful check available."
                  else "Showing last successful check from ${result.checkedAt}.")
          result == null -> "${rule.name}: not checked yet. Click Check now to scan."
          else ->
              "${rule.name}: ${result.issues.size} violation(s) · Last checked ${result.checkedAt}"
        }
  }

  private fun error(message: String) {
    Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait()
  }

  override fun stop() {
    if (::monitor.isInitialized) monitor.close()
    tray?.let { SystemTray.getSystemTray().remove(it) }
    lock?.release()
    channel?.close()
  }
}
