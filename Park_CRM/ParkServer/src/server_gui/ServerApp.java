package server_gui;

import java.io.File;
import java.net.URL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Entry point for the Bpark Server JavaFX application.
 * Loads the main server GUI from the FXML file and applies an application icon.
 */
public class ServerApp extends Application {

    /**
     * Starts the JavaFX application by loading the server GUI from FXML
     * and setting the window properties including title, icon, and size.
     *
     * @param primaryStage the main application window
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            URL fxmlPath = getClass().getResource("fxml/ServerGUI.fxml");
            // System.out.println("📂 FXML Path = " + fxmlPath);
            if (fxmlPath == null) {
                throw new RuntimeException("❌ FXML not found — check if 'server_fxml' is marked as a source folder");
            }

            Parent root = FXMLLoader.load(fxmlPath);
            primaryStage.setTitle("Bpark Server");

            // Set application window icon if available
            URL iconUrl = getClass().getResource("/lib/Bpark_server_icon.png");
            if (iconUrl != null) {
                primaryStage.getIcons().add(new Image(iconUrl.toExternalForm()));
            } else {
                System.err.println("❌ Icon not found in resources: /lib/Bpark_server_icon.png");
            }


            primaryStage.setScene(new Scene(root));
            primaryStage.setResizable(false);
            primaryStage.show();

        } catch (Exception e) {
            System.err.println("❌ Failed to start application");
            e.printStackTrace();
        }
    }

    /**
     * Main method to launch the JavaFX application.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        launch(args); // Triggers the start method
    }
}
