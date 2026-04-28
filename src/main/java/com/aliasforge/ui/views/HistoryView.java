package com.aliasforge.ui.views;

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
import java.util.List;
import java.util.stream.Collectors;

public class HistoryView extends VBox {

    private final AppController      controller;
    private TableView<HistoryRow>    table;
    private Label totalLabel, availableLabel, takenLabel, errorLabel;
    private ComboBox<String> filterCombo, platformCombo;
    private TextField searchField;

    public HistoryView(AppController controller) {
        this.controller = controller;
        getStyleClass().add("history-view");
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

        Label filterLbl = new Label("filter");
        filterLbl.getStyleClass().add("af-label-muted");
        filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("all", "available", "taken", "rate limit", "error");
        filterCombo.setValue("all");
        filterCombo.getStyleClass().add("af-combo");
        filterCombo.setPrefWidth(110);
        filterCombo.setOnAction(e -> applyFilter());

        Label platformLbl = new Label("platform");
        platformLbl.getStyleClass().add("af-label-muted");
        platformCombo = new ComboBox<>();
        platformCombo.getItems().addAll("all", "minecraft", "custom");
        platformCombo.setValue("all");
        platformCombo.getStyleClass().add("af-combo");
        platformCombo.setPrefWidth(110);
        platformCombo.setOnAction(e -> applyFilter());

        Label searchLbl = new Label("search");
        searchLbl.getStyleClass().add("af-label-muted");
        searchField = new TextField();
        searchField.setPromptText("username...");
        searchField.getStyleClass().add("af-search");
        searchField.setPrefWidth(160);
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnFavSelected = new Button("★ favorite selected");
        Button btnCopyAvail   = new Button("copy available");
        Button btnExport      = new Button("export csv");
        Button btnClear       = new Button("clear history");

        btnFavSelected.getStyleClass().add("af-btn");
        btnFavSelected.setStyle("-fx-text-fill: #ffc107;");
        btnCopyAvail.getStyleClass().add("af-btn");
        btnExport.getStyleClass().add("af-btn");
        btnClear.getStyleClass().add("af-btn");

        btnFavSelected.setOnAction(e -> table.getItems().stream()
                .filter(r -> r.selectedProperty().get())
                .forEach(r -> controller.toggleFavorite(r.getUsername(), r.getPlatform())));

        btnCopyAvail.setOnAction(e -> {
            String text = table.getItems().stream()
                    .filter(r -> "available".equals(r.statusProperty().get()))
                    .map(HistoryRow::getUsername)
                    .collect(Collectors.joining("\n"));
            if (!text.isEmpty()) {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(text);
                Clipboard.getSystemClipboard().setContent(cc);
            }
        });

        btnExport.setOnAction(e -> exportCsv());

        btnClear.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Clear all history? This cannot be undone.", ButtonType.YES, ButtonType.NO);
            alert.setHeaderText(null);
            alert.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) controller.clearHistory();
            });
        });

        bar.getChildren().addAll(filterLbl, filterCombo, platformLbl, platformCombo,
                searchLbl, searchField, spacer, btnFavSelected, btnCopyAvail, btnExport, btnClear);
        return bar;
    }

    // ── Tabela ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private TableView<HistoryRow> buildTable() {
        table = new TableView<>();
        table.getStyleClass().add("af-table");
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("no history yet") {{
            setStyle("-fx-text-fill: #555555; -fx-font-size: 13px;");
        }});

        TableColumn<HistoryRow, Boolean> colCheck = new TableColumn<>("");
        colCheck.setCellValueFactory(c -> c.getValue().selectedProperty());
        colCheck.setCellFactory(CheckBoxTableCell.forTableColumn(colCheck));
        colCheck.setMaxWidth(36); colCheck.setMinWidth(36); colCheck.setResizable(false);

        TableColumn<HistoryRow, Boolean> colFav = new TableColumn<>("★");
        colFav.setCellValueFactory(c -> c.getValue().favoritedProperty());
        colFav.setCellFactory(col -> new FavoriteCell(controller));
        colFav.setMaxWidth(40); colFav.setMinWidth(40); colFav.setResizable(false);

        TableColumn<HistoryRow, String> colName = new TableColumn<>("username");
        colName.setCellValueFactory(c -> c.getValue().nameProperty());
        colName.setPrefWidth(150);

        TableColumn<HistoryRow, String> colStatus = new TableColumn<>("status");
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
        colStatus.setCellFactory(col -> new StatusCell());
        colStatus.setPrefWidth(90);

        TableColumn<HistoryRow, String> colPlatform = new TableColumn<>("api");
        colPlatform.setCellValueFactory(c -> c.getValue().platformProperty());
        colPlatform.setPrefWidth(80);

        TableColumn<HistoryRow, String> colDate = new TableColumn<>("checked at");
        colDate.setCellValueFactory(c -> c.getValue().dateProperty());
        colDate.setPrefWidth(150);

        TableColumn<HistoryRow, String> colTime = new TableColumn<>("ms");
        colTime.setCellValueFactory(c -> c.getValue().timeProperty());
        colTime.setPrefWidth(60);

        table.getColumns().addAll(colCheck, colFav, colName, colStatus, colPlatform, colDate, colTime);
        return table;
    }

    // ── Stats bar ──────────────────────────────────────────────────────

    private HBox buildStatsBar() {
        HBox bar = new HBox(18);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(5, 12, 5, 12));
        bar.setAlignment(Pos.CENTER_LEFT);

        totalLabel     = new Label("total: 0");
        availableLabel = new Label("available: 0");
        takenLabel     = new Label("taken: 0");
        errorLabel     = new Label("error / rate limit: 0");

        totalLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");
        availableLabel.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 12px;");
        takenLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 12px;");
        errorLabel.setStyle("-fx-text-fill: #757575; -fx-font-size: 12px;");

        bar.getChildren().addAll(totalLabel, availableLabel, takenLabel, errorLabel);
        return bar;
    }

    // ── Bind e filtro ──────────────────────────────────────────────────

    private void bindData() {
        controller.getHistory().addListener(
                (javafx.collections.ListChangeListener<UsernameResult>) c -> applyFilter());
        applyFilter();
    }

    private void applyFilter() {
        String statusFilter   = filterCombo.getValue();
        String platformFilter = platformCombo.getValue();
        String search         = searchField.getText().toLowerCase().trim();

        List<HistoryRow> filtered = controller.getHistory().stream()
                .filter(r -> "all".equals(statusFilter) ||
                        r.getStatus().getDisplayName().equals(statusFilter))
                .filter(r -> "all".equals(platformFilter) ||
                        r.getPlatform().displayName.equals(platformFilter))
                .filter(r -> search.isEmpty() ||
                        r.getUsername().toLowerCase().contains(search))
                .map(HistoryRow::new)
                .collect(Collectors.toList());

        table.getItems().setAll(filtered);
        updateStats(filtered);
    }

    private void updateStats(List<HistoryRow> rows) {
        long avail  = rows.stream().filter(r -> "available".equals(r.statusProperty().get())).count();
        long taken  = rows.stream().filter(r -> "taken".equals(r.statusProperty().get())).count();
        long errors = rows.stream().filter(r ->
                "error".equals(r.statusProperty().get()) ||
                        "rate limit".equals(r.statusProperty().get())).count();
        totalLabel.setText("total: " + rows.size());
        availableLabel.setText("available: " + avail);
        takenLabel.setText("taken: " + taken);
        errorLabel.setText("error / rate limit: " + errors);
    }

    // ── Export CSV ─────────────────────────────────────────────────────

    private void exportCsv() {
        List<HistoryRow> rows = table.getItems();
        if (rows.isEmpty()) {
            showAlert("Export CSV", "No history to export.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export History as CSV");
        chooser.setInitialFileName("aliasforge_history.csv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write("username,status,platform,checked_at,ms");
            bw.newLine();
            for (HistoryRow r : rows) {
                bw.write(String.join(",",
                        r.getUsername(),
                        r.statusProperty().get(),
                        r.platformProperty().get(),
                        r.dateProperty().get(),
                        r.timeProperty().get()
                ));
                bw.newLine();
            }
            showAlert("Export Complete", "History exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            showAlert("Export Error", "Failed to export: " + e.getMessage());
        }
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    // ── HistoryRow ─────────────────────────────────────────────────────

    public static class HistoryRow {
        private final BooleanProperty selected  = new SimpleBooleanProperty(false);
        private final BooleanProperty favorited = new SimpleBooleanProperty(false);
        private final StringProperty  name      = new SimpleStringProperty();
        private final StringProperty  status    = new SimpleStringProperty();
        private final StringProperty  platform  = new SimpleStringProperty();
        private final StringProperty  date      = new SimpleStringProperty();
        private final StringProperty  time      = new SimpleStringProperty();
        private Platform platformEnum;

        public HistoryRow(UsernameResult r) {
            name.set(r.getUsername());
            status.set(r.getStatus().getDisplayName());
            platform.set(r.getPlatform().displayName);
            date.set(r.getCheckedAtFormatted());
            time.set(r.getResponseTimeDisplay());
            favorited.set(r.isFavorited());
            platformEnum = r.getPlatform();
        }

        public BooleanProperty selectedProperty()  { return selected; }
        public BooleanProperty favoritedProperty() { return favorited; }
        public StringProperty  nameProperty()      { return name; }
        public StringProperty  statusProperty()    { return status; }
        public StringProperty  platformProperty()  { return platform; }
        public StringProperty  dateProperty()      { return date; }
        public StringProperty  timeProperty()      { return time; }
        public String          getUsername()       { return name.get(); }
        public Platform        getPlatform()       { return platformEnum; }
    }

    // ── Cells ──────────────────────────────────────────────────────────

    private static class StatusCell extends TableCell<HistoryRow, String> {
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
                default           -> "#cccccc";
            };
            setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        }
    }

    private static class FavoriteCell extends TableCell<HistoryRow, Boolean> {
        private final Button btn = new Button("☆");
        private final AppController controller;

        public FavoriteCell(AppController controller) {
            this.controller = controller;
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #555555;" +
                    "-fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0 4px;");
            btn.setOnAction(e -> {
                HistoryRow row = getTableView().getItems().get(getIndex());
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