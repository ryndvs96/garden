package com.garden.planner.gui;

import com.garden.planner.data.SeedBankSerializer;
import com.garden.planner.project.SeedBank;
import com.garden.planner.project.SeedEntry;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.converter.IntegerStringConverter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sidebar panel showing the global seed bank as an editable TableView.
 * Edits are auto-saved 500 ms after the last change.
 */
public class SeedBankController extends VBox {

    /** UUID → SeedEntry transfer registry for drag-and-drop to BedEditorPane. */
    public static final Map<String, SeedEntry> DRAG_REGISTRY = new ConcurrentHashMap<>();

    private final SeedBank seedBank;
    private final SeedBankSerializer serializer = new SeedBankSerializer();
    private final PauseTransition savePause = new PauseTransition(Duration.millis(500));

    @SuppressWarnings("this-escape")
    public SeedBankController(SeedBank seedBank) {
        this.seedBank = seedBank;
        setStyle("-fx-background-color: #252526;");

        Label header = new Label("SEED BANK");
        header.setStyle("-fx-text-fill: #bbb; -fx-font-size: 10; -fx-padding: 8 8 4 8; -fx-font-weight: bold;");

        TableView<SeedEntry> table = new TableView<>(seedBank.observableEntries());
        table.setEditable(true);
        table.setStyle("-fx-background-color: #2d2d2d;");
        table.setPlaceholder(new Label("No seeds. Click \"+ Add\" to create one."));
        VBox.setVgrow(table, Priority.ALWAYS);

        // --- Zone column ---
        TableColumn<SeedEntry, String> zoneCol = new TableColumn<>("Zone");
        zoneCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().zone()));
        zoneCol.setCellFactory(TextFieldTableCell.forTableColumn());
        zoneCol.setOnEditCommit(e -> replace(e.getRowValue(), e.getNewValue(), null, null, null, null, null));
        zoneCol.setPrefWidth(60);

        // --- Plant type column ---
        TableColumn<SeedEntry, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().plantType()));
        typeCol.setCellFactory(TextFieldTableCell.forTableColumn());
        typeCol.setOnEditCommit(e -> replace(e.getRowValue(), null, e.getNewValue(), null, null, null, null));
        typeCol.setPrefWidth(70);

        // --- Plant name column ---
        TableColumn<SeedEntry, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().plantName()));
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(e -> replace(e.getRowValue(), null, null, e.getNewValue(), null, null, null));
        nameCol.setPrefWidth(90);

        // --- Width column ---
        TableColumn<SeedEntry, Integer> widthCol = new TableColumn<>("W(in)");
        widthCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().widthIn()));
        widthCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        widthCol.setOnEditCommit(e -> {
            if (e.getNewValue() != null) replace(e.getRowValue(), null, null, null, e.getNewValue(), null, null);
        });
        widthCol.setPrefWidth(50);

        // --- Strict column (CheckBoxTableCell) ---
        // We use the Callback<Integer,ObservableValue<Boolean>> overload so the
        // checkbox can write back to the model via a listener on each property.
        TableColumn<SeedEntry, Boolean> strictCol = new TableColumn<>("Strict");
        strictCol.setCellValueFactory(c -> {
            SeedEntry entry = c.getValue();
            SimpleBooleanProperty prop = new SimpleBooleanProperty(entry.isStrict());
            prop.addListener((obs, was, is) -> replace(entry, null, null, null, null, is, null));
            return prop;
        });
        strictCol.setCellFactory(col -> new CheckBoxTableCell<>());
        strictCol.setEditable(true);
        strictCol.setPrefWidth(55);

        // --- Notes column ---
        TableColumn<SeedEntry, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().notes()));
        notesCol.setCellFactory(TextFieldTableCell.forTableColumn());
        notesCol.setOnEditCommit(e -> replace(e.getRowValue(), null, null, null, null, null, e.getNewValue()));
        notesCol.setPrefWidth(110);

        table.getColumns().addAll(zoneCol, typeCol, nameCol, widthCol, strictCol, notesCol);

        // --- Drag-and-drop: drag a row onto a BedEditorPane canvas ---
        table.setRowFactory(tv -> {
            TableRow<SeedEntry> row = new TableRow<>();
            row.setOnDragDetected(e -> {
                if (row.isEmpty()) return;
                SeedEntry entry = row.getItem();
                DRAG_REGISTRY.put(entry.id(), entry);
                Dragboard db = row.startDragAndDrop(TransferMode.COPY);
                ClipboardContent cc = new ClipboardContent();
                cc.putString(entry.id());
                db.setContent(cc);
                e.consume();
            });
            return row;
        });

        // --- Buttons ---
        Button addBtn    = new Button("+ Add");
        Button deleteBtn = new Button("Delete");
        String btnStyle  = "-fx-background-color: #3c3c3c; -fx-text-fill: #ccc;";
        addBtn.setStyle(btnStyle);
        deleteBtn.setStyle(btnStyle);

        addBtn.setOnAction(e -> {
            SeedEntry newEntry = new SeedEntry(UUID.randomUUID().toString(),
                    "Back", "", "", 12, 12, true, "");
            seedBank.observableEntries().add(newEntry);
            triggerAutoSave();
        });
        deleteBtn.setOnAction(e -> {
            SeedEntry sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                seedBank.observableEntries().remove(sel);
                triggerAutoSave();
            }
        });

        HBox buttons = new HBox(6, addBtn, deleteBtn);
        buttons.setPadding(new Insets(4, 8, 4, 8));

        savePause.setOnFinished(e -> {
            try { serializer.save(seedBank); } catch (Exception ignored) {}
        });

        getChildren().addAll(header, table, buttons);
    }

    /**
     * Replace an entry in-place. Pass null for any field to keep the existing value.
     */
    private void replace(SeedEntry old, String zone, String type, String name,
                         Integer width, Boolean strict, String notes) {
        int idx = seedBank.observableEntries().indexOf(old);
        if (idx < 0) return;
        SeedEntry updated = new SeedEntry(
                old.id(),
                zone   != null ? zone   : old.zone(),
                type   != null ? type   : old.plantType(),
                name   != null ? name   : old.plantName(),
                width  != null ? width  : old.widthIn(),
                old.heightIn(),
                strict != null ? strict : old.isStrict(),
                notes  != null ? notes  : old.notes()
        );
        seedBank.observableEntries().set(idx, updated);
        triggerAutoSave();
    }

    private void triggerAutoSave() {
        savePause.playFromStart();
    }
}
