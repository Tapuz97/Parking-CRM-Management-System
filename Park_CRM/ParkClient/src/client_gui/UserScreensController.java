package client_gui;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import javafx.util.Pair;

/**
 * Controller for the User interface in the Bpark system.
 * Handles user-side functionality such as parking, pickup, reservation,
 * viewing history, and updating profile settings.
 */
public class UserScreensController extends AbstractScreensController {

    @FXML private TabPane mainScreen;

    // =================== Deposit Vehicle Tab ===================
    @FXML private TextField OrderInput;
    @FXML private Button ParkBtn;

    // =================== PickUp / Extend Tab ===================
    @FXML private TextField ParkingConfirmationInput;
    @FXML private Button PickupBtn;
    @FXML private Button ExtendBtn;
    @FXML private Button RecoverBtn;

    // =================== Reserve Parking Tab ===================
    @FXML private DatePicker DateInput;
    @FXML private MenuButton HourInput;
    @FXML private MenuButton AmPmInput;
    @FXML private Button ReserveBtn;

    // =================== Parking History Tab ===================
    @FXML private ListView<String> UserHistoryList;

    // =================== Settings Tab ===================
    @FXML private TextField UserPasswordInput;
    @FXML private TextField UserPhoneInput;
    @FXML private TextField UserNameInput;
    @FXML private TextField UserIdInput;
    @FXML private TextField UserEmailInput;
    @FXML private Button EditBtn;
    @FXML private Button LogoutBtn;
    @FXML private ToggleButton themeToggle;

    /** Flag to track whether user fields are currently editable */
    private boolean editable = false;

    /**
     * Initializes the user screen.
     * - Applies theme
     * - Locks ID and Name fields
     * - Limits reservation date picker to 7 days ahead
     */
    @FXML
    private void initialize() {
        switchTheme(); // Set initial theme based on toggle state
        UserIdInput.setDisable(true); // User ID should not be editable
        UserNameInput.setDisable(true); // User Name should not be editable
        limitDaySelection(DateInput); // Limit date selection to today and next 7 days
    }

    // =================== Deposit Vehicle ===================

    /**
     * Called when the user clicks the "Park" button.
     * Sends a DEPOSIT request to the server with or without a user-provided order number.
     * Displays a confirmation code or error message based on the result.
     */
    @FXML
    private void ParkCar() {
        String orderNum = OrderInput.getText().trim();
        String subscriberId = clientCore.getSubscriberId();

        Pair<Integer, String> response;
        if (orderNum.isEmpty()) {
            response = clientCore.depositVehicle(subscriberId, null);
        } else {
            response = clientCore.depositVehicle(subscriberId, orderNum);
        }

        if (response.getKey() == 200) {
            showAlert("✅ Success", "Your confirmation code: " + response.getValue(), Alert.AlertType.INFORMATION, themeToggle.isSelected());
        } else {
            showAlert("❌ Error: " + response.getKey(), response.getValue(), Alert.AlertType.ERROR, themeToggle.isSelected());
        }
    }

    
    /**
     * Handles the pickup of a parked vehicle using a confirmation code.
     * Validates the input, sends a request to the server, and displays the result.
     * 
     * @FXML
     */
    @FXML
    private void PickUp() {
        String confirmationCode = ParkingConfirmationInput.getText().trim();

        if (confirmationCode.isEmpty()) {
            showAlert("Missing Input", "Please enter your confirmation code.", Alert.AlertType.WARNING, themeToggle.isSelected());
            return;
        }

        String subscriberId = clientCore.getSubscriberId();

        Pair<Integer, String> response = clientCore.pickup(subscriberId, confirmationCode);

        if (response.getKey() == 200) {
            showAlert("✅ Success", "Your car has been retrieved successfully.", Alert.AlertType.INFORMATION, themeToggle.isSelected());
        } else {
            showAlert("❌ Error: " + response.getKey(), response.getValue(), Alert.AlertType.ERROR, themeToggle.isSelected());
        }
    }

    /**
     * Extends the parking session for a parked vehicle using a confirmation code.
     * Validates the input, sends a request to the server, and displays the result.
     * 
     * @FXML
     */
    @FXML
    private void ExtendParking() {
        String confirmationCode = ParkingConfirmationInput.getText().trim();

        if (confirmationCode.isEmpty()) {
            showAlert("Missing Input", "Please enter your confirmation code.", Alert.AlertType.WARNING, themeToggle.isSelected());
            return;
        }

        String subscriberId = clientCore.getSubscriberId();

        Pair<Integer, String> response = clientCore.extendParking(subscriberId, confirmationCode);

        if (response.getKey() == 200) {
            showAlert("✅ Success", "Your parking session has been extended.", Alert.AlertType.INFORMATION, themeToggle.isSelected());
        } else {
            showAlert("❌ Error: " + response.getKey(), response.getValue(), Alert.AlertType.ERROR, themeToggle.isSelected());
        }
    }

    /**
     * Recovers the parking confirmation code for the logged-in user.
     * Retrieves the user's parking details from the server and displays the result.
     * 
     * @FXML
     */
    @FXML
    private void RecoverUser() {
        String userId = clientCore.getSubscriberId(); // Get logged-in user ID
        Map<Integer, List<String>> result = clientCore.RecoverUser(userId);

        if (result == null || result.isEmpty()) {
            showAlert("Error", "No response from server.", Alert.AlertType.ERROR, themeToggle.isSelected());
            return;
        }

        int code = result.keySet().iterator().next();
        List<String> data = result.get(code);

        if (code == 200 && data != null && data.size() >= 4) {
            String confirmationCode = data.get(3); // index 3 = subscriber_parking_confirmationCode
            if (confirmationCode.equals("No active parking")) {
                showAlert("🅿️ Parking confirmation code recovery", "No active parking", Alert.AlertType.WARNING, themeToggle.isSelected());
            } else {
                showAlert("🅿️ Parking confirmation code recovery", "Your parking confirmation code has been sent via Discord", Alert.AlertType.INFORMATION, themeToggle.isSelected());
            }
        } else {
            showAlert("Not Found", "No active parking found for your account.", Alert.AlertType.WARNING, themeToggle.isSelected());
        }
    }

    
// ===================  Reserve Parking ===================

    /**
     * Handles the reservation of a parking spot.
     * Validates the selected date, hour, and AM/PM values, formats the reservation time,
     * and sends a request to the server. Displays a confirmation or error message based on the response.
     *
     * @FXML
     */
    @FXML
    private void ReserveOrder() {
        LocalDate date = DateInput.getValue();
        String hourStr = HourInput.getText();
        String ampmStr = AmPmInput.getText();
        System.out.println(("Selected Date: " + date + ", Hour: " + hourStr + ", AM/PM: " + ampmStr));
        if (date == null || hourStr.equals("Hour") || ampmStr.equals("AM/PM")) {
            showAlert("Invalid Input", "Please select date, hour, and AM/PM.", Alert.AlertType.ERROR, themeToggle.isSelected());
            return;
        }

        int hour = Integer.parseInt(hourStr);
        if (ampmStr.equalsIgnoreCase("PM") && hour != 12) hour += 12;
        if (ampmStr.equalsIgnoreCase("AM") && hour == 12) hour = 0;

        if (!isValidReservationTime(date, hour)) return;

        String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String formattedTime = String.format("%02d:00:00", hour);

        String subscriberId = clientCore.getSubscriberId();
        Pair<Integer, String> response = clientCore.reserveParking(subscriberId, formattedDate, formattedTime);

        if (response.getKey() == 200) {
            showAlert("✅ Reserved", "Reservation confirmed.\nConfirmation code: " + response.getValue(), Alert.AlertType.INFORMATION, themeToggle.isSelected());
        } else {
            showAlert("❌ Error: " + response.getKey(), response.getValue(), Alert.AlertType.ERROR, themeToggle.isSelected());
        }
    }

    /**
     * Handles the selection of a time (hour) from the dropdown menu.
     * Updates the `HourInput` text with the selected hour.
     *
     * @param event The `ActionEvent` triggered by selecting a time.
     * @FXML
     */
    @FXML
    private void handleTimeSelection(ActionEvent event) {
        MenuItem selected = (MenuItem) event.getSource();
        HourInput.setText(selected.getText());
    }

    /**
     * Handles the selection of AM/PM from the dropdown menu.
     * Updates the `AmPmInput` text with the selected value.
     *
     * @param event The `ActionEvent` triggered by selecting AM/PM.
     * @FXML
     */
    @FXML
    private void handleAmPmSelection(ActionEvent event) {
        MenuItem selected = (MenuItem) event.getSource();
        AmPmInput.setText(selected.getText());
    }

    /**
     * Retrieves and displays the parking history of the logged-in user.
     * Fetches the history from the server and formats it into a list view.
     * If no history is available, displays an appropriate message.
     *
     * @param event The `Event` triggered by selecting the history tab.
     * @FXML
     */
    @FXML
    private void getUserHistory(Event event) {
        Tab tab = (Tab) event.getSource();
        if (!tab.isSelected()) return;

        String userId = clientCore.getSubscriberId();
        List<Map<String, String>> result = clientCore.getUserHistory(userId);

        UserHistoryList.getItems().clear();

        if (result == null || result.isEmpty()) {
            UserHistoryList.getItems().add("No parking history available.");
            return;
        }

        // Table header
        String header = String.format("%-15s %-15s %-10s %-15s",
            "Parking Num", "Date", "Time", "Event");
        String divider = "------------------------------------------------------";

        UserHistoryList.getItems().add(header);
        UserHistoryList.getItems().add(divider);

        for (Map<String, String> row : result) {
            String formatted = String.format("%-15s %-15s %-10s %-15s",
                row.getOrDefault("parking_num", "N/A"),
                row.getOrDefault("parking_date", "N/A"),
                row.getOrDefault("parking_time", "N/A"),
                row.getOrDefault("event_type", "N/A"));
            UserHistoryList.getItems().add(formatted);
        }
    }


    
    /**
     * Displays the current subscriber's information in the settings tab.
     * Disables editing and pulls fresh data from {@link client_core.ClientCore}.
     *
     * @param event The UI event that triggered this action.
     */
    @FXML
    private void showUserDetails(Event event) {
        setEditable(false);
        UserPasswordInput.setText(clientCore.getSubscriberPassword());
        UserPhoneInput.setText(clientCore.getSubscriberPhone());
        UserNameInput.setText(clientCore.getSubscriberName());
        UserIdInput.setText(clientCore.getSubscriberId());
        UserEmailInput.setText(clientCore.getSubscriberEmail());
    }

    /**
     * Toggles between editing and saving user details.
     * If saving, it validates inputs and sends an EDIT_USER request to the server.
     */
    @FXML
    private void EditUserDetails() {
        editable = !editable; // Toggle editable state
        setEditable(editable);

        if (!editable) { // If saving changes
            String newPassword = UserPasswordInput.getText();
            String newPhone = UserPhoneInput.getText();
            String newEmail = UserEmailInput.getText();

            String[] fields = { newPassword, newPhone, newEmail };

            if (areValidInputs(fields, themeToggle.isSelected())) {
                Pair<Integer, String> response = clientCore.editUserDetails(newEmail, newPassword, newPhone);

                if (response.getKey() == 200) {
                    setEditable(false);
                    showAlert("✔️ Success", "User details updated successfully.", Alert.AlertType.INFORMATION, themeToggle.isSelected());
                } else {
                    showAlert("❌ Error: " + response.getKey(), response.getValue(), Alert.AlertType.ERROR, themeToggle.isSelected());
                }
            }
        }
    }

    /**
     * Applies the selected light or dark theme to the current scene.
     * Called when the theme toggle is switched.
     */
    @FXML
    private void switchTheme() {
        Scene scene = mainScreen.getScene(); // ← safely retrieve scene here
        if (scene == null) return; // handle case if scene isn't ready yet
        scene.getStylesheets().clear();
        if (themeToggle.isSelected()) {
            scene.getStylesheets().add(getClass().getResource("fxml/style_dark.css").toExternalForm());
        } else {
            scene.getStylesheets().add(getClass().getResource("fxml/style_light.css").toExternalForm());
        }
    }

    /**
     * Logs the user out and loads the login screen.
     * Disconnects the {@link ClientCore} session and resets the stage.
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

    // =================== Helper methods ===================

    /**
     * Toggles the editable state of password, phone, and email input fields.
     * Updates the Edit button label accordingly.
     *
     * @param editable Whether the fields should be editable.
     */
    private void setEditable(boolean editable) {
        UserPasswordInput.setEditable(editable);
        UserPhoneInput.setEditable(editable);
        UserEmailInput.setEditable(editable);
        UserPasswordInput.setDisable(!editable);
        UserPhoneInput.setDisable(!editable);
        UserEmailInput.setDisable(!editable);

        if (editable) {
            EditBtn.setText("Save 💾");
        } else {
            EditBtn.setText("Edit 🔧");
        }
    }

    /**
     * Validates a selected reservation time based on current time and business rules.
     * Must be between 24 hours and 7 days from now.
     *
     * @param date The selected reservation date.
     * @param hour The selected hour (0–23).
     * @return true if the time is valid; false otherwise.
     */
    private boolean isValidReservationTime(LocalDate date, int hour) {
        LocalDateTime now = LocalDateTime.now();

        // Invalid hour or date check
        if (date == null || hour < 0 || hour > 23) {
            showAlert("❌ Invalid Time", "Please select a valid date and hour.", Alert.AlertType.WARNING, themeToggle.isSelected());
            return false;
        }

        LocalDateTime selected = LocalDateTime.of(date, LocalTime.of(hour, 0));
        Duration diff = Duration.between(now, selected);

        if (selected.isBefore(now)) {
            showAlert("❌ Invalid Time", "Cannot reserve in the past.", Alert.AlertType.WARNING, themeToggle.isSelected());
            return false;
        }

        if (diff.toHours() < 24) {
            showAlert("❌ Too Soon", "Reservations must be at least 24 hours in advance.", Alert.AlertType.WARNING, themeToggle.isSelected());
            return false;
        }

        if (diff.toDays() > 7) {
            showAlert("❌ Too Late", "Reservations can be made up to 7 days in advance.", Alert.AlertType.WARNING, themeToggle.isSelected());
            return false;
        }

        return true;
    }

    /**
     * Restricts the selectable dates in a {@link DatePicker} to today through the next 7 days.
     *
     * @param datePicker The {@link DatePicker} to configure.
     */
    private void limitDaySelection(DatePicker datePicker) {
        LocalDate minDate  = LocalDate.now();
        LocalDate maxDate = minDate.plusDays(7);
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty || item.isBefore(minDate) || item.isAfter(maxDate));
            }
        });
    }
}
