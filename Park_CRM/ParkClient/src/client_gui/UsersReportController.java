/**
 * Controller class for managing the Users Report screen in the GUI.
 * This class extends the AbstractReportController and is responsible for
 * rendering a pie chart that visualizes user activity data and displaying
 * the report's month and year.
 */
package client_gui;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class UsersReportController extends AbstractReportController {
    @FXML private PieChart userPieChart;
    @FXML private Text reportMonthYear;
    @FXML private ImageView CSVexportBtn;
    

    /**
     * Initializes the report by displaying the month and year and rendering the graph.
     */
    @Override
    public void initializeReport() {
        ShowReportMonthYear();
        renderGraph();
    }

    /**
     * Displays the report's month and year in the corresponding Text element.
     */
    @Override
    public void ShowReportMonthYear() {
        reportMonthYear.setText(MonthYearFormat);
    }
    @FXML
    private AnchorPane mainScreen;

    /**
     * Renders the pie chart based on the aggregated user activity data.
     * The chart includes slices for different activity types and inactive users.
     */
    @Override
    protected void renderGraph() {
        Map<String, Integer> aggregated = new HashMap<>();

        for (Map<String, String> row : reportData) {
            for (String type : List.of("picked_up", "deposited", "cancelled", "extended", "late", "reserved")) {
                int value = Integer.parseInt(row.getOrDefault(type, "0"));
                aggregated.put(type, aggregated.getOrDefault(type, 0) + value);
            }
        }

        int inactiveUsers = countInactiveUsers(reportData);

        userPieChart.getData().clear();

        for (Map.Entry<String, Integer> entry : aggregated.entrySet()) {
            if (entry.getValue() == 0) continue;

            PieChart.Data slice = new PieChart.Data(entry.getKey(), entry.getValue());
            userPieChart.getData().add(slice);

            Tooltip tooltip = new Tooltip(entry.getKey() + ": " + entry.getValue());
            Tooltip.install(slice.getNode(), tooltip);
            tooltip.setShowDelay(Duration.ZERO);
        }

        if (inactiveUsers > 0) {
            PieChart.Data inactiveSlice = new PieChart.Data("inactive", inactiveUsers);
            userPieChart.getData().add(inactiveSlice);

            Tooltip tooltip = new Tooltip("inactive: " + inactiveUsers);
            Tooltip.install(inactiveSlice.getNode(), tooltip);
            tooltip.setShowDelay(Duration.ZERO);
        }
    }

    /**
     * Counts the number of inactive users based on the provided report data.
     * A user is considered inactive if all activity types have a value of zero.
     *
     * @param data The list of user activity data.
     * @return The number of inactive users.
     */
    private int countInactiveUsers(List<Map<String, String>> data) {
        int count = 0;
        for (Map<String, String> row : data) {
            boolean allZero = true;
            for (String type : List.of("picked_up", "deposited", "cancelled", "extended", "late", "reserved")) {
                if (Integer.parseInt(row.getOrDefault(type, "0")) > 0) {
                    allZero = false;
                    break;
                }
            }
            if (allZero) count++;
        }
        return count;
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
