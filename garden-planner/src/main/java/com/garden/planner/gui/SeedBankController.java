package com.garden.planner.gui;

import com.garden.planner.core.model.PlantInstance;
import com.garden.planner.data.SeedBankSerializer;
import com.garden.planner.project.SeedBank;
import com.garden.planner.project.SeedEntry;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Sidebar panel showing the global seed bank as a read-only TableView.
 * Double-click or Enter adds the selected plant to the open bed.
 * "Edit" opens the properties dialog for in-place editing of the seed entry.
 * A fixed-height plant info panel at the bottom has a vertical drag handle.
 */
public class SeedBankController extends VBox {

    private static final double INFO_PANE_DEFAULT_HEIGHT = 130;
    private static final double INFO_PANE_MIN_HEIGHT     = 60;
    private static final double INFO_PANE_MAX_HEIGHT     = 350;

    /** UUID → SeedEntry transfer registry for drag-and-drop to BedEditorPane. */
    public static final Map<String, SeedEntry> DRAG_REGISTRY = new ConcurrentHashMap<>();

    private final SeedBank seedBank;
    private final SeedBankSerializer serializer = new SeedBankSerializer();
    private final PauseTransition savePause = new PauseTransition(Duration.millis(500));
    private Consumer<SeedEntry> onAddSeed;

    private double dragStartY;
    private double dragStartInfoHeight;

    public void setOnAddSeed(Consumer<SeedEntry> cb) { this.onAddSeed = cb; }

    @SuppressWarnings("this-escape")
    public SeedBankController(SeedBank seedBank) {
        this.seedBank = seedBank;
        setStyle("-fx-background-color: #252526;");

        Label header = new Label("SEED BANK");
        header.setStyle("-fx-text-fill: #bbb; -fx-font-size: 10; -fx-padding: 8 8 4 8; -fx-font-weight: bold;");

        TableView<SeedEntry> table = new TableView<>(seedBank.observableEntries());
        table.setEditable(false);
        table.setStyle("-fx-background-color: #2d2d2d;");
        table.setPlaceholder(new Label("No seeds. Click \"New\" to add one."));
        VBox.setVgrow(table, Priority.ALWAYS);

        // --- Plant type column (display only) ---
        TableColumn<SeedEntry, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().plantType()));
        typeCol.setPrefWidth(75);

        // --- Plant name column (display only) ---
        TableColumn<SeedEntry, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().plantName()));
        nameCol.setPrefWidth(105);

        // --- Quantity column ---
        TableColumn<SeedEntry, Number> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().quantity()));
        qtyCol.setPrefWidth(40);
        qtyCol.setStyle("-fx-alignment: CENTER;");

        table.getColumns().addAll(typeCol, nameCol, qtyCol);

        // --- Row interactions: drag-and-drop + double-click to add; red text at qty 0 ---
        table.setRowFactory(tv -> {
            TableRow<SeedEntry> row = new TableRow<>() {
                @Override
                protected void updateItem(SeedEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setStyle("");
                    } else if (item.quantity() <= 0) {
                        setStyle("-fx-text-fill: #e05555; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            };
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
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY
                        && e.getClickCount() == 2
                        && !row.isEmpty()) {
                    triggerAddSelected(table);
                }
            });
            return row;
        });

        // Enter key → add selected to bed
        table.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                triggerAddSelected(table);
                e.consume();
            }
        });

        // --- Buttons ---
        Button newBtn      = new Button("New");
        Button editBtn     = new Button("Edit");
        Button deleteBtn   = new Button("Delete");
        Button addToBedBtn = new Button("\uD83C\uDF31 Sow");
        String btnStyle    = "-fx-background-color: #3c3c3c; -fx-text-fill: #ccc;";
        newBtn.setStyle(btnStyle);
        editBtn.setStyle(btnStyle);
        deleteBtn.setStyle(btnStyle);
        addToBedBtn.setStyle("-fx-background-color: #2d7a3a; -fx-text-fill: #e8ffe8; -fx-font-weight: bold;");

        newBtn.setOnAction(e -> {
            Stage owner = (Stage) table.getScene().getWindow();
            new PlantEditorDialog(owner, null).show().ifPresent(data -> {
                SeedEntry entry = new SeedEntry(UUID.randomUUID().toString(),
                        "", data.plantType(), data.plantName(),
                        data.widthIn(), data.widthIn(), data.isStrict(), "", 25);
                seedBank.observableEntries().add(entry);
                table.getSelectionModel().select(entry);
                triggerAutoSave();
            });
        });
        editBtn.setOnAction(e -> {
            SeedEntry sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            Stage owner = (Stage) table.getScene().getWindow();
            // Build a PlantInstance to pre-fill the dialog (count field is hidden for non-null)
            String selCode = sel.plantName().length() >= 2
                    ? sel.plantName().substring(0, 2).toUpperCase()
                    : sel.plantName().toUpperCase();
            PlantInstance existing = PlantInstance.builder()
                    .zone("Any").plantType(sel.plantType()).plantName(sel.plantName())
                    .widthIn(sel.widthIn()).heightIn(sel.widthIn())
                    .isStrict(sel.isStrict()).instanceIdx(0).code(selCode)
                    .build();
            new PlantEditorDialog(owner, existing).show().ifPresent(data ->
                    replaceAll(sel, data.plantType(), data.plantName(),
                               data.widthIn(), data.isStrict()));
        });
        deleteBtn.setOnAction(e -> {
            SeedEntry sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                seedBank.observableEntries().remove(sel);
                triggerAutoSave();
            }
        });
        addToBedBtn.setOnAction(e -> triggerAddSelected(table));

        HBox buttons = new HBox(6, newBtn, editBtn, deleteBtn, addToBedBtn);
        buttons.setPadding(new Insets(4, 8, 4, 8));

        // --- Plant info panel — always present, fixed height ---
        VBox infoPane = new VBox(4);
        infoPane.setStyle("-fx-background-color: #1e1e1e;");
        infoPane.setPadding(new Insets(8, 10, 8, 10));
        infoPane.setPrefHeight(INFO_PANE_DEFAULT_HEIGHT);
        infoPane.setMinHeight(INFO_PANE_MIN_HEIGHT);
        infoPane.setMaxHeight(INFO_PANE_MAX_HEIGHT);
        VBox.setVgrow(infoPane, Priority.NEVER);

        showPlaceholder(infoPane);

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel == null) showPlaceholder(infoPane);
            else             updateInfoPane(infoPane, sel);
        });

        // Vertical drag handle between buttons and info pane
        Region resizeHandle = new Region();
        resizeHandle.setPrefHeight(5);
        resizeHandle.setMinHeight(5);
        resizeHandle.setMaxHeight(5);
        resizeHandle.setMaxWidth(Double.MAX_VALUE);
        resizeHandle.setStyle("-fx-background-color: #3a3a3a;");
        resizeHandle.setCursor(Cursor.V_RESIZE);
        resizeHandle.setOnMousePressed(e -> {
            dragStartY          = e.getScreenY();
            dragStartInfoHeight = infoPane.getPrefHeight();
        });
        resizeHandle.setOnMouseDragged(e -> {
            double newH = Math.max(INFO_PANE_MIN_HEIGHT,
                    Math.min(INFO_PANE_MAX_HEIGHT,
                            dragStartInfoHeight - (e.getScreenY() - dragStartY)));
            infoPane.setPrefHeight(newH);
        });

        savePause.setOnFinished(e -> {
            try { serializer.save(seedBank); } catch (Exception ignored) {}
        });

        getChildren().addAll(header, table, buttons, resizeHandle, infoPane);
    }

    private void showPlaceholder(VBox pane) {
        pane.getChildren().clear();
        Label ph = new Label("Select a plant to view details.");
        ph.setStyle("-fx-text-fill: #666; -fx-font-size: 11; -fx-font-style: italic;");
        ph.setWrapText(true);
        pane.getChildren().add(ph);
    }

    private void updateInfoPane(VBox pane, SeedEntry e) {
        pane.getChildren().clear();

        Label title = new Label("Plant Info");
        title.setFont(Font.font(null, FontWeight.BOLD, 11));
        title.setStyle("-fx-text-fill: #aaa;");
        pane.getChildren().add(title);

        pane.getChildren().add(infoLabel("Type:     " + e.plantType()));
        pane.getChildren().add(infoLabel("Name:     " + e.plantName()));
        pane.getChildren().add(infoLabel("Width:    " + e.widthIn() + " in"));
        pane.getChildren().add(infoLabel("Kind:     " + (e.isStrict() ? "Strict" : "Loose")));
        String qtyText = e.quantity() <= 0 ? "0  \u26a0 out of stock" : String.valueOf(e.quantity());
        Label qtyLabel = infoLabel("In stock: " + qtyText);
        if (e.quantity() <= 0) qtyLabel.setStyle("-fx-text-fill: #e05555; -fx-font-size: 11;");
        pane.getChildren().add(qtyLabel);
        if (e.notes() != null && !e.notes().isBlank()) {
            pane.getChildren().add(infoLabel("Notes:  " + e.notes()));
        }
    }

    private Label infoLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private void triggerAddSelected(TableView<SeedEntry> table) {
        SeedEntry sel = table.getSelectionModel().getSelectedItem();
        if (sel != null && onAddSeed != null) onAddSeed.accept(sel);
    }

    private void replaceAll(SeedEntry old, String type, String name, int widthIn, boolean isStrict) {
        int idx = seedBank.observableEntries().indexOf(old);
        if (idx < 0) return;
        SeedEntry updated = new SeedEntry(
                old.id(), old.zone(), type, name,
                widthIn, widthIn, isStrict, old.notes(), old.quantity());
        seedBank.observableEntries().set(idx, updated);
        triggerAutoSave();
    }

    private void triggerAutoSave() {
        savePause.playFromStart();
    }
}
