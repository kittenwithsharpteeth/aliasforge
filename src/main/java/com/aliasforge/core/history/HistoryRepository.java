package com.aliasforge.core.history;

import com.aliasforge.model.CheckStatus;
import com.aliasforge.model.Platform;
import com.aliasforge.model.UsernameResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Persiste e consulta o histórico de verificações em JSON.
 * Arquivo: ~/.aliasforge/history.json
 */
public class HistoryRepository {

    private static final Logger LOGGER    = LoggerFactory.getLogger(HistoryRepository.class);
    private static final String FILE_NAME = "history.json";
    private static final String DIR_NAME  = ".aliasforge";

    private static HistoryRepository instance;

    private final Path                    filePath;
    private final Gson                    gson;
    private final List<UsernameResult>    entries;

    // ── Singleton ──────────────────────────────────────────────────────

    private HistoryRepository() {
        Path dir   = Paths.get(System.getProperty("user.home"), DIR_NAME);
        this.filePath = dir.resolve(FILE_NAME);
        this.gson  = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();
        this.entries = new CopyOnWriteArrayList<>(load());
    }

    public static synchronized HistoryRepository getInstance() {
        if (instance == null) instance = new HistoryRepository();
        return instance;
    }

    // ── API pública ────────────────────────────────────────────────────

    public synchronized void add(UsernameResult result) {
        // Não salva status intermediários
        if (result.getStatus() == CheckStatus.CHECKING ||
                result.getStatus() == CheckStatus.PENDING) return;

        entries.add(0, result); // mais recente primeiro
        save();
        LOGGER.debug("History: added {} ({})", result.getUsername(), result.getStatus());
    }

    public List<UsernameResult> getAll() {
        return Collections.unmodifiableList(entries);
    }

    public List<UsernameResult> getByStatus(CheckStatus status) {
        return entries.stream()
                .filter(r -> r.getStatus() == status)
                .collect(Collectors.toList());
    }

    public List<UsernameResult> getByPlatform(Platform platform) {
        return entries.stream()
                .filter(r -> r.getPlatform() == platform)
                .collect(Collectors.toList());
    }

    public List<UsernameResult> getFavorites() {
        return entries.stream()
                .filter(UsernameResult::isFavorited)
                .collect(Collectors.toList());
    }

    public boolean alreadyChecked(String username, Platform platform) {
        return entries.stream().anyMatch(r ->
                r.getUsername().equalsIgnoreCase(username) &&
                        r.getPlatform() == platform);
    }

    public synchronized void toggleFavorite(String username, Platform platform) {
        for (int i = 0; i < entries.size(); i++) {
            UsernameResult r = entries.get(i);
            if (r.getUsername().equalsIgnoreCase(username) && r.getPlatform() == platform) {
                entries.set(i, r.withFavorited(!r.isFavorited()));
                save();
                return;
            }
        }
    }

    public synchronized void clear() {
        entries.clear();
        save();
        LOGGER.info("History cleared.");
    }

    public int size() { return entries.size(); }

    // ── Persistência ───────────────────────────────────────────────────

    private List<UsernameResult> load() {
        if (!Files.exists(filePath)) return new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(filePath)) {
            Type type = new TypeToken<List<HistoryEntry>>(){}.getType();
            List<HistoryEntry> raw = gson.fromJson(reader, type);
            if (raw == null) return new ArrayList<>();
            return raw.stream().map(HistoryEntry::toResult).collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.error("Failed to load history", e);
            return new ArrayList<>();
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(filePath.getParent());
            List<HistoryEntry> raw = entries.stream()
                    .map(HistoryEntry::fromResult)
                    .collect(Collectors.toList());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                gson.toJson(raw, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save history", e);
        }
    }

    // ── DTO para serialização JSON ─────────────────────────────────────

    private static class HistoryEntry {
        String  username;
        String  platform;
        String  status;
        long    responseTimeMs;
        String  origin;
        String  checkedAt;
        boolean favorited;

        static HistoryEntry fromResult(UsernameResult r) {
            HistoryEntry e = new HistoryEntry();
            e.username      = r.getUsername();
            e.platform      = r.getPlatform().name();
            e.status        = r.getStatus().name();
            e.responseTimeMs= r.getResponseTimeMs();
            e.origin        = r.getOrigin();
            e.checkedAt     = r.getCheckedAtFormatted();
            e.favorited     = r.isFavorited();
            return e;
        }

        UsernameResult toResult() {
            return new UsernameResult(
                    username,
                    Platform.valueOf(platform),
                    CheckStatus.valueOf(status),
                    responseTimeMs,
                    origin,
                    checkedAt != null
                            ? LocalDateTime.parse(checkedAt,
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            : LocalDateTime.now(),
                    favorited
            );
        }
    }
}