package server_core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A utility class for managing a MySQL database connection.
 * This class provides methods to load the JDBC driver, establish a connection,
 * close the connection, and retrieve the current connection object.
 */
public class DBconnector {

    private Connection conn = null;

    /**
     * Loads the MySQL JDBC driver.
     * 
     * @throws ClassNotFoundException if the MySQL JDBC driver is not found.
     */
    public DBconnector() throws ClassNotFoundException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("✔️ MySQL JDBC Driver loaded.");
        } catch (ClassNotFoundException ex) {
            System.err.println("❌ MySQL JDBC Driver not found.");
            throw new ClassNotFoundException();
        }
    }

    /**
     * Establishes a connection to the database.
     * 
     * @param url      the database URL.
     * @param user     the database username.
     * @param password the database password.
     * @return true if the connection is successful, false otherwise.
     */
    public boolean connect(String url, String user, String password) {
        try {
            this.conn = DriverManager.getConnection(url, user, password);
        } catch (SQLException ex) {
            System.err.println("SQLException: " + ex.getMessage());
            System.err.println("SQLState: " + ex.getSQLState());
            System.err.println("VendorError: " + ex.getErrorCode());
            return false;
        }
        System.out.println("✔️ SQL connection succeed");
        return true;
    }

    /**
     * Closes the database connection.
     * Ensures that the connection is safely closed if it is open.
     */
    public void disconnect() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                System.out.println("✔️ Database disconnected.");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error closing DB: " + e.getMessage());
        }
    }

    /**
     * Retrieves the current database connection.
     * 
     * @return the current {@link Connection} object, or null if no connection is established.
     */
    public Connection getConnection() {
        return this.conn;
    }
}
