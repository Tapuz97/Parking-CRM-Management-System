package server_gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import ocsf.server.ConnectionToClient;
import server_core.ServerCore;

import java.net.URL;
import java.util.List;

/**
 * Controller for the Server GUI that displays live connection updates.
 * Listens for incoming and outgoing client connections and updates the GUI accordingly.
 */
public class ServerConnectionsController {

    @FXML private AnchorPane mainScreen;
    @FXML private ListView<String> connectionList;
    @FXML private ToggleButton activeFilter;
    @FXML private ImageView CSVexportBtn;
    private ServerCore serverCore;


    /**
     * Sets the ServerCore instance for this controller.
     * This allows the controller to interact with the server's core functionality,
     * such as retrieving connection logs and live client data.
     *
     * @param serverCore the ServerCore instance to be set
     */
    public void setServerCore(ServerCore serverCore) {
        this.serverCore = serverCore;
    }

    /**
     * Displays the connection log in the ListView.
     * The connection log contains entries about client connections, disconnections, 
     * and terminations. Each entry is styled based on its content:
     * - Entries containing "Connected" are styled with the "list-active-cell" class.
     * - Entries containing "Disconnected" are styled with the "list-inactive-cell" class.
     * - Entries containing "Terminated" are styled with the "list-terminated-cell" class.
     *
     * The method sets the items of the ListView to the log list retrieved from the ServerCore
     * and applies a custom cell factory to style the entries dynamically.
     */
    public void ShowConnectionLog() {
        connectionList.setItems(serverCore.getLogList());
        connectionList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    getStyleClass().removeAll("list-active-cell", "list-inactive-cell");
                } else {
                    setText(entry);
                    getStyleClass().removeAll("list-active-cell", "list-inactive-cell");

                    if (entry.contains("Connected")) {
                        getStyleClass().add("list-active-cell");
                    } else if (entry.contains("Disconnected")) {
                        getStyleClass().add("list-inactive-cell");
                    } else if (entry.contains("Terminated")) {
                        getStyleClass().add("list-terminated-cell");
                    }
                }
            }
        });
    }

    
    /**
     * Displays the list of active clients in the ListView.
     * The active clients are retrieved from the ServerCore instance.
     * Each entry in the list is styled with the "list-active-cell" class.
     * 
     * The method sets the items of the ListView to the list of live clients
     * and applies a custom cell factory to style the entries dynamically.
     */
    public void showActiveClients() {
        connectionList.setItems(serverCore.getLiveClients());
        connectionList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    getStyleClass().removeAll("list-active-cell", "list-inactive-cell", "list-terminated-cell");
                } else {
                    setText(entry);
                    getStyleClass().removeAll("list-active-cell", "list-inactive-cell", "list-terminated-cell");
                    getStyleClass().add("list-active-cell");
                }
            }
        });
    }

    /**
     * Toggles between displaying the connection log and the list of active clients.
     * If the active filter toggle button is selected, the list of active clients is displayed.
     * Otherwise, the connection log is displayed.
     */
    @FXML
    private void switchList() {
        if (activeFilter.isSelected()) {
            showActiveClients();
        } else {
            ShowConnectionLog();
        }
    }

    /**
     * Switches the theme of the application between dark mode and light mode.
     * The method clears the current stylesheets and applies the appropriate stylesheet
     * based on the `isDarkMode` parameter.
     * 
     * @param isDarkMode true to switch to dark mode, false to switch to light mode
     */
    public void switchTheme(boolean isDarkMode) {
        Scene scene = mainScreen.getScene();
        scene.getStylesheets().clear();
        String themePath = isDarkMode ? "fxml/style_dark.css" : "fxml/style_light.css";
        URL cssUrl = getClass().getResource(themePath);
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("❌ Theme file not found: " + themePath);
        }
    }
    
    @FXML
    private void handleCSVexport() {
    	serverCore.exportLogToCSV();
    }
    
}
