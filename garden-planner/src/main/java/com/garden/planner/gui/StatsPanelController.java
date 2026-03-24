package com.garden.planner.gui;

import com.garden.planner.core.model.*;
import com.garden.planner.core.search.SearchMetrics;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Map;

/**
 * Shows live stats: score, placed count, species count, states/sec.
 */
public class StatsPanelController extends VBox {

    private final Label selectedLabel = new Label("");
    private final Label scoreLabel = new Label("Score: —");
    private final Label placedLabel = new Label("Placed: —");
    private final Label uniqueLabel = new Label("Unique: —");
    private final Label overlapLabel = new Label("Overlaps: —");
    private final Label statesSecLabel = new Label("States/sec: —");
    private final Label elapsedLabel = new Label("Elapsed: —");
    private final VBox speciesBox = new VBox(2);

    @SuppressWarnings("this-escape")
    public StatsPanelController() {
        setSpacing(6);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #f0ece0; -fx-border-color: #ccccaa; -fx-border-width: 1;");
        setPrefWidth(220);
        setMinWidth(160);

        Label header = new Label("Statistics");
        header.setFont(Font.font(null, FontWeight.BOLD, 14));

        selectedLabel.setWrapText(true);
        selectedLabel.setMaxWidth(Double.MAX_VALUE);
        selectedLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #444;");

        for (Label l : new Label[]{scoreLabel, placedLabel, uniqueLabel, overlapLabel,
                                    statesSecLabel, elapsedLabel}) {
            l.setWrapText(true);
            l.setMaxWidth(Double.MAX_VALUE);
        }

        Label speciesHeader = new Label("Species:");
        speciesHeader.setFont(Font.font(null, FontWeight.BOLD, 12));

        ScrollPane speciesScroll = new ScrollPane(speciesBox);
        speciesScroll.setFitToWidth(true);
        speciesScroll.setPrefHeight(200);
        speciesScroll.setStyle("-fx-background-color: transparent;");

        getChildren().addAll(header, selectedLabel, scoreLabel, placedLabel, uniqueLabel, overlapLabel,
                statesSecLabel, elapsedLabel, speciesHeader, speciesScroll);
    }

    public void update(PlacementState state) {
        if (state == null) return;
        scoreLabel.setText(String.format("Score: %.1f", state.getScore()));
        placedLabel.setText(String.format("Placed: %d", state.getNPlaced()));
        uniqueLabel.setText(String.format("Unique: %d", state.getNUnique()));
        overlapLabel.setText(String.format("Overlaps: %d cells", state.countStrictOverlaps()));

        speciesBox.getChildren().clear();
        for (Map.Entry<PlantSpecies, Integer> e : state.getSpeciesCount().entrySet()) {
            if (e.getValue() > 0) {
                speciesBox.getChildren().add(new Label(
                        String.format("  %s: %d", e.getKey().name(), e.getValue())));
            }
        }
    }

    public void updateSearch(SearchMetrics metrics) {
        if (metrics == null) {
            statesSecLabel.setText("States/sec: —");
            elapsedLabel.setText("Elapsed: —");
            return;
        }
        statesSecLabel.setText(String.format("States/sec: %.0f", metrics.statesPerSecond()));
        elapsedLabel.setText(String.format("Elapsed: %.1fs", metrics.elapsedSeconds()));
    }

    public void updateSelected(PlacedPlant pp) {
        if (pp == null) { selectedLabel.setText(""); return; }
        selectedLabel.setText(pp.plant().plantType() + " \u2013 " + pp.plant().plantName()
                + "\n  zone: " + pp.plant().zone()
                + "  size: " + pp.plant().widthIn() + "in"
                + "  " + (pp.plant().isStrict() ? "strict" : "loose")
                + (pp.locked() ? "  [locked]" : ""));
    }
}
