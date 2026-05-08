package com.aliasforge.ui.views;

import com.aliasforge.core.state.AppState;
import com.aliasforge.model.CheckStatus;
import com.aliasforge.model.UsernameResult;
import com.aliasforge.service.ExportService;
import com.aliasforge.ui.AppController;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class LogsView extends VBox {

    private final AppController   controller;
    private final AppState        state;
    private TableView<LogRow>     table;
    private Label                 totalLabel;
    private Label                 rateLimitLabel;
    private Label                 errorLabel;
    private ComboBox<String>      typeCombo;
    private TextField             searchField;

    public LogsView(AppController controller) {
        this.controller = controller;
        this.state      = controller.getState();
        getStyleClass().add("logs-view");
        setFillWidth(true);
        VBox.setVgrow(this, Priority.ALWAYS);
        buildUI();
        bindData();
    }

    private void buildUI() {
        getChildren().addAll(buildToolbar(), buildTable(), buildStatsBar());
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    // ── Toolbar ────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        HBox bar = new HBox(8);
        bar.getStyleClass().add("af-action-bar");
        bar.setPadding(new Insets(6, 10, 6, 10));
        bar.setAlignment(Pos.CENTER_LEFT);

        Label typeLbl = new Label("type");
        typeLbl.getStyleClass().add("af-label-muted");
        typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("all errors", "error", "rate limit");
        typeCombo.setValue("all errors");
        typeCombo.getStyleClass().add("af-combo");
        typeCombo.setPrefWidth(120);
        typeCombo.setOnAction(e -> applyFilter());

        Label searchLbl = new Label("search");
        searchLbl.getStyleClass().add("af-label-muted");
        searchField = new TextField();
        searchField.setPromptText("username or detail...");
        searchField.getStyleClass().add("af-search");
        searchField.setPrefWidth(180);
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnExport = new Button("export csv");
        Button btnClear  = new Button("clear logs");
        btnExport.getStyleClass().add("af-btn");
        btnClear.getStyleClass().add("af-btn");

        btnExport.setOnAction(e -> exportCsv());
        btnClear.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Clear all history including logs? This cannot be undone.",
                    ButtonType.YES, ButtonType.NO);
            alert.setHeaderText(null);
            alert.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) controller.clearHistory();
            });
        });

        bar.getChildren().addAll(typeLbl, typeCombo, searchLbl, searchField,
                spacer, btnExport, btnClear);
        return bar;
    }

    // ── Tabela ─────────────────────────────────────────────────────────

    private TableView<LogRow> buildTable() {
        table = new TableView<>();
        table.getStyleClass().add("af-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("no errors logged yet") {{
            setStyle("-fx-text-fill: #555555; -fx-font-size: 13px;");
        }});

        TableColumn<LogRow, String> colTime = new TableColumn<>("time");
        colTime.setCellValueFactory(c -> c.getValue().timeProperty());
        colTime.setPrefWidth(150);
        colTime.setResizable(false);

        TableColumn<LogRow, String> colType = new TableColumn<>("type");
        colType.setCellValueFactory(c -> c.getValue().typeProperty());
        colType.setCellFactory(col -> new TypeCell());
        colType.setPrefWidth(90);
        colType.setResizable(false);

        TableColumn<LogRow, String> colUsername = new TableColumn<>("username");
        colUsername.setCellValueFactory(c -> c.getValue().usernameProperty());
        colUsername.setPrefWidth(150);

        TableColumn<LogRow, String> colPlatform = new TableColumn<>("api");
        colPlatform.setCellValueFactory(c -> c.getValue().platformProperty());
        colPlatform.setPrefWidth(80);
        colPlatform.setResizable(false);

        TableColumn<LogRow, String> colDetail = new TableColumn<>("detail");
        colDetail.setCellValueFactory(c -> c.getValue().detailProperty());
        colDetail.setCellFactory(col -> new DetailCell());

        table.getColumns().addAll(colTime, colType, colUsername, colPlatform, colDetail);

        // Tooltip com detalhe completo ao passar o mouse
        table.setRowFactory(tv -> {
            TableRow<LogRow> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null && newItem.detailProperty().get() != null
                        && !newItem.detailProperty().get().isEmpty()) {
                    Tooltip tip = new Tooltip(newItem.detailProperty().get());
                    tip.setWrapText(true);
                    tip.setMaxWidth(400);
                    Tooltip.install(row, tip);
                } else {
                    Tooltip.uninstall(row, null);
                }
            });
            return row;
        });

        return table;
    }

    // ── Stats bar ──────────────────────────────────────────────────────

    private HBox buildStatsBar() {
        HBox bar = new HBox(18);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(5, 12, 5, 12));
        bar.setAlignment(Pos.CENTER_LEFT);

        totalLabel      = new Label("total: 0");
        rateLimitLabel  = new Label("rate limit: 0");
        errorLabel      = new Label("error: 0");

        totalLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");
        rateLimitLabel.setStyle("-fx-text-fill: #ffc107; -fx-font-size: 12px;");
        errorLabel.setStyle("-fx-text-fill: #757575; -fx-font-size: 12px;");

        Label hint = new Label("ⓘ hover over a row to see full error detail");
        hint.setStyle("-fx-text-fill: #555555; -fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(totalLabel, rateLimitLabel, errorLabel, spacer, hint);
        return bar;
    }

    // ── Bind ao AppState ───────────────────────────────────────────────

    /**
     * Antes: usava controller.getHistory() como ObservableList — acoplamento forte
     * que expunha a lista interna do controller para a UI.
     *
     * Depois: usa AppState via listener, igual às outras views.
     * Elimina o getHistory() do AppController que retornava ObservableList JavaFX,
     * violando a separação UI/Core.
     */
    private void bindData() {
        state.addOnHistoryChanged(() ->
                javafx.application.Platform.runLater(this::applyFilter));
        applyFilter();
    }

    private void applyFilter() {
        String type   = typeCombo.getValue();
        String search = searchField.getText().toLowerCase().trim();

        // Filtra de state.getHistory() em vez de controller.getHistory()
        List<LogRow> filtered = state.getHistory().stream()
                .filter(r -> r.getStatus() == CheckStatus.ERROR ||
                        r.getStatus() == CheckStatus.RATE_LIMIT)
                .filter(r -> {
                    if ("error".equals(type))      return r.getStatus() == CheckStatus.ERROR;
                    if ("rate limit".equals(type)) return r.getStatus() == CheckStatus.RATE_LIMIT;
                    return true; // "all errors"
                })
                .filter(r -> search.isEmpty() ||
                        r.getUsername().toLowerCase().contains(search) ||
                        (r.getErrorDetail() != null &&
                                r.getErrorDetail().toLowerCase().contains(search)))
                .map(LogRow::new)
                .collect(Collectors.toList());

        table.getItems().setAll(filtered);
        updateStats(filtered);
    }

    private void updateStats(List<LogRow> rows) {
        long rl  = rows.stream().filter(r -> "rate limit".equals(r.typeProperty().get())).count();
        long err = rows.stream().filter(r -> "error".equals(r.typeProperty().get())).count();
        totalLabel.setText("total: " + rows.size());
        rateLimitLabel.setText("rate limit: " + rl);
        errorLabel.setText("error: " + err);
    }

    // ── Export CSV — delega ao ExportService ───────────────────────────

    /**
     * Antes: lógica manual de escape inline — "\"" + detail.replace("\"", "'") + "\""
     * — propensa a bugs e diferente das outras views.
     *
     * Depois: delega ao ExportService que centraliza o escape CSV correto
     * (campos com vírgulas entre aspas duplas, aspas duplicadas).
     */
    private void exportCsv() {
        ExportService export = controller.getExportService();

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Logs as CSV");
        chooser.setInitialFileName(export.suggestFilename(ExportService.ExportType.LOGS));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;

        // Passa os UsernameResult originais — o ExportService sabe formatar os logs
        ExportService.ExportResult result = export.exportLogs(
                table.getItems().stream().map(LogRow::toResult).toList(),
                file.toPath()
        );

        showAlert("Export", result.userMessage());
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    // ── LogRow model ───────────────────────────────────────────────────

    public static class LogRow {
        private final StringProperty time     = new SimpleStringProperty();
        private final StringProperty type     = new SimpleStringProperty();
        private final StringProperty username = new SimpleStringProperty();
        private final StringProperty platform = new SimpleStringProperty();
        private final StringProperty detail   = new SimpleStringProperty();
        private UsernameResult originalResult;

        public LogRow(UsernameResult r) {
            this.originalResult = r;
            time.set(r.getCheckedAtFormatted());
            type.set(r.getStatus().getDisplayName());
            username.set(r.getUsername());
            platform.set(r.getPlatform().displayName);
            detail.set(r.getErrorDetail() != null ? r.getErrorDetail() : "");
        }

        /** Retorna o UsernameResult original para o ExportService. */
        public UsernameResult toResult() {
            return originalResult;
        }

        public StringProperty timeProperty()     { return time; }
        public StringProperty typeProperty()     { return type; }
        public StringProperty usernameProperty() { return username; }
        public StringProperty platformProperty() { return platform; }
        public StringProperty detailProperty()   { return detail; }
    }

    // ── Cells ──────────────────────────────────────────────────────────

    private static class TypeCell extends TableCell<LogRow, String> {
        @Override
        protected void updateItem(String type, boolean empty) {
            super.updateItem(type, empty);
            if (empty || type == null) { setText(null); setStyle(""); return; }
            setText(type);
            String color = switch (type) {
                case "rate limit" -> "#ffc107";
                case "error"      -> "#757575";
                default           -> "#cccccc";
            };
            setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        }
    }

    private static class DetailCell extends TableCell<LogRow, String> {
        @Override
        protected void updateItem(String detail, boolean empty) {
            super.updateItem(detail, empty);
            if (empty || detail == null || detail.isEmpty()) {
                setText(null);
                setStyle("-fx-text-fill: #555555;");
                return;
            }
            // Trunca detalhes longos na célula — tooltip mostra o completo
            String display = detail.length() > 80 ? detail.substring(0, 77) + "..." : detail;
            setText(display);
            setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        }
    }
}