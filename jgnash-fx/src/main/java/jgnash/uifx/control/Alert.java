/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2015 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.uifx.control;

import java.io.IOException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import jgnash.uifx.MainApplication;
import jgnash.util.NotNull;
import jgnash.util.ResourceUtils;

import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.GlyphFont;
import org.controlsfx.glyphfont.GlyphFontRegistry;

/**
 * A Better behaved Alert class
 *
 * @author Craig Cavanaugh
 */
public class Alert {

    private static final int ICON_SIZE = 48;

    public static enum AlertType {
        ERROR,
        WARNING,
        INFORMATION,
        YES_NO
    }

    AlertType alertType;

    AlertDialogController alertDialogController;

    Stage dialog;

    public Alert(@NotNull final AlertType alertType, final String contentText) {
        final ResourceBundle resources = ResourceUtils.getBundle();

        dialog = new Stage(StageStyle.DECORATED);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(MainApplication.getPrimaryStage());

        this.alertType = alertType;

        try {
            final FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("AlertDialog.fxml"), ResourceUtils.getBundle());
            dialog.setScene(new Scene(fxmlLoader.load()));
            alertDialogController = fxmlLoader.getController();
        } catch (final IOException e) {
            Logger.getLogger(Alert.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        }

        final GlyphFont fontAwesome = GlyphFontRegistry.font("FontAwesome");

        switch (alertType) {
            case ERROR:
                alertDialogController.setGraphic(fontAwesome.create(FontAwesome.Glyph.EXCLAMATION_TRIANGLE).color(Color.DARKRED).size(ICON_SIZE));
                setButtons(new ButtonType(resources.getString("Button.Close"), ButtonBar.ButtonData.CANCEL_CLOSE));
                break;
            case WARNING:
                alertDialogController.setGraphic(fontAwesome.create(FontAwesome.Glyph.EXCLAMATION_CIRCLE).color(Color.DARKGOLDENROD).size(ICON_SIZE));
                setButtons(new ButtonType(resources.getString("Button.Close"), ButtonBar.ButtonData.CANCEL_CLOSE));
                break;
            case INFORMATION:
                alertDialogController.setGraphic(fontAwesome.create(FontAwesome.Glyph.INFO_CIRCLE).color(Color.DARKGOLDENROD).size(ICON_SIZE));
                setButtons(new ButtonType(resources.getString("Button.Close"), ButtonBar.ButtonData.CANCEL_CLOSE));
                break;
            case YES_NO:
                alertDialogController.setGraphic(fontAwesome.create(FontAwesome.Glyph.QUESTION_CIRCLE).size(ICON_SIZE));
                ButtonType buttonTypeYes = new ButtonType(resources.getString("Button.Yes"), ButtonBar.ButtonData.YES);
                ButtonType buttonTypeNo = new ButtonType(resources.getString("Button.No"), ButtonBar.ButtonData.NO);
                setButtons(buttonTypeYes, buttonTypeNo);
                break;
            default:
        }

        setContentText(contentText);
    }

    public void setTitle(final String title) {
        dialog.setTitle(title);
    }

    public void setContentText(final String message) {
        alertDialogController.setContentText(message);
    }

    public void initOwner(final Window window) {
        dialog.initOwner(window);
    }

    public void setButtons(final ButtonType... buttons) {
        alertDialogController.setButtons(buttons);
    }

    public Optional<ButtonType> showAndWait() {
        dialog.setResizable(false);
        dialog.showAndWait();
        return alertDialogController.getButtonType();
    }
}
