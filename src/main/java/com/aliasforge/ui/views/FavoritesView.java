package com.aliasforge.ui.views;

import com.aliasforge.core.state.AppState;
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
import java.util.List;
import java.util.stream.Collectors;

public class FavoritesView extends VBox {

    private final AppController    controller;
    private final AppState         state;
    private TableView<FavoriteRow> table;
    private Label totalLabel, availableLabel, takenLabel;
    private ComboBox<String> platformCombo;
    private TextField searchField;

    public FavoritesView(AppController controller) {
        this.controller = controller;
        this.state      = controller.getState();
        getStyleClass().add("favorites-view");
        setFillWidth(true);
        VBox.setVgrow(this, Priority.ALWAYS);
        buildUI();
        bindState();
    }

    private void buildUI() {
        table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(buildToolbar(), buildActionBar(), table, buildStatsBar());
    }

    // ── Toolbar ────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        HBox bar = new HBox(8);
        bar.getStyleClass().add("af-action-bar");
        bar.setPadding(new Insets(6, 10, 6, 10));
        bar.setAlignment(Pos.CENTER_LEFT);

        Label platformLbl = new Label("platform");
        platformLbl.getStyleClass().add("af-label-muted");

        platformCombo = new ComboBox<>();
        platformCombo.getItems().add("all");
        for (Platform p : Platform.values()) {
            platformCombo.getItems().add(p.displayName);
        }
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

        Button btnCopyAll = new Button("copy all");
        Button btnExport  = new Button("export csv");
        btnCopyAll.getStyleClass().add("af-btn");
        btnExport.getStyleClass().add("af-btn");

        btnCopyAll.setOnAction(e -> {
            String text = table.getItems().stream()
                    .map(FavoriteRow::getUsername)
                    .collect(Collectors.joining("\n"));
            if (!text.isEmpty()) {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(text);
                Clipboard.getSystemClipboard().setContent(cc);
            }
        });

        btnExport.setOnAction(e -> exportCsv());

        bar.getChildren().addAll(platformLbl, platformCombo, searchLbl, searchField,
                spacer, btnCopyAll, btnExport);
        return bar;
    }

    private HBox buildActionBar() {
        HBox bar = new HBox(6);
        bar.setPadding(new Insets(5, 10, 5, 10));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #222222; " +
                "-fx-border-color: transparent transparent #333333 transparent; " +
                "-fx-border-width: 1px;");

        Button btnAll    = new Button("all");
        Button btnCopy   = new Button("copy to clipboard");
        Button btnRemove = new Button("remove from favorites");
        btnAll.getStyleClass().add("af-btn");
        btnCopy.getStyleClass().add("af-btn");
        btnRemove.getStyleClass().add("af-btn");

        btnAll.setOnAction(e ->
                table.getItems().forEach(r -> r.selectedProperty().set(true)));

        btnCopy.setOnAction(e -> {
            String text = table.getItems().stream()
                    .filter(r -> r.selectedProperty().get())
                    .map(FavoriteRow::getUsername)
                    .collect(Collectors.joining("\n"));
            if (!text.isEmpty()) {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(text);
                Clipboard.getSystemClipboard().setContent(cc);
            }
        });

        btnRemove.setOnAction(e -> table.getItems().stream()
                .filter(r -> r.selectedProperty().get())
                .collect(Collectors.toList())
                .forEach(r -> controller.toggleFavorite(r.getUsername(), r.getPlatform())));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label hint = new Label("★ saved usernames");
        hint.getStyleClass().add("af-label-muted");

        bar.getChildren().addAll(btnAll, btnCopy, btnRemove, spacer, hint);
        return bar;
    }

    // ── Tabela ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private TableView<FavoriteRow> buildTable() {
        TableView<FavoriteRow> tv = new TableView<>();
        tv.getStyleClass().add("af-table");
        tv.setEditable(true);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.setPlaceholder(new Label("no favorites yet — click ★ on any result") {{
            setStyle("-fx-text-fill: #555555; -fx-font-size: 13px;");
        }});

        TableColumn<FavoriteRow, Boolean> colCheck = new TableColumn<>("");
        colCheck.setCellValueFactory(c -> c.getValue().selectedProperty());
        colCheck.setCellFactory(CheckBoxTableCell.forTableColumn(colCheck));
        colCheck.setMaxWidth(36); colCheck.setMinWidth(36); colCheck.setResizable(false);

        TableColumn<FavoriteRow, String> colStar = new TableColumn<>("★");
        colStar.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("★");
            {
                btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffc107;" +
                        "-fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0 4px;");
                btn.setOnAction(e -> {
                    FavoriteRow row = getTableView().getItems().get(getIndex());
                    controller.toggleFavorite(row.getUsername(), row.getPlatform());
                });
            }
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setGraphic(empty ? null : btn);
            }
        });
        colStar.setMaxWidth(40); colStar.setMinWidth(40); colStar.setResizable(false);

        TableColumn<FavoriteRow, String> colName = new TableColumn<>("username");
        colName.setCellValueFactory(c -> c.getValue().nameProperty());
        colName.setPrefWidth(180);

        TableColumn<FavoriteRow, String> colStatus = new TableColumn<>("status");
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
        colStatus.setCellFactory(col -> new StatusCell());
        colStatus.setPrefWidth(100);

        TableColumn<FavoriteRow, String> colPlatform = new TableColumn<>("platform");
        colPlatform.setCellValueFactory(c -> c.getValue().platformProperty());
        colPlatform.setPrefWidth(100);

        TableColumn<FavoriteRow, String> colDate = new TableColumn<>("saved at");
        colDate.setCellValueFactory(c -> c.getValue().dateProperty());
        colDate.setPrefWidth(150);

        tv.getColumns().addAll(colCheck, colStar, colName, colStatus, colPlatform, colDate);
        return tv;
    }

    // ── Stats bar ──────────────────────────────────────────────────────

    private HBox buildStatsBar() {
        HBox bar = new HBox(18);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(5, 12, 5, 12));
        bar.setAlignment(Pos.CENTER_LEFT);

        totalLabel     = new Label("saved: 0");
        availableLabel = new Label("available: 0");
        takenLabel     = new Label("taken: 0");

        totalLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");
        availableLabel.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 12px;");
        takenLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 12px;");

        bar.getChildren().addAll(totalLabel, availableLabel, takenLabel);
        return bar;
    }

    // ── Bind ao AppState ───────────────────────────────────────────────

    private void bindState() {
        state.addOnFavoritesChanged(() ->
                javafx.application.Platform.runLater(this::applyFilter));
        applyFilter();
    }

    private void applyFilter() {
        String platformFilter = platformCombo.getValue();
        String search         = searchField.getText().toLowerCase().trim();

        List<FavoriteRow> filtered = state.getFavorites().stream()
                .filter(r -> "all".equals(platformFilter) ||
                        r.getPlatform().displayName.equals(platformFilter))
                .filter(r -> search.isEmpty() ||
                        r.getUsername().toLowerCase().contains(search))
                .map(FavoriteRow::new)
                .collect(Collectors.toList());

        table.getItems().setAll(filtered);
        updateStats(filtered);
    }

    private void updateStats(List<FavoriteRow> rows) {
        long avail = rows.stream().filter(r -> "available".equals(r.statusProperty().get())).count();
        long taken = rows.stream().filter(r -> "taken".equals(r.statusProperty().get())).count();
        totalLabel.setText("saved: " + rows.size());
        availableLabel.setText("available: " + avail);
        takenLabel.setText("taken: " + taken);
    }

    // ── Export CSV — delega ao ExportService ───────────────────────────

    /**
     * Antes: lógica embutida sem escape CSV correto.
     * Depois: delega ao ExportService — UI só cuida do FileChooser e do alerta.
     */
    private void exportCsv() {
        ExportService export = controller.getExportService();

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Favorites as CSV");
        chooser.setInitialFileName(export.suggestFilename(ExportService.ExportType.FAVORITES));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;

        ExportService.ExportResult result = export.exportFavorites(
                table.getItems().stream().map(FavoriteRow::toResult).toList(),
                file.toPath()
        );

        showAlert("Export", result.userMessage());
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setTitle(title); alert.setHeaderText(null); alert.showAndWait();
    }

    // ── FavoriteRow ────────────────────────────────────────────────────

    public static class FavoriteRow {
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private final StringProperty  name     = new SimpleStringProperty();
        private final StringProperty  status   = new SimpleStringProperty();
        private final StringProperty  platform = new SimpleStringProperty();
        private final StringProperty  date     = new SimpleStringProperty();
        private Platform       platformEnum;
        private UsernameResult originalResult;

        public FavoriteRow(UsernameResult r) {
            this.originalResult = r;
            name.set(r.getUsername());
            status.set(r.getStatus().getDisplayName());
            platform.set(r.getPlatform().displayName);
            date.set(r.getCheckedAtFormatted());
            platformEnum = r.getPlatform();
        }

        /** Retorna o UsernameResult original para o ExportService. */
        public UsernameResult toResult() {
            return originalResult;
        }

        public BooleanProperty selectedProperty() { return selected; }
        public StringProperty  nameProperty()     { return name; }
        public StringProperty  statusProperty()   { return status; }
        public StringProperty  platformProperty() { return platform; }
        public StringProperty  dateProperty()     { return date; }
        public String          getUsername()      { return name.get(); }
        public Platform        getPlatform()      { return platformEnum; }
    }

    private static class StatusCell extends TableCell<FavoriteRow, String> {
        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);
            if (empty || status == null) { setText(null); setStyle(""); return; }
            setText(status);
            String color = switch (status) {
                case "available" -> "#4caf50";
                case "taken"     -> "#f44336";
                case "error"     -> "#757575";
                default          -> "#cccccc";
            };
            setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        }
    }
}