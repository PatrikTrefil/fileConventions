package com.patriktrefil

import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.io.File
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.InputEvent
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.util.Callback

private const val defaultWindowWidth = 840.0
private const val defaultWindowHeight = 480.0
private const val headingCssClass = "heading"
private const val errorTextFieldCssClass = "error-text-field"
private const val errorLabelCssClass = "error-label"
private const val vboxColStyleClass = "vbox-col"

class MainPage : Application() {
  // TODO: rule list persistence
  private val ruleList = FXCollections.observableArrayList<Rule>()
  private var directoryToMonitor = SimpleObjectProperty<File?>(null)
  private val directoryToMonitorChooser = DirectoryChooser()
  private val selectedRule = SimpleObjectProperty<Rule?>(null)

  override fun start(stage: Stage) {
    val mainGrid = GridPane()
    mainGrid.padding = Insets(0.0, 10.0, 10.0, 10.0)

    val colConstraints = ColumnConstraints()
    colConstraints.hgrow = Priority.ALWAYS
    mainGrid.columnConstraints.add(colConstraints)
    mainGrid.columnConstraints.add(colConstraints)
    mainGrid.columnConstraints.add(colConstraints)

    val rowConstraints = RowConstraints()
    rowConstraints.vgrow = Priority.ALWAYS
    mainGrid.rowConstraints.add(rowConstraints)

    // TODO: add ability to edit rules
    // TODO: add time of last check
    // TODO: add list of detected issues in last check
    // TODO: show indicator if a check is currently running

    mainGrid.add(createLeftColumn(stage), 0, 0)

    val rightColVBox = createRightColumn()
    mainGrid.add(rightColVBox, 2, 0)

    mainGrid.add(createMiddleColumn(rightColVBox), 1, 0)

    val scene = Scene(mainGrid, defaultWindowWidth, defaultWindowHeight)
    scene.stylesheets.add("mainpage.css")

    stage.title = "File conventions"
    stage.scene = scene
    stage.show()

    val trayIcon =
        addSystemTrayIcon(stage) // This will be used to send notifications from RuleBreakNotifier
  }

  private fun addSystemTrayIcon(stage: Stage): TrayIcon {
    val trayIcon = TrayIcon(stage, object {}.javaClass.getResource("/file-check-white.png"), 64, 64)
    val toggleStageVisibility = { if (stage.isShowing) stage.hide() else stage.show() }

    trayIcon.setMouseListener(
        object : MouseListener {
          override fun mouseClicked(e: MouseEvent) {
            if (e.button == MouseEvent.BUTTON1 && !stage.isShowing)
                Platform.runLater { stage.show() }
          }

          override fun mousePressed(e: MouseEvent) {}

          override fun mouseReleased(e: MouseEvent) {}

          override fun mouseEntered(e: MouseEvent) {}

          override fun mouseExited(e: MouseEvent) {}
        })

    val showOrHideMenuItem = MenuItem("Show/hide")
    showOrHideMenuItem.setOnAction { toggleStageVisibility() }

    val quitAppMenuItem = MenuItem("Quit application")
    quitAppMenuItem.setOnAction {
      // Cleanly stop all threads
      Platform.exit()
      trayIcon.hide()
    }

    trayIcon.addMenuItems(showOrHideMenuItem, quitAppMenuItem)

    trayIcon.setTrayIconTooltip(stage.title)

    trayIcon.show()

    return trayIcon
  }

  private fun createLeftColumn(stage: Stage): VBox {
    val addRuleHeading = Label("New rule")
    addRuleHeading.styleClass.add(headingCssClass)

    val ruleNameLabel = Label("Rule name")
    val ruleNameTextField = TextField()
    ruleNameTextField.textProperty().addListener { _ ->
      ruleNameTextField.styleClass.remove(errorTextFieldCssClass)
    }

    // TODO: add simpler alternative to regexes
    val regexLabel = Label("Regex")
    val regexTextField = TextField()
    regexTextField.textProperty().addListener { _ ->
      regexTextField.styleClass.remove(errorTextFieldCssClass)
    }

    val directoryToMonitorLabel = Label("Directory to monitor")
    val directoryToMonitorOpenChooserButton = Button("Browse")
    // this button loses padding for some reason
    val actionHandler =
        EventHandler<InputEvent> {
          val newDirectoryToMonitor = directoryToMonitorChooser.showDialog(stage)
          if (newDirectoryToMonitor != null) {
            directoryToMonitor.value = newDirectoryToMonitor
          }
        }
    directoryToMonitorOpenChooserButton.onKeyPressed = actionHandler
    directoryToMonitorOpenChooserButton.onMouseClicked = actionHandler
    val directoryToMonitorValueLabel = Label()
    directoryToMonitorValueLabel
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                { directoryToMonitor.value?.name ?: "No directory selected" }, directoryToMonitor))
    directoryToMonitor.addListener { _, _, _ ->
      directoryToMonitorValueLabel.styleClass.remove(errorLabelCssClass)
    }

    val timeBetweenChecksInMinutesLabel = Label("Time between checks in minutes")
    val timeBetweenChecksInMinutesTextField = TextField("60")

    val addRuleBtn = Button("Add rule")
    addRuleBtn.maxWidth = Double.MAX_VALUE
    addRuleBtn.padding = Insets(5.0)
    addRuleBtn.setOnMouseClicked {
      if (ruleNameTextField.text.isBlank()) {
        ruleNameTextField.styleClass.add(errorTextFieldCssClass)
        return@setOnMouseClicked
      } else if (regexTextField.text.isBlank()) {
        regexTextField.styleClass.add(errorTextFieldCssClass)
        return@setOnMouseClicked
      } else if (directoryToMonitor.value == null) {
        directoryToMonitorValueLabel.styleClass.add(errorLabelCssClass)
        return@setOnMouseClicked
      }

      val regex: Regex
      try {
        regex = Regex(regexTextField.text)
      } catch (e: Exception) {
        regexTextField.styleClass.add(errorTextFieldCssClass)
        return@setOnMouseClicked
      }

      val timeBetweenChecksInMinutes: Int
      try {
        timeBetweenChecksInMinutes = timeBetweenChecksInMinutesTextField.text.toInt()
      } catch (e: NumberFormatException) {
        timeBetweenChecksInMinutesTextField.styleClass.add(errorTextFieldCssClass)
        return@setOnMouseClicked
      }

      ruleList.add(Rule(ruleNameTextField.text, regex, true, timeBetweenChecksInMinutes))
      regexTextField.text = ""
      ruleNameTextField.text = ""
      directoryToMonitor.value = null
    }

    val directoryToMonitorHBox =
        HBox(directoryToMonitorOpenChooserButton, directoryToMonitorValueLabel)
    directoryToMonitorHBox.alignment = Pos.CENTER_LEFT
    directoryToMonitorHBox.spacing = 10.0

    val contentVbox =
        VBox(
            addRuleHeading,
            VBox(
                ruleNameLabel,
                ruleNameTextField,
            ),
            VBox(
                regexLabel,
                regexTextField,
            ),
            VBox(
                directoryToMonitorLabel,
                directoryToMonitorHBox,
            ),
            VBox(timeBetweenChecksInMinutesLabel, timeBetweenChecksInMinutesTextField))
    contentVbox.spacing = 7.0
    val leftColVBox = VBox(contentVbox, addRuleBtn)
    leftColVBox.styleClass.add(vboxColStyleClass)
    leftColVBox.spacing = 20.0

    return leftColVBox
  }

  private fun createMiddleColumn(rightColVBox: VBox): VBox {
    val ruleListHeading = Label("Rule list")
    ruleListHeading.styleClass.add(headingCssClass)
    val ruleList = ListView(ruleList)
    ruleList.cellFactory = Callback { RuleListCell() }

    val ruleDetailsContentVBox = createRuleDetailsContentVBox()
    ruleList.setOnMouseClicked {
      selectedRule.value = ruleList.selectionModel.selectedItem
      rightColVBox.children[1] =
          ruleDetailsContentVBox // HACK: to improve the quality of the code, the right column
      // should probably be a custom component
    }

    VBox.setVgrow(ruleList, Priority.ALWAYS)
    val middleColVBox = VBox(ruleListHeading, ruleList)
    middleColVBox.styleClass.add(vboxColStyleClass)
    return middleColVBox
  }

  private fun createRuleDetailsContentVBox(): VBox {
    val nameLabel = Label()
    nameLabel
        .textProperty()
        .bind(Bindings.createStringBinding({ "Name: ${selectedRule.value?.name}" }, selectedRule))

    val regexValueLabel = Label()
    regexValueLabel
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                { "Regex: ${selectedRule.value?.regex?.pattern}" }, selectedRule))

    val isEnabledValueLabel = Label()
    isEnabledValueLabel
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                { "Is enabled?: ${if (selectedRule.value?.isEnabled == true) "yes" else "no"}" },
                selectedRule))

    val timeBetweenChecksInMinuteValue = Label()
    timeBetweenChecksInMinuteValue
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                {
                  "Time between checks: ${selectedRule.value?.timeBetweenChecksInMinutes.toString()} mins"
                },
                selectedRule))

    val runCheckManuallyButton = Button("Run manually") // TODO: make this button work
    val ruleDetailsContentVBox =
        VBox(
            nameLabel,
            regexValueLabel,
            isEnabledValueLabel,
            timeBetweenChecksInMinuteValue,
            runCheckManuallyButton)
    ruleDetailsContentVBox.spacing = 5.0

    return ruleDetailsContentVBox
  }

  private fun createRightColumn(): VBox {
    val ruleDetailsHeading = Label("Rule details")
    ruleDetailsHeading.styleClass.add(headingCssClass)
    val noRuleSelectedLabel = Label("No rule selected")
    noRuleSelectedLabel.id = "no-rule-selected-label"
    val noRuleSelectedVBox = VBox(noRuleSelectedLabel)
    noRuleSelectedVBox.alignment = Pos.CENTER // Horizontal centering
    VBox.setVgrow(noRuleSelectedVBox, Priority.ALWAYS)

    val rightColVBox = VBox(ruleDetailsHeading, noRuleSelectedVBox)
    rightColVBox.styleClass.add(vboxColStyleClass)

    return rightColVBox
  }
}

class RuleListCell : ListCell<Rule>() {
  override fun updateItem(item: Rule?, empty: Boolean) {
    super.updateItem(item, empty)
    text =
        if (empty || item == null) {
          null
        } else {
          item.name
        }
  }
}
