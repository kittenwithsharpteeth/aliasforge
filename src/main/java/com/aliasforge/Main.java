package com.aliasforge;

import atlantafx.base.theme.PrimerDark;
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

        // Permite que o app continue rodando sem janelas abertas
        Platform.setImplicitExit(false);

        AppController controller = new AppController();
        MainWindow mainWindow    = new MainWindow(controller);
        Scene scene              = new Scene(mainWindow, 1280, 760);

        String css = getClass().getResource("/css/app.css").toExternalForm();
        scene.getStylesheets().add(css);

        // ── Ícone da janela ────────────────────────────────────────────
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
                // onShow — restaura a janela
                () -> Platform.runLater(() -> {
                    stage.show();
                    stage.setIconified(false);
                    stage.toFront();
                }),
                // onExit — encerra completamente
                () -> {
                    LOGGER.info("Exit via tray.");
                    controller.stop();
                    tray.uninstall();
                    Platform.exit();
                    System.exit(0);
                },
                // onPause
                () -> Platform.runLater(controller::pause),
                // onResume
                () -> Platform.runLater(controller::resume)
        );

        // ── Minimizar para bandeja ─────────────────────────────────────
        stage.iconifiedProperty().addListener((obs, wasMin, isMin) -> {
            if (isMin && tray.isInstalled()) {
                Platform.runLater(() -> {
                    stage.hide();
                    // Notificação única ao minimizar pela primeira vez
                    tray.showMessage(
                            "AliasForge is running in the background",
                            "Double-click the tray icon to restore."
                    );
                });
            }
        });

        // ── Fechar janela (X) → vai para bandeja ───────────────────────
        stage.setOnCloseRequest(e -> {
            e.consume();
            if (tray.isInstalled()) {
                stage.hide();
                // Notificação ao fechar pela primeira vez
                tray.showMessage(
                        "AliasForge is running in the background",
                        "Double-click the tray icon to restore.\n" +
                                "Right-click the icon to exit completely."
                );
            } else {
                // Sem bandeja — encerra normalmente
                LOGGER.info("Shutting down (no tray).");
                controller.stop();
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