package com.garden.planner.gui;

import com.garden.planner.data.SeedBankSerializer;
import com.garden.planner.project.SeedBank;
import com.garden.planner.project.SeedEntry;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Primary "Add Plant" dialog — multi-select with quantity spinner and two add modes.
 */
public class SeedPickerDialog {

    public enum AddMode { NORMAL, BEST_SCORE }

    public record AddRequest(List<SeedEntry> entries, AddMode mode) {}

    private static final ButtonType ADD_NORMAL =
            new ButtonType("Add (Top-Left)", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType ADD_BEST =
            new ButtonType("Add (Best Score)", ButtonBar.ButtonData.OTHER);

    private final Dialog<AddRequest> dialog;
    private final SeedBank seedBank;
    private final Stage owner;
    private TableView<SeedEntry> table;
    private FilteredList<SeedEntry> filtered;
    private Spinner<Integer> qtySpin;
    private Label selectionLabel;

    public SeedPickerDialog(Stage owner, SeedBank seedBank) {
        this.owner = owner;
        this.seedBank = seedBank;

        dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Add Plant");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().setPrefHeight(500);

        build();
    }

    private void build() {
        // --- Filter bar + New Plant button ---
        TextField filterField = new TextField();
        filterField.setPromptText("Search by name or type…");
        HBox.setHgrow(filterField, Priority.ALWAYS);

        Button newPlantBtn = new Button("+ New Plant…");
        newPlantBtn.setOnAction(e -> doNewPlant());

        HBox topBar = new HBox(8, filterField, newPlantBtn);
        topBar.setPadding(new Insets(0, 0, 6, 0));

        // --- Table (multi-select) ---
        filtered = new FilteredList<>(seedBank.observableEntries(), p -> true);
        filterField.textProperty().addListener((obs, was, now) -> {
            String q = now == null ? "" : now.trim().toLowerCase();
            filtered.setPredicate(q.isEmpty() ? p -> true :
                    p -> p.plantType().toLowerCase().contains(q)
                      || p.plantName().toLowerCase().contains(q)
                      || p.notes().toLowerCase().contains(q));
        });

        table = new TableView<>(filtered);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPrefHeight(330);
        table.setPlaceholder(new Label("No seeds found. Use \"+ New Plant…\" to create one."));
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<SeedEntry, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().plantType()));
        typeCol.setPrefWidth(110);

        TableColumn<SeedEntry, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().plantName()));
        nameCol.setPrefWidth(130);

        TableColumn<SeedEntry, Integer> widthCol = new TableColumn<>("Width");
        widthCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().widthIn()));
        widthCol.setPrefWidth(55);

        TableColumn<SeedEntry, String> strictCol = new TableColumn<>("Strict");
        strictCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isStrict() ? "✓" : ""));
        strictCol.setPrefWidth(45);

        TableColumn<SeedEntry, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().notes()));
        notesCol.setPrefWidth(160);

        table.getColumns().addAll(typeCol, nameCol, widthCol, strictCol, notesCol);

        // --- Quantity row ---
        qtySpin = new Spinner<>(1, 20, 1);
        qtySpin.setEditable(true);
        qtySpin.setPrefWidth(70);

        selectionLabel = new Label("0 plants selected");

        HBox qtyRow = new HBox(8, new Label("Quantity:"), qtySpin, selectionLabel);
        qtyRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        qtyRow.setPadding(new Insets(4, 0, 0, 0));

        // Update quantity spinner + label on selection change
        table.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<SeedEntry>) c -> updateQtyRow());

        // Double-click = normal add of single item
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                SeedEntry item = table.getSelectionModel().getSelectedItem();
                if (item != null) {
                    dialog.setResult(new AddRequest(List.of(item), AddMode.NORMAL));
                    dialog.close();
                }
            }
        });

        VBox content = new VBox(topBar, table, qtyRow);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ADD_NORMAL, ADD_BEST, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.CANCEL) return null;
            List<SeedEntry> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
            if (selected.isEmpty()) return null;

            AddMode mode = (btn == ADD_BEST) ? AddMode.BEST_SCORE : AddMode.NORMAL;
            List<SeedEntry> entries;
            if (selected.size() == 1) {
                int qty = qtySpin.getValue();
                entries = new ArrayList<>(qty);
                for (int i = 0; i < qty; i++) entries.add(selected.get(0));
            } else {
                // multiple rows selected — qty spinner ignored, one of each
                entries = selected;
            }
            return new AddRequest(entries, mode);
        });
    }

    private void updateQtyRow() {
        int count = table.getSelectionModel().getSelectedItems().size();
        if (count == 1) {
            qtySpin.setDisable(false);
            selectionLabel.setText("1 plant selected");
        } else if (count > 1) {
            qtySpin.setDisable(true);
            selectionLabel.setText(count + " plants selected (qty ignored)");
        } else {
            qtySpin.setDisable(false);
            selectionLabel.setText("0 plants selected");
        }
    }

    /** Opens an inline "New Seed Entry" form, adds to bank, selects the result. */
    private void doNewPlant() {
        Dialog<SeedEntry> newDlg = new Dialog<>();
        newDlg.initOwner(owner);
        newDlg.setTitle("New Seed Entry");
        newDlg.setHeaderText(null);

        TextField typeField  = new TextField();
        typeField.setPromptText("e.g. Tomato, Cherry");
        TextField nameField  = new TextField();
        nameField.setPromptText("e.g. Gold Nugget");
        Spinner<Integer> widthSpin = new Spinner<>(6, 36, 12, 6);
        widthSpin.setEditable(true);
        CheckBox strictCheck = new CheckBox();
        strictCheck.setSelected(true);
        TextField notesField = new TextField();
        notesField.setPromptText("optional");

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(8); grid.setPadding(new Insets(10));
        grid.add(new Label("Plant type:"), 0, 0); grid.add(typeField,  1, 0);
        grid.add(new Label("Plant name:"), 0, 1); grid.add(nameField,  1, 1);
        grid.add(new Label("Width (in):"), 0, 2); grid.add(widthSpin,  1, 2);
        grid.add(new Label("Strict:"),     0, 3); grid.add(strictCheck, 1, 3);
        grid.add(new Label("Notes:"),      0, 4); grid.add(notesField, 1, 4);

        newDlg.getDialogPane().setContent(grid);
        newDlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        newDlg.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            String type = typeField.getText().trim();
            String name = nameField.getText().trim();
            if (type.isEmpty() || name.isEmpty()) return null;
            return new SeedEntry(UUID.randomUUID().toString(),
                    "Any", type, name,
                    widthSpin.getValue(), widthSpin.getValue(),
                    strictCheck.isSelected(), notesField.getText().trim());
        });

        newDlg.showAndWait().ifPresent(entry -> {
            seedBank.observableEntries().add(entry);
            // Auto-save
            try { new SeedBankSerializer().save(seedBank); } catch (Exception ignored) {}
            // Clear filter so the new entry is visible, then select it
            table.getScene();
            filtered.setPredicate(p -> true);
            table.getSelectionModel().clearSelection();
            table.getSelectionModel().select(entry);
            table.scrollTo(entry);
        });
    }

    public Optional<AddRequest> show() {
        return dialog.showAndWait();
    }
}
