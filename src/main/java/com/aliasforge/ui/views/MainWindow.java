package com.aliasforge.ui.views;

import com.aliasforge.ui.AppController;
import com.aliasforge.ui.panels.ResultsPanel;
import com.aliasforge.ui.panels.SidebarPanel;
import com.aliasforge.ui.panels.StatusBarPanel;
import com.aliasforge.ui.panels.ToolbarPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class MainWindow extends BorderPane {

    private final AppController controller;

    private ToolbarPanel    toolbarPanel;
    private ResultsPanel    resultsPanel;
    private SidebarPanel    sidebarPanel;
    private StatusBarPanel  statusBarPanel;
    private HistoryView     historyView;
    private FavoritesView   favoritesView;
    private ApiSettingsView apiSettingsView;

    public MainWindow(AppController controller) {
        this.controller = controller;
        getStyleClass().add("main-window");
        buildUI();
        wireController();
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
                buildApiSettingsTab()
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
        scroll.setStyle("-fx-background-color: #1a1a1a; -fx-background: #1a1a1a; -fx-border-color: transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Tab tab = new Tab("api settings");
        tab.setContent(scroll);
        return tab;
    }

    // ── Wire controller ────────────────────────────────────────────────

    private void wireController() {
        // Play
        toolbarPanel.getBtnPlay().setOnAction(e -> {
            if (controller.isPaused()) {
                controller.resume();
                toolbarPanel.setRunningState(true, false);
            } else {
                controller.start(sidebarPanel.buildConfig());
                toolbarPanel.setRunningState(true, false);
            }
        });

        // Pause / Resume
        toolbarPanel.getBtnPause().setOnAction(e -> {
            if (controller.isPaused()) {
                controller.resume();
                toolbarPanel.setRunningState(true, false);
            } else {
                controller.pause();
                toolbarPanel.setRunningState(true, true);
            }
        });

        // Stop
        toolbarPanel.getBtnStop().setOnAction(e -> {
            controller.stop();
            toolbarPanel.setRunningState(false, false);
        });

        // Stats
        controller.setOnStatsUpdate(stats -> {
            statusBarPanel.updateStats(
                    stats.available(), stats.taken(),
                    stats.rateLimit(), stats.error(),
                    stats.currentlyChecking()
            );
            int total = stats.checked() + stats.remaining();
            if (total > 0) {
                double pct = (double) stats.checked() / total;
                statusBarPanel.updateProgress(pct);
                toolbarPanel.updateProgress(pct, stats.checked(), total);
            }
        });

        // Completed
        controller.setOnCompleted(() ->
                toolbarPanel.setRunningState(false, false)
        );
    }
}