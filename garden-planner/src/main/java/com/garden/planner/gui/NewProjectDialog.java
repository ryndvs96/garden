package com.garden.planner.gui;

import com.garden.planner.project.GardenProject;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

public class NewProjectDialog {

    private final Dialog<GardenProject> dialog;

    public NewProjectDialog(Stage owner) {
        dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("New Garden Plan");
        dialog.setHeaderText(null);

        TextField nameField     = new TextField("My Garden");
        TextField locationField = new TextField(System.getProperty("user.home"));
        Button browseBtn        = new Button("Browse...");

        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Choose Location");
            dc.setInitialDirectory(new File(locationField.getText().isBlank()
                    ? System.getProperty("user.home")
                    : locationField.getText()));
            File dir = dc.showDialog(owner);
            if (dir != null) locationField.setText(dir.getAbsolutePath());
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        ColumnConstraints col0 = new ColumnConstraints();
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col0, col1);

        grid.add(new Label("Plan name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Location:"), 0, 1);
        grid.add(locationField, 1, 1);
        grid.add(browseBtn, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(480);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            String name = nameField.getText().trim();
            String loc  = locationField.getText().trim();
            if (name.isEmpty() || loc.isEmpty()) return null;
            String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
            Path projectDir = Path.of(loc, slug + ".gardenplan");
            return new GardenProject(name, projectDir);
        });
    }

    public Optional<GardenProject> show() {
        return dialog.showAndWait();
    }
}
