package client_gui;

import javafx.util.Pair;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Controller for the Manager interface in the Bpark system.
 * Handles administrative functionality including user creation,
 * account recovery, parking status, and report generation.
 */
public class ManagerScreensController extends AbstractScreensController {

    @FXML private TabPane mainScreen;
    private AbstractReportController reportController;

    // =================== Create User Tab ===================
    @FXML private TextField CreateUserName;
    @FXML private TextField CreateUserPassword;
    @FXML private TextField CreateUserEmail;
    @FXML private TextField CreateUserPhone;
    @FXML private TextField CreateUserID;
    @FXML private Button CreateUserBtn;

    // =================== Show User History Tab ===================
    @FXML private TextField UserHistoryInput;
    @FXML private Button SearchHistoryBtn;
    @FXML private ListView<String> UserHistoryList;

    // =================== Recover User Tab ===================
    @FXML private TextField UserRecoverInput;
    @FXML private Button RecoverBtn;
    @FXML private TextField RecoverUserName;
    @FXML private TextField RecoverUserPhone;
    @FXML private TextField RecoverUserEmail;
    @FXML private TextField RecoverUserParking;

    // =================== Current Parking Status Tab ===================
    @FXML private TextField Capicity;
    @FXML private ListView<String> CurrentParkingList;

    // =================== Admin Tab ===================
    @FXML private Button LogoutBtn;
    @FXML private Pane AdminPane;
    @FXML private MenuButton MonthInput;
    @FXML private MenuButton YearInput;
    @FXML private Button ParkingReportBtn;
    @FXML private Button UserReportBtn;
    @FXML private ToggleButton themeToggle;

    /**
     * Called when the screen loads.
     * Applies the current UI theme (light/dark).
     */
    @FXML
    private void initialize() {
        switchTheme();
    }

    /**
     * Handles the Create User button click.
     * Validates input fields, sends a CREATE request to the server,
     * and displays a success or error alert based on the response.
     */
    @FXML
    private void CreateUser() {
        Pair<Integer, String> result = null;
        String name = CreateUserName.getText();
        String password = CreateUserPassword.getText();
        String email = CreateUserEmail.getText();
        String phone = CreateUserPhone.getText();
        String[] fields = {name, password, email, phone};

        if (areValidInputs(fields, themeToggle.isSelected())) {
            result = clientCore.createUser(name, password, email, phone);
            if (result.getKey() == 200) {
                CreateUserID.setText(result.getValue());
                showAlert("Success", "User created successfully!\n User ID: " + result.getValue(), Alert.AlertType.INFORMATION, themeToggle.isSelected());
            } else {
                showAlert("Error: " + result.getKey(), "Failed to create user: " + result.getValue(), Alert.AlertType.ERROR, themeToggle.isSelected());
            }
        }
        return;
    }


    // =================== Show User History ===================
    /**
     * Searches and displays the parking history of a user based on the provided User ID.
     * This method retrieves the user history from the server, formats the data, and displays it
     * in the `UserHistoryList`. If no history is found or the input is invalid, appropriate messages are shown.
     */
    @FXML
    private void SearchUserHistory() {
        String userId = UserHistoryInput.getText();
        if (userId == null || userId.trim().isEmpty()) {
            showAlert("Invalid Input", "User ID must be provided.", Alert.AlertType.ERROR, themeToggle.isSelected());
            return;
        }

        List<Map<String, String>> result = clientCore.getUserHistory(userId);

        UserHistoryList.getItems().clear(); // Clear old results

        if (result != null && !result.isEmpty()) {
            // Header
            String header = String.format("%-15s %-15s %-10s %-15s",
                    "Parking Num", "Date", "Time", "Event");
            String divider = "------------------------------------------------------";

            UserHistoryList.getItems().add(header);
            UserHistoryList.getItems().add(divider);

            for (Map<String, String> row : result) {
                String line = String.format("%-15s %-15s %-10s %-15s",
                        row.getOrDefault("parking_num", "N/A"),
                        row.getOrDefault("parking_date", "N/A"),
                        row.getOrDefault("parking_time", "N/A"),
                        row.getOrDefault("event_type", "N/A"));
                UserHistoryList.getItems().add(line);
            }
        } else {
            UserHistoryList.getItems().add("No history found for user ID: " + userId);
        }
    }


    // =================== Recover User ===================
    /**
     * Handles the recovery of user details based on the provided User ID.
     * This method retrieves user information from the server and populates the recovery fields.
     * If the input is invalid or the user is not found, appropriate alerts are displayed.
     */
    @FXML
    private void RecoverUser() {
        String userId = UserRecoverInput.getText();

        if (userId == null || userId.trim().isEmpty()) {
            showAlert("Invalid Input", "Please enter a valid User ID.", Alert.AlertType.ERROR, themeToggle.isSelected());
            return;
        }

        Map<Integer, List<String>> result = clientCore.RecoverUser(userId);

        if (result == null || result.isEmpty()) {
            showAlert("Recovery Failed", "No response from server or user not found.", Alert.AlertType.ERROR, themeToggle.isSelected());
            return;
        }

        int code = result.keySet().iterator().next();
        List<String> data = result.get(code);

        if (code == 200 && data != null && data.size() >= 4) {
            RecoverUserName.setText(data.get(0)); // subscriber_name
            RecoverUserEmail.setText(data.get(1)); // subscriber_email
            RecoverUserPhone.setText(data.get(2)); // subscriber_phone
            RecoverUserParking.setText(data.get(3)); // subscriber_parking_confirmationCode
        } else {
            showAlert("Recovery Failed", "User not found or invalid response.", Alert.AlertType.WARNING, themeToggle.isSelected());
            RecoverUserName.clear();
            RecoverUserEmail.clear();
            RecoverUserPhone.clear();
            RecoverUserParking.clear();
        }
    }

    
    // =================== Current Parking Status ===================
    /**
     * Requests current parking status from the server and displays it in the UI.
     * Formats the response table into a readable list view.
     * Shows capacity percentage in the associated text field.
     */
    @FXML
    private void getCurrentParkingStatus() {
        Pair<List<Map<String, String>>, String> result = clientCore.getCurrentParkingStatus();

        CurrentParkingList.getItems().clear();
        Capicity.clear();
        List<Map<String, String>> table = result.getKey();
        String percent = result.getValue();

        if (table == null || table.isEmpty()) {
            CurrentParkingList.getItems().add("No parking data available.");
        } else {
            // Header with nice alignment
            String header = String.format("%-15s %20s %15s %25s",
                "Subscriber ID", "Parking Space", "Status", "Confirmation Code");
            String separator = "-".repeat(header.length());

            CurrentParkingList.getItems().add(header);
            CurrentParkingList.getItems().add(separator);

            for (Map<String, String> row : table) {
                String id = row.getOrDefault("subscriber_id", "\t- - - - ");
                String space = row.getOrDefault("parking_space", "N/A");
                String status = row.getOrDefault("status", "N/A");
                String code = row.getOrDefault("confirmation_code", "\t - - - -");
                
                String formatted = String.format("\t%-15s %15s %25s %25s", space, id, status, code);
                CurrentParkingList.getItems().add(formatted);
            }
        }

        Capicity.setText(percent + "%");
    }

    // =================== Admin tab ===================

    /**
     * Loads and displays the user activity report for the selected month and year.
     */
    @FXML
    private void ShowUserReport() {
        ShowReport("USERS", "fxml/UsersReport.fxml", "Bpark - User Report");
    }

    /**
     * Loads and displays the parking status report for the selected month and year.
     */
    @FXML
    private void ShowParkingReport() {
        ShowReport("PARKING", "fxml/ParkingReport.fxml", "Bpark - Parking Report");
    }

    /**
     * Generic report display method shared between user and parking reports.
     * Fetches data from the server, validates it, and loads the corresponding FXML scene.
     *
     * @param type         Report type ("USERS" or "PARKING").
     * @param fxmlPath     Path to the report FXML file.
     * @param windowTitle  Title of the report window.
     */
    private void ShowReport(String type, String fxmlPath, String windowTitle) {
        String selectedMonth = MonthInput.getText();
        String selectedYear = YearInput.getText();

        // Validate month and year selected
        if (selectedMonth.equals("Month") || selectedYear.equals("Year")) {
            showAlert("Missing Selection", "Please select both month and year.", Alert.AlertType.WARNING, themeToggle.isSelected());
            return;
        }

        Pair<List<Map<String, String>>, Integer> result = clientCore.getReport(type, selectedMonth, selectedYear);
        if (result == null || result.getKey() == null) {
            showAlert("Error", "Failed to retrieve report data.", Alert.AlertType.ERROR, themeToggle.isSelected());
            return;
        }

        List<Map<String, String>> reportData = result.getKey();
        if (reportData.isEmpty()) {
            showAlert("No Data", "No report data available for the selected month and year.", Alert.AlertType.INFORMATION, themeToggle.isSelected());
            return;
        }

        int statusCode = result.getValue();
        if (statusCode != 200) {
            showAlert("Error", "Failed to retrieve report data. Status code: " + statusCode, Alert.AlertType.ERROR, themeToggle.isSelected());
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            reportController = loader.getController();
            reportController.setMonthYear(selectedMonth, selectedYear);
            reportController.setReportData(reportData);
            reportController.setReportType(type);
            reportController.initializeReport();
            Stage adminStage = (Stage) mainScreen.getScene().getWindow();
            Scene scene = new Scene(root);
            AbstractReportController.applyTheme(scene, themeToggle.isSelected());
            reportController.closeWithParentStage(root, adminStage);

            Stage stage = new Stage();
            stage.setTitle(windowTitle);
            stage.setScene(scene);
            stage.setResizable(false);

            URL iconUrl = getClass().getResource("/lib/Bpark_client_icon.png");
            if (iconUrl != null) {
            	stage.getIcons().add(new Image(iconUrl.toExternalForm()));
            } else {
                System.err.println("❌ Window icon resource not found: /lib/Bpark_server_icon.png");
            }

            stage.show();

        } catch (IOException e) {
            showAlert("Error", "Failed to load report: " + e.getMessage(), Alert.AlertType.ERROR, themeToggle.isSelected());
        }
    }



    /**
     * Switches the application's theme between dark and light modes.
     * Clears the current stylesheets and applies the selected theme
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
            if(reportController != null) {
            	reportController.switchTheme(themeToggle.isSelected());
			}
        } else {
            System.err.println("❌ Theme file not found: " + themePath);
        }
    }

    /**
     * Logs out the current user and redirects to the login screen.
     * This method clears the current session, loads the login screen FXML,
     * and sets it as the current scene.
     */
    @FXML
    private void Logout() {
        try {
            clientCore.logOut();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/ClientLogin.fxml"));
            Parent root = loader.load();
            ClientLoginController controller = loader.getController();
            Stage stage = (Stage) LogoutBtn.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Bpark - Login");
            stage.setResizable(false);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Disables the admin pane if the current user is not an admin.
     * This method checks the subscriber status and hides the admin pane
     * if the user does not have admin privileges.
     */
    @FXML
    private void DisableAdminPane() {
        if (!"admin".equalsIgnoreCase(clientCore.getSubscriberStatus())) {
            AdminPane.setVisible(false);
            AdminPane.setManaged(false);
        }
    }

    /**
     * Handles the selection of a month from the dropdown menu.
     * Updates the `MonthInput` text with the selected month.
     *
     * @param event The `ActionEvent` triggered by selecting a month.
     */
    @FXML
    private void handleMonthSelection(ActionEvent event) {
        MenuItem selected = (MenuItem) event.getSource();
        MonthInput.setText(selected.getText());
    }

    /**
     * Handles the selection of a year from the dropdown menu.
     * Updates the `YearInput` text with the selected year.
     *
     * @param event The `ActionEvent` triggered by selecting a year.
     */
    @FXML
    private void handleYearSelection(ActionEvent event) {
        MenuItem selected = (MenuItem) event.getSource();
        YearInput.setText(selected.getText());
    }

    /**
     * Fills the month and year inputs with default values for debugging purposes.
     * This method is intended for development and should be removed in production.
     */
    private void debugFiller() {
        MonthInput.setText("June");
        YearInput.setText("2025");
    }


    
}
