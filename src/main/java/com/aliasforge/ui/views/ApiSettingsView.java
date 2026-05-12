package com.aliasforge.ui.views;

import com.aliasforge.config.AppConfig;
import com.aliasforge.model.CustomApiSettings;
import com.aliasforge.model.CustomApiSettings.DetectionMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Aba "api settings" — configuração da Custom API e exibição das APIs ativas.
 *
 * A seção Custom API permite ao usuário escolher entre três modos:
 *  - Status Code apenas
 *  - Body Scraping apenas
 *  - Ambos (status code primeiro, scraping como fallback)
 *
 * As configurações são salvas em AppSettings.customApi e persistidas
 * automaticamente via AppConfig em ~/.aliasforge/settings.json.
 */
public class ApiSettingsView extends VBox {

    // ── Custom API fields ──────────────────────────────────────────────
    private TextField        urlField;
    private TextField        delayField;
    private TextField        headersField;
    private CheckBox         enabledCheck;

    // Detection mode
    private ToggleGroup      modeGroup;
    private RadioButton      rbStatusCode;
    private RadioButton      rbBodyScraping;
    private RadioButton      rbBoth;

    // Status code section
    private VBox             statusCodeSection;
    private TextField        availableCodeField;
    private TextField        takenCodeField;
    private TextField        rateLimitCodeField;

    // Body scraping section
    private VBox             bodyScrapingSection;
    private TextField        availableStringField;
    private TextField        takenStringField;
    private TextField        bodyLinesField;

    // Feedback
    private Label            statusLabel;

    // Guard — evita salvar durante loadSettings()
    private boolean loading = false;

    public ApiSettingsView() {
        getStyleClass().add("api-settings-view");
        setFillWidth(true);
        VBox.setVgrow(this, Priority.ALWAYS);
        setPadding(new Insets(16, 20, 16, 20));
        setSpacing(16);
        buildUI();
        loadSettings();
    }

    private void buildUI() {
        getChildren().addAll(
                buildActiveApisSection(),
                buildCustomApiSection(),
                buildComingSoonSection()
        );
    }

    // ── Active APIs ────────────────────────────────────────────────────

    private VBox buildActiveApisSection() {
        VBox section = buildSection("active apis");

        HBox header = new HBox();
        header.setPadding(new Insets(6, 8, 6, 8));
        header.setStyle("-fx-background-color: #252525; " +
                "-fx-border-color: transparent transparent #333333 transparent; -fx-border-width: 1px;");
        header.getChildren().addAll(
                buildColHeader("platform",     150),
                buildColHeader("method",       80),
                buildColHeader("endpoint url", 280),
                buildColHeader("delay (ms)",   90),
                buildColHeader("status",       80)
        );

        HBox minecraftRow = buildReadOnlyApiRow(
                "minecraft", "GET",
                "https://api.mojang.com/users/profiles/minecraft/",
                "600 ms", "active"
        );
        HBox githubRow = buildReadOnlyApiRow(
                "github", "GET",
                "https://api.github.com/users/",
                "1500 ms", "active"
        );
        HBox redditRow = buildReadOnlyApiRow(
                "reddit", "GET",
                "https://www.reddit.com/user/{u}/about.json",
                "1000 ms", "active"
        );
        HBox gunsRow = buildReadOnlyApiRow(
                "guns.lol", "GET",
                "https://guns.lol/",
                "1000 ms", "web check"
        );
        HBox caliberRow = buildReadOnlyApiRow(
                "caliber", "GET",
                "https://caliber.lol/u/",
                "1200 ms", "web check"
        );
        HBox tiktokRow = buildReadOnlyApiRow(
                "tiktok", "GET",
                "https://www.tiktok.com/@",
                "2500 ms", "web check"
        );
        HBox instaRow = buildReadOnlyApiRow(
                "instagram", "GET",
                "https://www.instagram.com/",
                "2500 ms", "web check"
        );

        section.getChildren().addAll(
                header, minecraftRow, githubRow, redditRow,
                gunsRow, caliberRow, tiktokRow, instaRow
        );
        return section;
    }

    // ── Custom API ─────────────────────────────────────────────────────

    private VBox buildCustomApiSection() {
        VBox section = buildSection("custom api");

        Label desc = new Label(
                "Configure your own endpoint. AliasForge calls GET {url}{username} " +
                        "and interprets the response using the mode you choose below.");
        desc.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        desc.setWrapText(true);

        // ── Endpoint URL ───────────────────────────────────────────────
        VBox urlGroup = buildLabeledField("Endpoint URL", "https://api.example.com/users/");
        urlField = (TextField) ((VBox) urlGroup).getChildren().get(1);
        urlField.textProperty().addListener((obs, o, n) -> saveSettings());

        // ── Delay ──────────────────────────────────────────────────────
        VBox delayGroup = buildLabeledField("Delay between requests (ms)", "1000");
        delayField = (TextField) ((VBox) delayGroup).getChildren().get(1);
        delayField.textProperty().addListener((obs, o, n) -> saveSettings());

        // ── Custom Headers ─────────────────────────────────────────────
        VBox headersGroup = buildLabeledField(
                "Custom headers (JSON, optional)",
                "{\"Authorization\": \"Bearer TOKEN\"}");
        headersField = (TextField) ((VBox) headersGroup).getChildren().get(1);
        headersField.textProperty().addListener((obs, o, n) -> saveSettings());

        // ── Detection mode ─────────────────────────────────────────────
        Label modeLbl = new Label("Detection mode");
        modeLbl.setStyle("-fx-text-fill: #999999; -fx-font-size: 11px;");

        modeGroup     = new ToggleGroup();
        rbStatusCode  = new RadioButton("Status Code only");
        rbBodyScraping= new RadioButton("Body Scraping only");
        rbBoth        = new RadioButton("Both (status code first, scraping as fallback)");

        for (RadioButton rb : new RadioButton[]{rbStatusCode, rbBodyScraping, rbBoth}) {
            rb.setToggleGroup(modeGroup);
            rb.getStyleClass().add("af-checkbox");
            rb.setStyle("-fx-text-fill: #aaaaaa;");
        }
        rbBoth.setSelected(true);

        modeGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            updateModeVisibility();
            saveSettings();
        });

        HBox modeRow = new HBox(16, rbStatusCode, rbBodyScraping, rbBoth);
        modeRow.setAlignment(Pos.CENTER_LEFT);
        modeRow.setPadding(new Insets(4, 0, 4, 0));

        // ── Status Code section ────────────────────────────────────────
        statusCodeSection = buildStatusCodeSection();

        // ── Body Scraping section ──────────────────────────────────────
        bodyScrapingSection = buildBodyScrapingSection();

        // ── Enable + Save ──────────────────────────────────────────────
        enabledCheck = new CheckBox("Enable custom API");
        enabledCheck.getStyleClass().add("af-checkbox");
        enabledCheck.setOnAction(e -> saveSettings());

        Button btnSave = new Button("Save & Enable");
        btnSave.getStyleClass().addAll("af-btn", "af-btn-play");
        btnSave.setOnAction(e -> onSaveAndEnable());

        Button btnTest = new Button("Test connection");
        btnTest.getStyleClass().add("af-btn");
        btnTest.setOnAction(e -> onTestConnection());

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 11px;");
        statusLabel.setWrapText(true);

        HBox btnRow = new HBox(8, enabledCheck, btnTest, btnSave);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #333333;");

        section.getChildren().addAll(
                desc,
                urlGroup,
                delayGroup,
                headersGroup,
                modeLbl, modeRow,
                statusCodeSection,
                bodyScrapingSection,
                sep,
                btnRow,
                statusLabel
        );
        return section;
    }

    private VBox buildStatusCodeSection() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8, 0, 4, 0));

        Label title = new Label("Status Code settings");
        title.setStyle("-fx-text-fill: #777777; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label hint = new Label(
                "Which HTTP status codes indicate each result. " +
                        "Common pattern: 404 = available, 200 = taken.");
        hint.setStyle("-fx-text-fill: #555555; -fx-font-size: 11px;");
        hint.setWrapText(true);

        availableCodeField  = buildSmallField("404");
        takenCodeField      = buildSmallField("200");
        rateLimitCodeField  = buildSmallField("429");

        for (TextField f : new TextField[]{availableCodeField, takenCodeField, rateLimitCodeField}) {
            f.textProperty().addListener((obs, o, n) -> saveSettings());
        }

        HBox row = new HBox(16,
                buildInlineField("Available when HTTP", availableCodeField),
                buildInlineField("Taken when HTTP",     takenCodeField),
                buildInlineField("Rate limit when HTTP (0 = ignore)", rateLimitCodeField)
        );
        row.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().addAll(title, hint, row);
        return box;
    }

    private VBox buildBodyScrapingSection() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8, 0, 4, 0));

        Label title = new Label("Body Scraping settings");
        title.setStyle("-fx-text-fill: #777777; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label hint = new Label(
                "Strings to search for in the response body (case-insensitive). " +
                        "\"Available\" signal is checked first. Leave blank to skip that check.");
        hint.setStyle("-fx-text-fill: #555555; -fx-font-size: 11px;");
        hint.setWrapText(true);

        availableStringField = buildWideField("user not found");
        takenStringField     = buildWideField("og:title");
        bodyLinesField       = buildSmallField("80");

        for (TextField f : new TextField[]{availableStringField, takenStringField, bodyLinesField}) {
            f.textProperty().addListener((obs, o, n) -> saveSettings());
        }

        VBox availGroup = buildLabeledFieldRaw("Body contains (available signal)", availableStringField);
        VBox takenGroup = buildLabeledFieldRaw("Body contains (taken signal)",     takenStringField);
        VBox linesGroup = buildLabeledFieldRaw("Max lines to read (0 = all)",      bodyLinesField);

        HBox row = new HBox(16, availGroup, takenGroup, linesGroup);
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(availGroup, Priority.ALWAYS);
        HBox.setHgrow(takenGroup, Priority.ALWAYS);

        box.getChildren().addAll(title, hint, row);
        return box;
    }

    // ── Coming Soon ────────────────────────────────────────────────────

    private VBox buildComingSoonSection() {
        VBox section = buildSection("coming soon");

        String[] platforms = {"discord (requires bot token)", "roblox", "twitter / x"};

        VBox list = new VBox(8);
        for (String name : platforms) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));

            Label badge = new Label("soon");
            badge.setStyle(
                    "-fx-background-color: #2a2a2a;" +
                            "-fx-text-fill: #666666;" +
                            "-fx-font-size: 10px;" +
                            "-fx-padding: 2 6 2 6;" +
                            "-fx-background-radius: 3;");

            Label nameLbl = new Label(name);
            nameLbl.setStyle("-fx-text-fill: #555555; -fx-font-size: 12px;");

            row.getChildren().addAll(badge, nameLbl);
            list.getChildren().add(row);
        }

        section.getChildren().add(list);
        return section;
    }

    // ── Lógica dos botões ──────────────────────────────────────────────

    /**
     * Salva e tenta habilitar a Custom API.
     * Valida os campos antes de marcar como enabled=true.
     */
    private void onSaveAndEnable() {
        saveSettings();

        var cfg = AppConfig.getInstance().getSettings().getCustomApi();

        if (cfg.getEndpointUrl().isBlank()) {
            setStatus("⚠  Endpoint URL is required.", "#ffc107");
            return;
        }
        if (!cfg.isConfigured()) {
            setStatus("⚠  Please configure at least one detection signal " +
                    "(status code or body string).", "#ffc107");
            return;
        }

        cfg.setEnabled(true);
        enabledCheck.setSelected(true);
        AppConfig.getInstance().save();
        setStatus("✓  Custom API saved and enabled.", "#4caf50");
    }

    /**
     * Testa a conexão com o endpoint usando um username de teste "test".
     * Não interpreta o resultado — apenas verifica se o servidor responde.
     */
    private void onTestConnection() {
        saveSettings();
        var cfg = AppConfig.getInstance().getSettings().getCustomApi();

        if (cfg.getEndpointUrl().isBlank()) {
            setStatus("⚠  Set an endpoint URL before testing.", "#ffc107");
            return;
        }

        setStatus("⏳  Testing connection...", "#4a90d9");

        // Executa em thread separada para não bloquear a UI
        Thread t = new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(cfg.getEndpointUrl() + "test");
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "AliasForge/1.0");

                // Aplica headers customizados se configurados
                applyHeadersForTest(conn, cfg.getCustomHeaders());

                int code = conn.getResponseCode();
                conn.disconnect();

                final String msg = "✓  Server responded with HTTP " + code +
                        ". Connection OK — now configure detection rules.";
                javafx.application.Platform.runLater(() -> setStatus(msg, "#4caf50"));

            } catch (java.net.UnknownHostException e) {
                javafx.application.Platform.runLater(() ->
                        setStatus("✗  Could not reach host. Check the URL.", "#f44336"));
            } catch (java.net.SocketTimeoutException e) {
                javafx.application.Platform.runLater(() ->
                        setStatus("✗  Connection timed out.", "#f44336"));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        setStatus("✗  Error: " + e.getMessage(), "#f44336"));
            }
        }, "aliasforge-api-test");
        t.setDaemon(true);
        t.start();
    }

    private void applyHeadersForTest(java.net.HttpURLConnection conn, String headersJson) {
        if (headersJson == null || headersJson.isBlank()) return;
        try {
            com.google.gson.JsonObject obj =
                    com.google.gson.JsonParser.parseString(headersJson.trim()).getAsJsonObject();
            obj.entrySet().forEach(e ->
                    conn.setRequestProperty(e.getKey(), e.getValue().getAsString()));
        } catch (Exception ignored) {}
    }

    // ── Visibilidade das seções de detecção ────────────────────────────

    private void updateModeVisibility() {
        DetectionMode mode = getSelectedMode();
        boolean showStatus  = mode == DetectionMode.STATUS_CODE  || mode == DetectionMode.BOTH;
        boolean showScraping= mode == DetectionMode.BODY_SCRAPING|| mode == DetectionMode.BOTH;

        statusCodeSection.setVisible(showStatus);
        statusCodeSection.setManaged(showStatus);
        bodyScrapingSection.setVisible(showScraping);
        bodyScrapingSection.setManaged(showScraping);
    }

    private DetectionMode getSelectedMode() {
        if (rbStatusCode.isSelected())   return DetectionMode.STATUS_CODE;
        if (rbBodyScraping.isSelected()) return DetectionMode.BODY_SCRAPING;
        return DetectionMode.BOTH;
    }

    // ── Load / Save ────────────────────────────────────────────────────

    private void loadSettings() {
        loading = true;
        try {
            var cfg = AppConfig.getInstance().getSettings().getCustomApi();

            urlField.setText(cfg.getEndpointUrl());
            delayField.setText(String.valueOf(cfg.getDelayMs()));
            headersField.setText(cfg.getCustomHeaders() != null ? cfg.getCustomHeaders() : "");
            enabledCheck.setSelected(cfg.isEnabled());

            // Modo de detecção
            switch (cfg.getDetectionMode()) {
                case STATUS_CODE   -> rbStatusCode.setSelected(true);
                case BODY_SCRAPING -> rbBodyScraping.setSelected(true);
                default            -> rbBoth.setSelected(true);
            }

            // Status code
            availableCodeField.setText(String.valueOf(cfg.getAvailableStatusCode()));
            takenCodeField.setText(String.valueOf(cfg.getTakenStatusCode()));
            rateLimitCodeField.setText(String.valueOf(cfg.getRateLimitStatusCode()));

            // Body scraping
            availableStringField.setText(cfg.getAvailableBodyString() != null
                    ? cfg.getAvailableBodyString() : "");
            takenStringField.setText(cfg.getTakenBodyString() != null
                    ? cfg.getTakenBodyString() : "");
            bodyLinesField.setText(String.valueOf(cfg.getBodyReadLines()));

            // Atualiza visibilidade após carregar
            updateModeVisibility();

            // Mostra status se já estiver configurado e habilitado
            if (cfg.isEnabled() && cfg.isConfigured()) {
                setStatus("✓  Custom API is active.", "#4caf50");
            } else if (cfg.isEnabled() && !cfg.isConfigured()) {
                setStatus("⚠  Enabled but not fully configured.", "#ffc107");
            }

        } finally {
            loading = false;
        }
    }

    private void saveSettings() {
        if (loading) return;

        var cfg = AppConfig.getInstance().getSettings().getCustomApi();

        cfg.setEndpointUrl(urlField.getText().trim());
        cfg.setDelayMs(parseIntSafe(delayField.getText(), 1000));
        cfg.setCustomHeaders(headersField.getText().trim());
        cfg.setEnabled(enabledCheck.isSelected());
        cfg.setDetectionMode(getSelectedMode());

        // Status code
        cfg.setAvailableStatusCode(parseIntSafe(availableCodeField.getText(), 404));
        cfg.setTakenStatusCode(parseIntSafe(takenCodeField.getText(), 200));
        cfg.setRateLimitStatusCode(parseIntSafe(rateLimitCodeField.getText(), 429));

        // Body scraping
        cfg.setAvailableBodyString(availableStringField.getText().trim());
        cfg.setTakenBodyString(takenStringField.getText().trim());
        cfg.setBodyReadLines(parseIntSafe(bodyLinesField.getText(), 80));

        AppConfig.getInstance().save();
    }

    // ── Helpers de UI ──────────────────────────────────────────────────

    private void setStatus(String text, String color) {
        statusLabel.setText(text);
        statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11px;");
    }

    private VBox buildSection(String title) {
        VBox section = new VBox(10);
        section.setStyle(
                "-fx-background-color: #232323; -fx-border-color: #333333; " +
                        "-fx-border-width: 1px; -fx-border-radius: 4px; " +
                        "-fx-background-radius: 4px; -fx-padding: 12px 14px 14px 14px;");
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #999999; -fx-font-size: 11px;");
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #333333;");
        section.getChildren().addAll(titleLabel, sep);
        return section;
    }

    /** Cria um VBox label + field usando um prompt de placeholder. */
    private VBox buildLabeledField(String label, String placeholder) {
        TextField field = new TextField();
        field.setPromptText(placeholder);
        field.getStyleClass().add("af-input");
        return buildLabeledFieldRaw(label, field);
    }

    private VBox buildLabeledFieldRaw(String label, TextField field) {
        VBox box = new VBox(4);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #777777; -fx-font-size: 11px;");
        box.getChildren().addAll(lbl, field);
        return box;
    }

    /** Cria um HBox "label + field" compacto para campos numéricos em linha. */
    private HBox buildInlineField(String label, TextField field) {
        VBox box = new VBox(3);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #777777; -fx-font-size: 10px;");
        box.getChildren().addAll(lbl, field);
        HBox wrapper = new HBox(box);
        return wrapper;
    }

    private TextField buildSmallField(String defaultValue) {
        TextField f = new TextField(defaultValue);
        f.getStyleClass().add("af-input");
        f.setPrefWidth(80);
        return f;
    }

    private TextField buildWideField(String placeholder) {
        TextField f = new TextField();
        f.setPromptText(placeholder);
        f.getStyleClass().add("af-input");
        f.setPrefWidth(200);
        return f;
    }

    private HBox buildReadOnlyApiRow(String name, String method,
                                     String url, String delay, String statusText) {
        HBox row = new HBox();
        row.setPadding(new Insets(7, 8, 7, 8));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: #1e1e1e; " +
                "-fx-border-color: transparent transparent #2a2a2a transparent; -fx-border-width: 1px;");
        row.setOnMouseEntered(e -> row.setStyle(
                "-fx-background-color: #252525; " +
                        "-fx-border-color: transparent transparent #2a2a2a transparent; -fx-border-width: 1px;"));
        row.setOnMouseExited(e -> row.setStyle(
                "-fx-background-color: #1e1e1e; " +
                        "-fx-border-color: transparent transparent #2a2a2a transparent; -fx-border-width: 1px;"));

        String statusColor = switch (statusText) {
            case "active"    -> "#4caf50";
            case "web check" -> "#4a90d9";
            default          -> "#888888";
        };

        row.getChildren().addAll(
                buildColLabel(name,       150, "#cccccc"),
                buildColLabel(method,     80,  "#4a90d9"),
                buildColLabel(url,        280, "#777777"),
                buildColLabel(delay,      90,  "#aaaaaa"),
                buildColLabel(statusText, 80,  statusColor)
        );
        return row;
    }

    private Label buildColHeader(String text, double width) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #555555; -fx-font-size: 11px;");
        lbl.setPrefWidth(width);
        return lbl;
    }

    private Label buildColLabel(String text, double width, String color) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
        lbl.setPrefWidth(width);
        lbl.setMaxWidth(width);
        return lbl;
    }

    private int parseIntSafe(String text, int defaultValue) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}