package com.patriktrefil

import java.io.File
import javafx.application.Application
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.util.Callback

private const val defaultWindowWidth = 640.0
private const val defaultWindowHeight = 480.0
private const val headingCssClass = "heading"
private const val errorTextFieldCssClass = "error-text-field"
private const val errorLabelCssClass = "error-label"

// TODO: upload to github
// TODO: setup CI with gradlew run, test and ktfmtFormat

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

  override fun start(stage: Stage) {
    val mainGrid = GridPane()
    mainGrid.prefWidth = Double.POSITIVE_INFINITY
    val constraints = ColumnConstraints()
    constraints.hgrow = Priority.ALWAYS
    mainGrid.columnConstraints.add(constraints)
    mainGrid.columnConstraints.add(constraints)

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
            "Browse") // TODO: fix bug - when there is no directory selected and we click add rule -
    // this button loses padding for some reason
    directoryToMonitorOpenChooserButton.setOnMouseClicked {
      val newDirectoryToMonitor = directoryToMonitorChooser.showDialog(stage)
      if (newDirectoryToMonitor != null) {
        directoryToMonitor.value = newDirectoryToMonitor
      }
    }
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

    // TODO: add ability to show details of rules
    // TODO: add ability to edit rules
    // TODO: add button to run the check manually
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
    leftColVBox.styleClass.add("vbox-col")
    leftColVBox.spacing = 20.0
    mainGrid.add(leftColVBox, 0, 0)

    val ruleListHeading = Label("Rule list")
    ruleListHeading.styleClass.add(headingCssClass)
    val ruleList = ListView(ruleList)
    ruleList.cellFactory = Callback { RuleListCell() }
    val rightColVBox = VBox(ruleListHeading, ruleList)
    rightColVBox.styleClass.add("vbox-col")
    mainGrid.add(rightColVBox, 1, 0)

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
