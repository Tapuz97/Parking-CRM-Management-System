package server_core;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Handles all database-related operations and message dispatching for the Bpark server.
 * Encapsulates SQL execution and optional Discord notifications.
 */
public class DBhandler {

    /** Active SQL database connection for all queries. */
    private Connection conn;

    /** Sends structured notifications to Discord (late pickups, cancellations, recovery, etc.). */
    private DiscordNotifier discordNotifier = new DiscordNotifier(); // notifier for discord messages. msg types: LatePickup, CancelOrder, UserRecovery

    /**
     * Constructs a DBhandler with an existing SQL connection.
     *
     * @param con An active JDBC connection.
     */
    public DBhandler(Connection con) {
        this.conn = con;
    }

    /**
     * Initializes DiscordNotifier with API keys for recovery and order-monitoring alerts.
     *
     * @param recoveryAPI       Webhook URL for recovery-related messages.
     * @param ordersMonitorAPI  Webhook URL for order-related monitoring messages.
     */
    public boolean setAPIkeys(String recoveryAPI, String ordersMonitorAPI) {
    	if (verifyAPIkey(recoveryAPI) && verifyAPIkey(ordersMonitorAPI)) {
    			discordNotifier = new DiscordNotifier(recoveryAPI, ordersMonitorAPI);
    			discordNotifier.enableMonitoring(true); // Enable monitoring if both keys are valid
    			return true; // ✅ Both keys are valid, notifier initialized
    		}
    	return false;
    }
    
    private boolean verifyAPIkey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }
        if (!apiKey.matches("^https://discord\\.com/api/webhooks/\\d+/[\\w-]+$")) {
            return false;
        }

        System.out.println("🔍 Verifying API key: " + apiKey);
        int result = discordNotifier.verifyKey(apiKey); // single call only
        System.out.println("🔁 Verification result code: " + result);

        return result == 204;
    }


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    
 // ========================================================= Create user =========================================================

    /**
     * Attempts to create a new subscriber in the database.
     * Performs uniqueness checks on email and phone before inserting.
     *
     * @param name     Subscriber full name.
     * @param email    Subscriber email (must be unique).
     * @param password Subscriber password (plaintext or hashed depending on usage).
     * @param phone    Subscriber phone number (must be unique).
     * @return  1  if insertion succeeded,  
     *         -2 if email already exists,  
     *         -3 if phone already exists,  
     *          0 if any other error occurred.
     */
    public int createSubscriber(String name, String email, String password, String phone) {
        try {
            // Check if email already exists
            String emailCheckQuery = String.format(
                "SELECT COUNT(*) FROM subscribers WHERE subscriber_email = '%s'", email
            );
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(emailCheckQuery);
                if (rs.next() && rs.getInt(1) > 0) return -2; // Email exists
            }

            // Check if phone already exists
            String phoneCheckQuery = String.format(
                "SELECT COUNT(*) FROM subscribers WHERE subscriber_phone = '%s'", phone
            );
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(phoneCheckQuery);
                if (rs.next() && rs.getInt(1) > 0) return -3; // Phone exists
            }

            // Insert new subscriber
            String insertQuery = String.format("""
                INSERT INTO subscribers (subscriber_name, subscriber_email, subscriber_password, subscriber_phone, subscription_status)
                VALUES ('%s', '%s', '%s', '%s', 'user')
            """, name, email, password, phone);

            try (Statement stmt = conn.createStatement()) {
                int rowsInserted = stmt.executeUpdate(insertQuery);
                return rowsInserted == 1 ? 1 : 0;
            }

        } catch (SQLException e) {
            System.err.println("❌ DB error during createSubscriber: " + e.getMessage());
            return 0;
        }
    }

    // ========================================================= Get parking table + parking Percent =========================================================

    /**
     * Retrieves the current state of all parking spaces.
     * Includes parking space number, status, confirmation code,
     * and the subscriber ID for active orders (if any).
     *
     * @return A list of maps representing rows of parking data, or null on error.
     */
    public List<Map<String, String>> getParkingTable() {
        String query = """
            SELECT p.parking_space,
                   p.status,
                   p.confirmation_code,
                   o.subscriber_id
            FROM parking p
            LEFT JOIN (
                SELECT confirmation_code, subscriber_id
                FROM orders
                WHERE order_status = 'active'
            ) o ON p.confirmation_code = o.confirmation_code
            ORDER BY p.parking_space
        """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            ResultSet rs = stmt.executeQuery();
            return formatResultSet(rs);
        } catch (SQLException e) {
            System.err.println("❌ Error in getParkingTable: " + e.getMessage());
            return null;
        }
    }

    /**
     * Calculates the percentage of parking spaces currently in use (non-available).
     *
     * @return A string representing the usage percentage (rounded up), or "0" on error.
     */
    public String getParkingUsagePercent() {
        try (Statement stmt = conn.createStatement()) {
            ResultSet total = stmt.executeQuery("SELECT COUNT(*) FROM parking");
            int totalSpots = 0;
            if (total.next()) totalSpots = total.getInt(1);

            ResultSet used = stmt.executeQuery("SELECT COUNT(*) FROM parking WHERE status != 'available'");
            int usedSpots = 0;
            if (used.next()) usedSpots = used.getInt(1);

            if (totalSpots == 0) return "0";
            int percent = (int) Math.ceil(100.0 * usedSpots / totalSpots);
            return String.valueOf(percent);

        } catch (SQLException e) {
            System.err.println("❌ Error calculating parking usage: " + e.getMessage());
            return "0";
        }
    }


    
    
    
 // ========================================================= Deposit vehicle =========================================================

    /**
     * Handles the deposit of a vehicle by a subscriber.
     * If an order number is provided, verifies the order is for today and within the 15-minute window.
     * Otherwise, finds the next available parking spot and inserts a new order.
     *
     * @param subscriberId The subscriber's ID.
     * @param orderNumber  Optional reservation order number; can be null for walk-ins.
     * @return A confirmation code if deposit is successful, or null if validation fails or no space is available.
     * @throws SQLException If a database error occurs.
     */
    public String depositVehicle(String subscriberId, String orderNumber) throws SQLException {
        if (userHasActiveDeposit(subscriberId)) return null;
        int parkingSpace = -1;
        String confirmationCode = String.valueOf(generateConfirmationCode());

        if (orderNumber != null && !orderNumber.isBlank() && !orderNumber.equals("")) {
            String query = """
                SELECT parking_space, order_date, order_time
                FROM orders
                WHERE order_number = %s AND subscriber_id = %s
            """.formatted(orderNumber, subscriberId);

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
                if (!rs.next()) return null;

                parkingSpace = rs.getInt("parking_space");
                LocalDate orderDate = rs.getDate("order_date").toLocalDate();
                LocalTime orderTime = rs.getTime("order_time").toLocalTime();
                LocalDateTime now = LocalDateTime.now();

                // Validate order date is today and within 15 min after the order time
                LocalDateTime allowedStart = LocalDateTime.of(orderDate, orderTime);
                LocalDateTime allowedEnd = allowedStart.plusMinutes(15);

                if (!now.toLocalDate().equals(orderDate) || now.isAfter(allowedEnd)) {
                    return null; // Too late to fulfill this reservation
                }

                updateParkingLot(parkingSpace, Integer.parseInt(confirmationCode));
                updateParkingHistory(subscriberId, Integer.parseInt(orderNumber), "deposited");
                return confirmationCode;
            }
        } else {
            // New deposit - no reservation
            String findQuery = "SELECT parking_space FROM parking WHERE status = 'available' LIMIT 1";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(findQuery)) {
                if (!rs.next()) return null;
                parkingSpace = rs.getInt("parking_space");
            }

            int newOrderNumber = insertNewOrder(subscriberId, parkingSpace, Integer.parseInt(confirmationCode));
            updateParkingLot(parkingSpace, Integer.parseInt(confirmationCode));
            updateParkingHistory(subscriberId, newOrderNumber, "deposited");
            return confirmationCode;
        }
    }

    /**
     * Checks if the user has a reservation later today (after current time).
     *
     * @param subscriberId The subscriber's ID.
     * @return true if an upcoming reservation exists today; false otherwise.
     * @throws SQLException If a database access error occurs.
     */
    public boolean userHasUpcomingReservation(String subscriberId) throws SQLException {
        String query = """
            SELECT 1
            FROM orders
            WHERE subscriber_id = ?
              AND order_date = CURRENT_DATE
              AND order_time > CURRENT_TIME
        """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, Integer.parseInt(subscriberId));
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    /**
     * Checks whether the user has a reservation for today with status 'pending'.
     *
     * @param subscriberId The subscriber's ID.
     * @return true if such a reservation exists; false if none or on error.
     */
    public boolean userHasReservationToday(String subscriberId) {
        String query = """
            SELECT 1
            FROM orders
            WHERE subscriber_id = ?
              AND order_date = CURRENT_DATE
              AND order_status = 'pending'
            LIMIT 1
        """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, Integer.parseInt(subscriberId));
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("❌ Error checking today’s reservation: " + e.getMessage());
            return false;
        }
    }


    
    /**
     * Checks if a user has an active deposit in the parking system.
     * An active deposit means the user has parked a vehicle that has not yet been picked up.
     *
     * @param subscriberId The ID of the subscriber to check.
     * @return true if the user has an active deposit, false otherwise.
     */
    public boolean userHasActiveDeposit(String subscriberId) {
    	    try {
    	        int id = Integer.parseInt(subscriberId);

    	        // 🔍 Check if there is an active or late order (still occupying space)
    	        String orderStatusQuery = """
    	            SELECT order_number
    	            FROM orders
    	            WHERE subscriber_id = ?
    	              AND order_status IN ('active', 'late')
    	            LIMIT 1
    	        """;

    	        try (PreparedStatement stmt = conn.prepareStatement(orderStatusQuery)) {
    	            stmt.setInt(1, id);
    	            ResultSet rs = stmt.executeQuery();

    	            if (rs.next()) {
    	                // ✅ Found a currently occupying order — cannot deposit again
    	                return true;
    	            }
    	        }

    	        // 🧹 No active/late order? Fallback to parking_history (just in case)
    	        String latestEventQuery = """
    	            SELECT parking_date, parking_time, event_type
    	            FROM parking_history
    	            WHERE subscriber_id = ?
    	            ORDER BY parking_date DESC, parking_time DESC
    	            LIMIT 1
    	        """;

    	        try (PreparedStatement stmt = conn.prepareStatement(latestEventQuery)) {
    	            stmt.setInt(1, id);
    	            ResultSet rs = stmt.executeQuery();

    	            if (!rs.next()) return false;

    	            String eventType = rs.getString("event_type");
    	            LocalDate date = rs.getDate("parking_date").toLocalDate();
    	            LocalTime time = rs.getTime("parking_time").toLocalTime();

    	            if (!"deposited".equalsIgnoreCase(eventType)) return false;

    	            // Check if a pickup occurred after the deposit
    	            String pickupCheck = """
    	                SELECT 1
    	                FROM parking_history
    	                WHERE subscriber_id = ?
    	                  AND event_type = 'picked_up'
    	                  AND (parking_date > ? OR (parking_date = ? AND parking_time > ?))
    	                LIMIT 1
    	            """;

    	            try (PreparedStatement ps = conn.prepareStatement(pickupCheck)) {
    	                ps.setInt(1, id);
    	                ps.setDate(2, java.sql.Date.valueOf(date));
    	                ps.setDate(3, java.sql.Date.valueOf(date));
    	                ps.setTime(4, java.sql.Time.valueOf(time));
    	                ResultSet pickupRs = ps.executeQuery();
    	                return !pickupRs.next(); // true if no pickup found
    	            }
    	        }

    	    } catch (Exception e) {
    	        System.err.println("❌ Error checking active deposit: " + e.getMessage());
    	    }

    	    return false;
    	

    	/*
        String latestEventQuery = """
            SELECT parking_date, parking_time, event_type
            FROM parking_history
            WHERE subscriber_id = ?
            ORDER BY parking_date DESC, parking_time DESC
            LIMIT 1
        """;

        try (PreparedStatement stmt = conn.prepareStatement(latestEventQuery)) {
            stmt.setInt(1, Integer.parseInt(subscriberId));
            ResultSet rs = stmt.executeQuery();

            // ✅ If there's no history at all
            if (!rs.next()) {
                return false;
            }

            String eventType = rs.getString("event_type");
            LocalDate date = rs.getDate("parking_date").toLocalDate();
            LocalTime time = rs.getTime("parking_time").toLocalTime();

            // ✅ If the last event was not a deposit, nothing is active
            if (!"deposited".equalsIgnoreCase(eventType)) return false;

            // ✅ Check if a pickup happened after the deposit
            String checkPickupQuery = """
                SELECT 1
                FROM parking_history
                WHERE subscriber_id = ?
                  AND event_type = 'picked_up'
                  AND (parking_date > ? OR (parking_date = ? AND parking_time > ?))
                LIMIT 1
            """;

            try (PreparedStatement checkStmt = conn.prepareStatement(checkPickupQuery)) {
                checkStmt.setInt(1, Integer.parseInt(subscriberId));
                checkStmt.setDate(2, java.sql.Date.valueOf(date));
                checkStmt.setDate(3, java.sql.Date.valueOf(date));
                checkStmt.setTime(4, java.sql.Time.valueOf(time));
                ResultSet pickupRs = checkStmt.executeQuery();

                // ✅ If there's no later pickup, deposit is still active
                return !pickupRs.next();
            }

        } catch (SQLException e) {
            System.err.println("❌ Error checking active deposit: " + e.getMessage());
        }

        return false;
        */
    }

    


    
    /**
     * Inserts a new order into the database with the provided details.
     * The order is marked as 'active' and the current date and time are used.
     *
     * @param subscriberId   The ID of the subscriber placing the order.
     * @param parkingSpace   The parking space assigned to the order.
     * @param confirmationCode The unique confirmation code for the order.
     * @return The generated order number if the insertion is successful,
     *         -1 if the insertion fails or no ID is returned.
     */
    public int insertNewOrder(String subscriberId, int parkingSpace, int confirmationCode) {
        String query = """
            INSERT INTO orders (parking_space, order_date, order_time, confirmation_code, subscriber_id, order_status)
            VALUES (?, CURRENT_DATE, CURRENT_TIME, ?, ?, 'active')
        """;

        try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, parkingSpace);
            stmt.setInt(2, confirmationCode);
            stmt.setInt(3, Integer.parseInt(subscriberId));
            int affected = stmt.executeUpdate();

            if (affected == 0) {
                System.err.println("❌ Insert failed: no rows affected.");
                return -1;
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1); // Return generated order_number
                } else {
                    System.err.println("❌ Insert succeeded but no ID returned.");
                    return -1;
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Failed to insert new order: " + e.getMessage());
            return -1;
        }
    }



 // ========================================================= Pickup vehicle =========================================================

    /**
     * Completes a vehicle pickup operation using a subscriber ID and confirmation code.
     * Checks the order status before proceeding. If valid, marks the order as "complete",
     * frees the parking space, and logs the pickup in history.
     *
     * @param subscriberId     The subscriber's ID.
     * @param confirmationCode The confirmation code used to identify the order.
     * @return A status code:
     *         <ul>
     *           <li>200 - Success</li>
     *           <li>402 - Order was cancelled</li>
     *           <li>403 - Order already completed</li>
     *           <li>404 - Order not found</li>
     *         </ul>
     * @throws SQLException If a database error occurs.
     */
    public int pickupVehicle(String subscriberId, String confirmationCode) throws SQLException {
        String query = """
            SELECT order_number ,parking_space, order_status
            FROM orders 
            WHERE confirmation_code = %s AND subscriber_id = %s
            """.formatted(confirmationCode, subscriberId);

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            if (!rs.next()) return 404;

            int parkingSpace = rs.getInt("parking_space");
            int orderNumber = rs.getInt("order_number");
            String orderStatus = rs.getString("order_status");

            if ("complete".equalsIgnoreCase(orderStatus)) return 403;
            if ("cancelled".equalsIgnoreCase(orderStatus)) return 402;

            updateOrderStatus(orderNumber, "complete");
            updateParkingLot(parkingSpace, null);
            updateParkingHistory(subscriberId, orderNumber, "picked_up");
            return 200;
        }
    }

    // ========================================================= Extend parking =========================================================

    /**
     * Attempts to extend a parking session (once per order).
     * Fails if already extended, if order is "late", or if order does not exist.
     *
     * @param subscriberId     The subscriber's ID.
     * @param confirmationCode The confirmation code of the parking session.
     * @return A status code:
     *         <ul>
     *           <li>200 - Extension successful</li>
     *           <li>403 - Cannot extend a late order</li>
     *           <li>404 - Order not found</li>
     *           <li>409 - Already extended</li>
     *           <li>500 - Server/database error</li>
     *         </ul>
     */
    public int extendParking(String subscriberId, String confirmationCode) {
        try {
            // Get the order and check eligibility
            String query = """
                SELECT order_number, is_extended, parking_space, order_status
                FROM orders
                WHERE subscriber_id = ? AND confirmation_code = ?
            """;

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, Integer.parseInt(subscriberId));
                stmt.setInt(2, Integer.parseInt(confirmationCode));
                ResultSet rs = stmt.executeQuery();

                if (!rs.next()) return 404;

                int orderNumber = rs.getInt("order_number");
                boolean isExtended = rs.getBoolean("is_extended");
                String orderStatus = rs.getString("order_status");

                if ("complete".equalsIgnoreCase(orderStatus)) return 407;
                if (isExtended) return 409;
                if ("late".equalsIgnoreCase(orderStatus)) return 403;
                
                // Update is_extended = true
                String update = "UPDATE orders SET is_extended = true WHERE order_number = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(update)) {
                    updateStmt.setInt(1, orderNumber);
                    int rows = updateStmt.executeUpdate();
                    if (rows != 1) return 500;
                }

                updateParkingHistory(subscriberId, orderNumber, "extended");

                return 200;
            }

        } catch (SQLException e) {
            System.err.println("❌ Error during extendParking: " + e.getMessage());
            return 500;
        }
    }

    
 // ========================================================= Reserve Parking =========================================================

    /**
     * Attempts to reserve a parking space for a subscriber on a specific date and time.
     * The reservation is accepted only if:
     * - Less than 40% of total parking spots are already reserved that day
     * - At least one spot is available at the requested time
     *
     * @param subscriberId The subscriber's ID.
     * @param date         The requested reservation date.
     * @param time         The requested reservation time (HH:mm).
     * @return A confirmation code if the reservation is successful,
     *         or error codes:
     *         <ul>
     *           <li>403 - Capacity exceeded (more than 40% booked)</li>
     *           <li>404 - No parking space available at that time</li>
     *           <li>500 - Database/server error</li>
     *         </ul>
     */
    public int reserveParking(String subscriberId, LocalDate date, LocalTime time) {
        try {
            // 1. Check usage for the date
            String countQuery = """
                SELECT COUNT(*) FROM orders
                WHERE order_date = '%s'
            """.formatted(date);
            String totalQuery = "SELECT COUNT(*) FROM parking";

            int ordersThatDay = 0, totalSpots = 0;
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(countQuery);
                if (rs.next()) ordersThatDay = rs.getInt(1);
                rs = stmt.executeQuery(totalQuery);
                if (rs.next()) totalSpots = rs.getInt(1);
            }

            if (ordersThatDay >= 0.4 * totalSpots) return 403; // capacity exceeded

            // 2. Find available parking space (not booked for this time)
            String availableQuery = """
                SELECT p.parking_space
                FROM parking p
                WHERE NOT EXISTS (
                    SELECT 1 FROM orders o
                    WHERE o.parking_space = p.parking_space
                    AND o.order_date = '%s'
                    AND o.order_time = '%s'
                )
                LIMIT 1
            """.formatted(date, time);

            int parkingSpace;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(availableQuery)) {
                if (!rs.next()) return 404; // no available spot
                parkingSpace = rs.getInt("parking_space");
            }

            // 3. Insert reservation
            int confirmationCode = generateConfirmationCode();
            String insert = """
                INSERT INTO orders (parking_space, order_date, order_time, confirmation_code, subscriber_id, order_status)
                VALUES (%d, '%s', '%s', %d, %s, 'pending')
            """.formatted(parkingSpace, date, time, confirmationCode, subscriberId);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(insert, Statement.RETURN_GENERATED_KEYS);
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    int orderNumber = keys.getInt(1);
                    updateParkingHistory(subscriberId, orderNumber, "reserved");
                }
            }

            return confirmationCode;

        } catch (SQLException e) {
            System.err.println("❌ Reserve error: " + e.getMessage());
            return 500;
        }
    }

    /**
     * Checks whether a subscriber already has a pending reservation for the given day.
     *
     * @param subscriberId The subscriber's ID.
     * @param orderDate    The date string in format YYYY-MM-DD.
     * @return true if a pending reservation exists for that day, false otherwise.
     */
    public boolean userHasReservationForTheDay(String subscriberId, String orderDate) {
        String query = """
            SELECT 1 FROM orders
            WHERE subscriber_id = ? 
              AND order_date = ? 
              AND order_status = 'pending'
            LIMIT 1
        """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, Integer.parseInt(subscriberId));
            stmt.setDate(2, java.sql.Date.valueOf(orderDate));
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("❌ Error checking reservation for the day: " + e.getMessage());
            return false;
        }
    }


    
 // ========================================================= Edit User =========================================================
    /**
     * Updates the subscriber's email, password, and phone number in the database.
     * Ensures that the email and phone number are unique and do not belong to another subscriber.
     *
     * @param id       The ID of the subscriber to update.
     * @param email    The new email address for the subscriber.
     * @param password The new password for the subscriber.
     * @param phone    The new phone number for the subscriber.
     * @return A status code:
     *         <ul>
     *           <li>200 - Update successful</li>
     *           <li>-2  - Email already exists for another subscriber</li>
     *           <li>-3  - Phone number already exists for another subscriber</li>
     *           <li>500 - Update failed due to a database error</li>
     *         </ul>
     */
    public int editSubscriber(String id, String email, String password, String phone) {
        try {
            // Check for existing email (not self)
            String emailCheck = "SELECT subscriber_id FROM subscribers WHERE subscriber_email = ? AND subscriber_id <> ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(emailCheck)) {
                checkStmt.setString(1, email);
                checkStmt.setString(2, id);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) return -2;
            }

            // Check for existing phone (not self)
            String phoneCheck = "SELECT subscriber_id FROM subscribers WHERE subscriber_phone = ? AND subscriber_id <> ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(phoneCheck)) {
                checkStmt.setString(1, phone);
                checkStmt.setString(2, id);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) return -3;
            }

            // Update record
            String update = """
                UPDATE subscribers 
                SET subscriber_email = ?, subscriber_password = ?, subscriber_phone = ? 
                WHERE subscriber_id = ?
            """;
            try (PreparedStatement stmt = conn.prepareStatement(update)) {
                stmt.setString(1, email);
                stmt.setString(2, password);
                stmt.setString(3, phone);
                stmt.setString(4, id);
                int rows = stmt.executeUpdate();
                return rows == 1 ? 200 : 500;
            }

        } catch (SQLException e) {
            System.err.println("❌ Edit error: " + e.getMessage());
            return 500;
        }
    }

    
 // ========================================================= Get User History =========================================================

    /**
     * Retrieves the parking history for a given subscriber.
     * The result is ordered with the most recent entries first (by date and time).
     *
     * @param userId The subscriber ID.
     * @return A list of maps representing the user's parking events,
     *         each row includes: parking number, date, time, and event type;
     *         or null if an error occurs.
     */
    public List<Map<String, String>> getUserHistory(String userId) {
        String query = """
            SELECT parking_num, parking_date, parking_time, event_type
            FROM parking_history
            WHERE subscriber_id = ?
            ORDER BY parking_date DESC, parking_time DESC
        """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            List<Map<String, String>> table = formatResultSet(rs);
            return table;
        } catch (SQLException e) {
            System.err.println("❌ Error retrieving user history: " + e.getMessage());
            return null;
        }
    }

    // ========================================================= Recover User =========================================================

    /**
     * Attempts to recover user information and current active parking session (if any).
     * Pulls subscriber name, email, phone number, and active confirmation code (if parking is occupied).
     * Sends a Discord notification through the configured notifier.
     *
     * @param userId The subscriber ID.
     * @return A map containing:
     *         <ul>
     *           <li>"subscriber_name"</li>
     *           <li>"subscriber_email"</li>
     *           <li>"subscriber_phone"</li>
     *           <li>"parking_confirmation_code"</li>
     *         </ul>
     *         Or null if user does not exist or an error occurs.
     */
    public Map<String, String> recoverUser(String userId) {
        Map<String, String> data = new HashMap<>();

        // Step 1: Basic subscriber info
        String subscriberQuery = """
            SELECT subscriber_name, subscriber_email, subscriber_phone
            FROM subscribers
            WHERE subscriber_id = ?
        """;

        // Step 2: Active parking info by joining orders + parking
        String parkingQuery = """
            SELECT o.confirmation_code
            FROM orders o
            JOIN parking p ON o.confirmation_code = p.confirmation_code
            WHERE o.subscriber_id = ?
              AND p.status = 'occupied'
              AND (o.order_status = 'active' OR o.order_status = 'late')
            LIMIT 1
        """;

        try (
            PreparedStatement subscriberStmt = conn.prepareStatement(subscriberQuery);
            PreparedStatement parkingStmt = conn.prepareStatement(parkingQuery)
        ) {
            subscriberStmt.setInt(1, Integer.parseInt(userId));
            ResultSet rsSub = subscriberStmt.executeQuery();

            if (rsSub.next()) {
                data.put("subscriber_name", rsSub.getString("subscriber_name"));
                data.put("subscriber_email", rsSub.getString("subscriber_email"));
                data.put("subscriber_phone", rsSub.getString("subscriber_phone"));
            } else {
                return null; // No such user
            }

            parkingStmt.setInt(1, Integer.parseInt(userId));
            ResultSet rsPark = parkingStmt.executeQuery();

            String confirmation;
            if (rsPark.next()) {
                confirmation = rsPark.getString("confirmation_code");
            } else {
                confirmation = "No active parking";
            }
            data.put("parking_confirmation_code", confirmation);

            // Notify
        	if (discordNotifier.isMonitoringEnabled()) {
                discordNotifier.DiscordMsg(
                        "UserRecovery",
                        confirmation,
                        userId,
                        data.get("subscriber_name"),
                        data.get("subscriber_email"),
                        data.get("subscriber_phone")
                    );
    		}


            return data;

        } catch (SQLException e) {
            System.err.println("❌ Error in recoverUser: " + e.getMessage());
            return null;
        }
    }

    
    
 // ========================================================= Order Monitor  =========================================================
    
    /*
     * Late Active - user did not pick up vehicle after 4 hours (or 8 if extended)
     * Late Pending - user did not deposit vehicle after up to 15 minutes from the reservation time
     */
    
    
    
    /**
     * Retrieves a list of overdue active orders where the parking duration has exceeded the allowed time.
     * The allowed time is 4 hours for regular orders and 8 hours for extended orders.
     *
     * @return A list of overdue order numbers.
     */
    public List<Integer> getLateActiveOrders() {
        List<Integer> overdueOrderNumbers = new ArrayList<>();

        String query = """
            SELECT o.order_number, o.is_extended, ph.parking_date, ph.parking_time
            FROM orders o
            JOIN parking_history ph ON o.order_number = ph.order_number
            WHERE o.order_status = 'active' AND ph.event_type = 'deposited'
            ORDER BY ph.parking_date DESC, ph.parking_time DESC
        """;

        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                int orderNumber = rs.getInt("order_number");
                boolean isExtended = rs.getBoolean("is_extended");
                LocalDate date = rs.getDate("parking_date").toLocalDate();
                LocalTime time = rs.getTime("parking_time").toLocalTime();
                LocalDateTime depositTime = LocalDateTime.of(date, time);
                LocalDateTime deadline = depositTime.plusHours(isExtended ? 8 : 4);
                if (LocalDateTime.now().isAfter(deadline)) {
                    overdueOrderNumbers.add(orderNumber);
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Error checking overdue active orders: " + e.getMessage());
        }

        // Debug print
        for (int orderNum : overdueOrderNumbers) {
            System.out.println("Overdue Order Number: " + orderNum);
        }
        
        return overdueOrderNumbers;
    }

    /**
     * Handles overdue active orders by marking them as "late", logging the event in the parking history,
     * and sending notifications to the subscribers.
     *
     * @param overdueOrderNumbers A list of overdue order numbers to process.
     */
    public void handleLateActiveOrders(List<Integer> overdueOrderNumbers) {
        for (int orderNumber : overdueOrderNumbers) {
            try {
                // Get subscriber info
                Map<String, String> subscriber = getSubscriberByOrderNumber(orderNumber);
                if (subscriber == null) continue;

                // Update notification status and send notification
                if (updateLateNotification(orderNumber)) {
                    sendLateNotification(subscriber);
                } else {
                    System.err.println("❌ Failed to mark order " + orderNumber + " as notified.");
                    continue; // Skip this order if notification update failed
                }

                // Update order status to 'late'
                updateOrderStatus(orderNumber, "late");

                // Log to parking history
                updateParkingHistory(subscriber.get("subscriber_id"), orderNumber, "late");

            } catch (Exception e) {
                System.err.println("❌ Error handling late active orders: " + e.getMessage());
            }
        }
    }

    
    private void sendLateNotification(Map<String, String> subscriber) {
    	if (discordNotifier.isMonitoringEnabled()) {
        	/*	Map<String, String> ={
        	 * order_number
        	 * subscriber_id
        	 * subscriber_name
        	 * subscriber_email
        	 * subscriber_phone
        	 */
        	discordNotifier.DiscordMsg("LatePickup", 
    				subscriber.get("order_number"), 
    				subscriber.get("subscriber_id"), 
    				subscriber.get("subscriber_name"), 
    				subscriber.get("subscriber_email"), 
    				subscriber.get("subscriber_phone"));
    		
    		System.out.println("✅ Late notification sent to subscriber: " + subscriber.get("subscriber_name"));
    		return;
		}

		System.err.println("Discord Service are disabled.");

    }

    												//handle late active orders -- END//
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
													//handle late pending orders -- START//
    /**
     * Retrieves a list of order numbers for all 'pending' orders
     * that were scheduled for earlier than 15 minutes ago today.
     *
     * @return A list of order numbers that are considered late.
     */
    public List<Integer> getLatePendingOrders() {
        String query = """
            SELECT order_number
            FROM orders
            WHERE order_status = 'pending'
              AND order_date = CURRENT_DATE
              AND TIMESTAMP(order_date, order_time) < NOW() - INTERVAL 15 MINUTE
        """;

        List<Integer> lateOrders = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                lateOrders.add(rs.getInt("order_number"));
            }

        } catch (SQLException e) {
            System.err.println("❌ Error in getLatePendingOrders: " + e.getMessage());
        }

        return lateOrders;
    }

    /**
     * Processes all late 'pending' orders by:
     * <ol>
     *   <li>Fetching subscriber info per order</li>
     *   <li>Updating the notification status</li>
     *   <li>Sending a Discord cancellation message</li>
     *   <li>Marking the order as 'cancelled'</li>
     *   <li>Logging the cancellation in parking history</li>
     * </ol>
     *
     * @param lateOrderNumbers A list of late 'pending' order numbers to cancel and notify.
     */
    public void handleLatePendingOrders(List<Integer> lateOrderNumbers) {
        for (int orderNumber : lateOrderNumbers) {
            try {
                // Get subscriber info
                Map<String, String> subscriber = getSubscriberByOrderNumber(orderNumber);
                if (subscriber == null) continue;

                if (updateLateNotification(orderNumber)) {
                    sendCancelledNotification(subscriber);
                } else {
                    System.err.println("❌ Failed to mark order " + orderNumber + " as notified.");
                    continue;
                }

                // Update order status to 'cancelled'
                updateOrderStatus(orderNumber, "cancelled");

                // Log to parking history
                updateParkingHistory(subscriber.get("subscriber_id"), orderNumber, "cancelled");

            } catch (Exception e) {
                System.err.println("❌ Error handling late active orders: " + e.getMessage());
            }
        }
    }

    /**
     * Sends a structured Discord notification for a cancelled order.
     * Includes order number and subscriber details.
     *
     * @param subscriber A map with subscriber info:
     *                   - subscriber_id
     *                   - subscriber_name
     *                   - subscriber_email
     *                   - subscriber_phone
     *                   - order_number
     */
    private void sendCancelledNotification(Map<String, String> subscriber) {
    	if (discordNotifier.isMonitoringEnabled()) {
            discordNotifier.DiscordMsg(
                    "CancelOrder",
                    subscriber.get("order_number"),
                    subscriber.get("subscriber_id"),
                    subscriber.get("subscriber_name"),
                    subscriber.get("subscriber_email"),
                    subscriber.get("subscriber_phone")
                );

                System.out.println("✅ Late notification sent to subscriber: " + subscriber.get("subscriber_name"));
			return;
		}

        System.err.println("Discord Service are disabled.");
    }

    
    												//handle late pending orders -- END//
    
    
 // ========================================================= Reports Methods -- START =======================================================
    
    /**
     * Generates a monthly report of user activity, summarizing parking events for each subscriber.
     * The report includes counts of various event types (e.g., deposited, picked_up, reserved, etc.)
     * for all users with a subscription status of "user".
     *
     * @param month The month for the report (e.g., "01" for January).
     * @param year  The year for the report (e.g., "2023").
     * @return A list of maps, where each map represents a subscriber and their event counts.
     *         Each map contains:
     *         <ul>
     *           <li>"user" - The subscriber ID</li>
     *           <li>"deposited" - Count of "deposited" events</li>
     *           <li>"picked_up" - Count of "picked_up" events</li>
     *           <li>"reserved" - Count of "reserved" events</li>
     *           <li>"late" - Count of "late" events</li>
     *           <li>"cancelled" - Count of "cancelled" events</li>
     *           <li>"extended" - Count of "extended" events</li>
     *         </ul>
     */
    public List<Map<String, String>> getMonthlyUsersReport(String month, String year) {
        List<Map<String, String>> result = new ArrayList<>();
        String query = """
            SELECT s.subscriber_id, ph.event_type
            FROM subscribers s
            LEFT JOIN parking_history ph
                ON s.subscriber_id = ph.subscriber_id
                AND MONTH(ph.parking_date) = ? AND YEAR(ph.parking_date) = ?
            WHERE s.subscription_status = 'user'
            ORDER BY s.subscriber_id ASC
        """;

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, month);
            ps.setString(2, year);

            ResultSet rs = ps.executeQuery();

            // TreeMap to keep keys sorted by subscriber_id
            Map<String, Map<String, Integer>> summary = new TreeMap<>();

            while (rs.next()) {
                String user = rs.getString("subscriber_id");
                String event = rs.getString("event_type");

                summary.putIfAbsent(user, new HashMap<>());
                if (event != null) {
                    Map<String, Integer> counts = summary.get(user);
                    counts.put(event, counts.getOrDefault(event, 0) + 1);
                }
            }

            for (Map.Entry<String, Map<String, Integer>> entry : summary.entrySet()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("user", entry.getKey());
                Map<String, Integer> events = entry.getValue();
                row.put("deposited", String.valueOf(events.getOrDefault("deposited", 0)));
                row.put("picked_up", String.valueOf(events.getOrDefault("picked_up", 0)));
                row.put("reserved", String.valueOf(events.getOrDefault("reserved", 0)));
                row.put("late", String.valueOf(events.getOrDefault("late", 0)));
                row.put("cancelled", String.valueOf(events.getOrDefault("cancelled", 0)));
                row.put("extended", String.valueOf(events.getOrDefault("extended", 0)));
                result.add(row);
            }

        } catch (Exception e) {
            System.err.println("❌ Error in getMonthlyUsersReport: " + e.getMessage());
        }

        return result;
    }



    /**
     * Generates a monthly parking report summarizing the number of vehicles deposited each day.
     * The report includes the date and the count of deposited vehicles for that day.
     *
     * @param month The month for the report (e.g., "01" for January).
     * @param year  The year for the report (e.g., "2023").
     * @return A list of maps, where each map represents a day and its parking capacity:
     *         <ul>
     *           <li>"day" - The date of the parking event</li>
     *           <li>"capacity" - The number of vehicles deposited on that day</li>
     *         </ul>
     */
    public List<Map<String, String>> getMonthlyParkingReport(String month, String year) {
        List<Map<String, String>> result = new ArrayList<>();
        String query = """
            SELECT parking_date
            FROM parking_history
            WHERE event_type = 'deposited' AND MONTH(parking_date) = ? AND YEAR(parking_date) = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, month);
            ps.setString(2, year);

            ResultSet rs = ps.executeQuery();
            Map<String, Integer> dailyCount = new TreeMap<>();

            while (rs.next()) {
                String date = rs.getDate("parking_date").toString();
                dailyCount.put(date, dailyCount.getOrDefault(date, 0) + 1);
            }

            for (Map.Entry<String, Integer> entry : dailyCount.entrySet()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("day", entry.getKey());
                row.put("capacity", String.valueOf(entry.getValue()));
                result.add(row);
            }

        } catch (Exception e) {
            System.err.println("❌ Error in getMonthlyParkingReport: " + e.getMessage());
        }

        return result;
    }

// ========================================================= Reports Methods -- END =======================================================


    
// ========================================================= Helper Methods -- START =======================================================

    /**
     * Verifies subscriber login credentials.
     *
     * @param email Subscriber's email.
     * @param pass  Subscriber's password.
     * @return A map containing subscriber fields (name, id, email, phone, etc.), or null if no match.
     *         If a DB error occurs, returns a map with key "error" and value "db".
     */
    public Map<String, String> getSubscriber(String email, String pass) {
        String query = "SELECT subscriber_id, subscriber_name, subscriber_email,subscriber_phone,subscriber_password, subscription_status "+
                       "FROM subscribers "+
                       "WHERE subscriber_email=? AND subscriber_password=?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, email);
            pstmt.setString(2, pass);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Map<String, String> subscriber = new HashMap<>();
                subscriber.put("subscriber_name", rs.getString("subscriber_name"));
                subscriber.put("subscriber_id", rs.getString("subscriber_id"));
                subscriber.put("subscriber_email", rs.getString("subscriber_email"));
                subscriber.put("subscription_status", rs.getString("subscription_status"));
                subscriber.put("subscriber_phone", rs.getString("subscriber_phone"));
                subscriber.put("subscriber_password", rs.getString("subscriber_password"));
                return subscriber;
            } else {
                return null; // No match
            }

        } catch (SQLException e) {
            System.err.println("❌ Login error: " + e.getMessage());
            return Map.of("error", "db"); // use special value to signal DB error
        }
    }

    /**
     * Retrieves all rows from a given database table.
     *
     * @param tb The table name.
     * @return A {@link ResultSet} containing the table's data, or null if table does not exist or error occurs.
     */
    public ResultSet getTable(String tb) {
        ResultSet rs = null;
        String query = "SELECT * FROM " + tb;

        try {
            if (verifyTable(tb)) {
                Statement stmt = conn.createStatement();
                rs = stmt.executeQuery(query);
            }
        } catch (SQLException e) {
            System.err.println("❌ Table error: " + e.getMessage());
            return null;
        }
        return rs;
    }

    /**
     * Checks whether a given table exists in the database.
     *
     * @param tableName The name of the table to check.
     * @return true if the table exists, false otherwise.
     * @throws SQLException If metadata cannot be retrieved.
     */
    private boolean verifyTable(String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"});
        return rs.next();
    }

    /**
     * Converts a {@link ResultSet} to a {@link List} of maps, where each map is a row of column-value pairs.
     *
     * @param rs The ResultSet to format.
     * @return A list of maps, one for each row.
     * @throws SQLException If ResultSet processing fails.
     */
    public List<Map<String, String>> formatResultSet(ResultSet rs) throws SQLException {
        List<Map<String, String>> result = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        while (rs.next()) {
            Map<String, String> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = meta.getColumnName(i);
                String value = rs.getString(i);
                row.put(columnName, value);
            }
            result.add(row);
        }

        return result;
    }

    /**
     * Logs a parking-related event to the {@code parking_history} table.
     * Supports different event types (deposited, picked_up, reserved, extended, cancelled, late).
     *
     * @param subscriberId Subscriber's ID.
     * @param orderNumber  Related order number.
     * @param eventType    Type of the event (e.g., "deposited", "cancelled", etc.).
     */
    public void updateParkingHistory(String subscriberId, int orderNumber, String eventType) {
        LocalDate date = LocalDate.now();
        LocalTime time = LocalTime.now();

        try {
            // Get parking space from order
            String query = "SELECT parking_space FROM orders WHERE order_number = ?";
            int parkingSpace;

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, orderNumber);
                ResultSet rs = stmt.executeQuery();

                if (!rs.next()) {
                    System.err.println("❌ Order not found for history update.");
                    return;
                }
                parkingSpace = rs.getInt("parking_space");
            }

            // Log to history
            String insertQuery = """
                INSERT INTO parking_history (subscriber_id, parking_num, parking_date, parking_time, event_type, order_number)
                VALUES (?, ?, ?, ?, ?, ?)
            """;

            try (PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
                stmt.setInt(1, Integer.parseInt(subscriberId));
                stmt.setInt(2, parkingSpace);
                stmt.setDate(3, java.sql.Date.valueOf(date));
                stmt.setTime(4, java.sql.Time.valueOf(time));
                stmt.setString(5, eventType);
                stmt.setInt(6, orderNumber);
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            System.err.println("❌ Failed to insert into parking_history: " + e.getMessage());
        }
    }

    
    /**
     * Updates the parking lot entry by either marking a space as 'occupied' (with a confirmation code)
     * or 'available' (clearing its confirmation code).
     *
     * @param space           The parking space number.
     * @param confirmationCode The confirmation code, or null to mark the space as available.
     * @throws SQLException If the update query fails.
     */
    public void updateParkingLot(int space, Integer confirmationCode) throws SQLException {
        String query;

        if (confirmationCode == null) {
            // Pickup: free the space
            query = "UPDATE parking SET status = 'available', confirmation_code = NULL WHERE parking_space = " + space;
        } else {
            // Deposit: occupy the space
            query = "UPDATE parking SET status = 'occupied', confirmation_code = " + confirmationCode +
                    " WHERE parking_space = " + space;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(query);
        }
    }

    /**
     * Updates the order status to a new value (e.g., 'active', 'cancelled', 'complete').
     *
     * @param orderNumber The order number.
     * @param newStatus   The new status string to assign.
     */
    private void updateOrderStatus(int orderNumber, String newStatus) {
        /*
            Status       | Meaning
            -------------|------------------------------------------------------
            active       | The vehicle is currently parked (order in progress).
            pending      | A future reservation (awaiting deposit).
            cancelled    | The reservation was not fulfilled in time.
            late         | Vehicle was picked up late.
            complete     | Vehicle was picked up on time, order fulfilled.
         */
        String query = "UPDATE orders SET order_status = ? WHERE order_number = ?";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, newStatus);
            stmt.setInt(2, orderNumber);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Failed to update order status: " + e.getMessage());
        }
    }

    /**
     * Marks the given order as 'notified' to avoid duplicate notifications.
     *
     * @param orderNumber The order number to update.
     * @return true if update was successful; false otherwise.
     */
    private boolean updateLateNotification(int orderNumber) {
        String query = "UPDATE orders SET is_notified = true WHERE order_number = ?";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, orderNumber);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("❌ Failed to update order status: " + e.getMessage());
        }
        return false;
    }

    /**
     * Returns the status string of a given order.
     *
     * @param orderNumber The order number.
     * @return The status string (e.g., "active", "late"), or null if not found or on error.
     */
    public String getOrderStatus(int orderNumber) {
        String query = "SELECT order_status FROM orders WHERE order_number = ?";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, orderNumber);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("order_status");
            } else {
                return null; // Order not found
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to get order status: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the total number of subscribers in the system.
     *
     * @return The total as a string. Returns "0" if query fails.
     */
    public String getTotalUsers() {
        String query = "SELECT COUNT(*) AS total FROM subscribers";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                return String.valueOf(rs.getInt("total"));
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to get total users: " + e.getMessage());
        }
        return "0"; // Return 0 if there's an error or no users
    }

    /**
     * Generates a random 4-digit confirmation code between 1000 and 9999.
     *
     * @return A new confirmation code.
     */
    private int generateConfirmationCode() {
        return (int)(Math.random() * 9000) + 1000;
    }

    /**
     * Retrieves subscriber information associated with a specific order number.
     *
     * @param orderNumber The order number.
     * @return A map with subscriber details (id, name, email, phone, order number), or null on error.
     */
    public Map<String, String> getSubscriberByOrderNumber(int orderNumber) {
        String query = """
            SELECT o.order_number, s.subscriber_id, s.subscriber_name, s.subscriber_email, s.subscriber_phone
            FROM orders o
            JOIN subscribers s ON o.subscriber_id = s.subscriber_id
            WHERE o.order_number = ?
        """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, orderNumber);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Map<String, String> result = new HashMap<>();
                result.put("order_number", String.valueOf(rs.getInt("order_number")));
                result.put("subscriber_id", String.valueOf(rs.getInt("subscriber_id")));
                result.put("subscriber_name", rs.getString("subscriber_name"));
                result.put("subscriber_email", rs.getString("subscriber_email"));
                result.put("subscriber_phone", rs.getString("subscriber_phone"));
                return result;
            }

        } catch (SQLException e) {
            System.err.println("❌ Failed to get subscriber by order: " + e.getMessage());
        }

        return null;
    }




    
 // ========================================================= Helper Methods -- END =======================================================    

    /**
     * Updates the order date and parking space if the provided values are valid.
     * Validations include:
     * - The new date must not be in the past.
     * - The order must exist.
     * - The parking space must exist.
     * - The parking space must be available on the new date.
     *
     * @param orderNumber    The order number to update.
     * @param newDate        The new date for the order.
     * @param newParkingSpace The new parking space for the order.
     * @return A status code:
     *         <ul>
     *           <li>200 - Update successful</li>
     *           <li>400 - New date is in the past</li>
     *           <li>404 - Order or parking space does not exist</li>
     *           <li>409 - Parking space is not available on the new date</li>
     *           <li>503 - Database error</li>
     *         </ul>
     */
    public int updateOrder(int orderNumber, LocalDate newDate, int newParkingSpace) {
        if (newDate.isBefore(LocalDate.now())) {
            return 400; // New date is in the past
        }

        try {
            boolean validOrder = verifyOrder(orderNumber);
            boolean validParking = isValidParking(newParkingSpace);
            boolean availableParking = isAvailableParkingAtDate(newParkingSpace, newDate);

            if (validOrder && validParking && availableParking) {
                String query = "UPDATE orders SET order_date = ?, parking_space = ? WHERE order_number = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setDate(1, java.sql.Date.valueOf(newDate));
                    pstmt.setInt(2, newParkingSpace);
                    pstmt.setInt(3, orderNumber);
                    int rows = pstmt.executeUpdate();
                    return 200; // Update successful
                }
            } else {
                if (!validOrder) {
                    System.err.println("❗ Order " + orderNumber + " does not exist.");
                    return 404; // Order does not exist
                } else if (!validParking) {
                    System.err.println("❗ Parking " + newParkingSpace + " does not exist.");
                    return 404; // Parking space does not exist
                } else {
                    System.err.println("❗ Parking space " + newParkingSpace + " is not available on " + newDate);
                    return 409; // Parking space not available
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Update error: " + e.getMessage());
            return 503; // Database error
        }
    }

    /**
     * Checks if an order exists in the database.
     *
     * @param orderNum The order number to verify.
     * @return true if the order exists, false otherwise.
     * @throws SQLException If a database error occurs.
     */
    private boolean verifyOrder(int orderNum) throws SQLException {
        String query = "SELECT order_number FROM orders WHERE order_number = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, orderNum);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        }
    }

    /**
     * Checks if a parking space is available on a specific date.
     *
     * @param parkingSpace The parking space to check.
     * @param date         The date to check availability for.
     * @return true if the parking space is available, false otherwise.
     * @throws SQLException If a database error occurs.
     */
    private boolean isAvailableParkingAtDate(int parkingSpace, LocalDate date) throws SQLException {
        String query = "SELECT 1 FROM orders WHERE parking_space = ? AND order_date = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, parkingSpace);
            pstmt.setDate(2, java.sql.Date.valueOf(date));
            ResultSet rs = pstmt.executeQuery();
            return !rs.next();
        }
    }

    /**
     * Checks if a parking space exists in the database.
     *
     * @param parkingSpace The parking space to verify.
     * @return true if the parking space exists, false otherwise.
     * @throws SQLException If a database error occurs.
     */
    private boolean isValidParking(int parkingSpace) throws SQLException {
        String query = "SELECT parking_space FROM parking WHERE parking_space = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, parkingSpace);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        }
    }

}
