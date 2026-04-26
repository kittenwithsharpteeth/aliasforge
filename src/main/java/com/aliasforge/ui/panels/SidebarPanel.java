package com.aliasforge.ui.panels;

import com.aliasforge.model.GeneratorConfig;
import com.aliasforge.model.Platform;
import com.aliasforge.ui.AppController;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;

public class SidebarPanel extends VBox {

    private final AppController controller;

    private TextField        quantityField;
    private TextField        minLenField;
    private TextField        maxLenField;
    private ComboBox<String> modeCombo;
    private ComboBox<String> algorithmPlatformCombo;

    private CheckBox chkStartsWith; private TextField startsWith;
    private CheckBox chkEndsWith;   private TextField endsWith;
    private CheckBox chkContains;   private TextField contains;

    private CheckBox chkLetters, chkNumbers, chkUnderscore, chkPeriod;

    private TextField        manualInput;
    private ComboBox<String> manualPlatformCombo;

    private Label manualApiIndicator;
    private Label algorithmApiIndicator;

    public SidebarPanel(AppController controller) {
        this.controller = controller;
        getStyleClass().add("sidebar-panel");
        setPrefWidth(300);
        setMinWidth(260);
        setMaxWidth(380);
        setFillWidth(true);
        setSpacing(0);
        buildUI();
    }

    private void buildUI() {
        VBox quickVerifier  = buildQuickVerifier();
        Separator sep       = new Separator();
        sep.getStyleClass().add("af-separator");
        VBox optionsHeader  = buildOptionsHeader();  // VBox agora
        VBox sections       = buildSections();
        VBox.setVgrow(sections, Priority.ALWAYS);
        getChildren().addAll(quickVerifier, sep, optionsHeader, sections);
    }

    // ── Quick Manual Verifier ──────────────────────────────────────────

    private VBox buildQuickVerifier() {
        VBox box = new VBox(6);
        box.getStyleClass().add("af-section");
        box.setPadding(new Insets(8, 10, 8, 10));

        // Header
        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("quick manual verifier");
        title.getStyleClass().add("af-section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        manualPlatformCombo = buildPlatformCombo("minecraft");
        manualPlatformCombo.setOnAction(e -> updateManualApiIndicator());
        header.getChildren().addAll(title, spacer, manualPlatformCombo);

        // API badge row
        HBox apiRow = new HBox(6);
        apiRow.setAlignment(Pos.CENTER_LEFT);
        Label apiLbl = new Label("using api:");
        apiLbl.getStyleClass().add("af-label-muted");
        manualApiIndicator = buildApiIndicator("minecraft");
        apiRow.getChildren().addAll(apiLbl, manualApiIndicator);

        // Input
        HBox inputRow = new HBox(6);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        manualInput = new TextField();
        manualInput.setPromptText("exampleuser");
        manualInput.getStyleClass().add("af-input");
        HBox.setHgrow(manualInput, Priority.ALWAYS);
        Button addBtn = new Button("+");
        addBtn.getStyleClass().add("af-btn");
        addBtn.setOnAction(e -> addManualUsername());
        manualInput.setOnAction(e -> addManualUsername());
        inputRow.getChildren().addAll(manualInput, addBtn);

        // Actions
        HBox actionBar = new HBox(6);
        actionBar.setAlignment(Pos.CENTER_LEFT);
        Button btnCheck = new Button("check");
        Button btnClear = new Button("clear");
        btnCheck.getStyleClass().add("af-btn");
        btnClear.getStyleClass().add("af-btn");
        btnCheck.setOnAction(e -> addManualUsername());

        TableView<ManualRow> miniTable = buildMiniTable();
        miniTable.setPrefHeight(140);
        btnClear.setOnAction(e -> miniTable.getItems().clear());

        actionBar.getChildren().addAll(btnCheck, btnClear);
        box.getChildren().addAll(header, apiRow, inputRow, actionBar, miniTable);
        return box;
    }

    private void addManualUsername() {
        String username = manualInput.getText().trim();
        if (username.isEmpty()) return;
        Platform platform = Platform.fromString(
                manualPlatformCombo.getValue() != null
                        ? manualPlatformCombo.getValue() : "minecraft");
        controller.addManualTask(username, platform);
        manualInput.clear();
    }

    private void updateManualApiIndicator() {
        String platform = manualPlatformCombo.getValue();
        if (platform == null) return;
        manualApiIndicator.setText("● " + platform);
        applyIndicatorStyle(manualApiIndicator, platform);
    }

    @SuppressWarnings("unchecked")
    private TableView<ManualRow> buildMiniTable() {
        TableView<ManualRow> tv = new TableView<>();
        tv.getStyleClass().add("af-table");
        tv.setEditable(true);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.setFixedCellSize(24);
        tv.setPlaceholder(new Label("") {{
            setStyle("-fx-text-fill: transparent;");
        }});

        TableColumn<ManualRow, Boolean> colCheck = new TableColumn<>("");
        colCheck.setCellValueFactory(c -> c.getValue().selectedProperty());
        colCheck.setCellFactory(CheckBoxTableCell.forTableColumn(colCheck));
        colCheck.setMaxWidth(30); colCheck.setMinWidth(30); colCheck.setResizable(false);

        TableColumn<ManualRow, String> colName = new TableColumn<>("username");
        colName.setCellValueFactory(c -> c.getValue().nameProperty());

        TableColumn<ManualRow, String> colStatus = new TableColumn<>("status");
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
        colStatus.setCellFactory(col -> new MiniStatusCell());
        colStatus.setPrefWidth(80);

        tv.getColumns().addAll(colCheck, colName, colStatus);

        controller.getResults().addListener(
                (javafx.collections.ListChangeListener<com.aliasforge.model.UsernameResult>) change -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            for (var r : change.getAddedSubList()) {
                                if ("manual".equals(r.getOrigin())) updateOrAddMiniRow(tv, r);
                            }
                        }
                        if (change.wasReplaced()) {
                            for (int i = change.getFrom(); i < change.getTo(); i++) {
                                var r = controller.getResults().get(i);
                                if ("manual".equals(r.getOrigin())) updateOrAddMiniRow(tv, r);
                            }
                        }
                    }
                });

        return tv;
    }

    private void updateOrAddMiniRow(TableView<ManualRow> tv,
                                    com.aliasforge.model.UsernameResult r) {
        for (ManualRow row : tv.getItems()) {
            if (row.nameProperty().get().equalsIgnoreCase(r.getUsername())) {
                row.statusProperty().set(r.getStatus().getDisplayName());
                return;
            }
        }
        tv.getItems().add(0, new ManualRow(r.getUsername(), r.getStatus().getDisplayName()));
    }

    // ── Algorithm Options header — agora VBox ──────────────────────────

    private VBox buildOptionsHeader() {
        VBox wrapper = new VBox(0);
        wrapper.setStyle("-fx-background-color: #222222;");

        // Linha principal
        HBox header = new HBox(6);
        header.getStyleClass().add("af-options-header");
        header.setPadding(new Insets(6, 10, 4, 10));
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("algorithm options");
        title.getStyleClass().add("af-section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        algorithmPlatformCombo = buildPlatformCombo("minecraft");
        algorithmPlatformCombo.setOnAction(e -> updateAlgorithmApiIndicator());

        Label platformLbl = new Label("platform:");
        platformLbl.getStyleClass().add("af-label-muted");
        header.getChildren().addAll(title, spacer, platformLbl, algorithmPlatformCombo);

        // API badge row
        HBox apiRow = new HBox(6);
        apiRow.setPadding(new Insets(2, 10, 6, 10));
        apiRow.setAlignment(Pos.CENTER_LEFT);
        apiRow.setStyle("-fx-background-color: #222222; " +
                "-fx-border-color: transparent transparent #333333 transparent; -fx-border-width: 1px;");
        Label apiLbl = new Label("using api:");
        apiLbl.getStyleClass().add("af-label-muted");
        algorithmApiIndicator = buildApiIndicator("minecraft");
        apiRow.getChildren().addAll(apiLbl, algorithmApiIndicator);

        wrapper.getChildren().addAll(header, apiRow);
        return wrapper;
    }

    private void updateAlgorithmApiIndicator() {
        String platform = algorithmPlatformCombo.getValue();
        if (platform == null) return;
        algorithmApiIndicator.setText("● " + platform);
        applyIndicatorStyle(algorithmApiIndicator, platform);
    }

    // ── Seções colapsáveis ─────────────────────────────────────────────

    private VBox buildSections() {
        VBox sections = new VBox(0);
        ScrollPane scroll = new ScrollPane(sections);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("af-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sections.getChildren().addAll(
                buildCollapsibleSection("generation",  buildGenerationContent()),
                buildCollapsibleSection("filters",     buildFiltersContent()),
                buildCollapsibleSection("characters",  buildCharactersContent())
        );
        VBox wrapper = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return wrapper;
    }

    private VBox buildCollapsibleSection(String title, VBox content) {
        VBox section = new VBox(0);
        section.getStyleClass().add("af-collapsible");
        HBox header = new HBox(6);
        header.getStyleClass().add("af-collapsible-header");
        header.setPadding(new Insets(6, 10, 6, 10));
        header.setAlignment(Pos.CENTER_LEFT);
        Label arrow = new Label("∨");
        arrow.getStyleClass().add("af-collapse-arrow");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("af-collapsible-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(arrow, titleLabel, spacer);
        header.setOnMouseClicked(e -> {
            boolean visible = content.isVisible();
            content.setVisible(!visible);
            content.setManaged(!visible);
            arrow.setText(visible ? "›" : "∨");
        });
        section.getChildren().addAll(header, content);
        return section;
    }

    // ── Generation ─────────────────────────────────────────────────────

    private VBox buildGenerationContent() {
        VBox box = new VBox(8);
        box.getStyleClass().add("af-section-content");
        box.setPadding(new Insets(8, 14, 10, 14));

        quantityField = buildNumberField("20");
        minLenField   = buildNumberField("3");
        maxLenField   = buildNumberField("5");

        minLenField.focusedProperty().addListener((obs, was, is) -> { if (!is) validateMinMax(); });
        maxLenField.focusedProperty().addListener((obs, was, is) -> { if (!is) validateMinMax(); });

        box.getChildren().addAll(
                buildSpinnerRow("quantity",   quantityField,
                        () -> stepField(quantityField, 1,  1, 9999),
                        () -> stepField(quantityField, -1, 1, 9999)),
                buildSpinnerRow("length min", minLenField,
                        () -> { int max = parseField(maxLenField, 5); stepField(minLenField, 1, 1, max); },
                        () -> stepField(minLenField, -1, 1, 16)),
                buildSpinnerRow("length max", maxLenField,
                        () -> stepField(maxLenField, 1, 1, 16),
                        () -> { int min = parseField(minLenField, 1); stepField(maxLenField, -1, min, 16); })
        );

        modeCombo = new ComboBox<>();
        modeCombo.getItems().addAll("random", "pronounceable");
        modeCombo.setValue("random");
        modeCombo.getStyleClass().add("af-combo");

        HBox modeRow = new HBox(6);
        modeRow.setAlignment(Pos.CENTER_LEFT);
        Label modeLbl = new Label("mode");
        modeLbl.getStyleClass().add("af-label");
        modeLbl.setPrefWidth(80);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        modeRow.getChildren().addAll(modeLbl, spacer, modeCombo);
        box.getChildren().add(modeRow);

        return box;
    }

    private void validateMinMax() {
        int min = Math.max(1, Math.min(parseField(minLenField, 3), 16));
        int max = Math.max(1, Math.min(parseField(maxLenField, 5), 16));
        if (min > max) max = min;
        minLenField.setText(String.valueOf(min));
        maxLenField.setText(String.valueOf(max));
    }

    // ── Filters ────────────────────────────────────────────────────────

    private VBox buildFiltersContent() {
        VBox box = new VBox(8);
        box.getStyleClass().add("af-section-content");
        box.setPadding(new Insets(8, 14, 10, 14));

        chkStartsWith = new CheckBox(); startsWith = new TextField();
        chkEndsWith   = new CheckBox(); endsWith   = new TextField();
        chkContains   = new CheckBox(); contains   = new TextField();

        for (TextField f : new TextField[]{startsWith, endsWith, contains}) {
            f.getStyleClass().add("af-input"); f.setPrefWidth(100);
        }
        for (CheckBox c : new CheckBox[]{chkStartsWith, chkEndsWith, chkContains}) {
            c.getStyleClass().add("af-checkbox");
        }
        startsWith.setPromptText("prefix");
        endsWith.setPromptText("suffix");
        contains.setPromptText("substring");

        box.getChildren().addAll(
                buildFilterRow("starts with", chkStartsWith, startsWith),
                buildFilterRow("ends with",   chkEndsWith,   endsWith),
                buildFilterRow("contains",    chkContains,   contains)
        );
        return box;
    }

    // ── Characters ─────────────────────────────────────────────────────

    private VBox buildCharactersContent() {
        VBox box = new VBox(8);
        box.getStyleClass().add("af-section-content");
        box.setPadding(new Insets(8, 14, 10, 14));

        chkLetters    = new CheckBox(); chkLetters.setSelected(true);
        chkNumbers    = new CheckBox();
        chkUnderscore = new CheckBox();
        chkPeriod     = new CheckBox();

        for (CheckBox c : new CheckBox[]{chkLetters, chkNumbers, chkUnderscore, chkPeriod}) {
            c.getStyleClass().add("af-checkbox");
        }

        box.getChildren().addAll(
                buildCheckRow("letters",    "a - z", chkLetters),
                buildCheckRow("numbers",    "0 - 9", chkNumbers),
                buildCheckRow("underscore", "_",     chkUnderscore),
                buildCheckRow("period",     ".",     chkPeriod)
        );
        return box;
    }

    // ── buildConfig() ──────────────────────────────────────────────────

    public GeneratorConfig buildConfig() {
        validateMinMax();
        GeneratorConfig config = new GeneratorConfig();
        config.setQuantity(parseField(quantityField, 20));
        config.setMinLength(parseField(minLenField, 3));
        config.setMaxLength(parseField(maxLenField, 5));
        config.setMode("pronounceable".equals(modeCombo.getValue())
                ? GeneratorConfig.Mode.PRONOUNCEABLE : GeneratorConfig.Mode.RANDOM);
        config.setStartsWith(chkStartsWith.isSelected() ? startsWith.getText() : "");
        config.setEndsWith(chkEndsWith.isSelected()     ? endsWith.getText()   : "");
        config.setContains(chkContains.isSelected()     ? contains.getText()   : "");
        config.setUseLetters(chkLetters.isSelected());
        config.setUseNumbers(chkNumbers.isSelected());
        config.setUseUnderscore(chkUnderscore.isSelected());
        config.setUsePeriod(chkPeriod.isSelected());
        config.setPlatform(Platform.fromString(
                algorithmPlatformCombo.getValue() != null
                        ? algorithmPlatformCombo.getValue() : "minecraft"));
        return config;
    }

    // ── Helpers de UI ──────────────────────────────────────────────────

    private Label buildApiIndicator(String platform) {
        Label lbl = new Label("● " + platform);
        applyIndicatorStyle(lbl, platform);
        return lbl;
    }

    private void applyIndicatorStyle(Label lbl, String platform) {
        String color = switch (platform) {
            case "minecraft" -> "#4a90d9";
            case "custom"    -> "#9c27b0";
            default          -> "#888888";
        };
        String bg = switch (platform) {
            case "minecraft" -> "#1a2a3a";
            case "custom"    -> "#2a1a3a";
            default          -> "#2a2a2a";
        };
        lbl.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 11px;" +
                        "-fx-background-color: %s;" +
                        "-fx-background-radius: 3; -fx-padding: 2 8 2 8;", color, bg));
    }

    private HBox buildSpinnerRow(String label, TextField field,
                                 Runnable onPlus, Runnable onMinus) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("af-label");
        lbl.setPrefWidth(80);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button minus = new Button("<");
        minus.getStyleClass().add("af-btn-small");
        minus.setOnAction(e -> onMinus.run());
        Button plus = new Button(">");
        plus.getStyleClass().add("af-btn-small");
        plus.setOnAction(e -> onPlus.run());
        row.getChildren().addAll(lbl, spacer, minus, field, plus);
        return row;
    }

    private TextField buildNumberField(String defaultValue) {
        TextField field = new TextField(defaultValue);
        field.getStyleClass().add("af-input");
        field.setPrefWidth(50);
        field.setAlignment(Pos.CENTER);
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) field.setText(oldVal);
        });
        return field;
    }

    private void stepField(TextField field, int delta, int min, int max) {
        int next = Math.max(min, Math.min(max, parseField(field, min) + delta));
        field.setText(String.valueOf(next));
    }

    private int parseField(TextField field, int defaultVal) {
        try {
            String text = field.getText().trim();
            return text.isEmpty() ? defaultVal : Integer.parseInt(text);
        } catch (NumberFormatException e) { return defaultVal; }
    }

    private HBox buildFilterRow(String label, CheckBox chk, TextField field) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("af-label");
        lbl.setPrefWidth(80);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(lbl, spacer, chk, field);
        return row;
    }

    private HBox buildCheckRow(String label, String hint, CheckBox chk) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("af-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label hintLbl = new Label(hint);
        hintLbl.getStyleClass().add("af-label-muted");
        row.getChildren().addAll(chk, lbl, spacer, hintLbl);
        return row;
    }

    private ComboBox<String> buildPlatformCombo(String defaultValue) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll("minecraft", "custom");
        combo.setValue(defaultValue);
        combo.getStyleClass().add("af-combo-platform");
        combo.setPrefWidth(110);
        return combo;
    }

    // ── ManualRow ──────────────────────────────────────────────────────

    public static class ManualRow {
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        private final SimpleStringProperty  name     = new SimpleStringProperty();
        private final SimpleStringProperty  status   = new SimpleStringProperty();

        public ManualRow(String name, String status) {
            this.name.set(name); this.status.set(status);
        }

        public SimpleBooleanProperty selectedProperty() { return selected; }
        public SimpleStringProperty  nameProperty()     { return name; }
        public SimpleStringProperty  statusProperty()   { return status; }
    }

    private static class MiniStatusCell extends TableCell<ManualRow, String> {
        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);
            if (empty || status == null || status.isEmpty()) { setText(null); setStyle(""); return; }
            setText(status);
            String color = switch (status) {
                case "available"  -> "#4caf50";
                case "taken"      -> "#f44336";
                case "rate limit" -> "#ffc107";
                case "error"      -> "#757575";
                case "checking"   -> "#2196f3";
                default           -> "#aaaaaa";
            };
            setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11px;");
        }
    }
}