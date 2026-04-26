package com.aliasforge;

import atlantafx.base.theme.PrimerDark;
import com.aliasforge.ui.AppController;
import com.aliasforge.ui.views.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        LOGGER.info("Starting AliasForge...");

        AppController controller = new AppController();
        MainWindow mainWindow    = new MainWindow(controller);
        Scene scene              = new Scene(mainWindow, 1280, 760);

        String css = getClass().getResource("/css/app.css").toExternalForm();
        scene.getStylesheets().add(css);

        stage.setTitle("AliasForge");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);

        // Para o checker ao fechar a janela
        stage.setOnCloseRequest(e -> {
            LOGGER.info("Shutting down...");
            controller.stop();
        });

        stage.show();
        LOGGER.info("AliasForge started.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}