package client_gui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Entry point for the Bpark client application.
 * Loads the login screen and sets the application icon and window properties.
 */
public class ClientApp extends Application {

    /**
     * JavaFX application start method.
     * Initializes the primary stage with the login screen and application icon.
     *
     * @param primaryStage The main application stage.
     */
	@Override
	public void start(Stage primaryStage) {
	    try {
	        Parent root = FXMLLoader.load(getClass().getResource("fxml/ClientLogin.fxml"));
	        primaryStage.setTitle("Bpark - ClientLogin");

	        // ✅ Load icon from JAR or classpath
	        InputStream iconStream = getClass().getResourceAsStream("/lib/Bpark_client_icon.png");
	        if (iconStream != null) {
	            primaryStage.getIcons().add(new Image(iconStream));
	        } else {
	            System.err.println("❌ Icon not found: /lib/Bpark_client_icon.png");
	        }

	        primaryStage.setScene(new Scene(root));
	        primaryStage.setResizable(false);
	        primaryStage.show();
	    } catch (IOException e) {
	        System.err.println("❌ Failed to load FXML: " + e.getMessage());
	        e.printStackTrace();
	    }
	}


    /**
     * Main entry point for the Java application.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
