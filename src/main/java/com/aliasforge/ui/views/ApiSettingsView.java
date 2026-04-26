package com.aliasforge.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ApiSettingsView extends VBox {

    private record ApiDefault(String name, String method, String url, int delay) {}

    private static final ApiDefault[] API_DEFAULTS = {
            new ApiDefault("minecraft", "GET", "https://api.mojang.com/users/profiles/minecraft/",  600),
            new ApiDefault("discord",   "—",  "requires Discord Bot Token",                         0),
            new ApiDefault("roblox",    "GET", "https://www.roblox.com/users/profile?username=",    600),
            new ApiDefault("instagram", "GET", "https://www.instagram.com/",                        1500),
    };

    public ApiSettingsView() {
        getStyleClass().add("api-settings-view");
        setFillWidth(true);
        VBox.setVgrow(this, Priority.ALWAYS);
        setPadding(new Insets(16, 20, 16, 20));
        setSpacing(16);
        buildUI();
    }

    private void buildUI() {
        getChildren().addAll(
                buildApiListSection(),
                buildAddApiSection()
        );
    }

    // ── Configured APIs ────────────────────────────────────────────────

    private VBox buildApiListSection() {
        VBox section = buildSection("configured apis");

        // Nota informativa sobre delay
        Label note = new Label(
                "ⓘ  The delay column controls how long AliasForge waits between requests to that platform. " +
                        "Higher values reduce rate limit risk.");
        note.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        note.setWrapText(true);

        // Header
        HBox header = new HBox();
        header.setPadding(new Insets(6, 8, 6, 8));
        header.setStyle("-fx-background-color: #252525; -fx-border-color: transparent transparent #333333 transparent; -fx-border-width: 1px;");
        header.getChildren().addAll(
                buildColHeader("platform",     150),
                buildColHeader("method",       80),
                buildColHeader("endpoint url", 260),
                buildColHeader("delay (ms)",   90),
                buildColHeader("enabled",      70),
                buildColHeader("actions",      130)
        );

        // Linhas
        VBox rows = new VBox(0);
        for (ApiDefault api : API_DEFAULTS) {
            boolean isDiscord = "discord".equals(api.name());
            rows.getChildren().add(buildApiRow(api, isDiscord));
        }

        section.getChildren().addAll(note, header, rows);
        return section;
    }

    // ── Add New API ────────────────────────────────────────────────────

    private VBox buildAddApiSection() {
        VBox section = buildSection("add new api");

        HBox row1 = new HBox(12);
        row1.setAlignment(Pos.CENTER_LEFT);
        VBox nameBox   = buildInputGroup("platform name", "e.g. twitter");
        VBox methodBox = buildComboGroup("method", new String[]{"GET", "POST", "HEAD"}, "GET");
        HBox.setHgrow(nameBox, Priority.ALWAYS);
        row1.getChildren().addAll(nameBox, methodBox);

        VBox urlBox = buildInputGroup("endpoint url", "https://api.example.com/{username}");

        HBox row3 = new HBox(12);
        row3.setAlignment(Pos.CENTER_LEFT);
        VBox delayBox = buildInputGroup("delay (ms)", "600");
        VBox availBox = buildComboGroup("available when http", new String[]{"404", "200", "302"}, "404");
        VBox takenBox = buildComboGroup("taken when http",     new String[]{"200", "404", "302"}, "200");
        HBox.setHgrow(delayBox, Priority.ALWAYS);
        row3.getChildren().addAll(delayBox, availBox, takenBox);

        VBox headersBox = buildInputGroup("custom headers (JSON)", "{\"User-Agent\": \"AliasForge/1.0\"}");

        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button btnTest = new Button("test endpoint");
        Button btnAdd  = new Button("+ add api");
        btnTest.getStyleClass().add("af-btn");
        btnAdd.getStyleClass().addAll("af-btn", "af-btn-play");
        btnTest.setOnAction(e -> showInfo("Test Endpoint",
                "Endpoint testing will be available in a future update."));
        btnAdd.setOnAction(e -> showInfo("Add API",
                "Custom API support coming soon!"));
        btnRow.getChildren().addAll(btnTest, btnAdd);

        section.getChildren().addAll(row1, urlBox, row3, headersBox, btnRow);
        return section;
    }

    // ── API Row ────────────────────────────────────────────────────────

    private HBox buildApiRow(ApiDefault api, boolean disabled) {
        HBox row = new HBox();
        row.setPadding(new Insets(7, 8, 7, 8));
        row.setAlignment(Pos.CENTER_LEFT);
        setRowStyle(row, false);

        row.setOnMouseEntered(e -> setRowStyle(row, true));
        row.setOnMouseExited(e  -> setRowStyle(row, false));

        // Campos editáveis (ocultos inicialmente)
        TextField urlField   = new TextField(api.url());
        TextField delayField = new TextField(disabled ? "" : String.valueOf(api.delay()));
        urlField.getStyleClass().add("af-input");
        delayField.getStyleClass().add("af-input");
        urlField.setPrefWidth(240);
        delayField.setPrefWidth(70);
        urlField.setVisible(false);   urlField.setManaged(false);
        delayField.setVisible(false); delayField.setManaged(false);

        // Labels
        Label nameLabel  = buildColLabel(api.name(),  150, "#cccccc");
        Label methodLabel= buildColLabel(
                disabled ? "—" : api.method(), 80,
                disabled ? "#555555" : "#4a90d9");
        Label urlLabel   = buildColLabel(api.url(),   260,
                disabled ? "#555555" : "#777777");
        urlLabel.setMaxWidth(240);
        Label delayLabel = buildColLabel(
                disabled ? "n/a" : api.delay() + " ms", 90,
                disabled ? "#555555" : "#aaaaaa");

        // Discord: badge especial
        if (disabled) {
            Label badge = new Label("requires bot");
            badge.setStyle(
                    "-fx-background-color: #3a2a1a;" +
                            "-fx-text-fill: #ffc107;" +
                            "-fx-font-size: 10px;" +
                            "-fx-padding: 2 6 2 6;" +
                            "-fx-background-radius: 3;");
            urlLabel.setText(""); // limpa url
            HBox urlWithBadge = new HBox(8, urlLabel, badge);
            urlWithBadge.setPrefWidth(260);
            urlWithBadge.setAlignment(Pos.CENTER_LEFT);

            CheckBox enabledChk = new CheckBox();
            enabledChk.setSelected(false);
            enabledChk.setDisable(true);
            enabledChk.getStyleClass().add("af-checkbox");
            HBox enabledBox = new HBox(enabledChk);
            enabledBox.setPrefWidth(70);

            Button btnInfo = new Button("how to setup");
            btnInfo.getStyleClass().add("af-btn-small");
            btnInfo.setOnAction(e -> showInfo("Discord Bot Setup",
                    "To check Discord usernames, you need to:\n\n" +
                            "1. Create a Bot at discord.com/developers\n" +
                            "2. Copy your Bot Token\n" +
                            "3. Paste it in the token field below\n\n" +
                            "Discord Bot integration coming in a future update."));

            HBox actions = new HBox(4, btnInfo);
            actions.setPrefWidth(130);

            row.getChildren().addAll(nameLabel, methodLabel, urlWithBadge, delayLabel, enabledBox, actions);
            return row;
        }

        // Enabled toggle
        CheckBox enabledChk = new CheckBox();
        enabledChk.setSelected(true);
        enabledChk.getStyleClass().add("af-checkbox");
        HBox enabledBox = new HBox(enabledChk);
        enabledBox.setPrefWidth(70);
        enabledBox.setAlignment(Pos.CENTER_LEFT);

        // Botões
        Button btnEdit  = new Button("edit");
        Button btnReset = new Button("reset");
        Button btnSave  = new Button("save");
        btnEdit.getStyleClass().add("af-btn-small");
        btnReset.getStyleClass().add("af-btn-small");
        btnSave.getStyleClass().add("af-btn-small");
        btnReset.setStyle("-fx-text-fill: #ffc107;");
        btnSave.setStyle("-fx-text-fill: #4caf50;");
        btnSave.setVisible(false); btnSave.setManaged(false);

        btnEdit.setOnAction(e -> {
            boolean editing = urlField.isVisible();
            urlField.setVisible(!editing);    urlField.setManaged(!editing);
            delayField.setVisible(!editing);  delayField.setManaged(!editing);
            urlLabel.setVisible(editing);     urlLabel.setManaged(editing);
            delayLabel.setVisible(editing);   delayLabel.setManaged(editing);
            btnSave.setVisible(!editing);     btnSave.setManaged(!editing);
            btnEdit.setText(editing ? "edit" : "cancel");
        });

        btnReset.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Reset \"" + api.name() + "\" to default settings?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    urlField.setText(api.url());
                    delayField.setText(String.valueOf(api.delay()));
                    urlLabel.setText(api.url());
                    delayLabel.setText(api.delay() + " ms");
                    // Fecha modo edição se aberto
                    if (urlField.isVisible()) {
                        urlField.setVisible(false);   urlField.setManaged(false);
                        delayField.setVisible(false); delayField.setManaged(false);
                        urlLabel.setVisible(true);    urlLabel.setManaged(true);
                        delayLabel.setVisible(true);  delayLabel.setManaged(true);
                        btnSave.setVisible(false);    btnSave.setManaged(false);
                        btnEdit.setText("edit");
                    }
                }
            });
        });

        btnSave.setOnAction(e -> {
            String newUrl   = urlField.getText().trim();
            String newDelay = delayField.getText().trim();
            if (!newUrl.isEmpty())   urlLabel.setText(newUrl);
            if (!newDelay.isEmpty()) delayLabel.setText(newDelay + " ms");
            urlField.setVisible(false);   urlField.setManaged(false);
            delayField.setVisible(false); delayField.setManaged(false);
            urlLabel.setVisible(true);    urlLabel.setManaged(true);
            delayLabel.setVisible(true);  delayLabel.setManaged(true);
            btnSave.setVisible(false);    btnSave.setManaged(false);
            btnEdit.setText("edit");
            showInfo("Saved", "API \"" + api.name() + "\" updated.");
        });

        HBox actions = new HBox(4, btnEdit, btnReset, btnSave);
        actions.setPrefWidth(130);
        actions.setAlignment(Pos.CENTER_LEFT);

        StackPane urlStack = new StackPane(urlLabel, urlField);
        urlStack.setPrefWidth(260);
        urlStack.setAlignment(Pos.CENTER_LEFT);

        StackPane delayStack = new StackPane(delayLabel, delayField);
        delayStack.setPrefWidth(90);
        delayStack.setAlignment(Pos.CENTER_LEFT);

        row.getChildren().addAll(nameLabel, methodLabel, urlStack, delayStack, enabledBox, actions);
        return row;
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void setRowStyle(HBox row, boolean hover) {
        String bg = hover ? "#252525" : "#1e1e1e";
        row.setStyle("-fx-background-color: " + bg +
                "; -fx-border-color: transparent transparent #2a2a2a transparent; -fx-border-width: 1px;");
    }

    private VBox buildSection(String title) {
        VBox section = new VBox(10);
        section.setStyle(
                "-fx-background-color: #232323;" +
                        "-fx-border-color: #333333;" +
                        "-fx-border-width: 1px;" +
                        "-fx-border-radius: 4px;" +
                        "-fx-background-radius: 4px;" +
                        "-fx-padding: 12px 14px 14px 14px;"
        );
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #999999; -fx-font-size: 11px;");
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #333333;");
        section.getChildren().addAll(titleLabel, sep);
        return section;
    }

    private VBox buildInputGroup(String label, String placeholder) {
        VBox box = new VBox(4);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("af-label-muted");
        TextField field = new TextField();
        field.setPromptText(placeholder);
        field.getStyleClass().add("af-input");
        box.getChildren().addAll(lbl, field);
        return box;
    }

    private VBox buildComboGroup(String label, String[] options, String selected) {
        VBox box = new VBox(4);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("af-label-muted");
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll(options);
        combo.setValue(selected);
        combo.getStyleClass().add("af-combo");
        box.getChildren().addAll(lbl, combo);
        return box;
    }

    private Label buildColHeader(String text, double width) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
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

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}