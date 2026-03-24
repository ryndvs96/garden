package com.garden.planner.gui;

import com.garden.planner.project.BedConfig;
import com.garden.planner.project.GardenProject;
import com.garden.planner.project.GardenZone;
import com.garden.planner.project.SeedBank;
import com.garden.planner.project.SeedEntry;
import javafx.scene.Cursor;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;

/**
 * Holds both the ExplorerPanel and SeedBankController in a StackPane.
 * ActivityBar drives which panel is visible; neither is torn down on switch.
 * A drag handle on the right edge allows resizing the sidebar width.
 */
public class SidebarController extends BorderPane {

    private final ExplorerPanel explorerPanel;
    private final SeedBankController seedBankController;

    private double dragStartX;
    private double dragStartWidth;

    @SuppressWarnings("this-escape")
    public SidebarController(SeedBank seedBank) {
        setStyle("-fx-background-color: #252526;");
        setMinWidth(220);
        setPrefWidth(220);

        explorerPanel        = new ExplorerPanel();
        seedBankController   = new SeedBankController(seedBank);

        StackPane stack = new StackPane(explorerPanel, seedBankController);
        seedBankController.setVisible(false);
        setCenter(stack);

        // Drag handle on right edge
        Region handle = new Region();
        handle.setPrefWidth(5);
        handle.setMinWidth(5);
        handle.setMaxWidth(5);
        handle.setStyle("-fx-background-color: #3a3a3a;");
        handle.setCursor(Cursor.H_RESIZE);
        handle.setOnMousePressed(e -> {
            dragStartX     = e.getScreenX();
            dragStartWidth = getWidth();
        });
        handle.setOnMouseDragged(e -> {
            double newW = Math.max(160, dragStartWidth + (e.getScreenX() - dragStartX));
            setPrefWidth(newW);
            setMinWidth(newW);
        });
        setRight(handle);
    }

    public void showView(ActivityBar.ActivityView view) {
        boolean explorer = (view == ActivityBar.ActivityView.EXPLORER);
        explorerPanel.setVisible(explorer);
        seedBankController.setVisible(!explorer);
    }

    public void setProject(GardenProject project) {
        explorerPanel.setProject(project);
    }

    public void setOnAddSeed(Consumer<SeedEntry> cb) {
        seedBankController.setOnAddSeed(cb);
    }

    // --- delegate callbacks to ExplorerPanel ---

    public void setOnOpenBed(Consumer<BedConfig> cb)     { explorerPanel.setOnOpenBed(cb); }
    public void setOnAddBed(Consumer<String> cb)         { explorerPanel.setOnAddBed(cb); }
    public void setOnAddZone(Runnable cb)                { explorerPanel.setOnAddZone(cb); }
    public void setOnRenameBed(Consumer<BedConfig> cb)   { explorerPanel.setOnRenameBed(cb); }
    public void setOnDeleteBed(Consumer<BedConfig> cb)   { explorerPanel.setOnDeleteBed(cb); }
    public void setOnRenameZone(Consumer<GardenZone> cb) { explorerPanel.setOnRenameZone(cb); }
    public void setOnDeleteZone(Consumer<GardenZone> cb) { explorerPanel.setOnDeleteZone(cb); }
}
