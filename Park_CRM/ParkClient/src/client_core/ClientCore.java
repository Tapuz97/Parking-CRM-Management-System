package client_core;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.gson.*;
import ocsf.client.*;
import client_core.CommandPacket;
import client_gui.ClientLoginController;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.util.Pair;


/**
 * Core client class that handles communication with the server and provides
 * methods for various client operations.
 */
public class ClientCore extends AbstractClient {

    // Fields and variables
    private String subscriberId, subscriberName, subscriberEmail, subscriberStatus;
    private volatile boolean loginSuccess = false, tableSuccess = false;
    private CountDownLatch loginLatch, getTableLatch, updateLatch, createLatch, userHistoryLatch, recoverLatch, currentParkingLatch, editUserLatch, pickupLatch, extendLatch, depositLatch, reserveLatch, reportLatch;
    private int loginCode, updateCode, createCode, recoverCode, editUserCode, pickupCode, extendCode, depositCode, reserveCode, reportCode;
    private String loginDesc, updateDesc, createDesc, currentParkingDesc, editUserDesc, pickupDesc, extendDesc, depositDesc, reserveDesc, reportDesc;
    private List<Map<String, String>> getTableResult, userHistoryResult, currentParkingResult, reportResult;
    private List<String> recoverData;
    private Gson Gson = new Gson();
    private String subscriberPassword;
    private String subscriberPhone;

/*///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
========================================================================================================================================================
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////*/
	
// =================== System Commands -- START===================

    /**
     * Handles messages received from the server.
     *
     * @param msg The message received from the server.
     */
    @Override
    protected void handleMessageFromServer(Object msg) {
        if (!(msg instanceof String json)) return;

        CommandPacket packet = Gson.fromJson(json, CommandPacket.class);
        String command = packet.getCommand().toUpperCase();

        System.out.println("📥 Received CommandPacket: " + packet);
        switch (command) {
            case "LOGIN" -> handleLoginResponse(packet);
            case "LOGOUT" -> handleLogoutResponse(packet);
            case "CREATE" -> handleCreateResponse(packet);
            case "CURRENT_PARKING" -> handleCurrentParkingResponse(packet);
            case "REPORT" -> handleReportResponse(packet);
            case "DEPOSIT" -> handleDepositResponse(packet);
            case "PICKUP" -> handlePickupResponse(packet);
            case "EXTEND" -> handleExtendResponse(packet);
            case "RESERVE" -> handleReserveResponse(packet);
            case "EDIT_USER" -> handleEditUserResponse(packet);
            case "USER_HISTORY" -> handleUserHistoryResponse(packet);
            case "RECOVER" -> handleRecoverResponse(packet);
            case "DISCONNECT" -> handleDisconnectResponse(packet);
            case "SHUTDOWN" -> handleShutdown(packet);
            default -> handledefaultResponse(packet.getAnswer(), packet.getDescription());
        }
    }

	
    /**
     * Handles unknown server responses.
     *
     * @param code The response code.
     * @param dec  The response description.
     * @return A pair containing the response code and description.
     */
    private Pair<Integer, String> handledefaultResponse(int code, String dec) {
        return new Pair<>(code, dec);
    }


	
// =================== System Commands -- END===================
	
/*///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
========================================================================================================================================================
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////*/
	
// ========================================================= Admin Commands -- START =========================================================	
	
	//=================== Create User Command & Handler ===================
    /**
     * Sends a request to create a new user.
     *
     * @param name     The name of the user.
     * @param password The password of the user.
     * @param email    The email of the user.
     * @param phone    The phone number of the user.
     * @return A pair containing the response code and description.
     */
    public Pair<Integer, String> createUser(String name, String password, String email, String phone) {
        createLatch = new CountDownLatch(1);
        CommandPacket command = new CommandPacket();
        command.setCommand("CREATE");

        Map<String, String> args = new HashMap<>();
        args.put("name", name);
        args.put("password", password);
        args.put("email", email);
        args.put("phone", phone);
        command.setArgs(args);

        try {
            sendToServer(Gson.toJson(command));
        } catch (IOException e) {
            return new Pair<>(503, "Connection error: " + e.getMessage());
        }

        try {
            boolean responded = createLatch.await(5, TimeUnit.SECONDS);
            if (!responded) {
                return new Pair<>(504, "Server timeout.");
            }
        } catch (InterruptedException e) {
            return new Pair<>(503, "Interrupted while waiting for response.");
        }

        return new Pair<>(createCode, createDesc);
    }

    /**
     * Handles the server response for the create user command.
     *
     * @param packet The command packet received from the server.
     */
    private void handleCreateResponse(CommandPacket packet) {
        createCode = packet.getAnswer();
        createDesc = packet.getDescription();

        if (createLatch != null) {
            createLatch.countDown();
        }
    }

	
	
	//=================== Current Parking Command & Handler ===================
    /**
     * Retrieves the current parking status from the server.
     *
     * @return A pair containing the list of parking data and a description.
     */
    public Pair<List<Map<String, String>>, String> getCurrentParkingStatus() {
        currentParkingLatch = new CountDownLatch(1);
        currentParkingResult = null;
        currentParkingDesc = null;

        CommandPacket command = new CommandPacket();
        command.setCommand("CURRENT_PARKING");

        try {
            sendToServer(Gson.toJson(command));
        } catch (IOException e) {
            System.err.println("❌ Failed to send CURRENT_PARKING: " + e.getMessage());
            return null;
        }

        try {
            if (!currentParkingLatch.await(5, TimeUnit.SECONDS)) {
                System.err.println("❌ Timeout waiting for CURRENT_PARKING");
                return null;
            }
        } catch (InterruptedException e) {
            System.err.println("❌ Interrupted during CURRENT_PARKING");
            return null;
        }

        return new Pair<>(currentParkingResult, currentParkingDesc);
    }

    /**
     * Handles the server response for the current parking status command.
     *
     * @param packet The command packet received from the server.
     */
    private void handleCurrentParkingResponse(CommandPacket packet) {
        currentParkingResult = packet.getTable(); // table of parking spaces
        currentParkingDesc = packet.getDescription(); // % used
        if (currentParkingLatch != null) {
            currentParkingLatch.countDown();
        }
    }

	//=================== Reports Command & Handler ===================

    /**
     * Retrieves a report from the server based on the specified type, month, and year.
     *
     * @param type  The type of the report ("PARKING" or "USERS").
     * @param month The month of the report.
     * @param year  The year of the report.
     * @return A pair containing the report data and the response code.
     */
    public Pair<List<Map<String, String>>, Integer> getReport(String type, String month, String year) {
        reportLatch = new CountDownLatch(1);
        reportResult = null;
        reportCode = 500; // default error
        reportDesc = null;

        CommandPacket command = new CommandPacket();
        command.setCommand("REPORT");

        Map<String, String> args = new HashMap<>();
        args.put("report_month", month);
        args.put("report_year", year);
        args.put("report_type", type); // "PARKING" or "USERS"
        command.setArgs(args);

        try {
            sendToServer(Gson.toJson(command));
        } catch (IOException e) {
            return new Pair<>(null, 503); // connection error
        }

        try {
            boolean responded = reportLatch.await(5, TimeUnit.SECONDS);
            if (!responded) {
                return new Pair<>(null, 504); // timeout
            }
        } catch (InterruptedException e) {
            return new Pair<>(null, 503);
        }

        return new Pair<>(reportResult, reportCode);
    }

    /**
     * Handles the server response for the report command.
     *
     * @param packet The command packet received from the server.
     */
    private void handleReportResponse(CommandPacket packet) {
        reportCode = packet.getAnswer();
        reportDesc = packet.getDescription();
        reportResult = packet.getTable();

        if (reportLatch != null) {
            reportLatch.countDown();
        }
    }




	
	
// ========================================================= Admin Commands -- END =========================================================	
	
/*///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
========================================================================================================================================================
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////*/
	
// ========================================================= User Commands -- START =========================================================

	//=================== Deposit Vehicle Command & Handler ===================
    /**
     * Sends a request to deposit a vehicle.
     *
     * @param subscriberId The ID of the subscriber.
     * @param orderNumber  The order number associated with the deposit.
     * @return A pair containing the response code and description.
     */
    public Pair<Integer, String> depositVehicle(String subscriberId, String orderNumber) {
        depositLatch = new CountDownLatch(1);
        depositCode = 500;
        depositDesc = "Internal error";

        CommandPacket command = new CommandPacket();
        command.setCommand("DEPOSIT");

        Map<String, String> args = new HashMap<>();
        args.put("subscriber_id", subscriberId);
        if (orderNumber != null && !orderNumber.isEmpty() && !orderNumber.isBlank()) {
            args.put("order_number", orderNumber);
        } else {
            args.put("order_number", ""); // empty string if no order number
        }
        command.setArgs(args);

        try {
            sendToServer(Gson.toJson(command));
        } catch (IOException e) {
            return new Pair<>(503, "Connection error: " + e.getMessage());
        }

        try {
            if (!depositLatch.await(5, TimeUnit.SECONDS)) {
                return new Pair<>(504, "Timeout waiting for server");
            }
        } catch (InterruptedException e) {
            return new Pair<>(503, "Interrupted while waiting");
        }

        return new Pair<>(depositCode, depositDesc); // desc holds confirmation code
    }

    /**
     * Handles the server response for the deposit vehicle command.
     *
     * @param packet The command packet received from the server.
     */
    private void handleDepositResponse(CommandPacket packet) {
        depositCode = packet.getAnswer();
        depositDesc = packet.getDescription(); // confirmation code or error message
        if (depositLatch != null) depositLatch.countDown();
    }

	
	//=================== Reserve Parking Command & Handler ===================
    /**
     * Sends a request to reserve a parking spot.
     *
     * @param subscriberId The ID of the subscriber.
     * @param orderDate    The date of the reservation.
     * @param orderTime    The time of the reservation.
     * @return A pair containing the response code and description.
     */
    public Pair<Integer, String> reserveParking(String subscriberId, String orderDate, String orderTime) {
        reserveLatch = new CountDownLatch(1);
        reserveCode = 500;
        reserveDesc = "Internal client error";

        CommandPacket command = new CommandPacket();
        command.setCommand("RESERVE");

        Map<String, String> args = new HashMap<>();
        args.put("subscriber_id", subscriberId);
        args.put("order_date", orderDate);
        args.put("order_time", orderTime);
        command.setArgs(args);

        try {
            sendToServer(Gson.toJson(command));
        } catch (IOException e) {
            return new Pair<>(503, "Connection error: " + e.getMessage());
        }

        try {
            boolean responded = reserveLatch.await(5, TimeUnit.SECONDS);
            if (!responded) {
                return new Pair<>(504, "Timeout waiting for server");
            }
        } catch (InterruptedException e) {
            return new Pair<>(503, "Interrupted while waiting");
        }

        return new Pair<>(reserveCode, reserveDesc);
    }

    /**
     * Handles the server response for the reserve parking command.
     *
     * @param packet The command packet received from the server.
     */
    private void handleReserveResponse(CommandPacket packet) {
        reserveCode = packet.getAnswer();
        reserveDesc = packet.getDescription();

        if (reserveLatch != null) reserveLatch.countDown();
    }


	
	
	//=================== Pickup Parking Command & Handler ===================
    /**
     * Sends a request to pick up a vehicle.
     *
     * @param subscriberId     The ID of the subscriber.
     * @param confirmationCode The parking confirmation code.
     * @return A pair containing the response code and description.
     */
    public Pair<Integer, String> pickup(String subscriberId, String confirmationCode) {
        pickupLatch = new CountDownLatch(1);
        pickupCode = 500;
        pickupDesc = "Internal error";

        CommandPacket command = new CommandPacket();
        command.setCommand("PICKUP");

        Map<String, String> args = new HashMap<>();
        args.put("subscriber_id", subscriberId);
        args.put("parking_confirmation_code", confirmationCode);
        command.setArgs(args);

        try {
            sendToServer(Gson.toJson(command));
        } catch (IOException e) {
            return new Pair<>(503, "Connection error: " + e.getMessage());
        }

        try {
            if (!pickupLatch.await(5, TimeUnit.SECONDS)) {
                return new Pair<>(504, "Timeout waiting for server");
            }
        } catch (InterruptedException e) {
            return new Pair<>(503, "Interrupted while waiting");
        }

        return new Pair<>(pickupCode, pickupDesc);
    }

    /**
     * Handles the server response for the pickup command.
     *
     * @param packet The command packet received from the server.
     */
    private void handlePickupResponse(CommandPacket packet) {
        pickupCode = packet.getAnswer();
        pickupDesc = packet.getDescription();
        if (pickupLatch != null) pickupLatch.countDown();
    }


	
	
	
	
	//=================== Extend Parking Command & Handler ===================
    /**
     * Sends a request to extend a parking reservation.
     *
     * @param subscriberId     The ID of the subscriber.
     * @param confirmationCode The parking confirmation code.
     * @return A pair containing the response code and description.
     */
    public Pair<Integer, String> extendParking(String subscriberId, String confirmationCode) {
        extendLatch = new CountDownLatch(1);
        extendCode = 500;
        extendDesc = "Internal error";

        CommandPacket command = new CommandPacket();
        command.setCommand("EXTEND");

        Map<String, String> args = new HashMap<>();
        args.put("subscriber_id", subscriberId);
        args.put("parking_confirmation_code", confirmationCode);
        command.setArgs(args);

        try {
            sendToServer(Gson.toJson(command));
        } catch (IOException e) {
            return new Pair<>(503, "Connection error: " + e.getMessage());
        }

        try {
            if (!extendLatch.await(5, TimeUnit.SECONDS)) {
                return new Pair<>(504, "Timeout waiting for server");
            }
        } catch (InterruptedException e) {
            return new Pair<>(503, "Interrupted while waiting");
        }

        return new Pair<>(extendCode, extendDesc);
    }

    /**
     * Handles the server response for the extend parking command.
     *
     * @param packet The command packet received from the server.
     */
    private void handleExtendResponse(CommandPacket packet) {
        extendCode = packet.getAnswer();
        extendDesc = packet.getDescription();
        if (extendLatch != null) extendLatch.countDown();
    }


	
	
	
	//=================== Edit User Command & Handler ===================
    /**
     * Sends an EDIT_USER command to the server to update the subscriber's details.
     * Waits for a response containing a result code and description.
     *
     * @param email    The new email address to set for the subscriber.
     * @param password The new password to set.
     * @param phone    The new phone number to set.
     * @return A Pair containing:
     *         - Integer: response code (e.g., 200 for success, 503/504 for error),
     *         - String: description or error message.
     */
    public Pair<Integer, String> editUserDetails(String email, String password, String phone) {
        editUserLatch = new CountDownLatch(1);
        editUserCode = 500;
        editUserDesc = "Unknown error";

        CommandPacket command = new CommandPacket();
        command.setCommand("EDIT_USER");

        Map<String, String> args = new HashMap<>();
        args.put("subscriber_id", this.getSubscriberId());
        args.put("subscriber_email", email);
        args.put("subscriber_password", password);
        args.put("subscriber_phone", phone);
        command.setArgs(args);

        try {
            sendToServer(Gson.toJson(command));
        } catch (IOException e) {
            System.err.println("❌ Failed to send EDIT_USER: " + e.getMessage());
            return new Pair<>(503, "Connection error");
        }

        try {
            if (!editUserLatch.await(5, TimeUnit.SECONDS)) {
                System.err.println("❌ Timeout waiting for EDIT_USER");
                return new Pair<>(504, "Timeout waiting for server");
            }
        } catch (InterruptedException e) {
            System.err.println("❌ Interrupted during EDIT_USER");
            return new Pair<>(503, "Thread interrupted");
        }

        return new Pair<>(editUserCode, editUserDesc);
    }

    /**
     * Handles the server's response to the EDIT_USER command.
     * Extracts the response code and description, and releases the latch.
     *
     * @param packet The response CommandPacket received from the server.
     */
    private void handleEditUserResponse(CommandPacket packet) {
        this.editUserCode = packet.getAnswer();
        this.editUserDesc = packet.getDescription();
        if (editUserLatch != null) {
            editUserLatch.countDown();
        }
    }




	
// ========================================================= User Commands -- END =========================================================
	
/*///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
========================================================================================================================================================
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////*/
	
// ========================================================= Shared Commands -- START =========================================================
	
	//=================== Login Command & Handler ===================
    /**
     * Sends a LOGIN command to the server with the provided email and password.
     * Waits for the server's response and processes it to determine login success.
     *
     * @param email    The subscriber's email address.
     * @param password The subscriber's password.
     * @return A Pair containing:
     *         - Integer: status code (e.g., 200 for success, 503/504 for errors),
     *         - String: description or error message from the server.
     */
    public Pair<Integer, String> logIn(String email, String password, boolean forceLogin) {
        loginLatch = new CountDownLatch(1); // reset for each login
        CommandPacket command = new CommandPacket();
        Map<String, String> args = new HashMap<>();
        args.put("subscriber_email", email);
        args.put("subscriber_password", password);
        args.put("force_login", String.valueOf(forceLogin));
        command.setCommand("LOGIN");
        command.setArgs(args);
        try {
            sendToServer(Gson.toJson(command));
        } catch (IOException e) {
            System.err.println("❌ Failed to send CommandPacket : " + e.getMessage());
            return new Pair<>(503, "Connection error");
        }

        try {
            boolean responded = loginLatch.await(5, TimeUnit.SECONDS);
            if (!responded) {
                return new Pair<>(504, "Timeout waiting for server");
            }
        } catch (InterruptedException e) {
            return new Pair<>(503, "Thread interrupted");
        }
        return new Pair<>(loginCode, loginDesc);
    }
    

    /**
     * Handles the server's response to a LOGIN command.
     * If login is successful (status code 200), updates subscriber fields.
     * Releases the login latch to allow the logIn method to continue.
     *
     * @param packet The CommandPacket containing the server's login response.
     */
    private void handleLoginResponse(CommandPacket packet) {
        loginCode = packet.getAnswer();
        loginDesc = packet.getDescription();

        if (loginCode == 200) {
            Map<String, String> args = packet.getArgs();
            setUser(
                args.get("subscriber_id"),
                args.get("subscriber_name"),
                args.get("subscriber_email"),
                args.get("subscription_status"),
                args.get("subscriber_phone"),
                args.get("subscriber_password")
            );
            loginSuccess = true;
        }

        if (loginLatch != null) {
            loginLatch.countDown();
        }
    }

	
	
	//=================== Logout Command & Handler ===================
	
    /**
     * Sends a LOGOUT command to the server and closes the client connection.
     * This method ensures the logout request is sent before disconnecting.
     */
    public void logOut() {
        CommandPacket command = new CommandPacket();
        command.setCommand("LOGOUT");

        try {
            sendToServer(Gson.toJson(command));
        } catch (IOException e) {
            System.err.println("❌ Failed to send CommandPacket : " + e.getMessage());
        }
        
        try {
			Thread.sleep(300);
		} catch (InterruptedException e) {}
        
        while (this.isConnected()) {
            try {
                this.closeConnection();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Placeholder for handling LOGOUT server response.
     * Currently does nothing.
     *
     * @param packet The CommandPacket returned by the server (ignored).
     */
    private void handleLogoutResponse(CommandPacket packet) {

    }
    
    /**
     * Handles the server disconnect notification.
     * Displays an alert to the user indicating that they have been logged out.
     * The alert includes a custom title, header, and content, along with custom icons for the alert and window.
     * After the alert is dismissed, the application exits.
     *
     * @param packet The CommandPacket received from the server containing the disconnect notification.
     */
    private void handleDisconnectResponse(CommandPacket packet) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Disconnected");
            alert.setHeaderText("You have been logged out");
            alert.setContentText(packet.getDescription());

            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().add(getClass().getResource("/client_gui/fxml/style_light.css").toExternalForm());
            dialogPane.getStyleClass().add("custom-alert");

            // Custom left-side graphic
            try {
                Image iconImage = new Image(getClass().getResourceAsStream("/lib/Bpark_WARNING.png"));
                if (!iconImage.isError()) {
                    ImageView icon = new ImageView(iconImage);
                    icon.setFitHeight(50);
                    icon.setFitWidth(50);
                    alert.setGraphic(icon);
                }
            } catch (Exception e) {
                System.err.println("❌ Alert icon not found in resources: /lib/Bpark_WARNING.png");
            }

            // Window icon
            try {
                Image windowIcon = new Image(getClass().getResourceAsStream("/lib/Bpark_client_icon.png"));
                if (!windowIcon.isError()) {
                    ((Stage) dialogPane.getScene().getWindow()).getIcons().add(windowIcon);
                }
            } catch (Exception e) {
                System.err.println("❌ Window icon not found in resources: /lib/Bpark_client_icon.png");
            }

            alert.showAndWait();

            // ✅ Exit AFTER showing the alert
            Platform.exit();
        });
    }



    /**
     * Handles the server shutdown notification.
     * Displays an alert to the user indicating that the server is shutting down or experiencing issues.
     * The alert includes a custom title, header, and content, along with custom icons for the alert and window.
     * After the alert is dismissed, the application exits.
     *
     * @param packet The CommandPacket received from the server containing the shutdown notification.
     */
    private void handleShutdown(CommandPacket packet) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Shutdown");
            alert.setHeaderText("Your session will be terminated.\nPlease try again later.");
            alert.setContentText("The server is shutting down or experiencing issues");

            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().add(getClass().getResource("/client_gui/fxml/style_light.css").toExternalForm());
            dialogPane.getStyleClass().add("custom-alert");

            try {
                Image iconImage = new Image(getClass().getResourceAsStream("/lib/Bpark_WARNING.png"));
                if (!iconImage.isError()) {
                    ImageView icon = new ImageView(iconImage);
                    icon.setFitHeight(50);
                    icon.setFitWidth(50);
                    alert.setGraphic(icon);
                }
            } catch (Exception e) {
                System.err.println("❌ Alert icon not found in resources: /lib/Bpark_WARNING.png");
            }
            try {
                Image windowIcon = new Image(getClass().getResourceAsStream("/lib/Bpark_client_icon.png"));
                if (!windowIcon.isError()) {
                    ((Stage) dialogPane.getScene().getWindow()).getIcons().add(windowIcon);
                }
            } catch (Exception e) {
                System.err.println("❌ Window icon not found in resources: /lib/Bpark_client_icon.png");
            }

            alert.showAndWait();

            Platform.exit();
        });
    }


	
	
	//=================== User History Command & Handler ===================
	/**
	 * Retrieves the user history from the server.
	 *
	 * @param userId The ID of the user whose history is to be retrieved.
	 * @return A list of maps containing the user history data.
	 */
	public List<Map<String, String>> getUserHistory(String userId) {
	    userHistoryLatch = new CountDownLatch(1);
	    userHistoryResult = null; // clear previous results

	    CommandPacket command = new CommandPacket();
	    command.setCommand("USER_HISTORY");

	    Map<String, String> args = new HashMap<>();
	    args.put("user_id", userId);
	    command.setArgs(args);

	    try {
	        sendToServer(Gson.toJson(command));
	    } catch (IOException e) {
	        System.err.println("❌ Failed to send USER_HISTORY: " + e.getMessage());
	        return null;
	    }

	    try {
	        boolean responded = userHistoryLatch.await(5, TimeUnit.SECONDS);
	        if (!responded) {
	            System.err.println("⏱ USER_HISTORY timeout");
	            return null;
	        }
	    } catch (InterruptedException e) {
	        System.err.println("🛑 Interrupted while waiting for USER_HISTORY");
	        return null;
	    }

	    return userHistoryResult;
	}

	/**
	 * Handles the server response for the user history command.
	 *
	 * @param packet The command packet received from the server.
	 */
	private void handleUserHistoryResponse(CommandPacket packet) {
	    this.userHistoryResult = packet.getTable(); // assuming getTable() gives List<Map<...>>
	    if (userHistoryLatch != null) {
	        userHistoryLatch.countDown();
	    }
	}

	
	//=================== Recover User Command & Handler ===================
	/**
	 * Sends a RECOVER command to the server to retrieve subscriber information (e.g., for lost credentials).
	 * Waits for a response and returns the result mapped by the response code.
	 *
	 * @param userId The ID of the user attempting to recover their information.
	 * @return A Map where the key is the server's response code and the value is a list containing:
	 *         - subscriber name
	 *         - subscriber email
	 *         - subscriber phone
	 *         - parking confirmation code (if any)
	 *         Returns null on error or timeout.
	 */
	public Map<Integer, List<String>> RecoverUser(String userId) {
	    recoverLatch = new CountDownLatch(1);
	    recoverCode = 500;
	    recoverData = null;

	    CommandPacket command = new CommandPacket();
	    command.setCommand("RECOVER");

	    Map<String, String> args = new HashMap<>();
	    args.put("user_id", userId);
	    command.setArgs(args);

	    try {
	        sendToServer(Gson.toJson(command));
	    } catch (IOException e) {
	        System.err.println("❌ Failed to send RECOVER: " + e.getMessage());
	        return null;
	    }

	    try {
	        boolean responded = recoverLatch.await(5, TimeUnit.SECONDS);
	        if (!responded) {
	            System.err.println("⏱ RECOVER timeout");
	            return null;
	        }
	    } catch (InterruptedException e) {
	        System.err.println("🛑 Interrupted during RECOVER wait");
	        return null;
	    }

	    Map<Integer, List<String>> result = new HashMap<>();
	    result.put(recoverCode, recoverData);
	    return result;
	}

	/**
	 * Handles the server's response to the RECOVER command.
	 * Extracts subscriber recovery data if the response code is 200.
	 *
	 * @param packet The CommandPacket received from the server.
	 */
	private void handleRecoverResponse(CommandPacket packet) {
	    this.recoverCode = packet.getAnswer();
	    if (this.recoverCode != 200) {
	        this.recoverData = null; // no data if not successful
	        return;
	    }

	    List<String> data = new ArrayList<>();
	    Map<String, String> args = packet.getArgs();
	    data.add(args.getOrDefault("subscriber_name", ""));
	    data.add(args.getOrDefault("subscriber_email", ""));
	    data.add(args.getOrDefault("subscriber_phone", ""));
	    data.add(args.getOrDefault("parking_confirmation_code", ""));

	    this.recoverData = data;
	    if (recoverLatch != null) {
	        recoverLatch.countDown();
	    }
	}

	
// ========================================================= Shared Commands -- END =========================================================	
	
/*///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
========================================================================================================================================================
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////*/	
	
// ========================================================= Helper Methods -- Start =========================================================	
	
	/**
	 * Constructs a new ClientCore instance and opens a connection to the server.
	 *
	 * @param host The server host address.
	 * @param port The server port number.
	 * @throws IOException If the connection cannot be established.
	 */
	public ClientCore(String host, int port) throws IOException {
	    super(host, port);
	    this.openConnection();
	}

	/**
	 * Sets the internal user information for the currently logged-in subscriber.
	 *
	 * @param id       Subscriber ID.
	 * @param name     Subscriber full name.
	 * @param email    Subscriber email.
	 * @param status   Subscription status (e.g., active, frozen).
	 * @param phone    Subscriber phone number.
	 * @param password Subscriber password.
	 */
	private void setUser(String id, String name, String email, String status, String phone, String password) {
	    this.subscriberId = id;
	    this.subscriberName = name;
	    this.subscriberEmail = email;
	    this.subscriberStatus = status;
	    this.subscriberPhone = phone;
	    this.subscriberPassword = password;
	}

	/**
	 * @return The subscriber ID of the currently logged-in user.
	 */
	public String getSubscriberId() {
	    return this.subscriberId;
	}

	/**
	 * @return The name of the currently logged-in subscriber.
	 */
	public String getSubscriberName() {
	    return this.subscriberName;
	}

	/**
	 * @return The subscription status of the current subscriber (e.g., active, frozen).
	 */
	public String getSubscriberStatus() {
	    return this.subscriberStatus;
	}

	/**
	 * @return The email address of the current subscriber.
	 */
	public String getSubscriberEmail() {
	    return this.subscriberEmail;
	}

	/**
	 * @return The phone number of the current subscriber.
	 */
	public String getSubscriberPhone() {
	    return this.subscriberPhone;
	}

	/**
	 * @return The password of the current subscriber.
	 */
	public String getSubscriberPassword() {
	    return this.subscriberPassword;
	}

	/**
	 * Prints each row of the given table (List of maps) in raw format.
	 * Useful for debugging or inspecting server responses.
	 *
	 * @param tb The table to print.
	 */
	public void showRawTable(List<Map<String, String>> tb) {
	    if (tb == null || tb.isEmpty()) {
	        System.out.println("No data available in the table.");
	        return;
	    }

	    for (Map<String, String> row : tb) {
	        System.out.println(row);
	    }
	}

// ========================================================= Helper Methods -- END =========================================================	
	
/*///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
========================================================================================================================================================
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////*/	

}
