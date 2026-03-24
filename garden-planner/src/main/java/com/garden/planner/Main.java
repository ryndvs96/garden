package com.garden.planner;

import com.garden.planner.gui.MainController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX Application entry point.
 * Opens directly into MainController (no start screen).
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        MainController root = new MainController(stage);
        Scene scene = new Scene(root, 1600, 900);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
