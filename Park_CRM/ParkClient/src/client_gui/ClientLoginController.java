package client_gui;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Alert.AlertType;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.util.Pair;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;

import client_core.ClientCore;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for the Bpark Client Login screen.
 * Handles input validation, connection to the server, and login command dispatch.
 */
/**
 * Controller for the Bpark Client Login screen.
 * Handles:
 * - Validating login inputs
 * - Connecting to the server
 * - Handling user authentication
 * - Theme switching
 * - QR login dialog
 */
public class ClientLoginController {
    
    @FXML private AnchorPane mainScreen;
    @FXML private ToggleButton themeToggle;
    @FXML private ImageView QRloginBtn;

    @FXML private TextField c_email;
    @FXML private TextField c_password;
    @FXML private TextField c_sip;
    @FXML private TextField c_sport;
    @FXML private Button loginBtn;

    private ClientCore clientCore;
    private Pair<Integer, String> response;
    private String useremail;
    private String userpassword;
    private String serverIp;
    private String serverPort;
    private Image qrDefault;
    private Image qrHover;

    /**
     * Initializes the screen when it's loaded.
     * Sets default server fields, disables manual editing, and pre-fills debug login.
     */
    @FXML
    private void initialize() {
        switchTheme();
        debugFiller();
    }


    /**
     * Triggered when the login button is clicked.
     * Validates input fields and attempts to connect to the server using {@link ClientCore}.
     * Displays a welcome message and loads the main menu on successful login.
     * On failure, an alert with the server's response message is shown.
     */
    @FXML
    private void handleloginBtn() {
        String useremail = c_email.getText().trim();
        String userpassword = c_password.getText().trim();
        String serverIp = c_sip.getText().trim();
        String serverPort = c_sport.getText().trim();

        if (!validInputCheck(serverIp, serverPort, useremail, userpassword)) { return; }

        try {
            clientCore = new ClientCore(serverIp, Integer.parseInt(serverPort));
            response = clientCore.logIn(useremail, userpassword, false);
            if (response.getKey() == 200) {
                showAlert("✔️ Login successfull ", "Welcome " + clientCore.getSubscriberName(), AlertType.INFORMATION);
                loadMainMenu();
            }
            else if (response.getKey() == 403) {
                if (handleDoubleSession()) {
                    response = clientCore.logIn(useremail, userpassword, true);
                    if (response.getKey() == 200) {
                        showAlert("✔️ Login successful", "Welcome " + clientCore.getSubscriberName(), AlertType.INFORMATION);
                        loadMainMenu();
                    } else {
                        showAlert("❌ ERROR " + response.getKey(), response.getValue(), AlertType.ERROR);
                    }
                }
            }

            else {
                showAlert("❌ ERROR " + response.getKey(), response.getValue(), AlertType.ERROR);
            }
        } catch (IOException e) {
            showAlert("❌ ERROR: ", "Server Side Error, Try again later", AlertType.ERROR);
        }
    }

    /**
     * Validates login form inputs, including server IP, port number, and user credentials.
     * Shows alerts for empty fields, non-numeric ports, or invalid port range.
     *
     * @param serverIp     The server IP address as a string.
     * @param serverPort   The server port as a string (should be an integer between 1024 and 65535).
     * @param useremail    The user's email input.
     * @param userpassword The user's password input.
     * @return true if all inputs are valid; false otherwise.
     */
    private boolean validInputCheck(String serverIp, String serverPort, String useremail, String userpassword) {
        boolean valid = true;
        boolean portalert = false;

        if (serverPort.isEmpty() || serverIp.isEmpty() || useremail.isEmpty() || userpassword.isEmpty()) {
            valid = false;
        }

        try {
            int num = Integer.parseInt(serverPort);
            if (num < 1024 || num > 65535) {
                valid = false;
                showAlert("❌ Invalid port", "Port number out of range.", AlertType.ERROR);
            }
        } catch (NumberFormatException e) {
            portalert = true;
            showAlert("❌ Invalid port", "Port number must be integer.", AlertType.ERROR);
        }

        if (!valid && !portalert) {
            showAlert("❌ Missing/incorrent Fields", "Please fill/fix all required fields.", AlertType.ERROR);
        }

        return valid;
    }


    /**
     * Loads the appropriate FXML screen based on the subscriber's status ("user" or "admin").
     * Transfers the current {@link ClientCore} and stage to the new controller,
     * applies the selected theme, and initializes the new screen.
     */
    private void loadMainMenu() {
        try {
            String fxmlPath;
            if (clientCore.getSubscriberStatus().equals("user")) {
                fxmlPath = "fxml/UserScreens.fxml";
            } else {
                fxmlPath = "fxml/ManagerScreens.fxml";
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            AbstractScreensController controller = loader.getController();
            controller.setClientCore(this.clientCore);

            Stage stage = (Stage) loginBtn.getScene().getWindow();
            Scene scene = new Scene(root);
            AbstractScreensController.applyTheme(scene, themeToggle.isSelected());

            // Set stage and THEN pass it to controller
            stage.setScene(scene);
            stage.setTitle("Bpark");
            stage.setResizable(false);
            stage.show();

            // ✅ Only now root has a scene and stage is set
            controller.setStage(stage);
            controller.setStageAndHandleClose(root);

        } catch (IOException e) {
            System.err.println("❌ Failed to load FXML: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Pre-fills the login form with debug credentials (admin/admin).
     * Used for local development and testing.
     */
    private void debugFiller() {
        c_sip.setText("127.0.0.1");
        c_sport.setText("5555");
    }

    /**
     * Displays a custom alert dialog with a styled theme and icon.
     * Supports light/dark themes and different icons per {@link AlertType}.
     *
     * @param title   The title of the alert dialog.
     * @param message The message to show inside the alert.
     * @param type    The type of alert (e.g., ERROR, INFORMATION).
     */
    private void showAlert(String title, String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        DialogPane dialogPane = alert.getDialogPane();
        String theme = themeToggle.isSelected() ? "fxml/style_dark.css" : "fxml/style_light.css";
        dialogPane.getStylesheets().add(getClass().getResource(theme).toExternalForm());
        dialogPane.getStyleClass().add("custom-alert");

        String iconName = switch (type) {
            case INFORMATION -> "Bpark_INFORMATION.png";
            case WARNING -> "Bpark_WARNING.png";
            case ERROR -> "Bpark_ERROR.png";
            case CONFIRMATION -> "Bpark_CONFIRMATION.png";
            default -> null;
        };

        if (iconName != null) {
            // ✅ Load image from resources (inside JAR)
            URL iconUrl = getClass().getResource("/lib/" + iconName);
            if (iconUrl != null) {
                ImageView icon = new ImageView(new Image(iconUrl.toExternalForm()));
                icon.setFitHeight(50);
                icon.setFitWidth(50);
                alert.setGraphic(icon);
            } else {
                System.err.println("❌ Icon resource not found: /lib/" + iconName);
            }
        }

        // ✅ Set window icon (also from resource)
        Stage stage = (Stage) dialogPane.getScene().getWindow();
        URL windowIconUrl = getClass().getResource("/lib/Bpark_client_icon.png");
        if (windowIconUrl != null) {
            stage.getIcons().add(new Image(windowIconUrl.toExternalForm()));
        } else {
            System.err.println("❌ Window icon resource not found: /lib/Bpark_client_icon.png");
        }

        alert.showAndWait();
    }

    
    /**
     * Handles the QR login button click event.
     * This method generates a login token, loads the QR code screen, and displays it in a new stage.
     * The QR code screen is styled based on the current theme (dark or light).
     *
     * @param event The `MouseEvent` triggered by clicking the QR login button.
     */
    @FXML
    private void handleQRloginBtn(MouseEvent event) {
        try {
            // Generate token (could be UUID, timestamped string, etc.)
            String loginToken = "https://www.linkedin.com/in/galmitrani1/";

            // Load the QR screen
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/QR.fxml"));
            Parent root = loader.load();

            // Pass the token to the QR controller
            QRcontroller qrController = loader.getController();
            qrController.setTheme(themeToggle.isSelected());
            qrController.initToken(loginToken);

            // Create and configure the QR code stage
            Stage qrStage = new Stage();
            qrStage.setResizable(false);
            qrStage.setTitle("QR Code Login");

            // Set the stage icon
            try {
                Image icon = new Image(getClass().getResourceAsStream("/lib/Bpark_client_icon.png"));
                if (!icon.isError()) {
                    qrStage.getIcons().add(icon);
                }
            } catch (Exception e) {
                System.err.println("❌ Icon not found in resources: /lib/Bpark_client_icon.png");
            }

            // Create and style the scene
            Scene conScene = new Scene(root);
            URL css;
            if (themeToggle.isSelected()) {
                css = getClass().getResource("fxml/style_dark.css");
            } else {
                css = getClass().getResource("fxml/style_light.css");
            }

            if (css != null) {
                conScene.getStylesheets().add(css.toExternalForm());
            } else {
                System.err.println("⚠ Could not find stylesheet for connection window.");
            }

            // Show the QR code stage
            qrStage.setScene(conScene);
            qrStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
    /**
     * Switches the application's theme between dark and light modes.
     * This method clears the current stylesheets and applies the selected theme
     * based on the state of the `themeToggle` button. If the scene is not yet attached,
     * the method delays execution using `Platform.runLater`.
     */
    @FXML
    private void switchTheme() {
        Scene scene = mainScreen.getScene();
        if (scene == null) {
            Platform.runLater(this::switchTheme);
            return;
        }

        scene.getStylesheets().clear();
        String themePath = themeToggle.isSelected() ? "fxml/style_dark.css" : "fxml/style_light.css";

        URL cssUrl = getClass().getResource(themePath);
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("❌ Theme file not found: " + themePath);
        }
    }
    
    private boolean handleDoubleSession() {
    	
		Alert alert = new Alert(AlertType.CONFIRMATION);
		alert.setTitle("Other Connection Detected");
		alert.setHeaderText("You are already logged in on another device.");
		alert.setContentText("Do you wish to log out from the other Device and continue?");
		
		DialogPane dialogPane = alert.getDialogPane();
		String themePath;
		if(themeToggle.isSelected()) {
			themePath= "fxml/style_dark.css";
		}
		else {
			themePath= "fxml/style_light.css";
		}
		dialogPane.getStylesheets().add(getClass().getResource(themePath).toExternalForm());
		dialogPane.getStyleClass().add("custom-alert");
		
		//set alert icon
		String iconPath = "/lib/Bpark_CONFIRMATION.png";
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
        
		// Set window icon
		Stage stage = (Stage) dialogPane.getScene().getWindow();
		URL iconUrl = getClass().getResource("/lib/Bpark_client_icon.png");
		if (iconUrl != null) {
			stage.getIcons().add(new Image(iconUrl.toExternalForm()));
		}
		
		return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
	}
    
}
