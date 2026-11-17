package fileConventions

import com.trefilsoftware.fileConventions.*
import java.awt.SystemTray
import java.awt.Toolkit
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javafx.application.Application
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.util.StringConverter
import net.harawata.appdirs.AppDirsFactory

class MainPage : Application() {
  private val rules = FXCollections.observableArrayList<Rule>()
  private val list = ListView(rules)
  private val violations = FXCollections.observableArrayList<Issue>()
  private val issues = TableView(violations)
  private val summary = Label()
  private val ruleTitle = Label("Your naming checks, at a glance")
  private val ruleDescription = Label()
  private val overview = Label()
  private val runs = FXCollections.observableArrayList<CheckResult>()
  private val runList = ListView(runs)
  private val runDetails =
      TextArea().apply {
        isEditable = false
        isWrapText = true
      }
  private val notifications = CheckBox("Desktop alerts")
  private val status = Label("Ready")
  private val background = Label()
  private val results = mutableMapOf<String, CheckResult>()
  private val latestResults = mutableMapOf<String, CheckResult>()
  private val checking = mutableSetOf<String>()
  private val check = Button("Check now")
  private val pause = Button("Pause")
  private val welcome = VBox(12.0)
  private lateinit var content: TabPane
  private val store =
      RuleStore(
          File(
              System.getProperty("fileConventions.dataDir")
                  ?: AppDirsFactory.getInstance()
                      .getUserDataDir("File Conventions", null, "Trefil Software")))
  private lateinit var monitor: DirectoryMonitor
  private val settings by lazy { SettingsStore(store.directory) }
  private var tray: java.awt.TrayIcon? = null
  private var channel: FileChannel? = null
  private var lock: FileLock? = null
  private val timeFormat =
      DateTimeFormatter.ofPattern("d MMM, HH:mm:ss").withZone(ZoneId.systemDefault())

  private fun time(value: Instant) = timeFormat.format(value)

  private fun label(text: String, css: String = "muted") =
      Label(text).apply {
        styleClass.add(css)
        isWrapText = true
      }

  private fun spacer() = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }

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
        checking.remove(result.rule.id)
        runs.add(0, result)
        if (runs.size > 100) runs.removeAt(runs.lastIndex)
        latestResults[result.rule.id] = result
        if (result.error == null) results[result.rule.id] = result
        renderResults()
        list.refresh()
        status.text =
            if (result.error != null)
                "Could not check ${result.rule.name}. See Check history for details."
            else "Checked ${result.rule.name} at ${time(result.checkedAt)}"
        if (fresh > 0 && notifications.isSelected)
            tray?.displayMessage(
                "File Conventions",
                "${result.rule.name}: $fresh new naming violation(s). Open the app for details.",
                java.awt.TrayIcon.MessageType.WARNING)
      }
    }
    list.setCellFactory {
      object : ListCell<Rule>() {
        init {
          prefWidthProperty().bind(list.widthProperty().subtract(22))
        }

        override fun updateItem(item: Rule?, empty: Boolean) {
          super.updateItem(item, empty)
          text = null
          accessibleText = null
          graphic =
              if (empty || item == null) null
              else {
                val latest =
                    latestResults[item.id]?.takeIf {
                      it.rule.copy(isEnabled = item.isEnabled) == item
                    }
                val state =
                    when {
                      !item.isEnabled -> "Paused"
                      latest?.error != null -> "Check failed"
                      results[item.id] == null -> "Waiting for first check"
                      else -> "${results[item.id]!!.issues.size} violations"
                    }
                accessibleText =
                    "${item.name}. ${item.pathToMonitor.absolutePath}. $state. Every ${item.timeBetweenChecksInMinutes} minutes."
                VBox(
                    5.0,
                    label(item.name, "rule-name"),
                    Label(item.pathToMonitor.absolutePath).apply {
                      styleClass.add("muted")
                      maxWidth = 270.0
                      tooltip = Tooltip(text)
                    },
                    label("$state · Every ${item.timeBetweenChecksInMinutes} min", "rule-state"))
              }
        }
      }
    }
    list.placeholder = label("Your rules will appear here.\nStart by adding a folder to check.")
    val add =
        Button("+ Add rule").apply {
          styleClass.add("primary")
          maxWidth = Double.MAX_VALUE
          setOnAction { edit(stage, null) }
        }
    val edit =
        Button("Edit rule").apply {
          setOnAction { list.selectionModel.selectedItem?.let { edit(stage, it) } }
        }
    val delete =
        Button("Remove…").apply {
          styleClass.add("danger-button")
          setOnAction {
            list.selectionModel.selectedItem?.let { selected ->
              val answer =
                  Alert(
                          Alert.AlertType.CONFIRMATION,
                          "Stop monitoring “${selected.name}”? Your files will stay untouched.",
                          ButtonType.CANCEL,
                          ButtonType.OK)
                      .apply {
                        initOwner(stage)
                        title = "Remove rule"
                        headerText = "Remove this naming rule?"
                        (dialogPane.lookupButton(ButtonType.OK) as Button).text = "Remove rule"
                      }
                      .showAndWait()
              if (answer.orElse(ButtonType.CANCEL) == ButtonType.OK &&
                  commit(rules.filter { it.id != selected.id })) {
                results.remove(selected.id)
                latestResults.remove(selected.id)
                if (rules.isNotEmpty()) list.selectionModel.selectFirst()
                renderResults()
              }
            }
          }
        }
    pause.setOnAction {
      list.selectionModel.selectedItem?.let { selected ->
        val updated = selected.copy(isEnabled = !selected.isEnabled)
        if (commit(rules.map { if (it.id == selected.id) updated else it })) {
          list.selectionModel.select(updated)
          renderResults()
        }
      }
    }
    check.styleClass.add("primary")
    check.setOnAction {
      list.selectionModel.selectedItem?.let {
        checking.add(it.id)
        status.text = "Checking ${it.name}…"
        renderResults()
        monitor.checkNow(it)
      }
    }
    list.selectionModel.selectedItemProperty().addListener { _, _, value ->
      edit.isDisable = value == null
      delete.isDisable = value == null
      renderResults()
    }
    edit.isDisable = true
    delete.isDisable = true
    notifications.setOnAction {
      try {
        settings.saveNotificationsEnabled(notifications.isSelected)
      } catch (e: Exception) {
        notifications.isSelected = !notifications.isSelected
        error("Unable to save notification setting: ${e.message}")
      }
    }
    val help = Button("How it works").apply { setOnAction { showHelp(stage) } }
    val quit = Button("Quit").apply { setOnAction { Platform.exit() } }
    val header =
        HBox(
            16.0,
            VBox(
                5.0,
                label("File Conventions", "heading"),
                label("Consistent file names. Less checking by hand.")),
            spacer(),
            help,
            quit)
    val sidebar = VBox(14.0, label("MONITORED FOLDERS", "eyebrow"), overview, add, list)
    sidebar.styleClass.add("sidebar")
    sidebar.minWidth = 250.0
    VBox.setVgrow(list, Priority.ALWAYS)
    ruleTitle.styleClass.add("section-heading")
    ruleDescription.styleClass.add("muted")
    ruleDescription.isWrapText = true
    ruleDescription.minHeight = Region.USE_PREF_SIZE
    summary.isWrapText = true
    summary.styleClass.add("summary")
    content = createResultsPane()
    content.minHeight = 0.0
    welcome.children.addAll(
        label("A little consistency goes a long way.", "welcome-heading"),
        label(
            "Keep shared reports, invoices, or project files easy to find. File Conventions flags names that don’t follow your rules."),
        label("1   Choose a folder", "step-title"),
        label("Pick a folder you want to keep organized."),
        label("2   Describe a valid name", "step-title"),
        label("Try a wildcard such as report-????.pdf, or use a regular expression."),
        label("3   Let the app keep watch", "step-title"),
        label("See violations here and get an alert when a new one appears."),
        Button("Add your first rule").apply {
          styleClass.add("primary")
          setOnAction { edit(stage, null) }
        },
        label(
            "Your files stay yours: the app never renames, deletes, or changes them.", "safe-note"))
    welcome.styleClass.add("welcome")
    val welcomeScroll =
        ScrollPane(welcome).apply {
          isFitToWidth = true
          visibleProperty().bind(welcome.visibleProperty())
          managedProperty().bind(welcome.managedProperty())
          styleClass.add("welcome-scroll")
        }
    val actions =
        HBox(8.0, check, pause, edit, spacer(), delete).apply {
          visibleProperty().bind(list.selectionModel.selectedItemProperty().isNotNull)
          managedProperty().bind(visibleProperty())
        }
    val workspace = VBox(14.0, ruleTitle, ruleDescription, actions, welcomeScroll, content)
    workspace.styleClass.add("workspace")
    workspace.minWidth = 400.0
    VBox.setVgrow(content, Priority.ALWAYS)
    VBox.setVgrow(welcomeScroll, Priority.ALWAYS)
    val split = SplitPane(sidebar, workspace).apply { setDividerPositions(0.31) }
    val footer = VBox(5.0, status, background)
    footer.styleClass.add("footer")
    status.isWrapText = true
    background.isWrapText = true
    background.styleClass.add("muted")
    val preferences =
        HBox(
            12.0,
            notifications,
            spacer(),
            label("Checks file names only · Never changes your files"))
    val root = VBox(18.0, header, preferences, split, footer).apply { padding = Insets(24.0) }
    VBox.setVgrow(split, Priority.ALWAYS)
    stage.title = "File Conventions"
    val screen = Screen.getPrimary().visualBounds
    stage.scene =
        Scene(root, minOf(1120.0, screen.width - 40), minOf(780.0, screen.height - 80)).apply {
          stylesheets.add("main.css")
        }
    stage.minWidth = minOf(940.0, screen.width)
    stage.minHeight = minOf(640.0, screen.height - 40)
    stage.icons.add(Image(javaClass.getResourceAsStream("/file-check-white.png")))
    setupTray(stage)
    Platform.setImplicitExit(tray == null)
    background.text =
        if (tray == null) "No system tray available. Keep this window open to keep checking."
        else "Closing this window keeps monitoring in the system tray. Use Quit to stop."
    notifications.isDisable = tray == null
    notifications.tooltip =
        Tooltip(
            if (tray == null)
                "Desktop alerts require a supported system tray. Checks still work here."
            else
                "Alert once per new violation. Unchanged violations won’t repeat alerts during this session.")
    stage.setOnCloseRequest {
      if (tray != null) {
        it.consume()
        stage.hide()
      }
    }
    renderResults()
    if (rules.isNotEmpty()) list.selectionModel.selectFirst()
    stage.show()
    monitor.updateRules(rules.toList())
  }

  private fun showHelp(stage: Stage) {
    val dialog =
        Dialog<ButtonType>().apply {
          initOwner(stage)
          title = "How File Conventions works"
          dialogPane.buttonTypes.add(ButtonType.CLOSE)
          dialogPane.stylesheets.add("main.css")
          dialogPane.prefWidth = 580.0
        }
    val helpContent =
        VBox(
            14.0,
            label("Set a rule. Keep names consistent.", "section-heading"),
            label("1   Add a rule for a folder", "step-title"),
            label(
                "Choose the folder and describe valid file names. Wildcards are a simple start: *.pdf accepts any PDF name; report-????.pdf accepts report-2026.pdf. Matching is case-sensitive."),
            label("2   Try your pattern, then save", "step-title"),
            label(
                "Use the filename tester before saving. The entire name, including its extension, must match. Only regular files are checked; folders and symbolic links are skipped. Include subfolders only if you need them."),
            label("3   Review and resolve", "step-title"),
            label(
                "Select a rule to see violations. Check now refreshes immediately, even for a paused rule. Fix names yourself in your file manager; the next check clears resolved violations."),
            label("While you work", "step-title"),
            label(
                "Enabled rules check on startup, periodically, and after folder changes (with a short settling delay). Desktop alerts appear for new violations; unchanged ones won’t repeat during this session. Check history holds the latest 100 checks until you quit."),
            label(background.text),
            label("Open the app after signing in: installation does not enable automatic startup."),
            label("The app never renames, deletes, or changes your files.", "safe-note"))
    dialog.dialogPane.content =
        ScrollPane(helpContent).apply {
          isFitToWidth = true
          prefViewportHeight = minOf(580.0, Screen.getPrimary().visualBounds.height - 200)
        }
    dialog.showAndWait()
  }

  private fun createResultsPane(): TabPane {
    val fileColumn =
        TableColumn<Issue, String>("File name").apply {
          setCellValueFactory {
            javafx.beans.property.SimpleStringProperty(File(it.value.sourceFilePath).name)
          }
          prefWidth = 210.0
        }
    val pathColumn =
        TableColumn<Issue, String>("Full path").apply {
          setCellValueFactory {
            javafx.beans.property.SimpleStringProperty(it.value.sourceFilePath)
          }
          prefWidth = 400.0
          setCellFactory {
            object : TableCell<Issue, String>() {
              override fun updateItem(item: String?, empty: Boolean) {
                super.updateItem(item, empty)
                text = if (empty) null else item
                tooltip = if (empty || item == null) null else Tooltip(item)
              }
            }
          }
        }
    issues.columns.addAll(fileColumn, pathColumn)
    val copy =
        Button("Copy selected path").apply {
          disableProperty().bind(issues.selectionModel.selectedItemProperty().isNull)
          setOnAction {
            issues.selectionModel.selectedItem?.let {
              Clipboard.getSystemClipboard()
                  .setContent(ClipboardContent().apply { putString(it.sourceFilePath) })
              status.text = "File path copied."
            }
          }
        }
    val current =
        VBox(12.0, summary, issues, copy).apply {
          padding = Insets(16.0, 0.0, 0.0, 0.0)
          VBox.setVgrow(issues, Priority.ALWAYS)
        }
    runList.placeholder = label("No checks yet.\nEnabled rules check automatically after saving.")
    runList.setCellFactory {
      object : ListCell<CheckResult>() {
        override fun updateItem(item: CheckResult?, empty: Boolean) {
          super.updateItem(item, empty)
          text =
              if (empty || item == null) null
              else
                  "${item.rule.name} · ${time(item.checkedAt)}\n${if (item.error != null) "Check failed" else "${item.issues.size} violations"}"
        }
      }
    }
    runList.selectionModel.selectedItemProperty().addListener { _, _, run ->
      runDetails.text =
          if (run == null) "Select a check to see details."
          else
              "Rule: ${run.rule.name}\nChecked: ${time(run.checkedAt)}\nFolder: ${run.rule.pathToMonitor}\nPattern: ${run.rule.pattern}\nInclude subfolders: ${run.rule.recursive}\n\n" +
                  (run.error?.let { "Check failed: $it" }
                      ?: "${run.issues.size} violations\n" +
                          run.issues.joinToString("\n") { it.sourceFilePath })
    }
    runDetails.text = "Select a check to see details."
    val history =
        SplitPane(runList, runDetails).apply { orientation = javafx.geometry.Orientation.VERTICAL }
    val historyPane =
        VBox(12.0, label("Latest 100 checks · This session only"), history).apply {
          padding = Insets(16.0, 0.0, 0.0, 0.0)
          VBox.setVgrow(history, Priority.ALWAYS)
        }
    return TabPane(Tab("Current violations", current), Tab("Check history", historyPane)).apply {
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
    dialog.title = if (old == null) "Add naming rule" else "Edit naming rule"
    dialog.dialogPane.stylesheets.add("main.css")
    val save = ButtonType("Save rule", ButtonBar.ButtonData.OK_DONE)
    dialog.dialogPane.buttonTypes.addAll(save, ButtonType.CANCEL)
    dialog.dialogPane.lookupButton(save).styleClass.add("primary")
    val name = TextField(old?.name ?: "").apply { promptText = "e.g. Monthly reports" }
    val folder =
        TextField(old?.pathToMonitor?.absolutePath ?: "").apply {
          promptText = "Choose a folder to monitor"
        }
    val browse =
        Button("Browse…").apply {
          setOnAction {
            DirectoryChooser()
                .apply { title = "Choose folder to monitor" }
                .showDialog(dialog.dialogPane.scene.window)
                ?.let { folder.text = it.absolutePath }
          }
        }
    HBox.setHgrow(folder, Priority.ALWAYS)
    val language =
        ComboBox(FXCollections.observableArrayList(*PatternLanguage.values())).apply {
          converter =
              object : StringConverter<PatternLanguage>() {
                override fun toString(value: PatternLanguage?) =
                    when (value) {
                      PatternLanguage.Wildcard -> "Wildcard (simple)"
                      PatternLanguage.Regex -> "Regular expression (advanced)"
                      null -> ""
                    }

                override fun fromString(value: String) = PatternLanguage.valueOf(value)
              }
          value = old?.patternLanguage ?: PatternLanguage.Wildcard
          maxWidth = Double.MAX_VALUE
        }
    val pattern = TextField(old?.pattern ?: "").apply { promptText = "e.g. report-????.pdf" }
    val hint = label("")
    val example = TextField().apply { promptText = "e.g. report-2026.pdf" }
    val preview = label("Enter a pattern and a sample name to try it.")
    val previewWorker = PatternPreview()
    var previewVersion = 0
    dialog.setOnHidden {
      previewVersion++
      previewWorker.close()
    }
    fun updatePreview() {
      pattern.promptText =
          if (language.value == PatternLanguage.Wildcard) "e.g. report-????.pdf"
          else "e.g. invoice-[0-9]{4}\\.pdf"
      hint.text =
          if (language.value == PatternLanguage.Wildcard)
              "* = any number of characters; ? = one character. Example: report-????.pdf"
          else "Java regex. Example: invoice-[0-9]{4}\\.pdf accepts invoice-2026.pdf."
      preview.styleClass.removeAll("error-label", "success-label")
      val version = ++previewVersion
      val blank = pattern.text.isBlank()
      preview.text = if (blank) "Enter a pattern to try it." else "Testing pattern…"
      previewWorker.request(pattern.text, language.value, example.text) { result ->
        Platform.runLater {
          if (version != previewVersion || !dialog.isShowing) return@runLater
          preview.styleClass.removeAll("error-label", "success-label")
          preview.text =
              when {
                blank -> "Enter a pattern to try it."
                result.error != null -> "Invalid pattern: ${result.error}"
                result.matches == null -> "Enter a sample name to try this pattern."
                result.matches -> "Matches — this name is allowed."
                else -> "Does not match — this name would be a violation."
              }
          if (!blank && (result.error != null || result.matches != null))
              preview.styleClass.add(if (result.matches == true) "success-label" else "error-label")
        }
      }
    }
    pattern.textProperty().addListener { _, _, _ -> updatePreview() }
    example.textProperty().addListener { _, _, _ -> updatePreview() }
    language.valueProperty().addListener { _, _, _ -> updatePreview() }
    updatePreview()
    val interval = TextField(old?.timeBetweenChecksInMinutes?.toString() ?: "60")
    val recursive =
        CheckBox("Include files in subfolders").apply { isSelected = old?.recursive ?: false }
    val enabled = CheckBox("Monitor this folder").apply { isSelected = old?.isEnabled ?: true }
    val watchChanges =
        CheckBox("Also check after file names change").apply {
          isSelected = old?.watchChanges ?: true
        }
    val delay = TextField(old?.notificationDelaySeconds?.toString() ?: "7")
    delay.disableProperty().bind(watchChanges.selectedProperty().not())
    val advanced =
        TitledPane(
                "Monitoring options",
                VBox(
                    10.0,
                    enabled,
                    watchChanges,
                    label("Wait after changes (seconds, 0–3600)", "field-label"),
                    delay,
                    label(
                        "Waits for names to settle before checking. Periodic checks stay active.")))
            .apply {
              isExpanded =
                  old != null &&
                      (!old.watchChanges || !old.isEnabled || old.notificationDelaySeconds != 7)
            }
    val validation =
        label("", "error-label").apply {
          isVisible = false
          managedProperty().bind(visibleProperty())
        }
    val form =
        VBox(
                10.0,
                label("Describe the names you want to keep.", "section-heading"),
                label(
                    "Files that don’t match will be listed as violations. Your files are never changed."),
                label("Rule name", "field-label").apply { labelFor = name },
                name,
                label("Folder", "field-label").apply { labelFor = folder },
                HBox(8.0, folder, browse),
                recursive,
                label("Pattern type", "field-label").apply { labelFor = language },
                language,
                label("Valid file name pattern", "field-label").apply { labelFor = pattern },
                pattern,
                hint,
                label("Matches the whole file name, including extension. Case-sensitive."),
                label("Try a file name (optional)", "field-label").apply { labelFor = example },
                example,
                preview,
                label("Check every (minutes, 1–525600)", "field-label").apply {
                  labelFor = interval
                },
                interval,
                advanced,
                validation)
            .apply { padding = Insets(8.0, 16.0, 8.0, 0.0) }
    val scroll =
        ScrollPane(form).apply {
          isFitToWidth = true
          prefViewportHeight = minOf(620.0, Screen.getPrimary().visualBounds.height - 200)
        }
    dialog.dialogPane.content = scroll
    dialog.dialogPane.prefWidth = 620.0
    dialog.dialogPane.lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION) { event ->
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
          if (old != rule) {
            results.remove(rule.id)
            latestResults.remove(rule.id)
          }
          list.selectionModel.select(rule)
          content.selectionModel.selectFirst()
          status.text =
              if (old == rule) "Rule saved. Monitoring settings are unchanged."
              else if (rule.isEnabled) "Rule saved. The first check will start shortly."
              else "Rule saved and paused. Use Check now for a one-time check."
          renderResults()
        }
      } catch (e: Exception) {
        validation.text = e.message
        validation.isVisible = true
        scroll.vvalue = 1.0
        event.consume()
      }
    }
    dialog.setOnShown { name.requestFocus() }
    dialog.showAndWait()
  }

  private fun commit(updated: List<Rule>): Boolean =
      try {
        val selectedId = list.selectionModel.selectedItem?.id
        store.save(updated)
        checking.removeAll { id ->
          rules.firstOrNull { it.id == id } != updated.firstOrNull { it.id == id }
        }
        rules.setAll(updated)
        list.selectionModel.select(updated.firstOrNull { it.id == selectedId })
        monitor.updateRules(updated)
        renderResults()
        true
      } catch (e: Exception) {
        error("Unable to save rules: ${e.message}")
        false
      }

  private fun renderResults() {
    val rule = list.selectionModel.selectedItem
    val result =
        rule
            ?.let { results[it.id] }
            ?.takeIf { it.rule == rule || it.rule.copy(isEnabled = rule.isEnabled) == rule }
    violations.setAll(result?.issues ?: emptyList())
    val latest =
        rule?.let { selected ->
          latestResults[selected.id]?.takeIf {
            it.rule.copy(isEnabled = selected.isEnabled) == selected
          }
        }
    val empty = rules.isEmpty()
    welcome.isVisible = empty
    welcome.isManaged = empty
    if (::content.isInitialized) {
      content.isVisible = !empty
      content.isManaged = !empty
    }
    overview.text =
        "${rules.count { it.isEnabled }} active · ${rules.count { !it.isEnabled }} paused"
    ruleTitle.text =
        rule?.name ?: if (empty) "Welcome to File Conventions" else "Select a naming rule"
    ruleDescription.text =
        rule?.let {
          "${it.pathToMonitor}\n${if (it.patternLanguage == PatternLanguage.Wildcard) "Wildcard" else "Regex"}: ${it.pattern} · ${if (it.recursive) "Including subfolders" else "This folder only"}"
        } ?: "Choose what a good file name looks like. We’ll flag the exceptions."
    check.isDisable = rule == null || rule.id in checking
    check.text = if (rule?.id in checking) "Checking…" else "Check now"
    pause.isDisable = rule == null
    pause.text = if (rule?.isEnabled == false) "Resume" else "Pause"
    summary.styleClass.removeAll("error-summary", "success-summary")
    summary.text =
        when {
          rule == null -> "Select a rule from the left to see its current violations."
          rule.id in checking -> "Checking file names…"
          latest?.error != null ->
              "Check failed: ${latest.error}\n" +
                  (if (result == null)
                      "No successful check available. Check folder access, then try Check now."
                  else
                      "Showing previous results from ${time(result.checkedAt)}. They may be out of date.")
          result == null ->
              if (rule.isEnabled) "Waiting for the first check. You can also use Check now."
              else "This rule is paused. Use Check now to check once, or Resume to monitor."
          else ->
              "${if (result.issues.isEmpty()) "All names match" else "${result.issues.size} naming violation(s)"} · Checked ${time(result.checkedAt)}" +
                  if (!rule.isEnabled)
                      "\nMonitoring paused. These results won’t update automatically."
                  else ""
        }
    if (latest?.error != null) summary.styleClass.add("error-summary")
    else if (result != null && result.issues.isEmpty()) summary.styleClass.add("success-summary")
    issues.placeholder =
        label(
            when {
              rule == null -> "Choose a rule to see results."
              result == null -> "Results will appear after a successful check."
              latest?.error != null ->
                  "Previous check found no violations.\nThe latest check failed; results may be out of date."
              else -> "No naming violations.\nEvery file name matched this rule at the last check."
            })
  }

  private fun error(message: String) {
    Alert(Alert.AlertType.ERROR, message, ButtonType.OK)
        .apply {
          title = "File Conventions"
          headerText = "Something needs your attention"
        }
        .showAndWait()
  }

  override fun stop() {
    if (::monitor.isInitialized) monitor.close()
    tray?.let { SystemTray.getSystemTray().remove(it) }
    lock?.release()
    channel?.close()
  }
}
