package com.bacplus;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class Main extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/view.fxml"));
        try {
            fxmlLoader.setResources(java.util.ResourceBundle.getBundle("i18n/messages"));
        } catch (Exception e) {
            System.out.println("Could not load resources: " + e.getMessage());
        }
        scene = new Scene(fxmlLoader.load());
        // Load CSS
        scene.getStylesheets().add(Main.class.getResource("/styles.css").toExternalForm());

        stage.setTitle("Baccalaureat+");
        stage.setScene(scene);
        stage.show();
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/" + fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }
}
