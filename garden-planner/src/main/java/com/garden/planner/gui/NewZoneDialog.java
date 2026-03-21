package com.garden.planner.gui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.Optional;

public class NewZoneDialog {

    private final Dialog<String> dialog;

    public NewZoneDialog(Stage owner) {
        dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("New Zone");
        dialog.setHeaderText(null);

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Raised Beds");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.add(new Label("Zone name:"), 0, 0);
        grid.add(nameField, 1, 0);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            String name = nameField.getText().trim();
            return name.isEmpty() ? null : name;
        });
    }

    public Optional<String> show() {
        return dialog.showAndWait();
    }
}
