package com.aliasforge.ui.views;

import com.aliasforge.core.state.AppState;
import com.aliasforge.service.UsernameCheckService;
import com.aliasforge.ui.AppController;
import com.aliasforge.ui.panels.ResultsPanel;
import com.aliasforge.ui.panels.SidebarPanel;
import com.aliasforge.ui.panels.StatusBarPanel;
import com.aliasforge.ui.panels.ToolbarPanel;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * MainWindow — monta a janela e conecta AppState à UI via listeners.
 */
public class MainWindow extends BorderPane {

    private final AppController controller;
    private final AppState      state;

    private ToolbarPanel    toolbarPanel;
    private ResultsPanel    resultsPanel;
    private SidebarPanel    sidebarPanel;
    private StatusBarPanel  statusBarPanel;
    private HistoryView     historyView;
    private FavoritesView   favoritesView;
    private ApiSettingsView apiSettingsView;
    private LogsView        logsView;
    private SettingsView    settingsView;

    public MainWindow(AppController controller) {
        this.controller = controller;
        this.state      = controller.getState();
        getStyleClass().add("main-window");
        buildUI();
        wireActions();
        wireState();
    }

    private void buildUI() {
        setCenter(buildTabPane());
    }

    private TabPane buildTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("af-tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
                buildAliasForgeTab(),
                buildHistoryTab(),
                buildFavoritesTab(),
                buildApiSettingsTab(),
                buildLogsTab(),
                buildSettingsTab()
        );
        return tabPane;
    }

    // ── Abas ───────────────────────────────────────────────────────────

    private Tab buildAliasForgeTab() {
        toolbarPanel   = new ToolbarPanel();
        resultsPanel   = new ResultsPanel(controller);
        sidebarPanel   = new SidebarPanel(controller);
        statusBarPanel = new StatusBarPanel();

        SplitPane splitPane = new SplitPane(resultsPanel, sidebarPanel);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.setDividerPositions(0.68);
        SplitPane.setResizableWithParent(sidebarPanel, false);

        VBox content = new VBox(toolbarPanel, splitPane, statusBarPanel);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        Tab tab = new Tab("aliasforge");
        tab.setContent(content);
        return tab;
    }

    private Tab buildHistoryTab() {
        historyView = new HistoryView(controller);
        VBox content = new VBox(historyView);
        VBox.setVgrow(historyView, Priority.ALWAYS);
        Tab tab = new Tab("history");
        tab.setContent(content);
        return tab;
    }

    private Tab buildFavoritesTab() {
        favoritesView = new FavoritesView(controller);
        VBox content = new VBox(favoritesView);
        VBox.setVgrow(favoritesView, Priority.ALWAYS);
        Tab tab = new Tab("favorites");
        tab.setContent(content);
        return tab;
    }

    private Tab buildApiSettingsTab() {
        apiSettingsView = new ApiSettingsView();
        ScrollPane scroll = new ScrollPane(apiSettingsView);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #1a1a1a; -fx-background: #1a1a1a;" +
                "-fx-border-color: transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Tab tab = new Tab("api settings");
        tab.setContent(scroll);
        return tab;
    }

    private Tab buildLogsTab() {
        logsView = new LogsView(controller);
        VBox content = new VBox(logsView);
        VBox.setVgrow(logsView, Priority.ALWAYS);
        Tab tab = new Tab("logs");
        tab.setContent(content);
        return tab;
    }

    private Tab buildSettingsTab() {
        settingsView = new SettingsView();
        ScrollPane scroll = new ScrollPane(settingsView);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #1a1a1a; -fx-background: #1a1a1a;" +
                "-fx-border-color: transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Tab tab = new Tab("settings");
        tab.setContent(scroll);
        return tab;
    }

    // ── Ações dos botões (UI → Controller) ────────────────────────────

    private void wireActions() {
        toolbarPanel.getBtnPlay().setOnAction(e -> {
            if (controller.isPaused()) {
                controller.resume();
            } else {
                toolbarPanel.resetProgress();
                UsernameCheckService.StartResult result =
                        controller.start(sidebarPanel.buildConfig());
                if (result.isRejected()) {
                    showError("Cannot Start", result.rejectionReason());
                }
            }
        });

        toolbarPanel.getBtnPause().setOnAction(e -> {
            if (controller.isPaused()) {
                controller.resume();
            } else {
                controller.pause();
            }
        });

        toolbarPanel.getBtnStop().setOnAction(e -> controller.stop());

        toolbarPanel.setOnFilterChanged((filter, search) ->
                resultsPanel.applyFilter(filter, search));
    }

    // ── Estado (AppState → UI) ─────────────────────────────────────────

    private void wireState() {
        state.addOnRunningChanged(() -> Platform.runLater(() -> {
            boolean running = state.isRunning();
            boolean paused  = state.isPaused();
            toolbarPanel.setRunningState(running, paused);
        }));

        state.addOnStatsChanged(stats -> Platform.runLater(() -> {
            statusBarPanel.updateStats(
                    stats.available(), stats.taken(),
                    stats.rateLimit(), stats.inconclusive(), stats.error(),
                    stats.currentlyChecking()
            );
            int total = stats.checked() + stats.remaining();
            if (total > 0) {
                double pct = (double) stats.checked() / total;
                statusBarPanel.updateProgress(pct);
                toolbarPanel.updateProgress(pct, stats.checked(), total);
            }
        }));

        state.addOnCompleted(() -> Platform.runLater(() ->
                toolbarPanel.setRunningState(false, false)));
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
