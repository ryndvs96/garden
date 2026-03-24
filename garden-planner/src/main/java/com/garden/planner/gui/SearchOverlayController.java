package com.garden.planner.gui;

import com.garden.planner.core.search.SearchMetrics;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Modal overlay shown during search and to display the search result.
 */
public class SearchOverlayController extends StackPane {

    private final Label statusLabel = new Label("Searching...");
    private final Button stopButton = new Button("Stop");
    private final VBox box;
    private Timeline timeline;
    private AtomicBoolean cancelled;
    private Runnable onStop;

    @SuppressWarnings("this-escape")
    public SearchOverlayController() {
        setStyle("-fx-background-color: rgba(0,0,0,0.5);");

        box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian,rgba(0,0,0,0.3),10,0,0,4);");
        box.setMaxWidth(360);
        box.setMaxHeight(160);

        statusLabel.setStyle("-fx-font-size: 14;");
        stopButton.setStyle("-fx-background-color: #e57373; -fx-text-fill: white;");
        stopButton.setOnAction(e -> {
            if (cancelled != null) cancelled.set(true);
            if (onStop != null) onStop.run();
        });

        box.getChildren().addAll(statusLabel, stopButton);
        getChildren().add(box);
        setAlignment(box, Pos.CENTER);
        setVisible(false);
    }

    public void start(SearchMetrics metrics, AtomicBoolean cancelled, Runnable onStop) {
        this.cancelled = cancelled;
        this.onStop = onStop;
        box.getChildren().setAll(statusLabel, stopButton);
        statusLabel.setStyle("-fx-font-size: 14;");
        setVisible(true);

        timeline = new Timeline(new KeyFrame(Duration.millis(250), e -> {
            if (metrics != null) {
                statusLabel.setText(String.format(
                    "Searching... best: %.1f | %.0f states/sec | %.1fs",
                    metrics.getBestScore(), metrics.statesPerSecond(), metrics.elapsedSeconds()));
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    /** Overload with a custom status text supplier — used by Flower Fill. */
    public void start(AtomicBoolean cancelled, Runnable onStop, Supplier<String> statusSupplier) {
        this.cancelled = cancelled;
        this.onStop = onStop;
        box.getChildren().setAll(statusLabel, stopButton);
        statusLabel.setStyle("-fx-font-size: 14;");
        setVisible(true);

        timeline = new Timeline(new KeyFrame(Duration.millis(250),
                e -> statusLabel.setText(statusSupplier.get())));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    /** Show a success result and auto-close after 1s. */
    public void showImproved(double from, double to) {
        stopTimeline();
        statusLabel.setText(String.format("Score improved: %.1f \u2192 %.1f", from, to));
        statusLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #2e7d32;");
        box.getChildren().setAll(statusLabel);
        setVisible(true);

        Timeline autoClose = new Timeline(new KeyFrame(Duration.millis(1200), e -> setVisible(false)));
        autoClose.play();
    }

    /** Show a no-improvement result with an option to extend the search. */
    public void showNoImprovement(Runnable onExtend) {
        stopTimeline();
        statusLabel.setText("Already at the best score found.\nExtend search by 30s?");
        statusLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #555;");
        statusLabel.setWrapText(true);

        Button extendBtn = new Button("Extend Search");
        extendBtn.setStyle("-fx-background-color: #388e3c; -fx-text-fill: white;");
        extendBtn.setOnAction(e -> {
            setVisible(false);
            onExtend.run();
        });

        Button dismissBtn = new Button("Dismiss");
        dismissBtn.setOnAction(e -> setVisible(false));

        HBox buttons = new HBox(10, extendBtn, dismissBtn);
        buttons.setAlignment(Pos.CENTER);

        box.getChildren().setAll(statusLabel, buttons);
        setVisible(true);
    }

    public void stop() {
        stopTimeline();
        setVisible(false);
    }

    private void stopTimeline() {
        if (timeline != null) timeline.stop();
    }
}
