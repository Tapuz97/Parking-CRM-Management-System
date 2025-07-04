package client_gui;

import client_core.ClientCore;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Abstract base controller class for all GUI screens in the client.
 * Manages core utilities like stage handling, scene switching, alert display, input validation, and theming.
 */
public abstract class AbstractScreensController {

    /** ClientCore instance for communicating with the server. */
    protected ClientCore clientCore;

    /** JavaFX Stage representing the current window. */
    protected Stage stage;

    /**
     * Sets the ClientCore instance used for server communication.
     *
     * @param clientCore The ClientCore instance.
     */
    public void setClientCore(ClientCore clientCore) {
        this.clientCore = clientCore;
    }

    /**
     * Sets the JavaFX stage for this controller and attaches a close handler.
     * Automatically calls {@code clientCore.logOut()} if the user closes the window.
     *
     * @param rootNode A node from the current scene, used to get the stage.
     */
    public void setStageAndHandleClose(Node rootNode) {
        this.stage = (Stage) rootNode.getScene().getWindow();
        Platform.runLater(() -> {
            stage.setOnCloseRequest(event -> {
                if (this.clientCore != null && this.clientCore.isConnected()) {
                    try {
                        this.clientCore.logOut();
                    } catch (Exception e) {
                        System.out.println("❗ Error during auto logout: " + e.getMessage());
                    }
                }
            });
        });
    }

    /**
     * Switches to a new scene defined by an FXML file, and applies stage settings.
     *
     * @param fxmlPath The relative path to the FXML file.
     * @param title    The title to be displayed on the stage window.
     */
    protected void switchScene(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Scene newScene = new Scene(root);

            stage.setScene(newScene);
            stage.setTitle(title);
            stage.setResizable(false);
            stage.show();

            AbstractScreensController controller = loader.getController();
            controller.setClientCore(this.clientCore);
            controller.setStage(this.stage);

        } catch (IOException e) {
            System.err.println("❌ Failed to load " + fxmlPath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sets the JavaFX stage for this controller.
     *
     * @param stage The JavaFX stage to assign.
     */
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /**
     * Displays a styled alert popup with optional dark/light theme and icon.
     *
     * @param title        Title of the alert dialog.
     * @param message      Main content/message of the alert.
     * @param type         Type of alert (e.g., ERROR, INFORMATION).
     * @param isDarkTheme  Whether to use the dark theme or light theme.
     */
    protected void showAlert(String title, String message, AlertType type, boolean isDarkTheme) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Style
        DialogPane dialogPane = alert.getDialogPane();
        if (isDarkTheme) {
            dialogPane.getStylesheets().add(
                getClass().getResource("fxml/style_dark.css").toExternalForm()
            );
        } else {
            dialogPane.getStylesheets().add(
                getClass().getResource("fxml/style_light.css").toExternalForm()
            );
        }
        dialogPane.getStyleClass().add("custom-alert");

        // Set graphic based on alert type
        String iconName = switch (type) {
            case INFORMATION -> "Bpark_INFORMATION.png";
            case WARNING     -> "Bpark_WARNING.png";
            case ERROR       -> "Bpark_ERROR.png";
            case CONFIRMATION-> "Bpark_CONFIRMATION.png";
            default          -> null;
        };
        
        if (iconName != null) {
            String iconPath = "/lib/" + iconName;
            try {
                Image iconImage = new Image(getClass().getResourceAsStream(iconPath));
                if (iconImage.isError()) throw new Exception(); // force fallback
                ImageView icon = new ImageView(iconImage);
                icon.setFitHeight(50);
                icon.setFitWidth(50);
                alert.setGraphic(icon);
            } catch (Exception e) {
                System.err.println("❌ Alert icon not found in resources: " + iconPath);
            }
        }

        // Set window icon (top-left of window)
        try {
            Image windowIcon = new Image(getClass().getResourceAsStream("/lib/Bpark_client_icon.png"));
            if (!windowIcon.isError()) {
                ((Stage) dialogPane.getScene().getWindow()).getIcons().add(windowIcon);
            }
        } catch (Exception e) {
            System.err.println("❌ Window icon not found in resources: /lib/Bpark_client_icon.png");
        }
        alert.showAndWait();
    }

    /**
     * Checks whether all the given input strings are non-null and non-empty.
     * If any are invalid, shows an error alert using {@code showAlert()}.
     *
     * @param inputs       Array of input values to validate.
     * @param isDarkTheme  Whether to style the alert using the dark theme.
     * @return true if all inputs are valid; false otherwise.
     */
    protected boolean areValidInputs(String[] inputs, boolean isDarkTheme) {
        for (String input : inputs) {
            if (input == null || input.trim().isEmpty()) {
                showAlert("Invalid Input", "All fields must be filled out.", Alert.AlertType.ERROR, isDarkTheme);
                return false;
            }
        }
        return true;
    }

    /**
     * Applies either the dark or light theme to the given JavaFX scene.
     *
     * @param scene   The JavaFX scene to style.
     * @param isDark  If true, applies the dark theme; otherwise applies the light theme.
     */
    protected static void applyTheme(Scene scene, boolean isDark) {
        if (scene == null) return;
        scene.getStylesheets().clear();

        String path = isDark ? "fxml/style_dark.css" : "fxml/style_light.css";
        URL css = AbstractScreensController.class.getResource(path);

        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        } else {
            System.err.println("❌ Could not find stylesheet: " + path);
        }
    }
}
