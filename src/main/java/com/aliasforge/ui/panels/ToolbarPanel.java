package com.aliasforge.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ToolbarPanel extends HBox {

    private Button        btnPlay;
    private Button        btnPause;
    private Button        btnStop;
    private Label         outputLabel;
    private ComboBox<String> filterCombo;
    private TextField     searchField;
    private ProgressBar   progressBar;
    private Label         progressLabel;

    public ToolbarPanel() {
        getStyleClass().add("toolbar-panel");
        setPadding(new Insets(6, 10, 6, 10));
        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);
        buildUI();
    }

    private void buildUI() {
        // ── Botões de controle ──────────────────────────────────────────
        btnPlay  = new Button("▶");
        btnPause = new Button("⏸");
        btnStop  = new Button("⏹");
        btnPlay.getStyleClass().addAll("af-btn", "af-btn-play");
        btnPause.getStyleClass().addAll("af-btn", "af-btn-pause");
        btnStop.getStyleClass().addAll("af-btn", "af-btn-stop");

        // Estado inicial: só play habilitado
        btnPause.setDisable(true);
        btnStop.setDisable(true);

        HBox controls = new HBox(4, btnPlay, btnPause, btnStop);
        controls.setAlignment(Pos.CENTER_LEFT);

        // ── Label ───────────────────────────────────────────────────────
        outputLabel = new Label("algorithm output");
        outputLabel.getStyleClass().add("af-output-label");

        // ── Spacer ──────────────────────────────────────────────────────
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // ── Filtro ──────────────────────────────────────────────────────
        Label filterLabel = new Label("filter");
        filterLabel.getStyleClass().add("af-label-muted");
        filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("all", "available", "taken", "rate limit", "error");
        filterCombo.setValue("all");
        filterCombo.getStyleClass().add("af-combo");
        filterCombo.setPrefWidth(110);

        // ── Busca ───────────────────────────────────────────────────────
        Label searchLabel = new Label("search");
        searchLabel.getStyleClass().add("af-label-muted");
        searchField = new TextField();
        searchField.setPromptText("username...");
        searchField.getStyleClass().add("af-search");
        searchField.setPrefWidth(130);

        // ── Progresso ───────────────────────────────────────────────────
        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("af-progress");
        progressBar.setPrefWidth(140);

        progressLabel = new Label("0%");
        progressLabel.getStyleClass().add("af-label-muted");
        progressLabel.setPrefWidth(50);

        getChildren().addAll(
                controls, outputLabel, spacer,
                filterLabel, filterCombo,
                searchLabel, searchField,
                progressBar, progressLabel
        );
    }

    // ── API pública ────────────────────────────────────────────────────

    public void setRunningState(boolean running, boolean paused) {
        btnPlay.setDisable(running && !paused);
        btnPause.setDisable(!running);
        btnStop.setDisable(!running);

        if (!running) {
            outputLabel.setText("algorithm output");
        } else if (paused) {
            outputLabel.setText("paused...");
        } else {
            outputLabel.setText("checking...");
        }
    }

    public void updateProgress(double value, int checked, int total) {
        progressBar.setProgress(value);
        int pct = (int) (value * 100);
        progressLabel.setText(pct + "%  (" + checked + "/" + total + ")");
    }

    public Button        getBtnPlay()        { return btnPlay; }
    public Button        getBtnPause()       { return btnPause; }
    public Button        getBtnStop()        { return btnStop; }
    public ComboBox<String> getFilterCombo() { return filterCombo; }
    public TextField     getSearchField()    { return searchField; }
    public ProgressBar   getProgressBar()    { return progressBar; }
}