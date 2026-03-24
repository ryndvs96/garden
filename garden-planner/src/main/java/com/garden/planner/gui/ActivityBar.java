package com.garden.planner.gui;

import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Narrow vertical bar (~40px) on the far left, VS Code-style.
 * Two toggle buttons switch between the Explorer and Seed Bank sidebar panels.
 */
public class ActivityBar extends VBox {

    public enum ActivityView { EXPLORER, SEED_BANK }

    private Consumer<ActivityView> onViewChanged;

    @SuppressWarnings("this-escape")
    public ActivityBar() {
        setStyle("-fx-background-color: #333333;");
        setMinWidth(40);
        setMaxWidth(40);
        setPrefWidth(40);

        ToggleGroup group = new ToggleGroup();

        ToggleButton explorerBtn = makeBtn("\u2630", "Explorer", group);  // ☰
        ToggleButton seedBtn     = makeBtn("\u273F", "Seed Bank", group); // ✿

        explorerBtn.setOnAction(e -> { if (onViewChanged != null) onViewChanged.accept(ActivityView.EXPLORER); });
        seedBtn.setOnAction(e ->     { if (onViewChanged != null) onViewChanged.accept(ActivityView.SEED_BANK); });

        // Default: explorer selected
        explorerBtn.setSelected(true);

        getChildren().addAll(explorerBtn, seedBtn);
    }

    private ToggleButton makeBtn(String icon, String tooltip, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(icon);
        btn.setToggleGroup(group);
        btn.setTooltip(new Tooltip(tooltip));
        btn.setMinSize(40, 40);
        btn.setMaxSize(40, 40);
        String idle   = "-fx-background-color: transparent; -fx-text-fill: #bbb; -fx-font-size: 16; -fx-cursor: hand; -fx-border-color: transparent;";
        String active = "-fx-background-color: #505050;    -fx-text-fill: white;  -fx-font-size: 16; -fx-cursor: hand; -fx-border-color: transparent;";
        btn.setStyle(idle);
        btn.selectedProperty().addListener((obs, was, is) -> btn.setStyle(is ? active : idle));
        return btn;
    }

    public void setOnViewChanged(Consumer<ActivityView> callback) {
        this.onViewChanged = callback;
    }
}
