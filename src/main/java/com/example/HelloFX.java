package com.example;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloFX extends Application {
    public static HostServices hostServices;

    public static HostServices hostServices() {
        return hostServices;
    }

    @Override
    public void start(Stage stage) throws Exception {
        hostServices = getHostServices();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/hello-view.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 720, 520);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        stage.setTitle("JavaFX NTFY Chat — mats_notiser");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
