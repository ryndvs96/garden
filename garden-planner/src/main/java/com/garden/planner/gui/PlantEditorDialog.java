package com.garden.planner.gui;

import com.garden.planner.core.model.PlantInstance;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dialog for editing an existing placed plant's properties (type, name, width, strict).
 * Zone is always "Any" — placement is unconstrained.
 */
public class PlantEditorDialog {

    public record PlantFormData(
            String plantType,
            String plantName,
            String zone,
            int widthIn,
            boolean isStrict,
            int count
    ) {}

    private final Dialog<PlantFormData> dialog;

    public PlantEditorDialog(Stage owner, PlantInstance existing) {
        dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(existing == null ? "Add Plant" : "Edit Plant");
        dialog.setHeaderText(null);

        TextField typeField = new TextField(existing != null ? existing.plantType() : "");
        TextField nameField = new TextField(existing != null ? existing.plantName() : "");

        Spinner<Integer> widthSpinner = new Spinner<>(3, 36, existing != null ? existing.widthIn() : 12, 1);
        widthSpinner.setEditable(true);

        CheckBox strictCheck = new CheckBox();
        strictCheck.setSelected(existing != null ? existing.isStrict() : true);

        Spinner<Integer> countSpinner = new Spinner<>(1, 20, 1);
        countSpinner.setEditable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.add(new Label("Plant type:"), 0, 0);
        grid.add(typeField, 1, 0);
        grid.add(new Label("Plant name:"), 0, 1);
        grid.add(nameField, 1, 1);
        grid.add(new Label("Width (in):"), 0, 2);
        grid.add(widthSpinner, 1, 2);
        grid.add(new Label("Strict:"), 0, 3);
        grid.add(strictCheck, 1, 3);
        int nextRow = 4;
        if (existing == null) {
            grid.add(new Label("Count:"), 0, nextRow);
            grid.add(countSpinner, 1, nextRow);
            nextRow++;
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            String name = nameField.getText().trim();
            String type = typeField.getText().trim();
            if (name.isEmpty() || type.isEmpty()) return null;
            return new PlantFormData(
                    type,
                    name,
                    "Any",
                    widthSpinner.getValue(),
                    strictCheck.isSelected(),
                    existing != null ? 1 : countSpinner.getValue()
            );
        });
    }

    public Optional<PlantFormData> show() {
        return dialog.showAndWait();
    }

    public static List<PlantInstance> createInstances(PlantFormData data, int baseIdx) {
        List<PlantInstance> result = new ArrayList<>();
        String name = data.plantName();
        String code = name.length() >= 2
                ? name.substring(0, 2).toUpperCase()
                : name.toUpperCase();
        for (int i = 0; i < data.count(); i++) {
            result.add(PlantInstance.builder()
                    .zone(data.zone())
                    .plantType(data.plantType())
                    .plantName(data.plantName())
                    .widthIn(data.widthIn())
                    .heightIn(data.widthIn()) // heightIn approximated as widthIn
                    .isStrict(data.isStrict())
                    .instanceIdx(baseIdx + i)
                    .code(code)
                    .build());
        }
        return result;
    }
}
