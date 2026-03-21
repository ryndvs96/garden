package com.garden.planner.gui;

import com.garden.planner.core.search.SearchMetrics;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Modal overlay shown during search. Polls SearchMetrics every 250ms.
 */
public class SearchOverlayController extends StackPane {

    private final Label statusLabel = new Label("Searching...");
    private final Button stopButton = new Button("Stop");
    private Timeline timeline;
    private AtomicBoolean cancelled;
    private Runnable onStop;

    @SuppressWarnings("this-escape")
    public SearchOverlayController() {
        setStyle("-fx-background-color: rgba(0,0,0,0.5);");

        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian,rgba(0,0,0,0.3),10,0,0,4);");
        box.setMaxWidth(360);
        box.setMaxHeight(140);

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
        setVisible(true);

        timeline = new Timeline(new KeyFrame(Duration.millis(250),
                e -> statusLabel.setText(statusSupplier.get())));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    public void stop() {
        if (timeline != null) timeline.stop();
        setVisible(false);
    }
}
