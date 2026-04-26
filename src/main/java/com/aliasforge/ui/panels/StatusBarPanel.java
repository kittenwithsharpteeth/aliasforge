package com.aliasforge.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;

public class StatusBarPanel extends HBox {

    private Label availableLabel;
    private Label takenLabel;
    private Label rateLimitLabel;
    private Label errorLabel;
    private Label checkingLabel;
    private ProgressBar progressBar;

    public StatusBarPanel() {
        getStyleClass().add("status-bar");
        setPadding(new Insets(5, 12, 5, 12));
        setSpacing(16);
        setAlignment(Pos.CENTER_LEFT);
        buildUI();
    }

    private void buildUI() {
        availableLabel = buildStatLabel("available: 0",  "#4caf50");
        takenLabel     = buildStatLabel("taken: 0",      "#f44336");
        rateLimitLabel = buildStatLabel("rate limit: 0", "#ffc107");
        errorLabel     = buildStatLabel("error: 0",      "#9e9e9e");
        checkingLabel  = buildStatLabel("currently checking: 0", "#2196f3");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("af-progress");
        progressBar.setPrefWidth(200);

        getChildren().addAll(
                availableLabel, takenLabel, rateLimitLabel,
                errorLabel, checkingLabel, spacer, progressBar
        );
    }

    private Label buildStatLabel(String text, String color) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
        return lbl;
    }

    public void updateStats(int available, int taken, int rateLimit, int error, int checking) {
        availableLabel.setText("available: " + available);
        takenLabel.setText("taken: " + taken);
        rateLimitLabel.setText("rate limit: " + rateLimit);
        errorLabel.setText("error: " + error);
        checkingLabel.setText("currently checking: " + checking);
    }

    public void updateProgress(double value) {
        progressBar.setProgress(value);
    }
}