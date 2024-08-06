package com.patriktrefil

import com.dustinredmond.fxtrayicon.FXTrayIcon
import java.awt.event.MouseListener
import java.net.URL
import javafx.stage.Stage

class TrayIcon(parentStage: Stage?, iconFile: URL?, iconWidth: Int, iconHeight: Int) :
    FXTrayIcon(parentStage, iconFile, iconWidth, iconHeight) {
  fun setMouseListener(listener: MouseListener) {
    getTrayIcon().addMouseListener(listener)
  }
}
