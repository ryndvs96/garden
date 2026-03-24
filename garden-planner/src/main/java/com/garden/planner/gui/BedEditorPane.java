package com.garden.planner.gui;

import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.model.*;
import com.garden.planner.core.search.*;
import com.garden.planner.data.StateSerializer;
import com.garden.planner.project.BedConfig;
import com.garden.planner.project.SeedBank;
import com.garden.planner.project.SeedEntry;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Single-bed editor pane. Refactored from AppController:
 * - Constructor takes (stage, BedConfig, bedsDir, seedBank)
 * - doSave() writes to bedsDir/config.fileName() — no file chooser
 * - Fires onDirtyChanged instead of updating the stage title
 * - No Save/Save As toolbar buttons (handled by MainController's menu bar)
 */
public class BedEditorPane extends BorderPane {

    private final BedCanvas canvas;
    private final StatsPanelController statsPanel;
    private final SearchOverlayController searchOverlay;
    private PlacementState state;
    private final BedConfig config;
    private final Path bedsDir;
    private final SeedBank seedBank;
    private final StateSerializer serializer = new StateSerializer();
    private final Stage stage;
    @SuppressWarnings("unused")
    private SearchMetrics lastMetrics;
    private Button editBtn;
    private CheckBox lockCheck;
    private Label selectionHeader;
    private Label statusBar;
    private ScrollPane scrollPane;
    private boolean dirty = false;
    private Consumer<Boolean> onDirtyChanged;

    // Undo stack
    private static final int MAX_UNDO = 50;
    private final ArrayDeque<PlacementState> undoStack = new ArrayDeque<>();

    // Move-undo anchor: snapshot taken when a plant is first selected so that
    // deselecting after N moves pushes the pre-selection state as one undo entry.
    private PlacementState moveAnchorState = null;
    private int moveAnchorInstanceIdx = -1;

    /** Fired after a species edit so MainController can propagate to seedbank + other beds. */
    @FunctionalInterface
    public interface SpeciesEditListener {
        void onEdit(String oldType, String oldName, PlantEditorDialog.PlantFormData data);
    }
    private SpeciesEditListener onSpeciesEdited;
    public void setOnSpeciesEdited(SpeciesEditListener listener) { this.onSpeciesEdited = listener; }

    private static final String STATUS_IDLE =
            "Click or Tab: select  \u2022  \u2190\u2191\u2192\u2193: move  \u2022  Enter: properties  \u2022  L: lock  \u2022  Del: remove  \u2022  Esc: deselect  \u2022  Ctrl+S: save";

    @SuppressWarnings("this-escape")
    public BedEditorPane(Stage stage, BedConfig config, Path bedsDir, SeedBank seedBank) {
        this.stage = stage;
        this.config = config;
        this.bedsDir = bedsDir;
        this.seedBank = seedBank;

        canvas = new BedCanvas(900, 300);
        scrollPane = new ScrollPane(canvas);
        scrollPane.setStyle("-fx-background-color: #e8e4d8;");
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);

        statsPanel = new StatsPanelController();
        searchOverlay = new SearchOverlayController();

        selectionHeader = new Label("");
        selectionHeader.setFont(Font.font(null, FontWeight.BOLD, 15));
        selectionHeader.setStyle("-fx-padding: 4 10 2 10; -fx-text-fill: #222;");
        selectionHeader.setMaxWidth(Double.MAX_VALUE);
        selectionHeader.setAlignment(Pos.CENTER);
        selectionHeader.setManaged(false);
        selectionHeader.setVisible(false);

        Button regenBtn    = new Button("Regenerate");
        editBtn            = new Button("Properties");
        editBtn.setDisable(true);
        lockCheck = new CheckBox("Locked");
        lockCheck.setDisable(true);
        Button zoomInBtn  = new Button("+");
        Button zoomOutBtn = new Button("\u2013");

        Button flowerFillBtn = new Button("Flower Fill");
        Button undoBtn       = new Button("Undo");

        regenBtn.setOnAction(e -> doRegenerate());
        flowerFillBtn.setOnAction(e -> doFlowerFill());
        undoBtn.setOnAction(e -> doUndo());
        editBtn.setOnAction(e -> doEditPlant());
        zoomInBtn.setOnAction(e -> canvas.zoom(1.25));
        zoomOutBtn.setOnAction(e -> canvas.zoom(0.8));
        lockCheck.setOnAction(e -> {
            int idx = canvas.getSelectedIdx();
            if (state != null && idx >= 0 && idx < state.getPlaced().size()) {
                toggleLock(idx);
            }
        });

        HBox toolbar = new HBox(8, regenBtn, flowerFillBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                undoBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                editBtn, lockCheck,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                zoomOutBtn, zoomInBtn);
        toolbar.setPadding(new Insets(6, 10, 6, 10));
        toolbar.setStyle("-fx-background-color: #d8d4c8;");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        statusBar = new Label(STATUS_IDLE);
        statusBar.setPadding(new Insets(3, 10, 3, 10));
        statusBar.setStyle("-fx-background-color: #c8c4b8; -fx-font-size: 11;");
        statusBar.setMaxWidth(Double.MAX_VALUE);

        VBox bottomBox = new VBox(toolbar, statusBar);

        StackPane centerStack = new StackPane(scrollPane, searchOverlay);
        VBox centerBox = new VBox(selectionHeader, centerStack);
        VBox.setVgrow(centerStack, Priority.ALWAYS);

        // Drag handle — grab left edge of stats panel to resize it
        Region resizeHandle = new Region();
        resizeHandle.setPrefWidth(5);
        resizeHandle.setMinWidth(5);
        resizeHandle.setMaxWidth(5);
        resizeHandle.setStyle("-fx-background-color: #bbba9a; -fx-cursor: h-resize;");

        final double[] resizeDrag = {0, 0};
        resizeHandle.setOnMousePressed(e -> {
            resizeDrag[0] = e.getScreenX();
            resizeDrag[1] = statsPanel.getPrefWidth();
        });
        resizeHandle.setOnMouseDragged(e -> {
            double delta = resizeDrag[0] - e.getScreenX();
            double newWidth = Math.max(160, resizeDrag[1] + delta);
            statsPanel.setPrefWidth(newWidth);
            statsPanel.setMinWidth(newWidth);
        });

        HBox statsWrapper = new HBox(resizeHandle, statsPanel);
        HBox.setHgrow(statsPanel, Priority.ALWAYS);

        setCenter(centerBox);
        setRight(statsWrapper);
        setBottom(bottomBox);

        // Ctrl+scroll to zoom
        scrollPane.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isControlDown()) {
                canvas.zoom(e.getDeltaY() > 0 ? 1.1 : 0.9);
                e.consume();
            }
        });

        // Click-and-drag panning
        final double[] dragStart = new double[2];
        scrollPane.setOnMousePressed(e -> { dragStart[0] = e.getX(); dragStart[1] = e.getY(); });
        scrollPane.setOnMouseDragged(e -> {
            double dx = e.getX() - dragStart[0];
            double dy = e.getY() - dragStart[1];
            scrollPane.setHvalue(scrollPane.getHvalue() - dx / canvas.getWidth());
            scrollPane.setVvalue(scrollPane.getVvalue() - dy / canvas.getHeight());
            dragStart[0] = e.getX();
            dragStart[1] = e.getY();
        });

        // Event filter — must intercept before ScrollPane consumes arrow keys
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (state == null) return;
            KeyCode code = event.getCode();

            if (event.isControlDown() && code == KeyCode.S) {
                doSave();
                event.consume();
                return;
            }

            if (event.isShortcutDown() && code == KeyCode.Z) {
                doUndo();
                event.consume();
                return;
            }

            int sel = canvas.getSelectedIdx();

            if (code == KeyCode.ESCAPE) {
                canvas.setSelectedIdx(-1);
                selectPlant(-1);
                event.consume();
                return;
            }

            if (code == KeyCode.TAB && !state.getPlaced().isEmpty()) {
                List<Integer> order = tabOrder();
                int current = canvas.getSelectedIdx();
                int pos = order.indexOf(current);
                int next;
                if (event.isShiftDown()) {
                    next = order.get(pos <= 0 ? order.size() - 1 : pos - 1);
                } else {
                    next = order.get(pos < 0 ? 0 : (pos + 1) % order.size());
                }
                canvas.setSelectedIdx(next);
                statsPanel.update(state);
                selectPlant(next);
                scrollToPlant(next);
                event.consume();
                return;
            }

            if (sel >= 0 && sel < state.getPlaced().size()) {
                int dr = 0, dc = 0;
                boolean isMove = false;
                if      (code == KeyCode.UP)    { dr = -1; isMove = true; }
                else if (code == KeyCode.DOWN)  { dr =  1; isMove = true; }
                else if (code == KeyCode.LEFT)  { dc = -1; isMove = true; }
                else if (code == KeyCode.RIGHT) { dc =  1; isMove = true; }

                if (isMove) {
                    moveSelected(sel, dr, dc);
                    event.consume();
                } else if (code == KeyCode.D) {
                    duplicatePlant(sel);
                    event.consume();
                } else if (code == KeyCode.ENTER) {
                    doEditPlant();
                    event.consume();
                } else if (code == KeyCode.L) {
                    toggleLock(sel);
                    event.consume();
                } else if (code == KeyCode.DELETE || code == KeyCode.BACK_SPACE) {
                    deletePlant(sel);
                    event.consume();
                }
            }
        });

        canvas.setOnMouseClicked(event -> {
            int idx = canvas.hitTest(event.getX(), event.getY());
            canvas.setSelectedIdx(idx);
            if (state != null) {
                statsPanel.update(state);
                selectPlant(idx);
            }
            requestFocus();
        });

        Tooltip hoverTip = new Tooltip();
        hoverTip.setStyle("-fx-font-size: 13;");
        canvas.setOnMouseMoved(event -> {
            int idx = canvas.hitTest(event.getX(), event.getY());
            if (state != null && idx >= 0 && idx < state.getPlaced().size()) {
                PlacedPlant pp = state.getPlaced().get(idx);
                hoverTip.setText(pp.plant().plantType() + "  \u2014  " + pp.plant().plantName());
                hoverTip.show(canvas, event.getScreenX() + 14, event.getScreenY() + 14);
            } else {
                hoverTip.hide();
            }
        });
        canvas.setOnMouseExited(event -> hoverTip.hide());

        // DnD: accept drops from seed bank sidebar
        canvas.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        canvas.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean ok = false;
            if (db.hasString() && state != null) {
                SeedEntry entry = SeedBankController.DRAG_REGISTRY.remove(db.getString());
                if (entry != null) {
                    PlantInstance plant = seedEntryToInstance(entry, nextInstanceIdx());
                    int[] rc = canvas.xyToCell(e.getX(), e.getY());
                    // Try drop position first, then scan for first valid placement
                    PlacedPlant placed = LocalSearchEngine.makePlacedPlant(
                            plant, rc[0], rc[1], state.getGridRows(), state.getGridCols());
                    if (placed == null) placed = findFirstValidPlacement(plant);
                    if (placed != null) placed = placed.withLocked(true);
                    commitPlacement(plant, placed);
                    ok = true;
                }
            }
            e.setDropCompleted(ok);
            e.consume();
        });

        setFocusTraversable(true);
        loadFromFile();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setOnDirtyChanged(Consumer<Boolean> callback) {
        this.onDirtyChanged = callback;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void doSave() {
        if (state == null) return;
        File file = bedsDir.resolve(config.fileName()).toFile();
        try {
            file.getParentFile().mkdirs();
            serializer.save(state, config.displayName(), file);
            setDirty(false);
            selectPlant(canvas.getSelectedIdx()); // refresh status bar dirty suffix
        } catch (Exception e) {
            showError("Save failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void loadFromFile() {
        File file = bedsDir.resolve(config.fileName()).toFile();
        if (file.exists()) {
            try {
                state = serializer.load(file);
            } catch (Exception e) {
                showError("Failed to load bed \"" + config.displayName() + "\": " + e.getMessage());
                state = emptyState();
            }
        } else {
            state = emptyState();
        }
        canvas.setState(state);
        statsPanel.update(state);
        selectPlant(-1);
    }

    private PlacementState emptyState() {
        return new PlacementState(List.of(), List.of(),
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS, PenaltyMode.CELL);
    }

    private void setDirty(boolean d) {
        this.dirty = d;
        if (onDirtyChanged != null) onDirtyChanged.accept(d);
    }

    private void pushUndo() {
        if (state == null) return;
        if (undoStack.size() >= MAX_UNDO) undoStack.removeLast();
        undoStack.push(state.snapshot());
    }

    private void doUndo() {
        if (undoStack.isEmpty()) return;
        moveAnchorState = null;
        moveAnchorInstanceIdx = -1;
        state = undoStack.pop();
        setDirty(true);
        canvas.setState(state);
        statsPanel.update(state);
        statsPanel.updateSearch(null);
        selectPlant(-1);
    }

    /** Central method to update all selection-dependent UI from a plant index. */
    private void selectPlant(int idx) {
        // Determine the instanceIdx of the newly selected plant (-1 if deselecting)
        int newInstanceIdx = (idx >= 0 && state != null && idx < state.getPlaced().size())
                ? state.getPlaced().get(idx).plant().instanceIdx()
                : -1;

        // Flush move-anchor only when genuinely switching to a different plant or deselecting.
        // If the same plant is re-selected (e.g. after an arrow-key move), keep the anchor intact.
        boolean switchingPlant = (newInstanceIdx != moveAnchorInstanceIdx);
        if (switchingPlant && moveAnchorState != null && state != null) {
            // Find the current position of the anchored plant
            PlacedPlant current = state.getPlaced().stream()
                    .filter(p -> p.plant().instanceIdx() == moveAnchorInstanceIdx)
                    .findFirst().orElse(null);
            if (current != null) {
                PlacedPlant anchor = moveAnchorState.getPlaced().stream()
                        .filter(p -> p.plant().instanceIdx() == moveAnchorInstanceIdx)
                        .findFirst().orElse(null);
                if (anchor != null
                        && (anchor.row() != current.row() || anchor.col() != current.col())) {
                    if (undoStack.size() >= MAX_UNDO) undoStack.removeLast();
                    undoStack.push(moveAnchorState);
                }
            }
            moveAnchorState = null;
            moveAnchorInstanceIdx = -1;
        }

        // Set new move anchor when selecting a plant (only if switching plants)
        if (switchingPlant) {
            if (newInstanceIdx >= 0) {
                moveAnchorState = state.snapshot();
                moveAnchorInstanceIdx = newInstanceIdx;
            } else {
                moveAnchorState = null;
                moveAnchorInstanceIdx = -1;
            }
        }

        boolean hasSel = state != null && idx >= 0 && idx < state.getPlaced().size();
        PlacedPlant pp = hasSel ? state.getPlaced().get(idx) : null;

        statsPanel.updateSelected(pp);

        if (pp != null) {
            String lockTag = pp.locked() ? "  \uD83D\uDD12" : "";
            selectionHeader.setText(pp.plant().plantType() + "  \u2013  " + pp.plant().plantName() + lockTag);
            selectionHeader.setManaged(true);
            selectionHeader.setVisible(true);
        } else {
            selectionHeader.setManaged(false);
            selectionHeader.setVisible(false);
        }

        editBtn.setDisable(!hasSel);
        lockCheck.setDisable(!hasSel);
        if (hasSel) lockCheck.setSelected(pp.locked());

        String dirtySuffix = dirty ? "  \u2014  \u26a0 unsaved changes" : "";
        if (pp != null) {
            statusBar.setText("zone: " + pp.plant().zone()
                    + "  |  " + pp.plant().widthIn() + "in"
                    + "  |  " + (pp.plant().isStrict() ? "strict" : "loose")
                    + (pp.locked() ? "  |  \uD83D\uDD12 locked" : "")
                    + dirtySuffix);
        } else {
            statusBar.setText(STATUS_IDLE + dirtySuffix);
        }
    }

    private void doRegenerate() {
        if (state == null) return;
        pushUndo();

        List<PlacedPlant> lockedPlants  = new ArrayList<>();
        List<PlacedPlant> unlockedPlaced = new ArrayList<>();
        List<PlantInstance> allPlants    = new ArrayList<>();
        for (PlacedPlant pp : state.getPlaced()) {
            if (pp.locked()) {
                lockedPlants.add(pp);
            } else {
                unlockedPlaced.add(pp);
                allPlants.add(pp.plant());
            }
        }

        // Use the undo snapshot's score as the baseline — it was computed via fullScore()
        // in the PlacementState constructor, so it has no incremental drift.
        double previousScore = undoStack.peek().getScore();

        SearchConfig config = SearchConfig.defaults()
                .gridRows(state.getGridRows())
                .gridCols(state.getGridCols())
                .fixedPlants(lockedPlants)
                .warmStart(unlockedPlaced)
                .build();

        runSearch(allPlants, config, previousScore);
    }

    private void runSearch(List<PlantInstance> allPlants, SearchConfig config, double previousScore) {
        SearchMetrics metrics = new SearchMetrics();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        searchOverlay.start(metrics, cancelled, () -> searchOverlay.stop());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            SearchEngine engine = new LocalSearchEngine();
            SearchResult result = engine.search(allPlants, config, metrics, cancelled);
            Platform.runLater(() -> {
                lastMetrics = metrics;

                // Locked plants are now included in the result (they were fixed constraints).
                PlacementState candidate = result.state();
                double candidateScore = candidate.snapshot().getScore();

                if (candidateScore > previousScore) {
                    state = candidate;
                    setDirty(true);
                    canvas.setState(state);
                    statsPanel.update(state);
                    statsPanel.updateSearch(metrics);
                    selectPlant(-1);
                    searchOverlay.showImproved(previousScore, candidateScore);
                } else {
                    // No improvement — discard result and pop the undo snapshot
                    undoStack.poll();
                    selectPlant(-1);
                    searchOverlay.showNoImprovement(
                            () -> runSearch(allPlants, config.withExtraTimeMs(30_000L), previousScore));
                }
            });
        });
        executor.shutdown();
    }

    private void addPlantsNormal(List<PlantInstance> plants) {
        pushUndo();
        int nextCol = 0;
        int lastIdx = -1;
        int failed = 0;
        for (PlantInstance plant : plants) {
            PlacedPlant placed = findFirstValidPlacementFrom(plant, nextCol);
            if (placed == null) placed = findFirstValidPlacement(plant);
            if (placed != null) {
                state.addPlant(placed.withLocked(true));
                lastIdx = state.getPlaced().size() - 1;
                nextCol = placed.col() + 3;
                if (nextCol >= state.getGridCols()) nextCol = 0;
            } else {
                failed++;
            }
        }
        setDirty(true);
        statsPanel.update(state);
        canvas.setSelectedIdx(lastIdx);
        selectPlant(lastIdx);
        canvas.redraw();
        if (lastIdx >= 0) scrollToPlant(lastIdx);
        if (failed > 0)
            showError(failed + " plant(s) could not be placed — the bed is full.");
    }

    private void addPlantsBestScore(List<PlantInstance> plants) {
        pushUndo();
        // Temporarily lock all existing placed plants; remember which were unlocked
        List<Integer> originallyUnlocked = new ArrayList<>();
        for (int i = 0; i < state.getPlaced().size(); i++) {
            if (!state.getPlaced().get(i).locked()) {
                originallyUnlocked.add(i);
                state.getPlaced().set(i, state.getPlaced().get(i).withLocked(true));
            }
        }

        int lastIdx = -1;
        int failed = 0;
        for (PlantInstance plant : plants) {
            PlacedPlant best = findBestScorePlacement(plant);
            if (best != null) {
                state.addPlant(best);  // NOT locked — optimizer chose position
                lastIdx = state.getPlaced().size() - 1;
            } else {
                failed++;
            }
        }

        // Restore unlock state (indices stable — we only appended)
        for (int i : originallyUnlocked)
            state.getPlaced().set(i, state.getPlaced().get(i).withLocked(false));

        setDirty(true);
        statsPanel.update(state);
        canvas.setSelectedIdx(lastIdx);
        selectPlant(lastIdx);
        canvas.redraw();
        if (lastIdx >= 0) scrollToPlant(lastIdx);
        if (failed > 0)
            showError(failed + " plant(s) could not be placed — the bed is full.");
    }

    private void doFlowerFill() {
        if (state == null) return;
        pushUndo();

        List<SeedEntry> flowerSeeds = seedBank.observableEntries().stream()
                .filter(e -> e.plantType().toLowerCase().startsWith("flower"))
                .toList();
        if (flowerSeeds.isEmpty()) {
            showError("No flower plants in seed bank.");
            return;
        }

        // Track which plants were unlocked so we can restore them afterward
        List<Integer> originallyUnlocked = new ArrayList<>();
        for (int i = 0; i < state.getPlaced().size(); i++) {
            if (!state.getPlaced().get(i).locked()) originallyUnlocked.add(i);
        }

        // Snapshot with all existing plants locked — background thread works on this
        List<PlacedPlant> lockedList = state.getPlaced().stream()
                .map(pp -> pp.withLocked(true))
                .toList();
        PlacementState snapshot = new PlacementState(
                lockedList, List.of(),
                state.getGridRows(), state.getGridCols(), state.getPenaltyMode());

        int instanceBase = nextInstanceIdx();
        SearchMetrics metrics = new SearchMetrics();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicInteger plantsPlaced = new java.util.concurrent.atomic.AtomicInteger(0);

        searchOverlay.start(cancelled, () -> { cancelled.set(true); searchOverlay.stop(); },
                () -> String.format("Filling flowers... %d placed  \u2022  %.0f states/sec  \u2022  %.1fs",
                        plantsPlaced.get(), metrics.statesPerSecond(), metrics.elapsedSeconds()));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            int lastIdx = -1;
            int rows = snapshot.getGridRows(), cols = snapshot.getGridCols();
            long deadline = System.currentTimeMillis() + 30_000L;

            while (!cancelled.get() && System.currentTimeMillis() < deadline) {
                // Pass 1: non-overlapping positions only
                PlacedPlant bestPp = null;
                double bestDelta = Double.NEGATIVE_INFINITY;
                for (SeedEntry flowerSeed : flowerSeeds) {
                    PlantInstance candidate = seedEntryToInstance(flowerSeed, instanceBase + plantsPlaced.get());
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            if (snapshot.isCenterOccupied(r, c)) continue;
                            PlacedPlant pp = LocalSearchEngine.makePlacedPlant(candidate, r, c, rows, cols);
                            if (pp == null || snapshot.hasAnyCellOverlap(pp)) continue;
                            metrics.recordState();
                            double delta = snapshot.addDelta(pp);
                            if (delta > bestDelta) { bestDelta = delta; bestPp = pp; }
                        }
                    }
                }

                // Pass 2: allow overlapping only if no non-overlapping option was found
                if (bestPp == null) {
                    for (SeedEntry flowerSeed : flowerSeeds) {
                        PlantInstance candidate = seedEntryToInstance(flowerSeed, instanceBase + plantsPlaced.get());
                        for (int r = 0; r < rows; r++) {
                            for (int c = 0; c < cols; c++) {
                                if (snapshot.isCenterOccupied(r, c)) continue;
                                PlacedPlant pp = LocalSearchEngine.makePlacedPlant(candidate, r, c, rows, cols);
                                if (pp == null) continue;
                                metrics.recordState();
                                double delta = snapshot.addDelta(pp);
                                if (delta > bestDelta) { bestDelta = delta; bestPp = pp; }
                            }
                        }
                    }
                }

                if (bestPp == null || bestDelta <= PlacementState.W_N_PLACED) break;  // no unique species can be placed

                snapshot.addPlant(bestPp);  // NOT locked
                metrics.updateBest(snapshot.getScore());
                lastIdx = snapshot.getPlaced().size() - 1;
                plantsPlaced.incrementAndGet();
            }

            final int finalPlaced = plantsPlaced.get();
            final int finalLastIdx = lastIdx;
            Platform.runLater(() -> {
                searchOverlay.stop();
                // Restore unlock state (indices stable — we only appended)
                for (int i : originallyUnlocked)
                    snapshot.getPlaced().set(i, snapshot.getPlaced().get(i).withLocked(false));
                state = snapshot;
                setDirty(true);
                canvas.setState(state);
                statsPanel.update(state);
                selectPlant(-1);
                if (finalPlaced == 0)
                    showError("No space left in bed for flowers.");
            });
        });
        executor.shutdown();
    }

    private PlacedPlant findBestScorePlacement(PlantInstance plant) {
        int rows = state.getGridRows(), cols = state.getGridCols();
        PlacedPlant best = null;
        double bestDelta = Double.NEGATIVE_INFINITY;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (state.isCenterOccupied(r, c)) continue;
                PlacedPlant candidate = LocalSearchEngine.makePlacedPlant(plant, r, c, rows, cols);
                if (candidate == null) continue;
                double delta = state.addDelta(candidate);
                if (best == null || delta > bestDelta) {
                    bestDelta = delta;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private PlacedPlant findFirstValidPlacementFrom(PlantInstance plant, int startCol) {
        int rows = state.getGridRows(), cols = state.getGridCols();
        for (int c = startCol; c < cols; c++)
            for (int r = 0; r < rows; r++) {
                if (state.isCenterOccupied(r, c)) continue;
                PlacedPlant pp = LocalSearchEngine.makePlacedPlant(plant, r, c, rows, cols);
                if (pp != null) return pp;
            }
        return null;
    }

    private void doEditPlant() {
        if (state == null) return;
        int idx = canvas.getSelectedIdx();
        if (idx < 0 || idx >= state.getPlaced().size()) return;
        PlacedPlant pp = state.getPlaced().get(idx);
        String oldType = pp.plant().plantType();
        String oldName = pp.plant().plantName();
        new PlantEditorDialog(stage, pp.plant()).show().ifPresent(data -> {
            requestFocus();
            applySpeciesEdit(oldType, oldName, data);
            if (onSpeciesEdited != null) onSpeciesEdited.onEdit(oldType, oldName, data);
        });
    }

    /**
     * Updates every plant of the given species in this bed to the new properties.
     * Called for the current bed after a properties edit, and by MainController for
     * all other open beds when a species edit propagates.
     */
    public void applySpeciesEdit(String oldType, String oldName, PlantEditorDialog.PlantFormData data) {
        if (state == null) return;
        pushUndo();
        // Remember selected plant's instanceIdx so we can re-select it after re-ordering
        int prevInstanceIdx = -1;
        int prevIdx = canvas.getSelectedIdx();
        if (prevIdx >= 0 && prevIdx < state.getPlaced().size()) {
            prevInstanceIdx = state.getPlaced().get(prevIdx).plant().instanceIdx();
        }

        String newName = data.plantName();
        String code = newName.length() >= 2 ? newName.substring(0, 2).toUpperCase() : newName.toUpperCase();
        boolean changed = false;

        // Iterate in reverse so removePlant(i) doesn't shift unvisited indices
        for (int i = state.getPlaced().size() - 1; i >= 0; i--) {
            PlacedPlant pp = state.getPlaced().get(i);
            if (!pp.plant().plantType().equals(oldType) || !pp.plant().plantName().equals(oldName)) continue;
            PlantInstance newPlant = pp.plant().toBuilder()
                    .plantType(data.plantType()).plantName(newName)
                    .widthIn(data.widthIn()).heightIn(data.widthIn())
                    .isStrict(data.isStrict()).code(code)
                    .build();
            state.removePlant(i);
            PlacedPlant placed = LocalSearchEngine.makePlacedPlant(
                    newPlant, pp.row(), pp.col(), state.getGridRows(), state.getGridCols());
            if (placed != null) state.addPlant(placed.withLocked(pp.locked()));
            changed = true;
        }

        if (changed) {
            // Re-find the previously selected plant by instanceIdx
            int newIdx = -1;
            if (prevInstanceIdx >= 0) {
                for (int i = 0; i < state.getPlaced().size(); i++) {
                    if (state.getPlaced().get(i).plant().instanceIdx() == prevInstanceIdx) {
                        newIdx = i;
                        break;
                    }
                }
            }
            setDirty(true);
            statsPanel.update(state);
            canvas.setSelectedIdx(newIdx);
            selectPlant(newIdx);
            canvas.redraw();
        }
    }

    private int nextInstanceIdx() {
        int max = 0;
        for (PlacedPlant pp : state.getPlaced()) max = Math.max(max, pp.plant().instanceIdx());
        return max + 1;
    }

    private PlantInstance seedEntryToInstance(SeedEntry entry, int idx) {
        String name = entry.plantName();
        String code = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : name.toUpperCase();
        return PlantInstance.builder()
                .zone("Any").plantType(entry.plantType()).plantName(name)
                .widthIn(entry.widthIn()).heightIn(entry.widthIn())
                .isStrict(entry.isStrict()).instanceIdx(idx).code(code)
                .build();
    }

    /** Scan top-left → bottom-right for the first valid placement position. */
    private PlacedPlant findFirstValidPlacement(PlantInstance plant) {
        int rows = state.getGridRows(), cols = state.getGridCols();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (state.isCenterOccupied(r, c)) continue;
                PlacedPlant pp = LocalSearchEngine.makePlacedPlant(plant, r, c, rows, cols);
                if (pp != null) return pp;
            }
        }
        return null;
    }

    /** Scan for the first position where pp's footprint has no cell overlap with any existing plant. */
    private PlacedPlant findFirstNonOverlappingPlacement(PlantInstance plant) {
        int rows = state.getGridRows(), cols = state.getGridCols();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (state.isCenterOccupied(r, c)) continue;
                PlacedPlant pp = LocalSearchEngine.makePlacedPlant(plant, r, c, rows, cols);
                if (pp != null && !state.hasAnyCellOverlap(pp)) return pp;
            }
        }
        return null;
    }

    /**
     * Add a plant from the seed bank to the current bed: unlocked, first non-overlapping
     * position, falling back to first valid position if the bed is full.
     */
    public void addFromSeed(SeedEntry entry) {
        if (state == null) return;
        pushUndo();
        PlantInstance plant = seedEntryToInstance(entry, nextInstanceIdx());
        PlacedPlant placed = findFirstNonOverlappingPlacement(plant);
        if (placed == null) placed = findFirstValidPlacement(plant);
        if (placed != null) {
            state.addPlant(placed.withLocked(false));
            setDirty(true);
            statsPanel.update(state);
            canvas.redraw();
        } else {
            showError("Plant could not be placed — the bed is full.");
        }
    }

    /** Add a plant to state if a valid placement exists, otherwise show an error. */
    private void commitPlacement(PlantInstance plant, PlacedPlant placed) {
        if (placed != null) {
            state.addPlant(placed);
            int newIdx = state.getPlaced().size() - 1;
            setDirty(true);
            statsPanel.update(state);
            canvas.setSelectedIdx(newIdx);
            selectPlant(newIdx);
            canvas.redraw();
            scrollToPlant(newIdx);
        } else {
            showError("No valid placement found — bed may be full.");
        }
    }

    private void moveSelected(int idx, int dr, int dc) {
        PlacedPlant pp = state.getPlaced().get(idx);
        int newRow = pp.row() + dr;
        int newCol = pp.col() + dc;
        if (newRow < 0 || newRow >= state.getGridRows() || newCol < 0 || newCol >= state.getGridCols()) return;
        PlacedPlant moved = LocalSearchEngine.makePlacedPlantClipped(pp.plant(), newRow, newCol,
                state.getGridRows(), state.getGridCols());
        if (moved == null) return;
        state.removePlant(idx);
        state.addPlant(moved.withLocked(true));
        setDirty(true);
        int newIdx = state.getPlaced().size() - 1;
        canvas.setSelectedIdx(newIdx);
        statsPanel.update(state);
        selectPlant(newIdx);
    }

    private void toggleLock(int idx) {
        PlacedPlant pp = state.getPlaced().get(idx);
        PlacedPlant toggled = pp.withLocked(!pp.locked());
        state.getPlaced().set(idx, toggled);
        setDirty(true);
        canvas.redraw();
        selectPlant(idx);
    }

    private void duplicatePlant(int idx) {
        pushUndo();
        PlacedPlant orig = state.getPlaced().get(idx);
        PlantInstance plant = orig.plant();
        PlantInstance newPlant = plant.toBuilder().instanceIdx(nextInstanceIdx()).build();

        // Try one cell to the right; fall back to first valid placement from there, then anywhere
        int rows = state.getGridRows(), cols = state.getGridCols();
        PlacedPlant placed = null;
        int tryCol = orig.col() + 1;
        if (tryCol < cols) {
            PlacedPlant candidate = LocalSearchEngine.makePlacedPlant(newPlant, orig.row(), tryCol, rows, cols);
            if (candidate != null && !state.isCenterOccupied(orig.row(), tryCol))
                placed = candidate;
        }
        if (placed == null) placed = findFirstValidPlacementFrom(newPlant, tryCol);
        if (placed == null) placed = findFirstValidPlacement(newPlant);

        if (placed == null) {
            showError("No space to duplicate plant.");
            return;
        }
        state.addPlant(placed.withLocked(true));
        int newIdx = state.getPlaced().size() - 1;
        setDirty(true);
        statsPanel.update(state);
        canvas.setSelectedIdx(newIdx);
        selectPlant(newIdx);
        canvas.redraw();
        scrollToPlant(newIdx);
    }

    private void deletePlant(int idx) {
        pushUndo();
        state.removePlant(idx);
        setDirty(true);
        canvas.setSelectedIdx(-1);
        canvas.redraw();
        statsPanel.update(state);
        selectPlant(-1);
    }

    private List<Integer> tabOrder() {
        List<PlacedPlant> placed = state.getPlaced();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < placed.size(); i++) indices.add(i);
        indices.sort((a, b) -> {
            int[] tla = topLeftCell(placed.get(a));
            int[] tlb = topLeftCell(placed.get(b));
            int cmp = Integer.compare(tla[0], tlb[0]);
            return cmp != 0 ? cmp : Integer.compare(tla[1], tlb[1]);
        });
        return indices;
    }

    private int[] topLeftCell(PlacedPlant pp) {
        int minRow = Integer.MAX_VALUE, minCol = Integer.MAX_VALUE;
        for (GridCell cell : pp.cells()) {
            if (cell.r() < minRow || (cell.r() == minRow && cell.c() < minCol)) {
                minRow = cell.r();
                minCol = cell.c();
            }
        }
        return new int[]{minRow, minCol};
    }

    private void scrollToPlant(int idx) {
        if (idx < 0 || state == null || idx >= state.getPlaced().size()) return;
        double[] xy = canvas.getPlantCenterXY(idx);
        if (xy == null) return;
        double vw = scrollPane.getViewportBounds().getWidth();
        double vh = scrollPane.getViewportBounds().getHeight();
        double cw = canvas.getWidth();
        double ch = canvas.getHeight();
        if (cw > vw)
            scrollPane.setHvalue(Math.max(0, Math.min(1, (xy[0] - vw / 2) / (cw - vw))));
        if (ch > vh)
            scrollPane.setVvalue(Math.max(0, Math.min(1, (xy[1] - vh / 2) / (ch - vh))));
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}
