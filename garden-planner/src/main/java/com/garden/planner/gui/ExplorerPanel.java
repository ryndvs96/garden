package com.garden.planner.gui;

import com.garden.planner.project.BedConfig;
import com.garden.planner.project.GardenProject;
import com.garden.planner.project.GardenZone;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * TreeView sidebar panel showing garden zones (folders) and beds (leaves).
 * Double-click a bed to open it; right-click for context menus.
 */
public class ExplorerPanel extends VBox {

    private final TreeView<Object> treeView = new TreeView<>();
    private Consumer<BedConfig> onOpenBed;
    private Consumer<String>    onAddBed;
    private Runnable            onAddZone;
    private Consumer<BedConfig>    onRenameBed;
    private Consumer<BedConfig>    onDeleteBed;
    private Consumer<BedConfig>    onDuplicateBed;
    private Consumer<GardenZone>   onRenameZone;
    private Consumer<GardenZone>   onDeleteZone;
    private Consumer<GardenZone>   onDuplicateZone;

    /** Zone IDs that were expanded before the last setProject() call. */
    private final Set<String> expandedZoneIds = new HashSet<>();

    @SuppressWarnings("this-escape")
    public ExplorerPanel() {
        setStyle("-fx-background-color: #252526;");

        Label header = new Label("EXPLORER");
        header.setStyle("-fx-text-fill: #bbb; -fx-font-size: 10; -fx-padding: 8 8 4 8; -fx-font-weight: bold;");

        treeView.setStyle("-fx-background-color: #252526; -fx-border-color: transparent;");
        treeView.setShowRoot(false);
        treeView.setRoot(new TreeItem<>("root"));
        VBox.setVgrow(treeView, Priority.ALWAYS);

        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setContextMenu(null);
                    setStyle("-fx-background-color: transparent; -fx-text-fill: #ccc;");
                } else if (item instanceof GardenZone zone) {
                    setText("\uD83D\uDCC2 " + zone.name());
                    setStyle("-fx-background-color: transparent; -fx-text-fill: #ccc; -fx-font-weight: bold;");
                    setContextMenu(buildZoneMenu(zone));
                } else if (item instanceof BedConfig bed) {
                    setText("   \uD83C\uDF3F " + bed.displayName());
                    setStyle("-fx-background-color: transparent; -fx-text-fill: #ccc; -fx-font-weight: normal;");
                    setContextMenu(buildBedMenu(bed));
                }
            }
        });

        treeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<Object> sel = treeView.getSelectionModel().getSelectedItem();
                if (sel != null && sel.getValue() instanceof BedConfig bed) {
                    if (onOpenBed != null) onOpenBed.accept(bed);
                }
            }
        });

        // Context menu on empty area → New Zone
        ContextMenu emptyMenu = new ContextMenu();
        MenuItem newZoneItem = new MenuItem("New Zone...");
        newZoneItem.setOnAction(e -> { if (onAddZone != null) onAddZone.run(); });
        emptyMenu.getItems().add(newZoneItem);
        treeView.setContextMenu(emptyMenu);

        getChildren().addAll(header, treeView);
    }

    /** Rebuild the tree from the given project; preserves expanded-zone state. */
    public void setProject(GardenProject project) {
        // Snapshot which zones are currently expanded
        expandedZoneIds.clear();
        for (TreeItem<Object> item : treeView.getRoot().getChildren()) {
            if (item.getValue() instanceof GardenZone zone && item.isExpanded()) {
                expandedZoneIds.add(zone.id());
            }
        }

        TreeItem<Object> root = new TreeItem<>("root");
        if (project != null) {
            boolean firstTime = expandedZoneIds.isEmpty();
            for (GardenZone zone : project.getZones()) {
                TreeItem<Object> zoneItem = new TreeItem<>(zone);
                // Expand by default on first load; restore state on refresh
                zoneItem.setExpanded(firstTime || expandedZoneIds.contains(zone.id()));
                for (BedConfig bed : zone.beds()) {
                    zoneItem.getChildren().add(new TreeItem<>(bed));
                }
                root.getChildren().add(zoneItem);
            }
        }
        treeView.setRoot(root);
    }

    // -------------------------------------------------------------------------
    // Context menus
    // -------------------------------------------------------------------------

    private ContextMenu buildZoneMenu(GardenZone zone) {
        ContextMenu menu = new ContextMenu();
        MenuItem addBedItem      = new MenuItem("Add Bed...");
        MenuItem duplicateItem   = new MenuItem("Duplicate Zone...");
        MenuItem renameItem      = new MenuItem("Rename Zone...");
        MenuItem deleteItem      = new MenuItem("Delete Zone");
        addBedItem.setOnAction(e ->    { if (onAddBed       != null) onAddBed.accept(zone.id()); });
        duplicateItem.setOnAction(e -> { if (onDuplicateZone != null) onDuplicateZone.accept(zone); });
        renameItem.setOnAction(e ->    { if (onRenameZone   != null) onRenameZone.accept(zone); });
        deleteItem.setOnAction(e ->    { if (onDeleteZone   != null) onDeleteZone.accept(zone); });
        menu.getItems().addAll(addBedItem, duplicateItem, new SeparatorMenuItem(), renameItem, deleteItem);
        return menu;
    }

    private ContextMenu buildBedMenu(BedConfig bed) {
        ContextMenu menu = new ContextMenu();
        MenuItem openItem      = new MenuItem("Open");
        MenuItem duplicateItem = new MenuItem("Duplicate Bed...");
        MenuItem renameItem    = new MenuItem("Rename...");
        MenuItem deleteItem    = new MenuItem("Delete from Project");
        openItem.setOnAction(e ->      { if (onOpenBed      != null) onOpenBed.accept(bed); });
        duplicateItem.setOnAction(e -> { if (onDuplicateBed != null) onDuplicateBed.accept(bed); });
        renameItem.setOnAction(e ->    { if (onRenameBed    != null) onRenameBed.accept(bed); });
        deleteItem.setOnAction(e ->    { if (onDeleteBed    != null) onDeleteBed.accept(bed); });
        menu.getItems().addAll(openItem, duplicateItem, new SeparatorMenuItem(), renameItem, deleteItem);
        return menu;
    }

    // -------------------------------------------------------------------------
    // Callback setters
    // -------------------------------------------------------------------------

    public void setOnOpenBed(Consumer<BedConfig> cb)    { onOpenBed   = cb; }
    public void setOnAddBed(Consumer<String> cb)        { onAddBed    = cb; }
    public void setOnAddZone(Runnable cb)               { onAddZone   = cb; }
    public void setOnRenameBed(Consumer<BedConfig> cb)      { onRenameBed    = cb; }
    public void setOnDeleteBed(Consumer<BedConfig> cb)      { onDeleteBed    = cb; }
    public void setOnDuplicateBed(Consumer<BedConfig> cb)   { onDuplicateBed = cb; }
    public void setOnRenameZone(Consumer<GardenZone> cb)    { onRenameZone   = cb; }
    public void setOnDeleteZone(Consumer<GardenZone> cb)    { onDeleteZone   = cb; }
    public void setOnDuplicateZone(Consumer<GardenZone> cb) { onDuplicateZone = cb; }
}
