package com.aliasforge.ui.panels;

import com.aliasforge.core.state.AppState;
import com.aliasforge.model.CheckStatus;
import com.aliasforge.model.Platform;
import com.aliasforge.model.UsernameResult;
import com.aliasforge.service.ExportService;
import com.aliasforge.ui.AppController;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ResultsPanel — observa AppState via listener.
 * Platform.runLater centralizado aqui — AppState não sabe de JavaFX.
 */
public class ResultsPanel extends VBox {

    private final AppController  controller;
    private final AppState       state;
    private TableView<ResultRow> table;

    private final List<ResultRow> allRows = new ArrayList<>();

    private String currentFilter = "all";
    private String currentSearch = "";

    public ResultsPanel(AppController controller) {
        this.controller = controller;
        this.state      = controller.getState();
        getStyleClass().add("results-panel");
        setFillWidth(true);
        VBox.setVgrow(this, Priority.ALWAYS);
        buildUI();
        bindState();
    }

    private void buildUI() {
        table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(buildActionBar(), table);
    }

    // ── Action bar ─────────────────────────────────────────────────────

    private HBox buildActionBar() {
        HBox bar = new HBox(6);
        bar.getStyleClass().add("af-action-bar");
        bar.setPadding(new Insets(5, 10, 5, 10));
        bar.setAlignment(Pos.CENTER_LEFT);

        Button btnAll      = new Button("all");
        Button btnCopy     = new Button("copy to clipboard");
        Button btnRecheck  = new Button("re-check");
        Button btnFavorite = new Button("★ favorite");
        Button btnExport   = new Button("export csv");
        Button btnClear    = new Button("clear");

        btnAll.getStyleClass().add("af-btn");
        btnCopy.getStyleClass().add("af-btn");
        btnRecheck.getStyleClass().add("af-btn");
        btnFavorite.getStyleClass().add("af-btn");
        btnFavorite.setStyle("-fx-text-fill: #ffc107;");
        btnExport.getStyleClass().add("af-btn");
        btnClear.getStyleClass().add("af-btn");

        btnAll.setOnAction(e ->
                table.getItems().forEach(r -> r.selectedProperty().set(true)));

        btnCopy.setOnAction(e -> {
            String text = table.getItems().stream()
                    .filter(r -> r.selectedProperty().get())
                    .map(ResultRow::getUsername)
                    .collect(Collectors.joining("\n"));
            if (!text.isEmpty()) copyToClipboard(text);
        });

        btnRecheck.setOnAction(e -> {
            List<ResultRow> selected = table.getItems().stream()
                    .filter(r -> r.selectedProperty().get())
                    .collect(Collectors.toList());
            if (selected.isEmpty()) {
                showAlert("Re-check", "Select at least one username to re-check.");
                return;
            }
            selected.forEach(r -> {
                r.statusProperty().set(CheckStatus.CHECKING.getDisplayName());
                r.timeProperty().set("");
                r.originProperty().set("");
                controller.addManualTask(r.getUsername(), r.getPlatform());
            });
            refreshTableView();
        });

        btnFavorite.setOnAction(e ->
                table.getItems().stream()
                        .filter(r -> r.selectedProperty().get())
                        .forEach(r -> controller.toggleFavorite(r.getUsername(), r.getPlatform())));

        btnExport.setOnAction(e -> exportCsv());

        // Clear — delega ao controller que atualiza o AppState
        btnClear.setOnAction(e -> controller.clearResults());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(
                btnAll, btnCopy, btnRecheck, btnFavorite, btnExport, btnClear, spacer);
        return bar;
    }

    // ── Tabela ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private TableView<ResultRow> buildTable() {
        TableView<ResultRow> tv = new TableView<>();
        tv.getStyleClass().add("af-table");
        tv.setEditable(true);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.setPlaceholder(new Label("press ▶ to start") {{
            setStyle("-fx-text-fill: #555555; -fx-font-size: 13px;");
        }});

        TableColumn<ResultRow, Boolean> colCheck = new TableColumn<>("");
        colCheck.setCellValueFactory(c -> c.getValue().selectedProperty());
        colCheck.setCellFactory(CheckBoxTableCell.forTableColumn(colCheck));
        colCheck.setMaxWidth(36); colCheck.setMinWidth(36); colCheck.setResizable(false);

        TableColumn<ResultRow, String> colName = new TableColumn<>("username");
        colName.setCellValueFactory(c -> c.getValue().nameProperty());
        colName.setPrefWidth(160);

        TableColumn<ResultRow, String> colStatus = new TableColumn<>("status");
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
        colStatus.setCellFactory(col -> new StatusCell());
        colStatus.setPrefWidth(90);

        TableColumn<ResultRow, String> colPlatform = new TableColumn<>("api");
        colPlatform.setCellValueFactory(c -> c.getValue().platformProperty());
        colPlatform.setCellFactory(col -> new PlatformCell());
        colPlatform.setPrefWidth(80);

        TableColumn<ResultRow, String> colTime = new TableColumn<>("ms");
        colTime.setCellValueFactory(c -> c.getValue().timeProperty());
        colTime.setPrefWidth(60);

        TableColumn<ResultRow, String> colOrigin = new TableColumn<>("origin");
        colOrigin.setCellValueFactory(c -> c.getValue().originProperty());
        colOrigin.setPrefWidth(50);

        TableColumn<ResultRow, Boolean> colFav = new TableColumn<>("★");
        colFav.setCellValueFactory(c -> c.getValue().favoritedProperty());
        colFav.setCellFactory(col -> new FavoriteCell(controller));
        colFav.setMaxWidth(40); colFav.setMinWidth(40); colFav.setResizable(false);

        tv.getColumns().addAll(colCheck, colName, colStatus, colPlatform, colTime, colOrigin, colFav);
        return tv;
    }

    // ── Bind ao AppState ───────────────────────────────────────────────

    /**
     * Observa o AppState via listener.
     * O Platform.runLater garante que updates de UI rodem na thread correta.
     */
    private void bindState() {
        state.addOnResultsChanged(() -> javafx.application.Platform.runLater(() -> {
            List<UsernameResult> current = state.getResults();

            // Se o estado ficou vazio (clear foi chamado), limpa a tabela
            if (current.isEmpty()) {
                allRows.clear();
                table.getItems().clear();
                return;
            }

            // Sincroniza cada resultado
            for (UsernameResult result : current) {
                updateOrAdd(result);
            }
        }));
    }

    // ── Filtro e busca ─────────────────────────────────────────────────

    public void applyFilter(String filter, String search) {
        this.currentFilter = filter;
        this.currentSearch = search;
        refreshTableView();
    }

    private void refreshTableView() {
        List<ResultRow> visible = allRows.stream()
                .filter(this::matchesFilter)
                .filter(this::matchesSearch)
                .collect(Collectors.toList());
        table.getItems().setAll(visible);
    }

    private boolean matchesFilter(ResultRow r) {
        if ("all".equals(currentFilter)) return true;
        return r.statusProperty().get().equalsIgnoreCase(currentFilter);
    }

    private boolean matchesSearch(ResultRow r) {
        if (currentSearch.isEmpty()) return true;
        return r.getUsername().toLowerCase().contains(currentSearch);
    }

    private void updateOrAdd(UsernameResult result) {
        for (int i = 0; i < allRows.size(); i++) {
            ResultRow row = allRows.get(i);
            if (row.getUsername().equalsIgnoreCase(result.getUsername()) &&
                    row.getPlatform() == result.getPlatform()) {
                row.update(result);
                refreshTableView();
                return;
            }
        }
        ResultRow newRow = new ResultRow(result);
        allRows.add(newRow);
        if (matchesFilter(newRow) && matchesSearch(newRow)) {
            table.getItems().add(newRow);
            int last = table.getItems().size() - 1;
            if (last >= 0) table.scrollTo(last);
        }
    }

    // ── Export CSV — delega ao ExportService ───────────────────────────

    /**
     * Antes: ~20 linhas com FileWriter, try-catch e formatação embutida.
     * Depois: delega ao ExportService — UI só cuida do FileChooser e do alerta.
     *
     * Nota: ResultRow mantém referência ao UsernameResult original via
     * toUsernameResult() para evitar perda de dados na conversão.
     */
    private void exportCsv() {
        ExportService export = controller.getExportService();

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Results as CSV");
        chooser.setInitialFileName(export.suggestFilename(ExportService.ExportType.RESULTS));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;

        ExportService.ExportResult result = export.exportResults(
                allRows.stream().map(ResultRow::toUsernameResult).toList(),
                file.toPath()
        );

        showAlert("Export", result.userMessage());
    }

    // ── Utilitários ────────────────────────────────────────────────────

    private void copyToClipboard(String text) {
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    // ── ResultRow ──────────────────────────────────────────────────────

    public static class ResultRow {
        private final BooleanProperty selected  = new SimpleBooleanProperty(false);
        private final BooleanProperty favorited = new SimpleBooleanProperty(false);
        private final StringProperty  name      = new SimpleStringProperty();
        private final StringProperty  status    = new SimpleStringProperty();
        private final StringProperty  platform  = new SimpleStringProperty();
        private final StringProperty  time      = new SimpleStringProperty();
        private final StringProperty  origin    = new SimpleStringProperty();
        private Platform     platformEnum;
        private UsernameResult originalResult; // mantida para toUsernameResult()

        public ResultRow(UsernameResult r) { update(r); }

        public void update(UsernameResult r) {
            this.originalResult = r;
            name.set(r.getUsername());
            status.set(r.getStatus().getDisplayName());
            platform.set(r.getPlatform().displayName);
            time.set(r.getResponseTimeDisplay());
            origin.set(r.getOrigin() != null ? r.getOrigin() : "");
            favorited.set(r.isFavorited());
            this.platformEnum = r.getPlatform();
        }

        /**
         * Retorna o UsernameResult original para o ExportService.
         * Garante que todos os campos (errorDetail, checkedAt, etc.) sejam preservados.
         */
        public UsernameResult toUsernameResult() {
            return originalResult;
        }

        public BooleanProperty selectedProperty()  { return selected; }
        public BooleanProperty favoritedProperty() { return favorited; }
        public StringProperty  nameProperty()      { return name; }
        public StringProperty  statusProperty()    { return status; }
        public StringProperty  platformProperty()  { return platform; }
        public StringProperty  timeProperty()      { return time; }
        public StringProperty  originProperty()    { return origin; }
        public String          getUsername()       { return name.get(); }
        public Platform        getPlatform()       { return platformEnum; }
    }

    // ── Cells ──────────────────────────────────────────────────────────

    private static class StatusCell extends TableCell<ResultRow, String> {
        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);
            if (empty || status == null) { setText(null); setStyle(""); return; }
            setText(status);
            String color = switch (status) {
                case "available"  -> "#4caf50";
                case "taken"      -> "#f44336";
                case "rate limit" -> "#ffc107";
                case "error"      -> "#757575";
                case "checking"   -> "#2196f3";
                default           -> "#cccccc";
            };
            setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        }
    }

    private static class PlatformCell extends TableCell<ResultRow, String> {
        @Override
        protected void updateItem(String platform, boolean empty) {
            super.updateItem(platform, empty);
            if (empty || platform == null) { setText(null); setStyle(""); return; }
            setText(platform);
            String color = switch (platform) {
                case "minecraft" -> "#4a90d9";
                case "github"    -> "#cccccc";
                case "reddit"    -> "#ff4500";
                case "guns.lol"  -> "#e53935";
                case "caliber"   -> "#7b1fa2";
                case "tiktok"    -> "#00bcd4";
                case "instagram" -> "#e91e8c";
                case "custom"    -> "#9c27b0";
                default          -> "#888888";
            };
            setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11px;");
        }
    }

    private static class FavoriteCell extends TableCell<ResultRow, Boolean> {
        private final Button btn = new Button("☆");
        private final AppController controller;

        public FavoriteCell(AppController controller) {
            this.controller = controller;
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #555555;" +
                    "-fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0 4px;");
            btn.setOnAction(e -> {
                ResultRow row = getTableView().getItems().get(getIndex());
                controller.toggleFavorite(row.getUsername(), row.getPlatform());
            });
        }

        @Override
        protected void updateItem(Boolean fav, boolean empty) {
            super.updateItem(fav, empty);
            if (empty) { setGraphic(null); return; }
            boolean f = fav != null && fav;
            btn.setText(f ? "★" : "☆");
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " +
                    (f ? "#ffc107" : "#555555") +
                    "; -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0 4px;");
            setGraphic(btn);
        }
    }
}