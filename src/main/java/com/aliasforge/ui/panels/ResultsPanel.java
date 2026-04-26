package com.aliasforge.ui.panels;

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

import java.util.stream.Collectors;

public class ResultsPanel extends VBox {

    private final AppController      controller;
    private TableView<ResultRow>     table;

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
        Button btnClear    = new Button("clear");

        btnAll.getStyleClass().add("af-btn");
        btnCopy.getStyleClass().add("af-btn");
        btnRecheck.getStyleClass().add("af-btn");
        btnFavorite.getStyleClass().add("af-btn");
        btnFavorite.setStyle("-fx-text-fill: #ffc107;");
        btnClear.getStyleClass().add("af-btn");

        btnAll.setOnAction(e ->
                table.getItems().forEach(r -> r.selectedProperty().set(true)));

        btnCopy.setOnAction(e -> {
            String text = table.getItems().stream()
                    .filter(r -> r.selectedProperty().get())
                    .map(ResultRow::getUsername)
                    .collect(Collectors.joining("\n"));
            if (!text.isEmpty()) {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(text);
                Clipboard.getSystemClipboard().setContent(cc);
            }
        });

        btnFavorite.setOnAction(e ->
                table.getItems().stream()
                        .filter(r -> r.selectedProperty().get())
                        .forEach(r -> controller.toggleFavorite(r.getUsername(), r.getPlatform())));

        btnClear.setOnAction(e -> table.getItems().clear());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label currentApi = new Label("current api ⓘ");
        currentApi.getStyleClass().add("af-label-muted");

        bar.getChildren().addAll(btnAll, btnCopy, btnRecheck, btnFavorite, btnClear, spacer, currentApi);
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
        colName.setPrefWidth(180);

        TableColumn<ResultRow, String> colStatus = new TableColumn<>("status");
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
        colStatus.setCellFactory(col -> new StatusCell());
        colStatus.setPrefWidth(100);

        TableColumn<ResultRow, String> colTime = new TableColumn<>("ms");
        colTime.setCellValueFactory(c -> c.getValue().timeProperty());
        colTime.setPrefWidth(70);

        TableColumn<ResultRow, String> colOrigin = new TableColumn<>("origin");
        colOrigin.setCellValueFactory(c -> c.getValue().originProperty());
        colOrigin.setPrefWidth(60);

        TableColumn<ResultRow, Boolean> colFav = new TableColumn<>("★");
        colFav.setCellValueFactory(c -> c.getValue().favoritedProperty());
        colFav.setCellFactory(col -> new FavoriteCell(controller));
        colFav.setMaxWidth(40); colFav.setMinWidth(40); colFav.setResizable(false);

        tv.getColumns().addAll(colCheck, colName, colStatus, colTime, colOrigin, colFav);
        return tv;
    }

    // ── Bind ao controller — SEM listener duplicado ────────────────────

    private void bindData() {
        controller.getResults().addListener(
                (javafx.collections.ListChangeListener<UsernameResult>) change -> {
                    while (change.next()) {
                        // Adicionado novo resultado
                        if (change.wasAdded()) {
                            for (UsernameResult r : change.getAddedSubList()) {
                                updateOrAdd(r);
                            }
                        }
                        // Resultado existente foi substituído (ex: CHECKING → AVAILABLE)
                        if (change.wasReplaced()) {
                            for (int i = change.getFrom(); i < change.getTo(); i++) {
                                updateOrAdd(controller.getResults().get(i));
                            }
                        }
                    }
                });
    }

    /**
     * Atualiza a linha existente se o username já estiver na tabela,
     * ou adiciona uma nova linha se não existir.
     * Garante que nunca há duplicatas.
     */
    private void updateOrAdd(UsernameResult result) {
        for (int i = 0; i < table.getItems().size(); i++) {
            ResultRow row = table.getItems().get(i);
            if (row.getUsername().equalsIgnoreCase(result.getUsername())) {
                row.update(result);
                // Força refresh da célula sem criar nova linha
                table.refresh();
                return;
            }
        }
        // Não existe ainda — adiciona
        table.getItems().add(new ResultRow(result));
        int last = table.getItems().size() - 1;
        if (last >= 0) table.scrollTo(last);
    }

    // ── ResultRow ──────────────────────────────────────────────────────

    public static class ResultRow {
        private final BooleanProperty selected  = new SimpleBooleanProperty(false);
        private final BooleanProperty favorited = new SimpleBooleanProperty(false);
        private final StringProperty  name      = new SimpleStringProperty();
        private final StringProperty  status    = new SimpleStringProperty();
        private final StringProperty  time      = new SimpleStringProperty();
        private final StringProperty  origin    = new SimpleStringProperty();
        private com.aliasforge.model.Platform platform;

        public ResultRow(UsernameResult r) { update(r); }

        public void update(UsernameResult r) {
            name.set(r.getUsername());
            status.set(r.getStatus().getDisplayName());
            time.set(r.getResponseTimeDisplay());
            origin.set(r.getOrigin() != null ? r.getOrigin() : "");
            favorited.set(r.isFavorited());
            this.platform = r.getPlatform();
        }

        public BooleanProperty selectedProperty()  { return selected; }
        public BooleanProperty favoritedProperty() { return favorited; }
        public StringProperty  nameProperty()      { return name; }
        public StringProperty  statusProperty()    { return status; }
        public StringProperty  timeProperty()      { return time; }
        public StringProperty  originProperty()    { return origin; }
        public String          getUsername()       { return name.get(); }
        public com.aliasforge.model.Platform getPlatform() { return platform; }
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