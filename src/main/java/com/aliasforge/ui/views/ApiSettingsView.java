package com.aliasforge.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ApiSettingsView extends VBox {

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
                buildActiveApisSection(),
                buildCustomApiSection(),
                buildComingSoonSection()
        );
    }

    // ── Active APIs ────────────────────────────────────────────────────

    private VBox buildActiveApisSection() {
        VBox section = buildSection("active apis");

        Label note = new Label(
                "ⓘ  Only Minecraft is fully supported. Other platforms are coming soon.");
        note.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        note.setWrapText(true);

        // Header
        HBox header = new HBox();
        header.setPadding(new Insets(6, 8, 6, 8));
        header.setStyle("-fx-background-color: #252525; " +
                "-fx-border-color: transparent transparent #333333 transparent; -fx-border-width: 1px;");
        header.getChildren().addAll(
                buildColHeader("platform",     150),
                buildColHeader("method",       80),
                buildColHeader("endpoint url", 280),
                buildColHeader("delay (ms)",   90),
                buildColHeader("enabled",      70),
                buildColHeader("actions",      120)
        );

        // Minecraft row
        HBox minecraftRow = buildApiRow(
                "minecraft", "GET",
                "https://api.mojang.com/users/profiles/minecraft/",
                "600", true, false
        );

        section.getChildren().addAll(note, header, minecraftRow);
        return section;
    }

    // ── Custom API ─────────────────────────────────────────────────────

    private VBox buildCustomApiSection() {
        VBox section = buildSection("custom api");

        Label desc = new Label(
                "Configure your own endpoint. AliasForge will call GET {url}{username} " +
                        "and interpret the HTTP response code.");
        desc.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        desc.setWrapText(true);

        // URL
        VBox urlBox = buildInputGroup("endpoint url", "https://api.example.com/users/");

        // Row: delay + available code + taken code
        HBox row2 = new HBox(12);
        row2.setAlignment(Pos.CENTER_LEFT);
        VBox delayBox = buildInputGroup("delay (ms)", "1000");
        VBox availBox = buildComboGroup("available when", new String[]{"404", "200", "302"}, "404");
        VBox takenBox = buildComboGroup("taken when",     new String[]{"200", "404", "302"}, "200");
        HBox.setHgrow(delayBox, Priority.ALWAYS);
        row2.getChildren().addAll(delayBox, availBox, takenBox);

        // Headers
        VBox headersBox = buildInputGroup(
                "custom headers (JSON, optional)",
                "{\"Authorization\": \"Bearer TOKEN\"}");

        // Enable toggle + save
        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        CheckBox enableChk = new CheckBox("enable custom api");
        enableChk.getStyleClass().add("af-checkbox");
        Button btnSave = new Button("save & enable");
        btnSave.getStyleClass().addAll("af-btn", "af-btn-play");
        btnSave.setOnAction(e -> showInfo("Custom API",
                "Custom API support will be fully wired in a future update. " +
                        "Settings saved for now."));
        btnRow.getChildren().addAll(enableChk, btnSave);

        section.getChildren().addAll(desc, urlBox, row2, headersBox, btnRow);
        return section;
    }

    // ── Coming Soon ────────────────────────────────────────────────────

    private VBox buildComingSoonSection() {
        VBox section = buildSection("coming soon");

        String[] platforms = {"discord (requires bot)", "roblox", "instagram", "twitter/x"};

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

    // ── API Row ────────────────────────────────────────────────────────

    private HBox buildApiRow(String name, String method, String url,
                             String delay, boolean enabled, boolean editable) {
        HBox row = new HBox();
        row.setPadding(new Insets(7, 8, 7, 8));
        row.setAlignment(Pos.CENTER_LEFT);
        setRowStyle(row, false);
        row.setOnMouseEntered(e -> setRowStyle(row, true));
        row.setOnMouseExited(e  -> setRowStyle(row, false));

        // Campos editáveis
        TextField urlField   = new TextField(url);
        TextField delayField = new TextField(delay);
        urlField.getStyleClass().add("af-input");
        delayField.getStyleClass().add("af-input");
        urlField.setPrefWidth(260);
        delayField.setPrefWidth(70);
        urlField.setVisible(false);   urlField.setManaged(false);
        delayField.setVisible(false); delayField.setManaged(false);

        Label nameLabel  = buildColLabel(name,        150, "#cccccc");
        Label methodLabel= buildColLabel(method,      80,  "#4a90d9");
        Label urlLabel   = buildColLabel(url,         280, "#777777");
        urlLabel.setMaxWidth(260);
        Label delayLabel = buildColLabel(delay + " ms", 90, "#aaaaaa");

        CheckBox enabledChk = new CheckBox();
        enabledChk.setSelected(enabled);
        enabledChk.getStyleClass().add("af-checkbox");
        HBox enabledBox = new HBox(enabledChk);
        enabledBox.setPrefWidth(70);
        enabledBox.setAlignment(Pos.CENTER_LEFT);

        Button btnEdit  = new Button("edit");
        Button btnReset = new Button("reset");
        Button btnSave  = new Button("save");
        btnEdit.getStyleClass().add("af-btn-small");
        btnReset.getStyleClass().add("af-btn-small");
        btnSave.getStyleClass().add("af-btn-small");
        btnReset.setStyle("-fx-text-fill: #ffc107;");
        btnSave.setStyle("-fx-text-fill: #4caf50;");
        btnSave.setVisible(false); btnSave.setManaged(false);

        final String defaultUrl   = url;
        final String defaultDelay = delay;

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
                    "Reset \"" + name + "\" to defaults?", ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    urlField.setText(defaultUrl);
                    delayField.setText(defaultDelay);
                    urlLabel.setText(defaultUrl);
                    delayLabel.setText(defaultDelay + " ms");
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
            if (!urlField.getText().isEmpty())   urlLabel.setText(urlField.getText());
            if (!delayField.getText().isEmpty()) delayLabel.setText(delayField.getText() + " ms");
            urlField.setVisible(false);   urlField.setManaged(false);
            delayField.setVisible(false); delayField.setManaged(false);
            urlLabel.setVisible(true);    urlLabel.setManaged(true);
            delayLabel.setVisible(true);  delayLabel.setManaged(true);
            btnSave.setVisible(false);    btnSave.setManaged(false);
            btnEdit.setText("edit");
        });

        HBox actions = new HBox(4, btnEdit, btnReset, btnSave);
        actions.setPrefWidth(130);
        actions.setAlignment(Pos.CENTER_LEFT);

        StackPane urlStack = new StackPane(urlLabel, urlField);
        urlStack.setPrefWidth(280); urlStack.setAlignment(Pos.CENTER_LEFT);

        StackPane delayStack = new StackPane(delayLabel, delayField);
        delayStack.setPrefWidth(90); delayStack.setAlignment(Pos.CENTER_LEFT);

        row.getChildren().addAll(nameLabel, methodLabel, urlStack, delayStack, enabledBox, actions);
        return row;
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void setRowStyle(HBox row, boolean hover) {
        row.setStyle("-fx-background-color: " + (hover ? "#252525" : "#1e1e1e") +
                "; -fx-border-color: transparent transparent #2a2a2a transparent; -fx-border-width: 1px;");
    }

    private VBox buildSection(String title) {
        VBox section = new VBox(10);
        section.setStyle(
                "-fx-background-color: #232323; -fx-border-color: #333333; -fx-border-width: 1px;" +
                        "-fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 12px 14px 14px 14px;");
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