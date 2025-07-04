/**
 * A thread that monitors orders in the database for specific conditions, such as late active or pending orders.
 * The thread periodically checks for these conditions and handles them accordingly.
 */
package server_core;

import java.util.List;

public class OrderMonitorThread extends Thread {

    private final DBhandler dbHandler;
    private volatile boolean running = true;

    /**
     * Constructs an OrderMonitorThread with the specified database handler.
     *
     * @param dbHandler The database handler used to interact with the database.
     */
    public OrderMonitorThread(DBhandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    /**
     * The main execution method of the thread. Continuously monitors for late active and pending orders
     * and processes them. The thread sleeps for 1 minute between checks.
     */
    @Override
    public void run() {
        try {
            Thread.sleep(6_000);
        } catch (InterruptedException e) {
            System.out.println("🛑 Initialization interrupted.");
            return;
        }
        System.out.println("📦 OrderMonitorThread started.");
        while (running) {
            try {
                // Retrieve and handle late active orders
                List<Integer> lateActiveOrders = dbHandler.getLateActiveOrders();
                handleLateActiveOrders(lateActiveOrders);

                // Retrieve and handle late pending orders
                List<Integer> latePendingOrders = dbHandler.getLatePendingOrders();
                handleLatePendingOrders(latePendingOrders);

                // Sleep for 1 minute
                Thread.sleep(60_000);

            }
            catch (InterruptedException e) {
                System.out.println("🛑 OrderMonitorThread interrupted. Exiting...");
                break; // exit the loop to stop the thread
            }
            catch (Exception e) {
                System.err.println("❌ OrderMonitorThread error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Stops the monitoring thread by setting the running flag to false.
     */
    public void stopMonitoring() {
        running = false;
        this.interrupt();
    }

    /**
     * Handles late pending orders by delegating the task to the database handler.
     *
     * @param pendingOrders A list of late pending order IDs to process.
     */
    private void handleLatePendingOrders(List<Integer> pendingOrders) {
        dbHandler.handleLatePendingOrders(pendingOrders);
    }

    /**
     * Handles late active orders by delegating the task to the database handler.
     *
     * @param lateOrders A list of late active order IDs to process.
     */
    private void handleLateActiveOrders(List<Integer> lateOrders) {
        dbHandler.handleLateActiveOrders(lateOrders);
    }
}
