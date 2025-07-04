package client_gui;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import client_core.ReportsCSV;

/**
 * Abstract base class for all report-related GUI controllers.
 * Manages common logic for report filtering, data storage, formatting, and theme application.
 */
public class AbstractReportController {
	protected ReportsCSV reportsCSV;
	
		/** The root node of the current stage's scene. */
	protected static void exporReportToCSV() {
	    if (reportData.isEmpty() || reportData == null) {
	        System.out.println("⚠️ No log entries to export.");
	        return;
	    }
	    String date = LocalDate.now().format(DateTimeFormatter.ofPattern("MM_yyyy"));
	    String fileName = reportType + "_report_" + date + ".csv";
	    FileChooser fileChooser = new FileChooser();
	    fileChooser.setTitle("Save Report as CSV");
	    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
	    fileChooser.setInitialFileName(fileName);
	    File file = fileChooser.showSaveDialog(new Stage());

	    if (file != null) {
	        boolean success = ReportsCSV.saveDataToCsv(file, reportData);
	        if (success) {
	            System.out.println("✔️ Log exported successfully to " + file.getAbsolutePath());
	        } else {
	            System.out.println("❌ Failed to export log to CSV.");
	        }
	    }
	}

    /** The selected report month in 2-digit format (e.g., "04"). */
	protected static String reportMonth;

    /** The selected report year (e.g., "2025"). */
    protected static String reportYear;

    /** The type of report (e.g., "PARKING" or "USERS"). */
    protected static String reportType;

    /** Formatted month/year string (e.g., "04/2025"). */
    protected String MonthYearFormat;
    

    /** The raw report data table returned from the server. */
    protected static List<Map<String, String>> reportData;
    
    /**
     * Closes the current stage when the parent stage is closed.
     * Sets an event handler on the parent stage's close request to ensure the current stage is also closed.
     *
     * @param reportRootNode The root node of the current stage's scene.
     * @param parentStage    The parent stage whose close request triggers the current stage to close.
     */
    
    
    public void closeWithParentStage(Node rootNode, Stage parentStage) {
        Platform.runLater(() -> {
            Scene scene = rootNode.getScene();
            if (scene == null || scene.getWindow() == null) {
                System.err.println("⚠️ Unable to attach close hook: scene or window is null");
                return;
            }

            Stage thisStage = (Stage) scene.getWindow();
            parentStage.setOnCloseRequest(e -> thisStage.close());
        });
    }
    


    /**
     * Sets the month and year based on UI selection, and generates a formatted MM/YYYY string.
     *
     * @param selectedMonth The selected month name (e.g., "April").
     * @param selectedYear  The selected year (e.g., "2025").
     */
    protected void setMonthYear(String selectedMonth, String selectedYear) {
        this.reportMonth = monthNameToNumber(selectedMonth);
        this.reportYear = selectedYear;    
        this.MonthYearFormat = this.reportMonth + "/" + this.reportYear;
    }

    /**
     * Sets the report data returned from the server.
     *
     * @param reportData List of table rows (each row is a map of field names to values).
     */
    protected void setReportData(List<Map<String, String>> reportData) {
        this.reportData = reportData;
    }

    /**
     * Sets the report type to be used for querying or displaying (e.g., "USERS", "PARKING").
     *
     * @param type The report type.
     */
    protected void setReportType(String type) {
        this.reportType = type;    
    }

    /** @return The selected report month in "MM" format. */
    protected String getReportMonth() {
        return reportMonth;
    }

    /** @return The selected report year. */
    protected String getReportYear() {
        return reportYear;
    }

    /** @return The current report type. */
    protected String getReportType() {
        return reportType;
    }

    /** @return The list of rows comprising the report data. */
    protected List<Map<String, String>> getReportData() {
        return reportData;
    }

    /**
     * Converts a month name (e.g., "April") to its 2-digit number format (e.g., "04").
     * If input is already in the format "01" to "12", it returns it directly.
     *
     * @param name The month name or number.
     * @return The month number as a 2-digit string.
     * @throws IllegalArgumentException if the input is not a valid month.
     */
    protected static String monthNameToNumber(String name) {
        if (name == null) return null;

        // If it's already in 01–12 format
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
     * Meant to be overridden by subclasses to display a label showing the selected month/year.
     */
    protected void ShowReportMonthYear() {}

    /**
     * Called during initialization to prepare the controller or view before display.
     */
    protected void initializeReport() {}

    /**
     * Called by subclasses to render a chart or graph based on report data.
     */
    protected void renderGraph() {}
    
    
    protected void switchTheme(boolean isDark) {}
    /**
     * Applies either a dark or light CSS theme to the given JavaFX scene.
     *
     * @param scene   The JavaFX scene to apply the stylesheet to.
     * @param isDark  If true, applies the dark theme; otherwise applies the light theme.
     */
    public static void applyTheme(Scene scene, boolean isDark) {
        if (scene == null) return;
        scene.getStylesheets().clear();
        String path = isDark ? "client_gui/fxml/style_dark.css" : "client_gui/fxml/style_light.css";
        URL css = AbstractReportController.class.getClassLoader().getResource(path);

        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        } else {
            System.err.println("❌ Could not find stylesheet: " + path);
        }
    }
    
}
