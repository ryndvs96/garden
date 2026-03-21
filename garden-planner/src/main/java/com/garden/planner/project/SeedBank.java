package com.garden.planner.project;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;

/**
 * Global seed bank — persists at ~/.garden-planner/seedbank.json.
 * Backed by an ObservableList so SeedBankController can bind directly.
 */
public class SeedBank {

    private final ObservableList<SeedEntry> entries = FXCollections.observableArrayList();
    private final Path filePath;
    private boolean dirty = false;

    public SeedBank(Path filePath) {
        this.filePath = filePath;
    }

    public ObservableList<SeedEntry> observableEntries() { return entries; }
    public Path getFilePath()                            { return filePath; }
    public boolean isDirty()                             { return dirty; }
    public void setDirty(boolean dirty)                  { this.dirty = dirty; }
}
