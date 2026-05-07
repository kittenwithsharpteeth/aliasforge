package com.aliasforge;

import atlantafx.base.theme.PrimerDark;
import com.aliasforge.config.AppConfig;
import com.aliasforge.ui.AppController;
import com.aliasforge.ui.views.MainWindow;
import com.aliasforge.util.SystemTrayService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class Main extends Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        LOGGER.info("Starting AliasForge...");

        Platform.setImplicitExit(false);

        AppController controller = new AppController();
        MainWindow mainWindow    = new MainWindow(controller);
        Scene scene              = new Scene(mainWindow, 1280, 760);

        String css = getClass().getResource("/css/app.css").toExternalForm();
        scene.getStylesheets().add(css);

        try {
            Image icon = new Image(
                    Objects.requireNonNull(
                            getClass().getResourceAsStream("/icons/icon.png")));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            LOGGER.warn("Could not load window icon: {}", e.getMessage());
        }

        stage.setTitle("AliasForge");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);

        // ── System Tray ────────────────────────────────────────────────
        SystemTrayService tray = SystemTrayService.getInstance();
        tray.install(
                () -> Platform.runLater(() -> {
                    stage.show();
                    stage.setIconified(false);
                    stage.toFront();
                }),
                () -> {
                    LOGGER.info("Exit via tray.");
                    controller.stopAll(); // para algoritmo + manual queue
                    tray.uninstall();
                    Platform.exit();
                    System.exit(0);
                },
                () -> Platform.runLater(controller::pause),
                () -> Platform.runLater(controller::resume)
        );

        // ── Minimizar para bandeja ─────────────────────────────────────
        stage.iconifiedProperty().addListener((obs, wasMin, isMin) -> {
            if (isMin && tray.isInstalled()) {
                boolean minimizeToTray = AppConfig.getInstance()
                        .getSettings().isMinimizeToTray();
                if (minimizeToTray) {
                    Platform.runLater(() -> {
                        stage.hide();
                        tray.showMessage(
                                "AliasForge is running in the background",
                                "Double-click the tray icon to restore.");
                    });
                }
            }
        });

        // ── Fechar janela ──────────────────────────────────────────────
        stage.setOnCloseRequest(e -> {
            e.consume();
            boolean minimizeToTray = AppConfig.getInstance()
                    .getSettings().isMinimizeToTray();

            if (tray.isInstalled() && minimizeToTray) {
                stage.hide();
                tray.showMessage(
                        "AliasForge is running in the background",
                        "Double-click the tray icon to restore.\n" +
                                "Right-click the icon to exit completely.");
            } else {
                LOGGER.info("Shutting down.");
                controller.stopAll(); // para tudo, incluindo manualQueue
                tray.uninstall();
                Platform.exit();
                System.exit(0);
            }
        });

        stage.show();
        LOGGER.info("AliasForge started.");
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.UIElement", "true");
        launch(args);
    }
}