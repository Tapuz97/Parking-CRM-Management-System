package server_core;

import java.io.*;
import java.util.*;

/**
 * Utility class for handling CSV report generation and reading for parking and user activity.
 * Reports are stored under the "reports" directory in CSV format and are named as:
 * <code>USERS_MM_YYYY.csv</code> or <code>PARKING_MM_YYYY.csv</code>.
 */
public class ReportsCSV {

    private static final String REPORTS_FOLDER = "reports";

    /**
     * Default constructor.
     */
    public ReportsCSV() {}

    /**
     * Saves a table (list of maps) into a CSV file.
     *
     * @param reportType Type of report (e.g., "USERS", "PARKING").
     * @param year       Report year (e.g., "2025").
     * @param month      Report month (e.g., "March" or "03").
     * @param table      List of rows where each row is a map of column name to value.
     * @return true if saved successfully, false otherwise.
     */
    public static boolean saveToCSV(String reportType, String year, String month, List<Map<String, String>> table) {
        if (table == null || table.isEmpty()) return false;
        String monthNumber = monthNameToNumber(month);
        String fileName = String.format("%s_%s_%s.csv", reportType.toUpperCase(), monthNumber, year);
        File dir = new File(REPORTS_FOLDER);
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, fileName);

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Extract headers from first row
            Set<String> headers = table.get(0).keySet();
            writer.println(String.join(",", headers));

            for (Map<String, String> row : table) {
                List<String> values = new ArrayList<>();
                for (String header : headers) {
                    values.add(row.getOrDefault(header, ""));
                }
                writer.println(String.join(",", values));
            }

            return true;
        } catch (IOException e) {
            System.err.println("❌ Error writing CSV: " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads a report from a CSV file into a list of maps.
     *
     * @param reportType Type of report ("USERS" or "PARKING").
     * @param year       Report year.
     * @param month      Report month (name or 01-12).
     * @return A list of rows (each as map), or null on error.
     */
    public static List<Map<String, String>> loadFromCSV(String reportType, String year, String month) {
        List<Map<String, String>> table = new ArrayList<>();
        String monthNumber = monthNameToNumber(month);
        String fileName = String.format("%s_%s_%s.csv", reportType.toUpperCase(), monthNumber, year);
        File file = new File(REPORTS_FOLDER, fileName);

        if (!file.exists()) {
            System.err.println("❌ Report file not found: " + file.getPath());
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return table;
            String[] headers = headerLine.split(",");

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i], i < values.length ? values[i] : "");
                }
                table.add(row);
            }
        } catch (IOException e) {
            System.err.println("❌ Error reading CSV: " + e.getMessage());
            return null;
        }

        return table;
    }

    /**
     * Converts a month name (or string) to a two-digit number string (e.g., "March" → "03").
     *
     * @param name Month name or number.
     * @return Two-digit month number string.
     * @throws IllegalArgumentException if the name is invalid.
     */
    private static String monthNameToNumber(String name) {
        if (name == null) return null;

        if (name.matches("^(0[1-9]|1[0-2])$")) {
            return name;
        }

        switch (name.toLowerCase()) {
            case "january": return "01";
            case "february": return "02";
            case "march": return "03";
            case "april": return "04";
            case "may": return "05";
            case "june": return "06";
            case "july": return "07";
            case "august": return "08";
            case "september": return "09";
            case "october": return "10";
            case "november": return "11";
            case "december": return "12";
            default: throw new IllegalArgumentException("Invalid month name: " + name);
        }
    }

    /**
     * Returns the file path of a report based on its type, year, and month.
     *
     * @param type  Report type.
     * @param year  Year string (e.g., "2025").
     * @param month Month string (e.g., "03").
     * @return Full relative file path to the report CSV.
     */
    public String getReportPath(String type, String year, String month) {
        return REPORTS_FOLDER + "/" + type + "_" + month + "_" + year + ".csv";
    }

    /**
     * Checks if a report CSV file exists for the given type, year, and month.
     *
     * @param type  Report type ("USERS" or "PARKING").
     * @param year  Year string.
     * @param month Month string.
     * @return true if the report file exists, false otherwise.
     */
    public boolean fileExists(String type, String year, String month) {
        return new File(getReportPath(type, year, month)).exists();
    }
    
    /**
     * Saves a table (list of maps) into a specified CSV file.
     *
     * @param file  The file to save the CSV data into.
     * @param table A list of rows where each row is a map of column name to value.
     *              The first row is used to determine the headers for the CSV file.
     * @return true if the data is successfully saved to the file, false otherwise.
     */
    public static boolean savDataToCsv(File file, List<Map<String, String>> table) {
        if (table == null || table.isEmpty()) return false;

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Headers from the first entry
            Set<String> headers = table.get(0).keySet();
            writer.println(String.join(",", headers));

            for (Map<String, String> row : table) {
                List<String> values = new ArrayList<>();
                for (String header : headers) {
                    values.add(row.getOrDefault(header, ""));
                }
                writer.println(String.join(",", values));
            }

            return true;
        } catch (IOException e) {
            System.err.println("❌ Error saving log CSV: " + e.getMessage());
            return false;
        }
    }

}
