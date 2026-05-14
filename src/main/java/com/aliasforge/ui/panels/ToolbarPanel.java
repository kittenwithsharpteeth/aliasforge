package com.aliasforge.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.BiConsumer;

public class ToolbarPanel extends HBox {

    private Button           btnPlay;
    private Button           btnPause;
    private Button           btnStop;
    private Label            outputLabel;
    private ComboBox<String> filterCombo;
    private TextField        searchField;
    private ProgressBar      progressBar;
    private Label            progressLabel;

    private BiConsumer<String, String> onFilterChanged;

    public ToolbarPanel() {
        getStyleClass().add("toolbar-panel");
        setPadding(new Insets(6, 10, 6, 10));
        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);
        buildUI();
    }

    private void buildUI() {
        btnPlay  = new Button("▶");
        btnPause = new Button("⏸");
        btnStop  = new Button("⏹");
        btnPlay.getStyleClass().addAll("af-btn", "af-btn-play");
        btnPause.getStyleClass().addAll("af-btn", "af-btn-pause");
        btnStop.getStyleClass().addAll("af-btn", "af-btn-stop");
        btnPause.setDisable(true);
        btnStop.setDisable(true);

        HBox controls = new HBox(4, btnPlay, btnPause, btnStop);
        controls.setAlignment(Pos.CENTER_LEFT);

        outputLabel = new Label("algorithm output");
        outputLabel.getStyleClass().add("af-output-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // ── Filter ──────────────────────────────────────────────────────
        Label filterLabel = new Label("filter");
        filterLabel.getStyleClass().add("af-label-muted");
        filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("all", "available", "taken", "rate limit", "inconclusive", "error", "checking");
        filterCombo.setValue("all");
        filterCombo.getStyleClass().add("af-combo");
        filterCombo.setPrefWidth(110);
        filterCombo.setOnAction(e -> notifyFilterChanged());

        // ── Search ──────────────────────────────────────────────────────
        Label searchLabel = new Label("search");
        searchLabel.getStyleClass().add("af-label-muted");
        searchField = new TextField();
        searchField.setPromptText("username...");
        searchField.getStyleClass().add("af-search");
        searchField.setPrefWidth(130);
        searchField.textProperty().addListener((obs, o, n) -> notifyFilterChanged());

        // ── Progress ────────────────────────────────────────────────────
        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("af-progress");
        progressBar.setPrefWidth(140);

        progressLabel = new Label("0%");
        progressLabel.getStyleClass().add("af-label-muted");
        progressLabel.setPrefWidth(60);

        getChildren().addAll(
                controls, outputLabel, spacer,
                filterLabel, filterCombo,
                searchLabel, searchField,
                progressBar, progressLabel
        );
    }

    private void notifyFilterChanged() {
        if (onFilterChanged != null) {
            onFilterChanged.accept(
                    filterCombo.getValue(),
                    searchField.getText().trim().toLowerCase()
            );
        }
    }

    // ── API pública ────────────────────────────────────────────────────

    public void setOnFilterChanged(BiConsumer<String, String> cb) {
        this.onFilterChanged = cb;
    }

    public void setRunningState(boolean running, boolean paused) {
        btnPlay.setDisable(running && !paused);
        btnPause.setDisable(!running);
        btnStop.setDisable(!running);
        if (!running)    outputLabel.setText("algorithm output");
        else if (paused) outputLabel.setText("paused...");
        else             outputLabel.setText("checking...");
    }

    public void updateProgress(double value, int checked, int total) {
        progressBar.setProgress(value);
        int pct = (int) (value * 100);
        progressLabel.setText(pct + "%  (" + checked + "/" + total + ")");
    }

    public void resetProgress() {
        progressBar.setProgress(0);
        progressLabel.setText("0%");
    }

    public Button           getBtnPlay()     { return btnPlay; }
    public Button           getBtnPause()    { return btnPause; }
    public Button           getBtnStop()     { return btnStop; }
    public ComboBox<String> getFilterCombo() { return filterCombo; }
    public TextField        getSearchField() { return searchField; }
}
