package server_gui;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import server_core.DBconnector;
import server_core.ServerCore;

/**
 * Controller for the Bpark Server GUI.
 * Handles server/database connection, theme switching, API setup, and live connections window.
 */
public class ServerGUIController {

    @FXML private AnchorPane mainScreen;
    @FXML private ToggleButton themeToggle;

    // ================================ FXML UI Components ================================
    @FXML private AnchorPane rootPane;
    @FXML private TextField S_ip;
    @FXML private TextField S_port;
    @FXML private TextField DB_user;
    @FXML private TextField DB_pass;
    @FXML private TextField DB_ip;
    @FXML private TextField DB_port;
    @FXML private TextField DB_name;
    @FXML private TextField RecoveryApiKey;
    @FXML private TextField OrdersApiKey;
    @FXML private Button connectBtn;
    @FXML private Button conBtn;

    // ================================ Server/DB Runtime State ================================
    private String serverIp;
    private String serverport;
    private boolean isServerRunning = false;
    private String dbIp;
    private String dbPort;
    private String dbUser;
    private String dbPassword;
    private String dbName;
    private String dbUrl;
    private Stage Constage;
    private DBconnector db;
    private ServerCore serverCore;
    private ServerConnectionsController connectionsController;
    

    /**
     * Pre-fills debug/default credentials for faster testing
     */
    private void debugFiller() {

        S_ip.setText("0.0.0.0");
        S_port.setText("5555");
        DB_user.setText("root");
        DB_pass.setText("Aa123456");
        DB_ip.setText("127.0.0.1");
        DB_port.setText("3306");
        DB_name.setText("park_db");
    }

    /**
     * Called on screen load. Initializes theme and sets exit behavior.
     */
    @FXML
    public void initialize() {
        switchTheme();
        debugFiller();
        conBtn.setDisable(true);

        Platform.runLater(() -> {
            Stage stage = (Stage) connectBtn.getScene().getWindow();
            stage.setOnCloseRequest(event -> handleDisconnect());
        });
    }

    /**
     * Handles click on connect/disconnect button.
     */
    @FXML
    private void handleConnectBtn() {
        if (!isServerRunning) {
            handleConnect();
        } else {
            handleDisconnect();
        }
    }

    /**
     * Validates input and starts DB and server connections.
     */
    private void handleConnect() {
        String serverIp = S_ip.getText().trim();
        String serverport = S_port.getText().trim();
        String dbIp = DB_ip.getText().trim();
        String dbPort = DB_port.getText().trim();
        String dbUser = DB_user.getText().trim();
        String dbPassword = DB_pass.getText().trim();
        String dbName = DB_name.getText().trim();

        if (!inputCheck(serverIp, serverport, dbIp, dbPort, dbUser, dbPassword, dbName)) return;

        dbUrl = "jdbc:mysql://" + dbIp + ":" + dbPort + "/" + dbName +
                "?serverTimezone=Asia/Jerusalem&allowLoadLocalInfile=true&useSSL=false";

        try {
            db = new DBconnector();
        } catch (ClassNotFoundException e) {
            showAlert("❌ Driver Error", "MySQL JDBC driver not found.", AlertType.ERROR);
            return;
        }

        if (db.connect(dbUrl, dbUser, dbPassword)) {
            serverCore = new ServerCore(serverIp, Integer.parseInt(serverport), db.getConnection());
            isServerRunning = serverCore.start();
            if (isServerRunning) {
                enableInput(false);
                showAlert("✔️ Success", "Connected to database!", AlertType.INFORMATION);
                connectBtn.setText("Disconnect");

                if (RecoveryApiKey.getText().trim().isEmpty() || OrdersApiKey.getText().trim().isEmpty()) {
                    showAlert("API Keys Missing", "Mailing services wouldn't run", AlertType.WARNING);
                } else {
                	if (serverCore.setAPIkeys(RecoveryApiKey.getText().trim(), OrdersApiKey.getText().trim())) {
                		showAlert("API Key Validation", "API Keys Validated\nMailing services is running.", AlertType.INFORMATION);
                	}
                	else {
						showAlert("API Key Validation", "Unable to validate API key\nMailing services wouldn't run.", AlertType.WARNING);
						return;
					}
                }
            } else {
                showAlert("❌ Server Error", "Connected to DB, but failed to start server.", AlertType.ERROR);
            }
        } else {
            showAlert("❌ Database Error", "Could not connect to database.", AlertType.ERROR);
        }
    }

    /**
     * Disconnects from DB and stops the server.
     */
    private void handleDisconnect() {
        if (db != null) db.disconnect();
        if (serverCore != null) serverCore.stop();

        enableInput(true);
        connectBtn.setText("Connect");
        isServerRunning = false;
        if (Constage != null) Constage.close();
        showAlert("✔️ Disconnected", "The server and database have been disconnected.", AlertType.INFORMATION);
    }

    /**
     * Opens a new window showing live client connections.
     */
    @FXML
    private void handleShowConnections() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/ServerConnection.fxml"));
            Parent root = loader.load();
            connectionsController = loader.getController();
            connectionsController.setServerCore(serverCore);
            connectionsController.ShowConnectionLog();
            Constage = new Stage();
            Constage.setResizable(false);
            Constage.setTitle("Live Connections");

            URL iconUrl = getClass().getResource("/lib/Bpark_server_icon.png");
            if (iconUrl != null) {
                Constage.getIcons().add(new Image(iconUrl.toExternalForm()));
            } else {
                System.err.println("❌ Window icon resource not found: /lib/Bpark_server_icon.png");
            }

            Scene conScene = new Scene(root);
            URL css = getClass().getResource(themeToggle.isSelected() ? "fxml/style_dark.css" : "fxml/style_light.css");
            if (css != null) {
                conScene.getStylesheets().add(css.toExternalForm());
            }

            Constage.setScene(conScene);
            Constage.setOnCloseRequest(e -> conBtn.setDisable(false));
            Constage.show();
            conBtn.setDisable(true);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Validates all necessary input fields before connection.
     */
    private boolean inputCheck(String serverIp, String serverPort, String dbIp, String dbPort,
                               String dbUser, String dbPassword, String dbName) {
        boolean valid = true;

        if (serverPort.isEmpty() || dbIp.isEmpty() || dbPort.isEmpty() || dbUser.isEmpty() || dbName.isEmpty()) {
            valid = false;
        }

        if (serverIp.isEmpty()) {
            this.serverIp = "0.0.0.0";
        }

        try {
            int sPort = Integer.parseInt(serverPort);
            int dPort = Integer.parseInt(dbPort);
            if (sPort < 1024 || sPort > 65535 || dPort < 1024 || dPort > 65535) {
                valid = false;
            }
        } catch (NumberFormatException e) {
            valid = false;
        }

        if (!valid) {
            showAlert("❌ Missing/Incorrect Fields", "Please fill/fix all required fields.", AlertType.ERROR);
        }

        return valid;
    }

    /**
     * Displays styled popup alerts with custom icons.
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
        URL windowIconUrl = getClass().getResource("/lib/Bpark_server_icon.png");
        if (windowIconUrl != null) {
            stage.getIcons().add(new Image(windowIconUrl.toExternalForm()));
        } else {
            System.err.println("❌ Window icon resource not found: /lib/Bpark_server_icon.png");
        }

        alert.showAndWait();
    }

    /**
     * Enables/disables server and DB input fields.
     */
    private void enableInput(boolean bol) {
        S_ip.setDisable(!bol);
        S_port.setDisable(!bol);
        DB_ip.setDisable(!bol);
        DB_port.setDisable(!bol);
        DB_name.setDisable(!bol);
        DB_user.setDisable(!bol);
        DB_pass.setDisable(!bol);
        RecoveryApiKey.setDisable(!bol);
        OrdersApiKey.setDisable(!bol);
        conBtn.setDisable(bol);
    }

    /**
     * Applies light or dark theme to the main screen.
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
            if (connectionsController != null) {
                connectionsController.switchTheme(themeToggle.isSelected());
			}
        } else {
            System.err.println("❌ Theme file not found: " + themePath);
        }
    }
}
