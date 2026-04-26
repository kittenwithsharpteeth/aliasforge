package com.aliasforge.config;

import com.aliasforge.model.AppSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Gerencia a persistência das configurações do app em JSON.
 * Arquivo salvo em: ~/.aliasforge/settings.json
 *
 * Uso:
 *   AppConfig config = AppConfig.getInstance();
 *   AppSettings settings = config.getSettings();
 *   settings.setDelayBetweenRequestsMs(500);
 *   config.save();
 */
public class AppConfig {

    private static final Logger LOGGER    = LoggerFactory.getLogger(AppConfig.class);
    private static final String DIR_NAME  = ".aliasforge";
    private static final String FILE_NAME = "settings.json";

    private static AppConfig instance;

    private final Path        settingsPath;
    private final Gson        gson;
    private       AppSettings settings;

    // ── Singleton ──────────────────────────────────────────────────────

    private AppConfig() {
        Path dir = Paths.get(System.getProperty("user.home"), DIR_NAME);
        this.settingsPath = dir.resolve(FILE_NAME);
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            Files.createDirectories(dir);
            LOGGER.info("Config directory ready: {}", dir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
        }

        this.settings = load();
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) instance = new AppConfig();
        return instance;
    }

    // ── Load ───────────────────────────────────────────────────────────

    private AppSettings load() {
        if (!Files.exists(settingsPath)) {
            LOGGER.info("No settings file found — using defaults.");
            return new AppSettings();
        }
        try (Reader reader = Files.newBufferedReader(settingsPath)) {
            AppSettings loaded = gson.fromJson(reader, AppSettings.class);
            if (loaded == null) return new AppSettings();
            LOGGER.info("Settings loaded from {}", settingsPath);
            return loaded;
        } catch (IOException e) {
            LOGGER.error("Failed to read settings file — using defaults", e);
            return new AppSettings();
        }
    }

    // ── Save ───────────────────────────────────────────────────────────

    public void save() {
        try (Writer writer = Files.newBufferedWriter(settingsPath)) {
            gson.toJson(settings, writer);
            LOGGER.info("Settings saved to {}", settingsPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save settings", e);
        }
    }

    // ── API pública ────────────────────────────────────────────────────

    public AppSettings getSettings() { return settings; }

    /** Reseta para os defaults e salva. */
    public void reset() {
        settings = new AppSettings();
        save();
        LOGGER.info("Settings reset to defaults.");
    }
}