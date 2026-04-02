package com.garden.planner.gui;

import com.garden.planner.data.ProjectSerializer;
import com.garden.planner.data.RecentProjects;
import com.garden.planner.data.SeedBankSerializer;
import com.garden.planner.project.BedConfig;
import com.garden.planner.project.GardenProject;
import com.garden.planner.project.GardenZone;
import com.garden.planner.project.SeedBank;
import com.garden.planner.project.SeedEntry;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root layout. Owns the MenuBar, ActivityBar, SidebarController, and TabPane.
 * Replaces AppController + StartScreenController as the single root pane.
 */
public class MainController extends BorderPane {

    private final Stage stage;
    private final TabPane tabPane = new TabPane();
    private SidebarController sidebar;
    private ActivityBar activityBar;
    private GardenProject currentProject;
    private SeedBank seedBank;

    /** bed UUID → open Tab */
    private final Map<String, Tab> openTabs = new HashMap<>();

    private final ProjectSerializer  projectSerializer  = new ProjectSerializer();
    private final SeedBankSerializer seedBankSerializer = new SeedBankSerializer();
    private final RecentProjects     recentProjects     = new RecentProjects();

    @SuppressWarnings("this-escape")
    public MainController(Stage stage) {
        this.stage = stage;
        initialize();
    }

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    private void initialize() {
        // Load (or create) global seed bank
        Path seedBankPath = Path.of(System.getProperty("user.home"), ".garden-planner", "seedbank.json");
        try {
            seedBank = seedBankSerializer.loadOrCreate(seedBankPath);
        } catch (Exception e) {
            seedBank = new SeedBank(seedBankPath);
        }

        // Sidebar
        activityBar = new ActivityBar();
        sidebar     = new SidebarController(seedBank);
        activityBar.setOnViewChanged(sidebar::showView);
        wireSidebarCallbacks();
        sidebar.setOnDeleteEntry(entry -> {
            // Collect open beds that contain this plant species
            List<BedEditorPane> inUse = tabPane.getTabs().stream()
                    .filter(t -> t.getContent() instanceof BedEditorPane)
                    .map(t -> (BedEditorPane) t.getContent())
                    .filter(ed -> ed.containsSpecies(entry.plantType(), entry.plantName()))
                    .toList();

            if (!inUse.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.initOwner(stage);
                alert.setTitle("Plant In Use");
                alert.setHeaderText("\"" + entry.plantName() + "\" is placed in "
                        + inUse.size() + " open bed" + (inUse.size() == 1 ? "" : "s") + ".");
                alert.setContentText("Remove it from those beds too?");
                ButtonType deleteAndRemove = new ButtonType("Delete & Remove from Beds");
                ButtonType deleteOnly      = new ButtonType("Delete Seed Only");
                ButtonType cancel          = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(deleteAndRemove, deleteOnly, cancel);
                alert.showAndWait().ifPresent(bt -> {
                    if (bt == deleteAndRemove) {
                        inUse.forEach(ed -> ed.removePlantsBySpecies(entry.plantType(), entry.plantName()));
                        seedBank.observableEntries().remove(entry);
                        try { seedBankSerializer.save(seedBank); } catch (Exception ignored) {}
                    } else if (bt == deleteOnly) {
                        seedBank.observableEntries().remove(entry);
                        try { seedBankSerializer.save(seedBank); } catch (Exception ignored) {}
                    }
                    // cancel — do nothing
                });
            } else {
                seedBank.observableEntries().remove(entry);
                try { seedBankSerializer.save(seedBank); } catch (Exception ignored) {}
            }
        });

        sidebar.setOnAddSeed(entry -> {
            Tab sel = tabPane.getSelectionModel().getSelectedItem();
            if (sel == null || !(sel.getContent() instanceof BedEditorPane editor)) return;

            // Show out-of-stock alert but still allow the add
            if (entry.quantity() <= 0) {
                Alert alert = new Alert(Alert.AlertType.WARNING,
                        "\"" + entry.plantName() + "\" has 0 left in your seed bank.\n" +
                        "You can still add it, or delete it from the bed after placing.",
                        ButtonType.OK);
                alert.setTitle("Out of Stock");
                alert.setHeaderText(null);
                alert.initOwner(stage);
                alert.showAndWait();
            }

            // Decrement quantity (clamp at 0)
            int idx = seedBank.observableEntries().indexOf(entry);
            if (idx >= 0 && entry.quantity() > 0) {
                SeedEntry decremented = new SeedEntry(
                        entry.id(), entry.zone(), entry.plantType(), entry.plantName(),
                        entry.widthIn(), entry.heightIn(), entry.isStrict(), entry.notes(),
                        entry.quantity() - 1);
                seedBank.observableEntries().set(idx, decremented);
                try { seedBankSerializer.save(seedBank); } catch (Exception ignored) {}
            }

            editor.addFromSeed(entry);
        });

        HBox leftBox = new HBox(activityBar, sidebar);

        // Center: tabs with welcome placeholder
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        showWelcomeTab();

        setTop(buildMenuBar());
        setLeft(leftBox);
        setCenter(tabPane);

        // Re-open the most recent project silently
        List<Path> recent = recentProjects.getAll();
        if (!recent.isEmpty()) {
            try {
                openProject(projectSerializer.loadProject(recent.get(0)));
            } catch (Exception ignored) {}
        }

        stage.setTitle("Garden Planner");
    }

    private void wireSidebarCallbacks() {
        sidebar.setOnOpenBed(this::openBed);
        sidebar.setOnAddBed(this::doAddBed);
        sidebar.setOnAddZone(this::doAddZone);
        sidebar.setOnRenameBed(this::doRenameBed);
        sidebar.setOnDeleteBed(this::doDeleteBed);
        sidebar.setOnDuplicateBed(this::doDuplicateBed);
        sidebar.setOnRenameZone(this::doRenameZone);
        sidebar.setOnDeleteZone(this::doDeleteZone);
        sidebar.setOnDuplicateZone(this::doDuplicateZone);
    }

    // -------------------------------------------------------------------------
    // Menu bar
    // -------------------------------------------------------------------------

    private MenuBar buildMenuBar() {
        // ---- File ----
        MenuItem newItem     = new MenuItem("New Garden Plan…");
        MenuItem openItem    = new MenuItem("Open Garden Plan…");
        MenuItem openFileItem= new MenuItem("Import Legacy Bed (.json)…");
        MenuItem saveItem    = new MenuItem("Save");
        MenuItem saveAllItem = new MenuItem("Save All");
        Menu     recentMenu  = new Menu("Open Recent");
        MenuItem quitItem    = new MenuItem("Quit");

        newItem.setOnAction(e -> doNewProject());
        openItem.setOnAction(e -> doOpenProjectDir());
        openFileItem.setOnAction(e -> doImportLegacyBed());
        saveItem.setOnAction(e -> saveCurrentBed());
        saveAllItem.setOnAction(e -> saveAll());
        quitItem.setOnAction(e -> javafx.application.Platform.exit());

        // Keyboard accelerators
        saveItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Shortcut+S"));
        saveAllItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Shortcut+Shift+S"));

        refreshRecentMenu(recentMenu);

        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(
                newItem, openItem, openFileItem,
                new SeparatorMenuItem(),
                saveItem, saveAllItem,
                new SeparatorMenuItem(),
                recentMenu,
                new SeparatorMenuItem(),
                quitItem);

        // ---- Edit (stubs) ----
        Menu editMenu = new Menu("Edit");
        editMenu.getItems().add(new MenuItem("(no actions)"));

        // ---- Settings (stub) ----
        Menu settingsMenu = new Menu("Settings");
        settingsMenu.getItems().add(new MenuItem("(no settings)"));

        MenuBar bar = new MenuBar(fileMenu, editMenu, settingsMenu);
        // On macOS this moves the menu bar to the system menu bar
        bar.setUseSystemMenuBar(true);
        return bar;
    }

    private void refreshRecentMenu(Menu recentMenu) {
        recentMenu.getItems().clear();
        List<Path> recent = recentProjects.getAll();
        if (recent.isEmpty()) {
            recentMenu.getItems().add(new MenuItem("(none)"));
            return;
        }
        for (Path p : recent) {
            MenuItem item = new MenuItem(p.getFileName().toString());
            item.setOnAction(e -> {
                try {
                    openProject(projectSerializer.loadProject(p));
                } catch (Exception ex) {
                    showError("Failed to open: " + ex.getMessage());
                }
            });
            recentMenu.getItems().add(item);
        }
    }

    // -------------------------------------------------------------------------
    // Project operations
    // -------------------------------------------------------------------------

    public void openProject(GardenProject project) {
        currentProject = project;
        recentProjects.add(project.getProjectDir());
        sidebar.setProject(project);
        stage.setTitle("Garden Planner \u2014 " + project.getName());
    }

    public void openBed(BedConfig config) {
        if (openTabs.containsKey(config.id())) {
            tabPane.getSelectionModel().select(openTabs.get(config.id()));
            return;
        }

        BedEditorPane editor = new BedEditorPane(stage, config, currentProject.bedsDir(), seedBank);
        editor.setOnDirtyChanged(dirty -> updateTabTitle(config.id(), dirty));
        editor.setOnSpeciesEdited((oldType, oldName, data) -> {
            // Update matching seedbank entry
            for (int i = 0; i < seedBank.observableEntries().size(); i++) {
                SeedEntry e = seedBank.observableEntries().get(i);
                if (e.plantType().equals(oldType) && e.plantName().equals(oldName)) {
                    seedBank.observableEntries().set(i, new SeedEntry(
                            e.id(), e.zone(), data.plantType(), data.plantName(),
                            data.widthIn(), data.widthIn(), data.isStrict(), e.notes(), e.quantity()));
                    try { seedBankSerializer.save(seedBank); } catch (Exception ignored) {}
                    break;
                }
            }
            // Propagate to all other open beds
            for (Tab t : tabPane.getTabs()) {
                if (t.getContent() instanceof BedEditorPane other && other != editor) {
                    other.applySpeciesEdit(oldType, oldName, data);
                }
            }
        });

        Tab tab = new Tab(config.displayName(), editor);
        tab.setOnCloseRequest(event -> {
            if (editor.isDirty()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.initOwner(stage);
                alert.setTitle("Unsaved Changes");
                alert.setHeaderText("\"" + config.displayName() + "\" has unsaved changes.");
                alert.setContentText("Save before closing?");
                ButtonType saveBtn    = new ButtonType("Save");
                ButtonType discardBtn = new ButtonType("Discard");
                ButtonType cancelBtn  = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(saveBtn, discardBtn, cancelBtn);
                alert.showAndWait().ifPresent(bt -> {
                    if (bt == saveBtn) {
                        editor.doSave();
                    } else if (bt == cancelBtn) {
                        event.consume(); // cancel the close
                    }
                    // discardBtn — just let the tab close without saving
                });
            }
        });
        tab.setOnClosed(e -> openTabs.remove(config.id()));

        openTabs.put(config.id(), tab);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    public void saveCurrentBed() {
        Tab sel = tabPane.getSelectionModel().getSelectedItem();
        if (sel != null && sel.getContent() instanceof BedEditorPane editor) {
            editor.doSave();
        }
    }

    public void saveAll() {
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getContent() instanceof BedEditorPane editor) {
                editor.doSave();
            }
        }
    }

    public void updateTabTitle(String bedId, boolean dirty) {
        Tab tab = openTabs.get(bedId);
        if (tab == null) return;
        currentProject.findBed(bedId).ifPresent(bed ->
                tab.setText(dirty ? "* " + bed.displayName() : bed.displayName()));
    }

    // -------------------------------------------------------------------------
    // Menu actions — new / open
    // -------------------------------------------------------------------------

    private void doNewProject() {
        new NewProjectDialog(stage).show().ifPresent(project -> {
            try {
                projectSerializer.saveManifest(project);
                openProject(project);
            } catch (Exception e) {
                showError("Failed to create project: " + e.getMessage());
            }
        });
    }

    private void doOpenProjectDir() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Open Garden Plan (.gardenplan folder)");
        File dir = dc.showDialog(stage);
        if (dir == null) return;
        try {
            openProject(projectSerializer.loadProject(dir.toPath()));
        } catch (Exception e) {
            showError("Failed to open project: " + e.getMessage());
        }
    }

    private void doImportLegacyBed() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import Legacy Bed JSON");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
        File file = fc.showOpenDialog(stage);
        if (file == null) return;
        try {
            openProject(projectSerializer.importLegacyBed(file));
        } catch (Exception e) {
            showError("Failed to import bed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Sidebar mutation actions
    // -------------------------------------------------------------------------

    public void doAddBed(String zoneId) {
        if (currentProject == null) return;
        new NewBedDialog(stage).show().ifPresent(name -> {
            BedConfig bed = currentProject.addBed(zoneId, name);
            saveManifest();
            sidebar.setProject(currentProject);
            openBed(bed);
        });
    }

    public void doAddZone() {
        if (currentProject == null) return;
        new NewZoneDialog(stage).show().ifPresent(name -> {
            currentProject.addZone(name);
            saveManifest();
            sidebar.setProject(currentProject);
        });
    }

    public void doRenameBed(BedConfig bed) {
        if (currentProject == null) return;
        TextInputDialog dlg = new TextInputDialog(bed.displayName());
        dlg.setTitle("Rename Bed");
        dlg.setHeaderText(null);
        dlg.setContentText("New name:");
        dlg.initOwner(stage);
        dlg.showAndWait().ifPresent(newName -> {
            if (newName.isBlank()) return;
            for (GardenZone zone : currentProject.getZones()) {
                int idx = zone.beds().indexOf(bed);
                if (idx >= 0) {
                    zone.beds().set(idx, new BedConfig(bed.id(), newName.trim(), bed.fileName()));
                    break;
                }
            }
            saveManifest();
            sidebar.setProject(currentProject);
            Tab tab = openTabs.get(bed.id());
            if (tab != null) tab.setText(newName.trim());
        });
    }

    public void doDeleteBed(BedConfig bed) {
        if (currentProject == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove \"" + bed.displayName() + "\" from the project? (File is not deleted.)",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Delete Bed");
        confirm.setHeaderText(null);
        confirm.initOwner(stage);
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            Tab tab = openTabs.remove(bed.id());
            if (tab != null) tabPane.getTabs().remove(tab);
            currentProject.removeBed(bed.id());
            saveManifest();
            sidebar.setProject(currentProject);
        });
    }

    public void doDuplicateBed(BedConfig source) {
        if (currentProject == null) return;
        // Find the zone that contains this bed
        GardenZone sourceZone = currentProject.getZones().stream()
                .filter(z -> z.beds().contains(source))
                .findFirst().orElse(null);
        if (sourceZone == null) return;

        TextInputDialog dlg = new TextInputDialog("Copy of " + source.displayName());
        dlg.setTitle("Duplicate Bed");
        dlg.setHeaderText(null);
        dlg.setContentText("New bed name:");
        dlg.initOwner(stage);
        dlg.showAndWait().ifPresent(newName -> {
            if (newName.isBlank()) return;
            boolean nameExists = sourceZone.beds().stream()
                    .anyMatch(b -> b.displayName().equalsIgnoreCase(newName.trim()));
            if (nameExists) {
                showError("A bed named \"" + newName.trim() + "\" already exists in this zone.");
                return;
            }
            BedConfig newBed = currentProject.addBed(sourceZone.id(), newName.trim());
            Path srcFile = currentProject.bedsDir().resolve(source.fileName());
            Path dstFile = currentProject.bedsDir().resolve(newBed.fileName());
            if (srcFile.toFile().exists()) {
                try { Files.copy(srcFile, dstFile); } catch (IOException e) {
                    showError("Failed to copy bed file: " + e.getMessage());
                }
            }
            saveManifest();
            sidebar.setProject(currentProject);
            openBed(newBed);
        });
    }

    public void doDuplicateZone(GardenZone source) {
        if (currentProject == null) return;
        TextInputDialog dlg = new TextInputDialog("Copy of " + source.name());
        dlg.setTitle("Duplicate Zone");
        dlg.setHeaderText(null);
        dlg.setContentText("New zone name:");
        dlg.initOwner(stage);
        dlg.showAndWait().ifPresent(newName -> {
            if (newName.isBlank()) return;
            boolean nameExists = currentProject.getZones().stream()
                    .anyMatch(z -> z.name().equalsIgnoreCase(newName.trim()));
            if (nameExists) {
                showError("A zone named \"" + newName.trim() + "\" already exists.");
                return;
            }
            GardenZone newZone = currentProject.addZone(newName.trim());
            for (BedConfig srcBed : source.beds()) {
                BedConfig newBed = currentProject.addBed(newZone.id(), srcBed.displayName());
                Path srcFile = currentProject.bedsDir().resolve(srcBed.fileName());
                Path dstFile = currentProject.bedsDir().resolve(newBed.fileName());
                if (srcFile.toFile().exists()) {
                    try { Files.copy(srcFile, dstFile); } catch (IOException e) {
                        showError("Failed to copy \"" + srcBed.displayName() + "\": " + e.getMessage());
                    }
                }
            }
            saveManifest();
            sidebar.setProject(currentProject);
        });
    }

    public void doRenameZone(GardenZone zone) {
        if (currentProject == null) return;
        TextInputDialog dlg = new TextInputDialog(zone.name());
        dlg.setTitle("Rename Zone");
        dlg.setHeaderText(null);
        dlg.setContentText("New name:");
        dlg.initOwner(stage);
        dlg.showAndWait().ifPresent(newName -> {
            if (newName.isBlank()) return;
            int idx = currentProject.getZones().indexOf(zone);
            if (idx >= 0) {
                currentProject.getZones().set(idx,
                        new GardenZone(zone.id(), newName.trim(), zone.beds()));
            }
            saveManifest();
            sidebar.setProject(currentProject);
        });
    }

    public void doDeleteZone(GardenZone zone) {
        if (currentProject == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove zone \"" + zone.name() + "\" and all its beds from the project?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Delete Zone");
        confirm.setHeaderText(null);
        confirm.initOwner(stage);
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            for (BedConfig bed : zone.beds()) {
                Tab tab = openTabs.remove(bed.id());
                if (tab != null) tabPane.getTabs().remove(tab);
            }
            currentProject.removeZone(zone.id());
            saveManifest();
            sidebar.setProject(currentProject);
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void showWelcomeTab() {
        Label msg = new Label(
                "Open or create a garden plan to get started.\n\n" +
                "File \u2192 New Garden Plan\u2026\n" +
                "File \u2192 Open Garden Plan\u2026\n" +
                "File \u2192 Import Legacy Bed (.json)\u2026");
        msg.setStyle("-fx-font-size: 13; -fx-text-fill: #888;");
        msg.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        msg.setAlignment(Pos.CENTER);
        Tab welcomeTab = new Tab("Welcome", msg);
        welcomeTab.setClosable(false);
        tabPane.getTabs().add(welcomeTab);
    }

    private void saveManifest() {
        try {
            projectSerializer.saveManifest(currentProject);
        } catch (Exception e) {
            showError("Failed to save project manifest: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}
