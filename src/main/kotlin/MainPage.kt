package com.patriktrefil

import javafx.application.Application
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.util.Callback
import javafx.event.EventHandler
import javafx.scene.input.InputEvent
import java.io.File

private const val defaultWindowWidth = 840.0
private const val defaultWindowHeight = 480.0
private const val headingCssClass = "heading"
private const val errorTextFieldCssClass = "error-text-field"
private const val errorLabelCssClass = "error-label"
private const val vboxColStyleClass = "vbox-col"

// TODO: refactor - split the start function

class Rule(
    val name: String,
    val regex: Regex,
    val isEnabled: Boolean,
    val timeBetweenChecksInMinutes: Int
) {
  // TODO: when the instance is created - start periodic checks of rule (possibly using coroutines)
  // TODO: when the rule is edited - rerun the check immediately
}

class MainPage : Application() {
  // TODO: rule list persistence
  private val ruleList = FXCollections.observableArrayList<Rule>()
  private var directoryToMonitor = SimpleObjectProperty<File?>(null)
  private val directoryToMonitorChooser = DirectoryChooser()
  private var selectedRule = SimpleObjectProperty<Rule?>(null)

  override fun start(stage: Stage) {
    val mainGrid = GridPane()
    mainGrid.padding = Insets(10.0)

    val colConstraints = ColumnConstraints()
    colConstraints.hgrow = Priority.ALWAYS
    mainGrid.columnConstraints.add(colConstraints)
    mainGrid.columnConstraints.add(colConstraints)
    mainGrid.columnConstraints.add(colConstraints)

    val rowConstraints = RowConstraints()
    rowConstraints.vgrow = Priority.ALWAYS
    mainGrid.rowConstraints.add(rowConstraints)

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
    val directoryToMonitorOpenChooserButton =
        Button(
            "Browse")
    // this button loses padding for some reason
    val actionHandler = EventHandler<InputEvent> {
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

    // TODO: add ability to edit rules
    // TODO: add time of last check
    // TODO: add list of detected issues in last check
    // TODO: show indicator if a check is currently running

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
    mainGrid.add(leftColVBox, 0, 0)

    val ruleDetailsHeading = Label("Rule details")
    ruleDetailsHeading.styleClass.add(headingCssClass)
    val nameLabel = Label()
    nameLabel.textProperty().bind(
      Bindings.createStringBinding(
        { "Name: ${selectedRule.value?.name}" },
        selectedRule
      ))
    val regexValueLabel = Label()
    regexValueLabel.textProperty().bind(
      Bindings.createStringBinding(
        { "Regex: ${selectedRule.value?.regex?.pattern}"},
        selectedRule
      ))
    val isEnabledValueLabel = Label()
    isEnabledValueLabel.textProperty().bind(
      Bindings.createStringBinding(
        { "Is enabled?: ${if (selectedRule.value?.isEnabled == true) "yes" else "no"}"},
        selectedRule
      ))
    val timeBetweenChecksInMinuteValue = Label()
    timeBetweenChecksInMinuteValue.textProperty().bind(
      Bindings.createStringBinding(
        {"Time between checks: ${selectedRule.value?.timeBetweenChecksInMinutes.toString()} mins"},
        selectedRule
      ))
    val runCheckManuallyButton = Button("Run manually") // TODO: make this button work
    val ruleDetailsContentVBox = VBox(nameLabel, regexValueLabel, isEnabledValueLabel, timeBetweenChecksInMinuteValue, runCheckManuallyButton)
    ruleDetailsContentVBox.spacing = 5.0
    val noRuleSelectedLabel = Label("No rule selected")
    noRuleSelectedLabel.id = "no-rule-selected-label"
    val noRuleSelectedVBox = VBox(noRuleSelectedLabel)
    noRuleSelectedVBox.alignment = Pos.CENTER // Horizontal centering
    VBox.setVgrow(noRuleSelectedVBox, Priority.ALWAYS)

    val rightColVBox = VBox(ruleDetailsHeading, noRuleSelectedVBox)
    rightColVBox.styleClass.add(vboxColStyleClass)
    mainGrid.add(rightColVBox, 2, 0)

    val ruleListHeading = Label("Rule list")
    ruleListHeading.styleClass.add(headingCssClass)
    val ruleList = ListView(ruleList)
    ruleList.cellFactory = Callback { RuleListCell() }
      ruleList.setOnMouseClicked {
        selectedRule.value = ruleList.selectionModel.selectedItem
        rightColVBox.children[1] = ruleDetailsContentVBox
      }
    VBox.setVgrow(ruleList, Priority.ALWAYS)
    val middleColVBox = VBox(ruleListHeading, ruleList)
    middleColVBox.styleClass.add(vboxColStyleClass)
    mainGrid.add(middleColVBox, 1, 0)


    val scene = Scene(mainGrid, defaultWindowWidth, defaultWindowHeight)
    scene.stylesheets.add("mainpage.css")
    stage.title = "File conventions"
    stage.scene = scene
    stage.show()
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
