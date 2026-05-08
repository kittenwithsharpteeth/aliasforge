package com.aliasforge.ui.views;

import com.aliasforge.config.AppConfig;
import com.aliasforge.model.AppSettings;
import com.aliasforge.service.RateLimitService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Aba de configurações gerais do AliasForge.
 * Persiste via AppConfig/AppSettings em ~/.aliasforge/settings.json
 */
public class SettingsView extends VBox {

    // Flag que bloqueia saveSettings() durante loadSettings()
    // Evita que os listeners dos sliders sobrescrevam os defaults durante reset
    private boolean loading = false;

    // ── Request ────────────────────────────────────────────────────────
    private Slider threadsSlider;
    private Label  threadsValue;
    private Slider delaySlider;
    private Label  delayValue;
    private Slider timeoutSlider;
    private Label  timeoutValue;

    // ── Retry ──────────────────────────────────────────────────────────
    private Slider maxRetriesSlider;
    private Label  maxRetriesValue;
    private Slider retryDelaySlider;
    private Label  retryDelayValue;

    // ── Behavior ───────────────────────────────────────────────────────
    private CheckBox chkMinimizeToTray;

    public SettingsView() {
        getStyleClass().add("settings-view");
        setFillWidth(true);
        VBox.setVgrow(this, Priority.ALWAYS);
        setPadding(new Insets(20, 24, 20, 24));
        setSpacing(16);
        buildUI();
        loadSettings();
    }

    private void buildUI() {
        Label pageTitle = new Label("settings");
        pageTitle.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 15px; -fx-font-weight: bold;");

        Label pageSubtitle = new Label("changes are saved automatically");
        pageSubtitle.setStyle("-fx-text-fill: #555555; -fx-font-size: 11px;");

        VBox header = new VBox(3, pageTitle, pageSubtitle);
        header.setPadding(new Insets(0, 0, 8, 0));

        getChildren().addAll(
                header,
                buildRequestSection(),
                buildRetrySection(),
                buildBehaviorSection(),
                buildResetRow()
        );
    }

    // ── Request section ────────────────────────────────────────────────

    private VBox buildRequestSection() {
        VBox section = buildSection("request");

        // Parallel threads — 1..8
        threadsSlider = buildSlider(1, 8, 1, 1);
        threadsValue  = buildValueLabel("1 thread");
        threadsSlider.valueProperty().addListener((obs, o, n) -> {
            int v = n.intValue();
            threadsValue.setText(v + (v == 1 ? " thread" : " threads"));
            saveSettings();
        });

        // Delay between requests — 0..2000ms step 50
        delaySlider = buildSlider(0, 2000, 420, 50);
        delayValue  = buildValueLabel("420 ms");
        delaySlider.valueProperty().addListener((obs, o, n) -> {
            int v = n.intValue();
            delayValue.setText(v + " ms");
            saveSettings();
        });

        // Request timeout — 5000..30000ms step 1000
        timeoutSlider = buildSlider(5000, 30000, 8000, 1000);
        timeoutValue  = buildValueLabel("8000 ms");
        timeoutSlider.valueProperty().addListener((obs, o, n) -> {
            int v = n.intValue();
            timeoutValue.setText(v + " ms");
            saveSettings();
        });

        Label threadNote = buildNote(
                "⚠  More threads = faster checking but higher chance of rate limits. " +
                        "1 thread is safest for Minecraft.");
        Label delayNote = buildNote(
                "ⓘ  Minimum enforced delay per platform: Minecraft requires 600 ms.");

        section.getChildren().addAll(
                buildSliderRow("parallel threads",       threadsSlider, threadsValue),
                threadNote,
                buildSliderRow("delay between requests", delaySlider,   delayValue),
                delayNote,
                buildSliderRow("request timeout",        timeoutSlider, timeoutValue)
        );
        return section;
    }

    // ── Retry section ──────────────────────────────────────────────────

    private VBox buildRetrySection() {
        VBox section = buildSection("rate limit & retry");

        // Max retries — 0..5
        maxRetriesSlider = buildSlider(0, 5, 3, 1);
        maxRetriesValue  = buildValueLabel("3 retries");
        maxRetriesSlider.valueProperty().addListener((obs, o, n) -> {
            int v = n.intValue();
            maxRetriesValue.setText(v + (v == 1 ? " retry" : " retries"));
            saveSettings();
            updateRetryDelayTooltip();
        });

        // Retry base delay — 15..120s step 15
        retryDelaySlider = buildSlider(15, 120, 30, 15);
        retryDelayValue  = buildValueLabel("30 s");
        retryDelaySlider.valueProperty().addListener((obs, o, n) -> {
            int v = n.intValue();
            retryDelayValue.setText(v + " s");
            saveSettings();
            updateRetryDelayTooltip();
        });

        Label retryNote = buildNote(
                "ⓘ  Retry delay grows per attempt: base → base×2 → base×3. " +
                        "Example with 30s: 30s → 60s → 90s.");

        section.getChildren().addAll(
                buildSliderRow("max retries",      maxRetriesSlider, maxRetriesValue),
                buildSliderRow("retry base delay", retryDelaySlider, retryDelayValue),
                retryNote
        );
        return section;
    }

    // ── Behavior section ───────────────────────────────────────────────

    private VBox buildBehaviorSection() {
        VBox section = buildSection("behavior");

        chkMinimizeToTray = new CheckBox("minimize to system tray instead of closing");
        chkMinimizeToTray.getStyleClass().add("af-checkbox");
        chkMinimizeToTray.setOnAction(e -> saveSettings());

        Label trayNote = buildNote(
                "ⓘ  When enabled, closing the window keeps AliasForge running in " +
                        "the background. Right-click the tray icon to exit completely.");

        section.getChildren().addAll(chkMinimizeToTray, trayNote);
        return section;
    }

    // ── Reset row ──────────────────────────────────────────────────────

    private HBox buildResetRow() {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);

        Button btnReset = new Button("reset to defaults");
        btnReset.getStyleClass().add("af-btn");
        btnReset.setStyle("-fx-text-fill: #f44336;");
        btnReset.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Reset all settings to default values?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    // AppConfig.reset() cria novo AppSettings com defaults e salva.
                    // loadSettings() lê esse novo objeto com loading=true,
                    // impedindo que os listeners sobrescrevam os defaults.
                    AppConfig.getInstance().reset();
                    loadSettings();
                }
            });
        });

        row.getChildren().add(btnReset);
        return row;
    }

    // ── Load / Save ────────────────────────────────────────────────────

    private void loadSettings() {
        // Bloqueia saveSettings() enquanto carrega — evita que os listeners
        // dos sliders disparem e sobrescrevam os valores recém carregados/resetados
        loading = true;
        try {
            AppSettings s = AppConfig.getInstance().getSettings();

            // Request
            int threads = clamp(s.getParallelThreads(), 1, 8);
            threadsSlider.setValue(threads);
            threadsValue.setText(threads + (threads == 1 ? " thread" : " threads"));

            int delay = clamp(s.getDelayBetweenRequestsMs(), 0, 2000);
            delaySlider.setValue(delay);
            delayValue.setText(delay + " ms");

            int timeout = clamp(s.getRequestTimeoutMs(), 5000, 30000);
            timeoutSlider.setValue(timeout);
            timeoutValue.setText(timeout + " ms");

            // Retry
            int maxRetries = clamp(s.getMaxRetries(), 0, 5);
            maxRetriesSlider.setValue(maxRetries);
            maxRetriesValue.setText(maxRetries + (maxRetries == 1 ? " retry" : " retries"));

            int retryDelay = clamp(s.getRetryDelaySeconds(), 15, 120);
            retryDelaySlider.setValue(retryDelay);
            retryDelayValue.setText(retryDelay + " s");

            // Behavior
            chkMinimizeToTray.setSelected(s.isMinimizeToTray());

        } finally {
            loading = false;
        }

        // Aplica o tooltip gerado pelo RateLimitService após carregar os valores
        updateRetryDelayTooltip();
    }

    private void saveSettings() {
        // Não salva enquanto loadSettings() estiver rodando
        if (loading) return;

        AppSettings s = AppConfig.getInstance().getSettings();
        s.setParallelThreads((int) threadsSlider.getValue());
        s.setDelayBetweenRequestsMs((int) delaySlider.getValue());
        s.setRequestTimeoutMs((int) timeoutSlider.getValue());
        s.setMaxRetries((int) maxRetriesSlider.getValue());
        s.setRetryDelaySeconds((int) retryDelaySlider.getValue());
        s.setMinimizeToTray(chkMinimizeToTray.isSelected());

        AppConfig.getInstance().save();
    }

    /**
     * Atualiza o tooltip do slider de retry com a descrição gerada pelo RateLimitService.
     *
     * Antes: o tooltip era uma nota estática hardcoded no buildRetrySection().
     * Depois: o RateLimitService.getPolicy().describe() gera o texto com os
     * valores reais atuais, incluindo o backoff máximo calculado.
     *
     * Chamado após saveSettings() e após loadSettings() para manter o tooltip
     * sempre sincronizado com os valores dos sliders.
     */
    private void updateRetryDelayTooltip() {
        String description = RateLimitService.getInstance().getPolicy().describe();
        retryDelaySlider.setTooltip(new Tooltip(description));
        maxRetriesSlider.setTooltip(new Tooltip(description));
    }

    // ── Builders ───────────────────────────────────────────────────────

    private VBox buildSection(String title) {
        VBox section = new VBox(10);
        section.setStyle(
                "-fx-background-color: #232323;" +
                        "-fx-border-color: #333333;" +
                        "-fx-border-width: 1px;" +
                        "-fx-border-radius: 4px;" +
                        "-fx-background-radius: 4px;" +
                        "-fx-padding: 12px 14px 14px 14px;");
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #999999; -fx-font-size: 11px;");
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #333333;");
        section.getChildren().addAll(titleLabel, sep);
        return section;
    }

    private HBox buildSliderRow(String label, Slider slider, Label valueLabel) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");
        lbl.setPrefWidth(170);
        lbl.setMinWidth(170);

        HBox.setHgrow(slider, Priority.ALWAYS);
        valueLabel.setPrefWidth(90);
        valueLabel.setMinWidth(90);
        valueLabel.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(lbl, slider, valueLabel);
        return row;
    }

    private Slider buildSlider(double min, double max, double value, double blockIncrement) {
        Slider s = new Slider(min, max, value);
        s.setBlockIncrement(blockIncrement);
        s.setSnapToTicks(true);
        s.setMajorTickUnit(blockIncrement);
        s.setMinorTickCount(0);
        s.setStyle("-fx-accent: #4a90d9;");
        return s;
    }

    private Label buildValueLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #4a90d9; -fx-font-size: 12px; -fx-font-weight: bold;");
        return lbl;
    }

    private Label buildNote(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #555555; -fx-font-size: 11px;");
        lbl.setWrapText(true);
        return lbl;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}