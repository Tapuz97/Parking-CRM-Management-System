package client_gui;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.text.Text;
import javafx.scene.control.ToggleButton;

/**
 * Controller for displaying parking report data in bar chart form.
 * Supports daily and weekly views, with tooltips showing capacity data.
 * Inherits shared behavior from {@link AbstractReportController}.
 */
public class ParkingReportController extends AbstractReportController {

    @FXML private BarChart<String, Number> parkingBarChart;
    @FXML private CategoryAxis chartDayAxies;
    @FXML private NumberAxis chartCapacityAxies;
    @FXML private ToggleButton weeklyToggle;
    @FXML private Text reportMonthYear;
    @FXML private AnchorPane mainScreen;
    @FXML private ImageView CSVexportBtn;

    /** Indicates whether the chart is showing weekly (true) or daily (false) data. */
    private boolean isWeekly = false;

    /**
     * Initializes the report by showing the selected month/year and rendering the graph.
     * Called automatically after the FXML is loaded.
     */
    @Override
    public void initializeReport() {
        ShowReportMonthYear();
        renderGraph();
    }

    /**
     * Displays the month and year currently set for this report.
     * Shown above the bar chart.
     */
    @Override
    public void ShowReportMonthYear() {
        reportMonthYear.setText(this.MonthYearFormat);
    }

    /**
     * Handles the toggle button action for switching between weekly and daily views.
     * Triggers a re-render of the chart.
     */
    @FXML
    private void handleWeeklyToggle() {
        renderGraph();
    }

    /**
     * Renders the bar chart based on current toggle state.
     * Adds tooltips to each bar showing the capacity value.
     */
    public void renderGraph() {
        parkingBarChart.getData().clear();
        Map<String, Integer> dataMap;

        if (weeklyToggle.isSelected()) {
            dataMap = aggregateWeekly();
        } else {
            dataMap = aggregateDaily();
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<String, Integer> entry : dataMap.entrySet()) {
            XYChart.Data<String, Number> bar = new XYChart.Data<>(entry.getKey(), entry.getValue());
            series.getData().add(bar);

            // Add tooltip on hover
            Tooltip tooltip = new Tooltip("Capacity: " + entry.getValue());
            tooltip.setShowDelay(Duration.ZERO);
            bar.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    Tooltip.install(newNode, tooltip);
                }
            });
        }

        parkingBarChart.getData().add(series);
    }

    /**
     * Aggregates the raw report data by day.
     *
     * @return A sorted map with each day and its associated parking capacity.
     */
    private Map<String, Integer> aggregateDaily() {
        Map<String, Integer> result = new TreeMap<>();
        for (Map<String, String> row : reportData) {
            String day = row.get("day");
            int count = Integer.parseInt(row.getOrDefault("capacity", "0"));
            result.put(day, count);
        }
        return result;
    }

    /**
     * Aggregates the raw report data into 5 weekly groups based on day-of-month.
     *
     * @return A map with labels like "1.MM - 7.MM" and total capacity for each week.
     */
    private Map<String, Integer> aggregateWeekly() {
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<Integer, Integer> weekCount = new HashMap<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (Map<String, String> row : reportData) {
            LocalDate date = LocalDate.parse(row.get("day"), formatter);
            int dayOfMonth = date.getDayOfMonth();
            int weekIndex = (dayOfMonth - 1) / 7;

            weekCount.put(weekIndex, weekCount.getOrDefault(weekIndex, 0) +
                    Integer.parseInt(row.getOrDefault("capacity", "0")));
        }

        for (int i = 0; i < 5; i++) {
            int start = 1 + i * 7;
            int end = Math.min(start + 6, 31);
            String label = start + "." + reportMonth + " - " + end + "." + reportMonth;
            result.put(label, weekCount.getOrDefault(i, 0));
        }

        return result;
    }
    
    /**
     * Switches the theme of the application between dark mode and light mode.
     * Ensures the `mainScreen` and its scene are not null before applying the theme.
     * The method clears the current stylesheets and applies the appropriate stylesheet
     * based on the `isDarkMode` parameter.
     *
     * @param isDarkMode true to switch to dark mode, false to switch to light mode
     */
    @Override
    public void switchTheme(boolean isDarkMode) {
        Platform.runLater(() -> {
            if (mainScreen == null || mainScreen.getScene() == null) {
                System.err.println("⚠️ mainScreen or scene is still null");
                return;
            }

            Scene scene = mainScreen.getScene();
            scene.getStylesheets().clear();

            String themePath = isDarkMode ? "fxml/style_dark.css" : "fxml/style_light.css";
            URL cssUrl = getClass().getResource(themePath);

            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                System.err.println("❌ Theme file not found: " + themePath);
            }
        });
    }
    
    @FXML
    private void handleCSVexport() {
    	exporReportToCSV();
    }


}
