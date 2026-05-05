package com.aliasforge.ui.panels;

import com.aliasforge.config.AppConfig;
import com.aliasforge.model.AppSettings;
import com.aliasforge.model.GeneratorConfig;
import com.aliasforge.model.Platform;
import com.aliasforge.ui.AppController;
import com.aliasforge.ui.components.GroupedPlatformComboBox;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SidebarPanel extends VBox {

    private final AppController controller;
    private final AppSettings   settings = AppConfig.getInstance().getSettings();

    // ── Geração ────────────────────────────────────────────────────────
    private TextField               quantityField;
    private boolean                 isInfinite;
    private TextField               minLenField;
    private TextField               maxLenField;
    private ComboBox<String>        modeCombo;
    private GroupedPlatformComboBox algorithmPlatformCombo;

    // ── Filtros ────────────────────────────────────────────────────────
    private CheckBox chkStartsWith; private TextField startsWith;
    private CheckBox chkEndsWith;   private TextField endsWith;
    private CheckBox chkContains;   private TextField contains;

    // ── Caracteres ─────────────────────────────────────────────────────
    private CheckBox chkLetters, chkNumbers, chkUnderscore, chkPeriod;

    // ── Manual verifier ────────────────────────────────────────────────
    private TextField               manualInput;
    private GroupedPlatformComboBox manualPlatformCombo;
    private TableView<ManualRow>    manualTable;

    // ── Seções reordenáveis ────────────────────────────────────────────
    private VBox sectionsContainer;
    private VBox sectionGeneration;
    private VBox sectionFilters;
    private VBox sectionCharacters;
    private final List<String> sectionOrder = new ArrayList<>();

    // ── Drag state ─────────────────────────────────────────────────────
    private String dragSourceName = null;
    private static final String HEADER_DRAG_OVER_STYLE =
            "-fx-background-color: #2e3d4f; " +
                    "-fx-border-color: #4a90d9 transparent #4a90d9 transparent; " +
                    "-fx-border-width: 1px;";

    public SidebarPanel(AppController controller) {
        this.controller = controller;
        getStyleClass().add("sidebar-panel");
        setPrefWidth(300);
        setMinWidth(260);
        setMaxWidth(380);
        setFillWidth(true);
        setSpacing(0);
        buildUI();
        loadSettings();
    }

    private void buildUI() {
        VBox quickVerifier = buildQuickVerifier();
        Separator sep = new Separator();
        sep.getStyleClass().add("af-separator");
        VBox optionsHeader = buildOptionsHeader();

        VBox generationContent = buildGenerationContent();
        VBox filtersContent    = buildFiltersContent();
        VBox charactersContent = buildCharactersContent();

        sectionGeneration = buildCollapsibleSection("generation",  generationContent);
        sectionFilters    = buildCollapsibleSection("filters",     filtersContent);
        sectionCharacters = buildCollapsibleSection("characters",  charactersContent);

        sectionsContainer = new VBox(0);

        ScrollPane scroll = new ScrollPane(sectionsContainer);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("af-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox scrollWrapper = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox.setVgrow(scrollWrapper, Priority.ALWAYS);

        getChildren().addAll(quickVerifier, sep, optionsHeader, scrollWrapper);
    }

    // ── Quick Manual Verifier ──────────────────────────────────────────

    private VBox buildQuickVerifier() {
        VBox box = new VBox(6);
        box.getStyleClass().add("af-section");
        box.setPadding(new Insets(8, 10, 8, 10));

        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("quick manual verifier");
        title.getStyleClass().add("af-section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        manualPlatformCombo = new GroupedPlatformComboBox("minecraft");
        manualPlatformCombo.setPrefWidth(130);
        manualPlatformCombo.setOnAction(e -> {
            settings.setLastManualPlatform(manualPlatformCombo.getValue());
            AppConfig.getInstance().save();
        });
        header.getChildren().addAll(title, spacer, manualPlatformCombo);

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

        HBox actionBar = new HBox(6);
        actionBar.setAlignment(Pos.CENTER_LEFT);
        Button btnCheck = new Button("check");
        Button btnClear = new Button("clear");
        btnCheck.getStyleClass().add("af-btn");
        btnClear.getStyleClass().add("af-btn");
        btnCheck.setOnAction(e -> addManualUsername());

        manualTable = buildMiniTable();
        manualTable.setPrefHeight(150);
        btnClear.setOnAction(e -> manualTable.getItems().clear());

        actionBar.getChildren().addAll(btnCheck, btnClear);
        box.getChildren().addAll(header, inputRow, actionBar, manualTable);
        return box;
    }

    private void addManualUsername() {
        String username = manualInput.getText().trim();
        if (username.isEmpty()) return;
        Platform platform = manualPlatformCombo.getSelectedPlatform();
        updateOrAddMiniRow(username, "checking", platform.displayName);
        controller.addManualTask(username, platform);
        manualInput.clear();
    }

    @SuppressWarnings("unchecked")
    private TableView<ManualRow> buildMiniTable() {
        TableView<ManualRow> tv = new TableView<>();
        tv.getStyleClass().add("af-table");
        tv.setEditable(true);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.setFixedCellSize(24);
        tv.setPlaceholder(new Label("enter a username above") {{
            setStyle("-fx-text-fill: #555555; -fx-font-size: 11px;");
        }});

        TableColumn<ManualRow, Boolean> colCheck = new TableColumn<>("");
        colCheck.setCellValueFactory(c -> c.getValue().selectedProperty());
        colCheck.setCellFactory(CheckBoxTableCell.forTableColumn(colCheck));
        colCheck.setMaxWidth(28); colCheck.setMinWidth(28); colCheck.setResizable(false);

        TableColumn<ManualRow, String> colName = new TableColumn<>("username");
        colName.setCellValueFactory(c -> c.getValue().nameProperty());

        TableColumn<ManualRow, String> colStatus = new TableColumn<>("status");
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
        colStatus.setCellFactory(col -> new MiniStatusCell());
        colStatus.setPrefWidth(75);

        TableColumn<ManualRow, String> colPlatform = new TableColumn<>("api");
        colPlatform.setCellValueFactory(c -> c.getValue().platformProperty());
        colPlatform.setCellFactory(col -> new MiniPlatformCell());
        colPlatform.setPrefWidth(65);
        colPlatform.setResizable(false);

        tv.getColumns().addAll(colCheck, colName, colStatus, colPlatform);

        controller.getResults().addListener(
                (javafx.collections.ListChangeListener<com.aliasforge.model.UsernameResult>) change -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            for (var r : change.getAddedSubList()) {
                                if (isManualEntry(r.getUsername(), r.getPlatform().displayName))
                                    updateOrAddMiniRow(r.getUsername(),
                                            r.getStatus().getDisplayName(),
                                            r.getPlatform().displayName);
                            }
                        }
                        if (change.wasReplaced()) {
                            for (int i = change.getFrom(); i < change.getTo(); i++) {
                                var r = controller.getResults().get(i);
                                if (isManualEntry(r.getUsername(), r.getPlatform().displayName))
                                    updateOrAddMiniRow(r.getUsername(),
                                            r.getStatus().getDisplayName(),
                                            r.getPlatform().displayName);
                            }
                        }
                    }
                });

        return tv;
    }

    private boolean isManualEntry(String username, String platform) {
        return manualTable.getItems().stream()
                .anyMatch(r -> r.nameProperty().get().equalsIgnoreCase(username)
                        && r.platformProperty().get().equals(platform));
    }

    private void updateOrAddMiniRow(String username, String status, String platform) {
        for (ManualRow row : manualTable.getItems()) {
            if (row.nameProperty().get().equalsIgnoreCase(username)
                    && row.platformProperty().get().equals(platform)) {
                row.statusProperty().set(status);
                return;
            }
        }
        manualTable.getItems().add(0, new ManualRow(username, status, platform));
    }

    // ── Algorithm Options header ───────────────────────────────────────

    private VBox buildOptionsHeader() {
        VBox wrapper = new VBox(0);
        wrapper.setStyle("-fx-background-color: #222222;");

        HBox header = new HBox(6);
        header.getStyleClass().add("af-options-header");
        header.setPadding(new Insets(6, 10, 6, 10));
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("algorithm options");
        title.getStyleClass().add("af-section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        algorithmPlatformCombo = new GroupedPlatformComboBox("minecraft");
        algorithmPlatformCombo.setPrefWidth(130);
        algorithmPlatformCombo.setOnAction(e -> saveSettings());

        Label platformLbl = new Label("platform:");
        platformLbl.getStyleClass().add("af-label-muted");
        header.getChildren().addAll(title, spacer, platformLbl, algorithmPlatformCombo);

        wrapper.getChildren().add(header);
        return wrapper;
    }

    // ── Seção colapsável com drag & drop ───────────────────────────────

    private VBox buildCollapsibleSection(String name, VBox content) {
        VBox section = new VBox(0);
        section.getStyleClass().add("af-collapsible");
        section.setUserData(name);

        HBox header = new HBox(6);
        header.getStyleClass().add("af-collapsible-header");
        header.setPadding(new Insets(6, 10, 6, 10));
        header.setAlignment(Pos.CENTER_LEFT);

        Label dragHandle = new Label("⋮⋮");
        dragHandle.setStyle(
                "-fx-text-fill: #444444; -fx-font-size: 10px; -fx-padding: 0 4px 0 0;");
        dragHandle.setTooltip(new Tooltip("Drag to reorder"));

        Label arrow = new Label("∨");
        arrow.getStyleClass().add("af-collapse-arrow");

        Label titleLabel = new Label(name);
        titleLabel.getStyleClass().add("af-collapsible-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(dragHandle, arrow, titleLabel, spacer);

        header.setOnMouseClicked(e -> {
            if (e.isStillSincePress()) {
                boolean visible = content.isVisible();
                content.setVisible(!visible);
                content.setManaged(!visible);
                arrow.setText(visible ? "›" : "∨");
                settings.setSectionOpen(name, !visible);
                AppConfig.getInstance().save();
            }
        });

        header.setOnDragDetected(e -> {
            dragSourceName = name;
            Dragboard db = header.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString(name);
            db.setContent(cc);
            db.setDragView(header.snapshot(null, null), e.getX(), e.getY() / 2);
            e.consume();
        });

        header.setOnDragOver(e -> {
            if (e.getGestureSource() != header &&
                    e.getDragboard().hasString() &&
                    !e.getDragboard().getString().equals(name)) {
                e.acceptTransferModes(TransferMode.MOVE);
                header.setStyle(HEADER_DRAG_OVER_STYLE);
            }
            e.consume();
        });

        header.setOnDragExited(e -> {
            header.setStyle("");
            e.consume();
        });

        header.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasString()) {
                String sourceName = db.getString();
                if (!sourceName.equals(name)) {
                    int srcIdx = sectionOrder.indexOf(sourceName);
                    int tgtIdx = sectionOrder.indexOf(name);
                    if (srcIdx >= 0 && tgtIdx >= 0) {
                        sectionOrder.remove(srcIdx);
                        sectionOrder.add(tgtIdx, sourceName);
                        rebuildSectionsContainer();
                        saveSectionOrder();
                    }
                }
                e.setDropCompleted(true);
            }
            header.setStyle("");
            e.consume();
        });

        header.setOnDragDone(e -> { dragSourceName = null; e.consume(); });

        section.getChildren().addAll(header, content);
        return section;
    }

    // ── Reordenação ────────────────────────────────────────────────────

    private void rebuildSectionsContainer() {
        sectionsContainer.getChildren().clear();
        for (String name : sectionOrder) {
            sectionsContainer.getChildren().add(getSectionByName(name));
        }
    }

    private VBox getSectionByName(String name) {
        return switch (name) {
            case "generation"  -> sectionGeneration;
            case "filters"     -> sectionFilters;
            case "characters"  -> sectionCharacters;
            default            -> sectionGeneration;
        };
    }

    private void saveSectionOrder() {
        settings.setSidebarSectionOrder(sectionOrder.toArray(new String[0]));
        AppConfig.getInstance().save();
    }

    // ── Generation content ─────────────────────────────────────────────

    private VBox buildGenerationContent() {
        VBox box = new VBox(8);
        box.getStyleClass().add("af-section-content");
        box.setPadding(new Insets(8, 14, 10, 14));

        quantityField = buildNumberField("20");
        minLenField   = buildNumberField("3");
        maxLenField   = buildNumberField("5");

        quantityField.focusedProperty().addListener((obs, was, is) -> { if (!is) saveSettings(); });
        minLenField.focusedProperty().addListener((obs, was, is)   -> { if (!is) { validateMinMax(); saveSettings(); } });
        maxLenField.focusedProperty().addListener((obs, was, is)   -> { if (!is) { validateMinMax(); saveSettings(); } });

        box.getChildren().add(buildQuantityRow());
        box.getChildren().addAll(
                buildSpinnerRow("length min", minLenField,
                        () -> { int max = parseField(maxLenField, 5); stepField(minLenField, 1, 1, max); saveSettings(); },
                        () -> { stepField(minLenField, -1, 1, 16); saveSettings(); }),
                buildSpinnerRow("length max", maxLenField,
                        () -> { stepField(maxLenField, 1, 1, 16); saveSettings(); },
                        () -> { int min = parseField(minLenField, 1); stepField(maxLenField, -1, min, 16); saveSettings(); })
        );

        modeCombo = new ComboBox<>();
        modeCombo.getItems().addAll("random", "pronounceable");
        modeCombo.setValue("random");
        modeCombo.getStyleClass().add("af-combo");
        modeCombo.setOnAction(e -> saveSettings());

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

    private HBox buildQuantityRow() {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label("quantity");
        lbl.getStyleClass().add("af-label");
        lbl.setPrefWidth(80);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button minus = new Button("<");
        minus.getStyleClass().add("af-btn-small");
        minus.setOnAction(e -> { if (!isInfinite) { stepField(quantityField, -1, 1, 9999); saveSettings(); } });
        Button plus = new Button(">");
        plus.getStyleClass().add("af-btn-small");
        plus.setOnAction(e -> { if (!isInfinite) { stepField(quantityField, 1, 1, 9999); saveSettings(); } });
        Button btnInf = new Button("∞");
        btnInf.getStyleClass().add("af-btn-small");
        btnInf.setStyle("-fx-text-fill: #4a90d9;");
        btnInf.setTooltip(new Tooltip("Toggle infinite mode"));
        btnInf.setOnAction(e -> { toggleInfinite(); saveSettings(); });
        row.getChildren().addAll(lbl, spacer, minus, quantityField, plus, btnInf);
        return row;
    }

    private void toggleInfinite() {
        isInfinite = !isInfinite;
        if (isInfinite) {
            quantityField.setText("∞");
            quantityField.setStyle("-fx-text-fill: #4a90d9; -fx-font-weight: bold;");
            quantityField.setEditable(false);
        } else {
            quantityField.setText(String.valueOf(settings.getLastQuantity()));
            quantityField.setStyle("");
            quantityField.setEditable(true);
        }
    }

    private void validateMinMax() {
        int min = Math.max(1, Math.min(parseField(minLenField, 3), 16));
        int max = Math.max(1, Math.min(parseField(maxLenField, 5), 16));
        if (min > max) max = min;
        minLenField.setText(String.valueOf(min));
        maxLenField.setText(String.valueOf(max));
    }

    // ── Filters content ────────────────────────────────────────────────

    private VBox buildFiltersContent() {
        VBox box = new VBox(8);
        box.getStyleClass().add("af-section-content");
        box.setPadding(new Insets(8, 14, 10, 14));

        chkStartsWith = new CheckBox(); startsWith = new TextField();
        chkEndsWith   = new CheckBox(); endsWith   = new TextField();
        chkContains   = new CheckBox(); contains   = new TextField();

        for (TextField f : new TextField[]{startsWith, endsWith, contains}) {
            f.getStyleClass().add("af-input"); f.setPrefWidth(100);
            f.focusedProperty().addListener((obs, was, is) -> { if (!is) saveSettings(); });
        }
        for (CheckBox c : new CheckBox[]{chkStartsWith, chkEndsWith, chkContains}) {
            c.getStyleClass().add("af-checkbox");
            c.setOnAction(e -> saveSettings());
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

    // ── Characters content ─────────────────────────────────────────────

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
            c.setOnAction(e -> saveSettings());
        }

        box.getChildren().addAll(
                buildCheckRow("letters",    "a - z", chkLetters),
                buildCheckRow("numbers",    "0 - 9", chkNumbers),
                buildCheckRow("underscore", "_",     chkUnderscore),
                buildCheckRow("period",     ".",     chkPeriod)
        );
        return box;
    }

    // ── Persistência ───────────────────────────────────────────────────

    private void loadSettings() {
        isInfinite = settings.isLastInfinite();
        if (isInfinite) {
            quantityField.setText("∞");
            quantityField.setStyle("-fx-text-fill: #4a90d9; -fx-font-weight: bold;");
            quantityField.setEditable(false);
        } else {
            quantityField.setText(String.valueOf(settings.getLastQuantity()));
        }
        minLenField.setText(String.valueOf(settings.getLastMinLength()));
        maxLenField.setText(String.valueOf(settings.getLastMaxLength()));
        modeCombo.setValue(settings.getLastMode());
        algorithmPlatformCombo.selectPlatform(settings.getLastPlatform());

        chkStartsWith.setSelected(settings.isFilterStartsWith());
        startsWith.setText(settings.getFilterStartsWithVal());
        chkEndsWith.setSelected(settings.isFilterEndsWith());
        endsWith.setText(settings.getFilterEndsWithVal());
        chkContains.setSelected(settings.isFilterContains());
        contains.setText(settings.getFilterContainsVal());

        chkLetters.setSelected(settings.isUseLetters());
        chkNumbers.setSelected(settings.isUseNumbers());
        chkUnderscore.setSelected(settings.isUseUnderscore());
        chkPeriod.setSelected(settings.isUsePeriod());

        manualPlatformCombo.selectPlatform(settings.getLastManualPlatform());

        // Ordem e estado das seções
        String[] savedOrder = settings.getSidebarSectionOrder();
        List<String> validNames =
                new ArrayList<>(Arrays.asList("generation", "filters", "characters"));
        sectionOrder.clear();
        if (savedOrder != null) {
            for (String name : savedOrder) {
                if (validNames.remove(name)) sectionOrder.add(name);
            }
        }
        sectionOrder.addAll(validNames);

        applyCollapseState(sectionGeneration, "generation");
        applyCollapseState(sectionFilters,    "filters");
        applyCollapseState(sectionCharacters, "characters");

        rebuildSectionsContainer();
    }

    private void applyCollapseState(VBox section, String name) {
        if (section.getChildren().size() < 2) return;
        boolean open = settings.isSectionOpen(name);
        VBox content = (VBox) section.getChildren().get(1);
        HBox header  = (HBox) section.getChildren().get(0);
        Label arrow  = (Label) header.getChildren().get(1);
        content.setVisible(open);
        content.setManaged(open);
        arrow.setText(open ? "∨" : "›");
    }

    private void saveSettings() {
        settings.setLastInfinite(isInfinite);
        settings.setLastQuantity(isInfinite ? 20 : parseField(quantityField, 20));
        settings.setLastMinLength(parseField(minLenField, 3));
        settings.setLastMaxLength(parseField(maxLenField, 5));
        settings.setLastMode(modeCombo.getValue() != null ? modeCombo.getValue() : "random");
        settings.setLastPlatform(algorithmPlatformCombo.getValue() != null
                ? algorithmPlatformCombo.getValue() : "minecraft");

        settings.setFilterStartsWith(chkStartsWith.isSelected());
        settings.setFilterStartsWithVal(startsWith.getText());
        settings.setFilterEndsWith(chkEndsWith.isSelected());
        settings.setFilterEndsWithVal(endsWith.getText());
        settings.setFilterContains(chkContains.isSelected());
        settings.setFilterContainsVal(contains.getText());

        settings.setUseLetters(chkLetters.isSelected());
        settings.setUseNumbers(chkNumbers.isSelected());
        settings.setUseUnderscore(chkUnderscore.isSelected());
        settings.setUsePeriod(chkPeriod.isSelected());

        settings.setLastManualPlatform(manualPlatformCombo.getValue() != null
                ? manualPlatformCombo.getValue() : "minecraft");

        AppConfig.getInstance().save();
    }

    // ── buildConfig() ──────────────────────────────────────────────────

    public GeneratorConfig buildConfig() {
        validateMinMax();
        GeneratorConfig config = new GeneratorConfig();
        config.setQuantity(isInfinite ? -1 : parseField(quantityField, 20));
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
        config.setPlatform(algorithmPlatformCombo.getSelectedPlatform());
        return config;
    }

    // ── Row helpers ────────────────────────────────────────────────────

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
            if (!isInfinite && !newVal.matches("\\d*")) field.setText(oldVal);
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
            if (text.isEmpty() || text.equals("∞")) return defaultVal;
            return Integer.parseInt(text);
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

    // ── ManualRow ──────────────────────────────────────────────────────

    public static class ManualRow {
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        private final SimpleStringProperty  name     = new SimpleStringProperty();
        private final SimpleStringProperty  status   = new SimpleStringProperty();
        private final SimpleStringProperty  platform = new SimpleStringProperty();

        public ManualRow(String name, String status, String platform) {
            this.name.set(name); this.status.set(status); this.platform.set(platform);
        }

        public SimpleBooleanProperty selectedProperty()  { return selected; }
        public SimpleStringProperty  nameProperty()      { return name; }
        public SimpleStringProperty  statusProperty()    { return status; }
        public SimpleStringProperty  platformProperty()  { return platform; }
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

    private static class MiniPlatformCell extends TableCell<ManualRow, String> {
        @Override
        protected void updateItem(String platform, boolean empty) {
            super.updateItem(platform, empty);
            if (empty || platform == null) { setText(null); setStyle(""); return; }
            setText(platform);
            setStyle("-fx-text-fill: #4a90d9; -fx-font-size: 10px;");
        }
    }
}