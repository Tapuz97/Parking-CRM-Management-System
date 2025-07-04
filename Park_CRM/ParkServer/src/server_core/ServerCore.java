package server_core;

import java.io.File;
import java.io.FileWriter;	
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import com.google.gson.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ocsf.server.*;

/**
 * ServerCore is the main server-side controller extending AbstractServer.
 * It handles incoming client requests, manages threads for monitoring parking orders and generating monthly reports,
 * and interfaces with the database via the DBhandler.
 */
public class ServerCore extends AbstractServer {

	private String ip;
	private int port;
	private Connection con;
	private DBhandler dbhandler;
	private OrderMonitorThread monitorThread;
	private MonthlyReportsThread reportsThread;
	private ReportsCSV CSV;
	private Map<ConnectionToClient,String > connectedClients;
	private final ObservableList<String> logList = FXCollections.observableArrayList();
	private final ObservableList<String> liveClients = FXCollections.observableArrayList();


	// =================== System Commands -- START ===================

	/**
	 * Constructs the ServerCore with IP, port, and a database connection.
	 *
	 * @param ip   the IP address of the server
	 * @param port the port number to listen on
	 * @param con  the JDBC database connection
	 */
	public ServerCore(String ip, int port, Connection con) {
		super(port);
		this.ip = ip;
		this.port = port;
		this.con = con;
		dbhandler = new DBhandler(con);
		CSV = new ReportsCSV();
		monitorThread = new OrderMonitorThread(dbhandler);
		reportsThread = new MonthlyReportsThread(dbhandler, CSV);
		monitorThread.start();
		reportsThread.start();
		connectedClients = new HashMap<>();
	}

	/**
	 * Sets the Discord API webhook URLs for recovery and orders monitor notifications.
	 *
	 * @param recoveryAPI     URL for recovery notifications
	 * @param ordersMonitorAPI URL for order-related notifications
	 */
	public boolean setAPIkeys(String recoveryAPI, String ordersMonitorAPI) {
		return dbhandler.setAPIkeys(recoveryAPI, ordersMonitorAPI);
	}

	/**
	 * Starts the server and begins listening for client connections.
	 *
	 * @return true if the server started successfully, false otherwise
	 */
	public boolean start() {
		try {
			this.listen();
			System.out.println("✔️ Server is now listening on port " + getPort());
			return true;
		} catch (Exception e) {
			System.err.println("❌ Failed to start server: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Stops the server and its associated monitoring threads.
	 */
	public void stop() {
		Gson Gson = new Gson();
		CommandPacket response = new CommandPacket();
		response.setCommand("SHUTDOWN");
		response.setAnswer(200);
		response.setDescription("The server is inactive, please try again later.");
		this.sendToAllClients(Gson.toJson(response));
		try {
			Thread.sleep(300);
		} catch (InterruptedException e) {}
		
		try {
			this.close();
			System.out.println("✔️ Server has been stopped.");
			if (monitorThread != null) {
				monitorThread.stopMonitoring();
				System.out.println("✔️ Order Monitor Thread has been stopped.");
			}
			if (reportsThread != null) {
				reportsThread.stopThread();
				System.out.println("✔️ Report Thread has been stopped.");
			}
		} catch (Exception e) {
			System.err.println("❌ Error while stopping server: " + e.getMessage());
		}
	}
	
	/**
	 * Called when a client connects. Logs the client's IP and hostname.
	 *
	 * @param client the client connection
	 */
	@Override
	protected void clientConnected(ConnectionToClient client) {
	}

	/**
	 * Called when a client disconnects. Logs the disconnect event.
	 *
	 * @param client the client that disconnected
	 */
	@Override
	synchronized protected void clientDisconnected(ConnectionToClient client) {
	    String ip = (String) client.getInfo("user_ip");
	    String userId = (String) client.getInfo("user_id");
	    boolean terminated = (boolean) client.getInfo("terminated");
	    liveRemove(userId);
	    logDisconnect(userId, ip, !terminated);
	}


	
	/**
	 * Logs a connection event for a user.
	 * The log entry includes the current time, user ID, IP address, and host name.
	 * The entry is added to the `logList`.
	 *
	 * @param userId the ID of the user
	 * @param ip     the IP address of the user
	 * @param host   the host name of the user
	 */
	private void logConnect(String userId, String ip, String host) {
	    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
	    String entry = String.format("[%s] | Connected | UserId: %s | IP:%s | Host:%s", time, userId, ip, host);
	    logList.add(entry);
	}

	/**
	 * Logs a disconnection event for a user.
	 * The log entry includes the current time, user ID, IP address, and the reason (safe disconnect or termination).
	 * The entry is added to the `logList`.
	 *
	 * @param userId the ID of the user
	 * @param ip     the IP address of the user
	 * @param safe   true if the disconnection was safe, false if it was terminated
	 */
	private void logDisconnect(String userId, String ip, boolean safe) {
	    String reason = safe ? "Disconnected" : "Terminated";
	    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
	    String entry = String.format("[%s] | %s | UserId: %s | IP: %s", time, reason, userId, ip);
	    logList.add(entry);
	}

	/**
	 * Adds a user to the list of live clients.
	 * The entry includes the current time, user ID, and IP address.
	 *
	 * @param userId the ID of the user
	 * @param ip     the IP address of the user
	 */
	public void liveAdd(String userId, String ip) {
	    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
	    String display = String.format("[%s] | Connected | User_ID: %s | IP: %s", time, userId, ip);
	    liveClients.add(display);
	}

	/**
	 * Removes a user from the list of live clients.
	 * The removal is based on a partial match of the user ID in the entries.
	 *
	 * @param userId the ID of the user to remove
	 */
	public void liveRemove(String userId) {
	    liveClients.removeIf(entry -> entry.contains("User_ID: " + userId));
	}

	/**
	 * Retrieves the list of live clients.
	 *
	 * @return an observable list of live client entries
	 */
	public ObservableList<String> getLiveClients() {
	    return liveClients;
	}

	/**
	 * Retrieves the connection log list.
	 *
	 * @return an observable list of log entries
	 */
	public ObservableList<String> getLogList() {
	    return logList;
	}


	/**
	 * Main message dispatcher for handling client requests.
	 *
	 * @param msg    the JSON-encoded CommandPacket from the client
	 * @param client the client connection
	 */
	@Override
	protected void handleMessageFromClient(Object msg, ConnectionToClient client) {
	    if (!(msg instanceof String json)) return;
	    Gson Gson = new Gson();
	    CommandPacket packet = Gson.fromJson(json, CommandPacket.class);
	    String request = packet.getCommand().toUpperCase();
	    Map<String, String> args = packet.getArgs();
	    CommandPacket response = new CommandPacket();

	    System.out.println("📥 Received CommandPacket: " + packet);

	    try {
	        switch (request) {
	        	// General commands
	            case "LOGIN" -> loginHandler(response, args, client);
	            case "LOGOUT" -> logoutHandler(client);

	            // Admin commands
	            case "CREATE" -> createHandler(response, args);
	            case "CURRENT_PARKING" -> CurrentParkingHandler(response, args);
	            case "REPORT" -> ReporteHandler(response, args);

	            // User commands
	            case "DEPOSIT" -> DepositHandler(response, args);
	            case "PICKUP" -> PickupHandler(response, args);
	            case "EXTEND" -> ExtendHandler(response, args);
	            case "RESERVE" -> ReserveHandler(response, args);
	            case "EDIT_USER" -> EditHandler(response, args);

	            // Shared commands
	            case "USER_HISTORY" -> UserHistoryHandler(response, args);
	            case "RECOVER" -> RecoverHandler(response, args);

	            default -> {
	                response.setCommand(request);
	                defaultHandler(response);
	            }
	        }

	        if (!request.equals("LOGOUT")) {
	        	System.out.println("📤 Sending CommandPacket: " + response);
	            client.sendToClient(Gson.toJson(response));
	        }
	    } catch (Exception e) {
	        try {
	            response = new CommandPacket();
	            response.setAnswer(503);
	            response.setArgs(Map.of("error", e.getMessage()));
	            client.sendToClient(response);
	        } catch (Exception ignored) {
	            System.err.println("❌ Failed to send error response: " + ignored.getMessage());
	        }
	        System.err.println("❌ Error handling command: " + e.getMessage());
	    }
	}

	/**
	 * Fallback handler for unrecognized commands.
	 *
	 * @param response the response CommandPacket to populate with error info
	 */
	private void defaultHandler(CommandPacket response) {
	    response.setAnswer(404);
	    response.setArgs(Map.of("error", "Unrecognized command"));
	}

	// =================== System Commands -- END ===================


	
/*///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
========================================================================================================================================================
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////*/
		
	// ========================================================= Admin Commands -- START =========================================================	

/**
 * Handles the creation of a new subscriber. Validates required fields and interacts with the database to create the subscriber.
 *
 * @param response The `CommandPacket` to store the response.
 * @param args     The arguments containing subscriber details (name, email, password, phone).
 */
private void createHandler(CommandPacket response, Map<String, String> args) {
    response.setCommand("CREATE");

    // Check required fields
    if (!args.containsKey("name") || !args.containsKey("email") || 
        !args.containsKey("password") || !args.containsKey("phone")) {
        response.setAnswer(400);
        response.setDescription("Missing required fields.");
        return;
    }

    String name = args.get("name").trim();
    String email = args.get("email").trim();
    String password = args.get("password").trim();
    String phone = args.get("phone").trim();

    int result = dbhandler.createSubscriber(name, email, password, phone);

    switch (result) {
        case 1 -> {
            response.setAnswer(200);
            response.setDescription(String.valueOf(dbhandler.getSubscriber(email, password).get("subscriber_id")));
        }
        case -2 -> {
            response.setAnswer(409);
            response.setDescription("Email already in use.");
        }
        case -3 -> {
            response.setAnswer(409);
            response.setDescription("Phone number already in use.");
        }
        default -> {
            response.setAnswer(500);
            response.setDescription("Database error occurred during creation.");
        }
    }
}
	
	/**
	 * Handles the retrieval of the current parking table and usage percentage.
	 *
	 * @param response The `CommandPacket` to store the response.
	 * @param args     The arguments (not used in this handler).
	 */
	private void CurrentParkingHandler(CommandPacket response, Map<String, String> args) {
	    response.setCommand("CURRENT_PARKING");
	
	    List<Map<String, String>> table = dbhandler.getParkingTable();
	    String percent = dbhandler.getParkingUsagePercent();
	
	    if (table == null) {
	        response.setAnswer(500);
	        response.setDescription("Failed to load parking data.");
	        return;
	    }
	
	    response.setAnswer(200);
	    response.setDescription(percent); // capacity percentage
	    response.setTable(table);
	}
	
	/**
	 * Handles the generation of reports (users or parking) for a specific month and year.
	 *
	 * @param response The `CommandPacket` to store the response.
	 * @param args     The arguments containing report details (report_month, report_year, report_type).
	 */
	private void ReporteHandler(CommandPacket response, Map<String, String> args) {
	    response.setCommand("REPORT");
	
	    // Validate required fields
	    if (!args.containsKey("report_month") || !args.containsKey("report_year") || !args.containsKey("report_type")) {
	        response.setAnswer(400);
	        response.setDescription("Missing required fields: report_month, report_year, or report_type.");
	        return;
	    }
	    String usersCount = dbhandler.getTotalUsers();
	    String month = args.get("report_month").trim();
	    String year = args.get("report_year").trim();
	    String type = args.get("report_type").trim(); // "USERS" or "PARKING"
	
	    List<Map<String, String>> reportTable;
	
	    switch (type.toUpperCase()) {
	        case "USERS" -> {
	            reportTable = CSV.loadFromCSV("USERS", year, month);
	        }
	        case "PARKING" -> {
	            reportTable = CSV.loadFromCSV("PARKING", year, month);
	        }
	        default -> {
	            response.setAnswer(400);
	            response.setDescription("Unknown report_type: " + type);
	            return;
	        }
	    }
	
	    response.setAnswer(200);
	    response.setDescription("Report generated successfully.");
	    response.setTable(reportTable);
	    response.setArgs(Map.of("users_count", usersCount, "report_month", month, "report_year", year, "report_type", type));
	}


	// ========================================================= Admin Commands -- END =========================================================	
	
/*///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
========================================================================================================================================================
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////*/
		
	// ========================================================= User Commands -- START =========================================================

	/**
	 * Handles the deposit vehicle request.
	 * Validates subscriber ID, optionally processes a reservation, and issues a confirmation code.
	 *
	 * @param response the response packet to populate
	 * @param args     the arguments including subscriber_id and optionally order_number
	 */
	private void DepositHandler(CommandPacket response, Map<String, String> args) {
	    response.setCommand("DEPOSIT");

	    if (!args.containsKey("subscriber_id")) {
	        response.setAnswer(400);
	        response.setDescription("Missing subscriber ID.");
	        return;
	    }

	    String subscriberId = args.get("subscriber_id").trim();
	    String orderNumber = args.get("order_number").trim();

	    if (dbhandler.userHasActiveDeposit(subscriberId)) {
	        response.setAnswer(409);
	        response.setDescription("Active order found. Please pickup your vehicle first.");
	        return;
	    }

	    try {
	        if (orderNumber == null || orderNumber.isBlank() || orderNumber.equals("")) {
	            if (dbhandler.userHasReservationToday(subscriberId)) {
	                response.setAnswer(403);
	                response.setDescription("Reservation found. Please enter your order number.");
	                return;
	            }

	            String confirmationCode = dbhandler.depositVehicle(subscriberId, "");
	            if (confirmationCode == null) {
	                response.setAnswer(404);
	                response.setDescription("No available parking spaces.");
	                return;
	            }

	            response.setAnswer(200);
	            response.setDescription(confirmationCode);
	            return;
	        }

	        String confirmationCode = dbhandler.depositVehicle(subscriberId, orderNumber);
	        if (confirmationCode == null) {
	            response.setAnswer(404);
	            response.setDescription("No matching order or parking space found.");
	            return;
	        }

	        response.setAnswer(200);
	        response.setDescription(confirmationCode);

	    } catch (SQLException e) {
	        System.err.println("❌ Error in DepositHandler: " + e.getMessage());
	        response.setAnswer(500);
	        response.setDescription("Server error during vehicle deposit.");
	    }
	}

	/**
	 * Handles the pickup vehicle request.
	 * Updates parking and order status and logs pickup in history.
	 *
	 * @param response the response packet to populate
	 * @param args     must contain subscriber_id and parking_confirmation_code
	 */
	private void PickupHandler(CommandPacket response, Map<String, String> args) {
	    response.setCommand("PICKUP");

	    if (!args.containsKey("subscriber_id") || !args.containsKey("parking_confirmation_code")) {
	        response.setAnswer(400);
	        response.setDescription("Missing subscriber ID or confirmation code.");
	        return;
	    }

	    String subscriberId = args.get("subscriber_id").trim();
	    String parkingCode = args.get("parking_confirmation_code").trim();

	    try {
	        int result = dbhandler.pickupVehicle(subscriberId, parkingCode);
	        switch (result) {
	            case 200 -> {
	                response.setAnswer(200);
	                response.setDescription("Vehicle successfully picked up.");
	            }
	            case 404 -> {
	                response.setAnswer(404);
	                response.setDescription("No valid pickup found for today.");
	            }
	            case 403 -> {
	                response.setAnswer(403);
	                response.setDescription("Order picked up already");
	            }
	            case 402 -> {
	                response.setAnswer(402);
	                response.setDescription("Order had been canceled.");
	            }
	            default -> {
	                response.setAnswer(500);
	                response.setDescription("Server error while processing pickup.");
	            }
	        }
	    } catch (SQLException e) {
	        response.setAnswer(503);
	        response.setDescription("Database error: " + e.getMessage());
	    }
	}

	/**
	 * Handles the parking extension request.
	 * Updates the order and logs the extension if valid.
	 *
	 * @param response the response packet to populate
	 * @param args     must include subscriber_id and parking_confirmation_code
	 */
	private void ExtendHandler(CommandPacket response, Map<String, String> args) {
	    response.setCommand("EXTEND");

	    if (!args.containsKey("subscriber_id") || !args.containsKey("parking_confirmation_code")) {
	        response.setAnswer(400);
	        response.setDescription("Missing subscriber ID or confirmation code.");
	        return;
	    }

	    String subscriberId = args.get("subscriber_id");
	    String confirmationCode = args.get("parking_confirmation_code");

	    try {
	        int result = dbhandler.extendParking(subscriberId, confirmationCode);
	        response.setAnswer(result);
	        switch (result) {
	            case 404 -> response.setDescription("No matching order found");
	            case 500 -> response.setDescription("Server failed to extend parking.");
	            case 200 -> response.setDescription("Parking session extended by 4 hours.");
	            case 409 -> response.setDescription("Parking already extended for today.");
	            case 403 -> response.setDescription("Parking time exceeded and late. Please pickup your vehicle.");
	            case 407 -> response.setDescription("Parking already picked up.");
	        }
	    } catch (Exception e) {
	        System.err.println("❌ Extend error: " + e.getMessage());
	        response.setAnswer(500);
	        response.setDescription("Internal error during extension.");
	    }
	}

	/**
	 * Handles the reservation request.
	 * Reserves a parking spot for the subscriber if constraints are met.
	 *
	 * @param response the response packet to populate
	 * @param args     must contain subscriber_id, order_date and order_time
	 */
	private void ReserveHandler(CommandPacket response, Map<String, String> args) {
	    response.setCommand("RESERVE");

	    if (!args.containsKey("subscriber_id") || !args.containsKey("order_date") || !args.containsKey("order_time")) {
	        response.setAnswer(400);
	        response.setDescription("Missing reservation details.");
	        return;
	    }

	    String subscriberId = args.get("subscriber_id");
	    String dateStr = args.get("order_date");
	    String timeStr = args.get("order_time");

	    if (dbhandler.userHasReservationForTheDay(subscriberId, dateStr)) {
	        response.setDescription("You already have a reservation.");
	        response.setAnswer(409);
	        return;
	    }

	    try {
	        LocalDate orderDate = LocalDate.parse(dateStr);
	        LocalTime orderTime = LocalTime.parse(timeStr);

	        int result = dbhandler.reserveParking(subscriberId, orderDate, orderTime);

	        if (result < 1000) {
	            response.setAnswer(result);
	            switch (result) {
	                case 404 -> response.setDescription("No available parking.");
	                case 409 -> response.setDescription("You already have a reservation.");
	                case 503 -> response.setDescription("Server error during reservation.");
	                case 403 -> response.setDescription("No available parking.");
	            }
	            return;
	        }

	        response.setAnswer(200);
	        response.setDescription(String.valueOf(result));

	    } catch (DateTimeParseException e) {
	        response.setAnswer(400);
	        response.setDescription("Invalid date or time format.");
	    } catch (Exception e) {
	        System.err.println("❌ Error in ReserveHandler: " + e.getMessage());
	        response.setAnswer(500);
	        response.setDescription("Internal server error.");
	    }
	}

	/**
	 * Handles user profile updates (email, password, phone).
	 *
	 * @param response the response packet to populate
	 * @param args     must include subscriber_id, subscriber_email, subscriber_password, subscriber_phone
	 */
	private void EditHandler(CommandPacket response, Map<String, String> args) {
	    response.setCommand("EDIT_USER");

	    if (!args.containsKey("subscriber_id") || !args.containsKey("subscriber_email") ||
	        !args.containsKey("subscriber_password") || !args.containsKey("subscriber_phone")) {
	        response.setAnswer(400);
	        response.setDescription("Missing required fields.");
	        return;
	    }

	    String id = args.get("subscriber_id");
	    String email = args.get("subscriber_email");
	    String password = args.get("subscriber_password");
	    String phone = args.get("subscriber_phone");

	    try {
	        int code = dbhandler.editSubscriber(id, email, password, phone);
	        switch (code) {
	            case 200 -> {
	                response.setAnswer(200);
	                response.setDescription("Details updated successfully.");
	            }
	            case -2 -> {
	                response.setAnswer(409);
	                response.setDescription("Email already in use.");
	            }
	            case -3 -> {
	                response.setAnswer(409);
	                response.setDescription("Phone number already in use.");
	            }
	            default -> {
	                response.setAnswer(500);
	                response.setDescription("Failed to update details.");
	            }
	        }
	    } catch (Exception e) {
	        response.setAnswer(503);
	        response.setDescription("Database error: " + e.getMessage());
	    }
	}

	// ========================================================= User Commands -- END =========================================================

/*///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
========================================================================================================================================================
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////*/
		
	// ========================================================= Shared Commands -- START =========================================================

	/**
	 * Handles the login command.
	 * Verifies user credentials and returns user data if valid.
	 *
	 * @param response the response packet to populate
	 * @param args     must include subscriber_email and subscriber_password
	 */
	private void loginHandler(CommandPacket response, Map<String, String> args,ConnectionToClient client) {
	    response.setCommand("LOGIN");

	    try {
	        if (!args.containsKey("subscriber_email") || !args.containsKey("subscriber_password")) {
	            response.setAnswer(400);
	            response.setDescription("Missing email or password.");
	            return;
	        }

	        String email = args.get("subscriber_email").trim();
	        String password = args.get("subscriber_password").trim();
	        String forceLogin = args.get("force_login").trim();
	        
	        Map<String, String> result = dbhandler.getSubscriber(email, password);
	        if (result == null || result.isEmpty()) {
	            response.setAnswer(401);
	            response.setDescription("Invalid email or password.");
	            return;
	        }
	        
	        String userId = result.get("subscriber_id").trim();
	        
	        if (getClientConnection(userId) != null) {
	            if ("true".equals(forceLogin)) {
	                terminateUserSession(getClientConnection(userId));
	            } else {
	                response.setAnswer(403);
	                response.setDescription("User already logged in.");
	                return;
	            }
	        }
	        
	        if ("db".equals(result.get("error"))) {
	            response.setAnswer(503);
	            response.setDescription("Database error during login.");
	        } else {
	            response.setAnswer(200);
	            response.setDescription("Login successful.");
	            response.setArgs(result);
	            initiateUserSession(userId, client);
	        }

	    } catch (Exception e) {
	        response.setAnswer(500);
	        response.setDescription("Server error: " + e.getMessage());
	    }
	}

	/**
	 * Handles the logout command.
	 * Closes the connection for the client.
	 *
	 * @param client the client to disconnect
	 */
	private void logoutHandler(ConnectionToClient client) {
		client.setInfo("terminated", false);
		connectedClients.remove(client);
		try {
			client.close();
		} catch (IOException e) {
			System.err.println("❌ Failed to close socket: " + e.getMessage());
		}
	}


	/**
	 * Handles the user recovery command.
	 * Retrieves user contact info and active parking status and triggers a Discord alert.
	 *
	 * @param response the response packet to populate
	 * @param args     must include user_id
	 */
	private void RecoverHandler(CommandPacket response, Map<String, String> args) {
	    response.setCommand("RECOVER");

	    if (!args.containsKey("user_id")) {
	        response.setAnswer(400);
	        response.setDescription("Missing user_id.");
	        return;
	    }

	    String userId = args.get("user_id");

	    try {
	        Map<String, String> data = dbhandler.recoverUser(userId);

	        if (data == null) {
	            response.setAnswer(404);
	            response.setDescription("No active order found for user.");
	        } else {
	            response.setAnswer(200);
	            response.setDescription("User recovery successful.");
	            response.setArgs(data);
	        }

	    } catch (Exception e) {
	        response.setAnswer(503);
	        response.setDescription("Database error during recovery: " + e.getMessage());
	    }
	}

	/**
	 * Handles the user history command.
	 * Retrieves the user’s parking history and returns it as a table.
	 *
	 * @param response the response packet to populate
	 * @param args     must include user_id
	 */
	private void UserHistoryHandler(CommandPacket response, Map<String, String> args) {
	    response.setCommand("USER_HISTORY");

	    if (!args.containsKey("user_id")) {
	        response.setAnswer(400);
	        response.setDescription("Missing user_id.");
	        return;
	    }

	    String userId = args.get("user_id");

	    try {
	        List<Map<String, String>> history = dbhandler.getUserHistory(userId);

	        if (history == null || history.isEmpty()) {
	            response.setAnswer(204); // No content
	            response.setDescription("No parking history found for this user.");
	        } else {
	            response.setAnswer(200);
	            response.setDescription("Parking history retrieved successfully.");
	            response.setTable(history);
	        }

	    } catch (Exception e) {
	        response.setAnswer(503);
	        response.setDescription("Database error: " + e.getMessage());
	    }
	}

	// ========================================================= Shared Commands -- END =========================================================

/*///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
========================================================================================================================================================
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////*/	
		
	// ========================================================= Helper Methods -- Start =======================================================
	
	/**
	 * Checks if a client is currently connected.
	 *
	 * @param client the client connection to check
	 * @return true if the client is connected, false otherwise
	 */
	private boolean isClientConnected(ConnectionToClient client) {
	    return connectedClients != null && connectedClients.containsKey(client);
	}

	/**
	 * Terminates the session of a connected client.
	 * Sends a logout notification to the client, removes the client from the connected clients map,
	 * and closes the client connection.
	 *
	 * @param client the client connection to terminate
	 */
	private void terminateUserSession(ConnectionToClient client) {
	    if (isClientConnected(client)) {
	        connectedClients.remove(client);
	        try {
	            CommandPacket logoutPacket = new CommandPacket();
	            Gson Gson = new Gson();
	            logoutPacket.setCommand("DISCONNECT");
	            logoutPacket.setAnswer(200);
	            logoutPacket.setDescription("New session was started.");
	            client.sendToClient(Gson.toJson(logoutPacket));
	        } catch (IOException e) {
	            System.err.println("⚠️ Failed to send logout notification: " + e.getMessage());
	        }
	    }

	    try {
	        Thread.sleep(300);
	        client.setInfo("terminated", true);
	        client.close();
	    } catch (IOException | InterruptedException e) {
	        System.err.println("❌ Failed to close socket: " + e.getMessage());
	    }
	}

	/**
	 * Initiates a user session by associating the user ID with the client connection.
	 * Logs the connection event and adds the user to the list of live clients.
	 *
	 * @param userId the ID of the user
	 * @param client the client connection
	 */
	private void initiateUserSession(String userId, ConnectionToClient client) {
	    if (connectedClients != null) {
	        connectedClients.put(client, userId);
	    }
	    String ip = client.getInetAddress().getHostAddress();
	    String host = client.getInetAddress().getHostName();
	    client.setInfo("user_id", userId);
	    client.setInfo("user_ip", ip);
	    client.setInfo("user_host", host);
	    logConnect(userId, ip, host);
	    liveAdd(userId, ip);
	}

	/**
	 * Retrieves the client connection associated with a given user ID.
	 *
	 * @param userId the ID of the user
	 * @return the client connection if found, or null if no connection exists for the user ID
	 */
	private ConnectionToClient getClientConnection(String userId) {
	    for (Map.Entry<ConnectionToClient, String> entry : connectedClients.entrySet()) {
	        if (entry.getValue().equals(userId)) {
	            return entry.getKey();
	        }
	    }
	    return null;
	}
	
	/**
	 * Exports the server connection log to a CSV file.
	 * The log entries are parsed into a structured format and saved to a user-specified file.
	 * If no log entries are available, the method exits with a warning message.
	 *
	 * The file name is pre-filled with the format "BparkConnectionLog_dd_MM_yyyy.csv",
	 * where "dd_MM_yyyy" is the current date.
	 *
	 * @see FileChooser for file selection dialog.
	 */
	public void exportLogToCSV() {
	    List<Map<String, String>> parsedLog = parseLogList(logList);
	    if (parsedLog.isEmpty()) {
	        System.out.println("⚠️ No log entries to export.");
	        return;
	    }
	    String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd_MM_yyyy"));
	    String fileName = "BparkConnectionLog_" + date + ".csv";
	    FileChooser fileChooser = new FileChooser();
	    fileChooser.setTitle("Save Log as CSV");
	    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
	    fileChooser.setInitialFileName(fileName);
	    File file = fileChooser.showSaveDialog(new Stage());

	    if (file != null) {
	        boolean success = ReportsCSV.savDataToCsv(file, parsedLog);
	        if (success) {
	            System.out.println("✔️ Log exported successfully to " + file.getAbsolutePath());
	        } else {
	            System.out.println("❌ Failed to export log to CSV.");
	        }
	    }
	}

	
	/**
	 * Parses a list of log entries into a structured list of maps.
	 * Each log entry is expected to follow the format:
	 * [Time] | Event | UserId: <user_id> | IP: <ip_address> | Host: <host_name>
	 *
	 * @param logList An observable list of log entries as strings.
	 * @return A list of maps where each map represents a parsed log entry with keys:
	 *         "color", "Time", "Event", "UserID", "IP", and "Host".
	 *         The "color" key provides a color code based on the event type.
	 */
	private List<Map<String, String>> parseLogList(ObservableList<String> logList) {
	    List<Map<String, String>> result = new ArrayList<>();

	    for (String line : logList) {
	        String[] parts = line.split("\\|");
	        if (parts.length < 4) continue;

	        String time = parts[0].trim().replaceAll("[\\[\\]]", "");
	        String event = parts[1].trim();
	        String userId = parts[2].trim().replace("UserId:", "").trim();
	        String ip = parts[3].trim().replace("IP:", "").trim();
	        String host = (parts.length >= 5) ? parts[4].trim().replace("Host:", "").trim() : "";
	        
	        String color = switch (event.toLowerCase()) {
	        case "connected"    -> "224,240,227"; // #e0f0e3
	        case "disconnected" -> "184,216,190"; // #b8d8be
	        case "terminated"   -> "253,224,224"; // #fde0e0
	        default             -> "";
	        };

	        Map<String, String> row = new LinkedHashMap<>();
	        //row.put("color", color);
	        row.put("Time", time);
	        row.put("Event", event);
	        row.put("UserID", userId);
	        row.put("IP", ip);
	        row.put("Host", host);

	        result.add(row);
	    }

	    return result;
	}





	// ========================================================= Helper Methods -- END =========================================================	
	
/*///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
========================================================================================================================================================
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////*/	

}
