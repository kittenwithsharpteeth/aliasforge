package com.aliasforge.ui.panels;

import com.aliasforge.model.CheckStatus;
import com.aliasforge.model.Platform;
import com.aliasforge.model.UsernameResult;
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ResultsPanel extends VBox {

    private final AppController  controller;
    private TableView<ResultRow> table;

    // Lista mestre — todos os resultados sem filtro
    private final List<ResultRow> allRows = new ArrayList<>();

    // Filtro atual
    private String currentFilter = "all";
    private String currentSearch = "";

    public ResultsPanel(AppController controller) {
        this.controller = controller;
        getStyleClass().add("results-panel");
        setFillWidth(true);
        VBox.setVgrow(this, Priority.ALWAYS);
        buildUI();
        bindData();
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

        // Selecionar todos visíveis
        btnAll.setOnAction(e ->
                table.getItems().forEach(r -> r.selectedProperty().set(true)));

        // Copiar selecionados
        btnCopy.setOnAction(e -> {
            String text = table.getItems().stream()
                    .filter(r -> r.selectedProperty().get())
                    .map(ResultRow::getUsername)
                    .collect(Collectors.joining("\n"));
            if (!text.isEmpty()) copyToClipboard(text);
        });

        // Re-check selecionados — reseta status para "checking" imediatamente
        btnRecheck.setOnAction(e -> {
            List<ResultRow> selected = table.getItems().stream()
                    .filter(r -> r.selectedProperty().get())
                    .collect(Collectors.toList());
            if (selected.isEmpty()) {
                showAlert("Re-check", "Select at least one username to re-check.");
                return;
            }
            selected.forEach(r -> {
                // Fix: reseta o status visual imediatamente para não confundir o usuário
                r.statusProperty().set(CheckStatus.CHECKING.getDisplayName());
                r.timeProperty().set("");
                r.originProperty().set("");
                // Enfileira o re-check no backend
                controller.addManualTask(r.getUsername(), r.getPlatform());
            });
            // Força refresh do filtro para refletir a mudança
            refreshTableView();
        });

        // Favoritar selecionados
        btnFavorite.setOnAction(e ->
                table.getItems().stream()
                        .filter(r -> r.selectedProperty().get())
                        .forEach(r -> controller.toggleFavorite(r.getUsername(), r.getPlatform())));

        // Export CSV
        btnExport.setOnAction(e -> exportCsv());

        // Limpar
        btnClear.setOnAction(e -> {
            allRows.clear();
            table.getItems().clear();
        });

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

    // ── Bind ao controller ─────────────────────────────────────────────

    private void bindData() {
        controller.getResults().addListener(
                (javafx.collections.ListChangeListener<UsernameResult>) change -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            for (UsernameResult r : change.getAddedSubList()) updateOrAdd(r);
                        }
                        if (change.wasReplaced()) {
                            for (int i = change.getFrom(); i < change.getTo(); i++) {
                                updateOrAdd(controller.getResults().get(i));
                            }
                        }
                        if (change.wasRemoved() && controller.getResults().isEmpty()) {
                            allRows.clear();
                            table.getItems().clear();
                        }
                    }
                });
    }

    private void updateOrAdd(UsernameResult result) {
        // Fix: busca por username E platform para evitar falsa dedup entre plataformas
        for (int i = 0; i < allRows.size(); i++) {
            ResultRow row = allRows.get(i);
            if (row.getUsername().equalsIgnoreCase(result.getUsername()) &&
                    row.getPlatform() == result.getPlatform()) {
                row.update(result);
                refreshTableView();
                return;
            }
        }
        // Novo resultado
        ResultRow newRow = new ResultRow(result);
        allRows.add(newRow);
        if (matchesFilter(newRow) && matchesSearch(newRow)) {
            table.getItems().add(newRow);
            int last = table.getItems().size() - 1;
            if (last >= 0) table.scrollTo(last);
        }
    }

    // ── Export CSV ─────────────────────────────────────────────────────

    private void exportCsv() {
        if (allRows.isEmpty()) {
            showAlert("Export CSV", "No results to export.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Results as CSV");
        chooser.setInitialFileName("aliasforge_results.csv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write("username,status,api,ms,origin");
            bw.newLine();
            for (ResultRow r : allRows) {
                bw.write(String.join(",",
                        r.getUsername(),
                        r.statusProperty().get(),
                        r.platformProperty().get(),
                        r.timeProperty().get(),
                        r.originProperty().get()
                ));
                bw.newLine();
            }
            showAlert("Export Complete",
                    "Results exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            showAlert("Export Error", "Failed to export: " + e.getMessage());
        }
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
        private Platform platformEnum;

        public ResultRow(UsernameResult r) { update(r); }

        public void update(UsernameResult r) {
            name.set(r.getUsername());
            status.set(r.getStatus().getDisplayName());
            platform.set(r.getPlatform().displayName);
            time.set(r.getResponseTimeDisplay());
            origin.set(r.getOrigin() != null ? r.getOrigin() : "");
            favorited.set(r.isFavorited());
            this.platformEnum = r.getPlatform();
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