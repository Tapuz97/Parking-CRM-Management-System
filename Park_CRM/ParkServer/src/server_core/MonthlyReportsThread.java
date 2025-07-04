package server_core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * A background thread that runs continuously to generate and save monthly report CSVs.
 * It checks once per day (at midnight) whether reports for the current month exist.
 * If missing, it queries the database and saves new "USERS" and "PARKING" reports via {@link ReportsCSV}.
 */
public class MonthlyReportsThread extends Thread {

    private final DBhandler dbHandler;
    private final ReportsCSV reportsCSV;
    private volatile boolean running = true;

    /** Keeps track of the last month for which reports were saved (formatted as "YYYY-MM"). */
    private String lastSavedMonth = "";

    /**
     * Constructs the report thread with database and CSV handler dependencies.
     *
     * @param dbHandler   The database handler used to retrieve monthly report data.
     * @param reportsCSV  The CSV handler used to save report files.
     */
    public MonthlyReportsThread(DBhandler dbHandler, ReportsCSV reportsCSV) {
        this.dbHandler = dbHandler;
        this.reportsCSV = reportsCSV;
    }

    /**
     * The main loop of the thread. Runs indefinitely while {@code running} is true.
     * Once per day, it checks if the current month's reports exist; if not, it generates them.
     */
    @Override
    public void run() {
        try {
            Thread.sleep(6_000);
        } catch (InterruptedException e) {
            System.out.println("🛑 Initialization interrupted.");
            return;
        }
        System.out.println("📅 MonthlyReportsThread started.");
        while (running) {
            try {
                String currentMonthKey = getCurrentMonthKey();
                String[] parts = currentMonthKey.split("-");
                String year = parts[0];
                String month = parts[1];

                boolean usersExists = reportsCSV.fileExists("USERS", year, month);
                boolean parkingExists = reportsCSV.fileExists("PARKING", year, month);

                if (!usersExists || !parkingExists) {
                    generateMonthlyReports(month, year);
                    lastSavedMonth = currentMonthKey;
                    System.out.println("📦 Monthly reports generated for " + currentMonthKey);
                }

                Thread.sleep(getMillisToNextMidnight());

            }
            catch (InterruptedException e) {
                System.out.println("🛑 MonthlyReportsThread interrupted. Exiting...");
                break; // exit the loop to stop the thread
            }
            catch (Exception e) {
                System.err.println("❌ MonthlyReportsThread error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Stops the thread gracefully by setting {@code running} to false.
     */
    public void stopThread() {
        running = false;
        this.interrupt();
    }

    /**
     * Calls the database handler to retrieve monthly data and saves it to CSV using the reports handler.
     *
     * @param month Two-digit month (e.g., "07").
     * @param year  Four-digit year (e.g., "2025").
     */
    private void generateMonthlyReports(String month, String year) {
        List<Map<String, String>> usersReport = dbHandler.getMonthlyUsersReport(month, year);
        List<Map<String, String>> parkingReport = dbHandler.getMonthlyParkingReport(month, year);

        reportsCSV.saveToCSV("USERS", year, month, usersReport);
        reportsCSV.saveToCSV("PARKING", year, month, parkingReport);
    }

    /**
     * @return A string representing the current month in "YYYY-MM" format.
     */
    private String getCurrentMonthKey() {
        LocalDate now = LocalDate.now();
        return String.format("%04d-%02d", now.getYear(), now.getMonthValue());
    }

    /**
     * @return Milliseconds remaining until the next midnight (00:00).
     */
    private long getMillisToNextMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = now.plusDays(1).toLocalDate().atStartOfDay();
        return ChronoUnit.MILLIS.between(now, nextMidnight);
    }
}
