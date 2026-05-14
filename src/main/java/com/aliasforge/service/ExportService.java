package com.aliasforge.service;

import com.aliasforge.model.UsernameResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Centraliza toda a lógica de exportação de dados.
 */
public class ExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportService.class);

    private static final DateTimeFormatter EXPORT_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static ExportService instance;

    private ExportService() {}

    public static synchronized ExportService getInstance() {
        if (instance == null) instance = new ExportService();
        return instance;
    }

    // ── Exportação de results ──────────────────────────────────────────

    public ExportResult exportResults(List<UsernameResult> results, Path destination) {
        if (results.isEmpty()) return ExportResult.empty("No results to export.");

        try (BufferedWriter bw = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            bw.write("username,status,api,response_ms,origin,checked_at");
            bw.newLine();

            for (UsernameResult r : results) {
                bw.write(row(
                        r.getUsername(),
                        r.getStatus().getDisplayName(),
                        r.getPlatform().displayName,
                        r.getResponseTimeDisplay(),
                        r.getOrigin(),
                        r.getCheckedAtFormatted()
                ));
                bw.newLine();
            }

            LOGGER.info("Exported {} results to {}", results.size(), destination);
            return ExportResult.success(destination, results.size());

        } catch (IOException e) {
            LOGGER.error("Failed to export results to {}: {}", destination, e.getMessage());
            return ExportResult.failure(e.getMessage());
        }
    }

    // ── Exportação de histórico ────────────────────────────────────────

    public ExportResult exportHistory(List<UsernameResult> history, Path destination) {
        if (history.isEmpty()) return ExportResult.empty("No history to export.");

        try (BufferedWriter bw = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            bw.write("username,status,api,checked_at,response_ms,favorited");
            bw.newLine();

            for (UsernameResult r : history) {
                bw.write(row(
                        r.getUsername(),
                        r.getStatus().getDisplayName(),
                        r.getPlatform().displayName,
                        r.getCheckedAtFormatted(),
                        r.getResponseTimeDisplay(),
                        String.valueOf(r.isFavorited())
                ));
                bw.newLine();
            }

            LOGGER.info("Exported {} history entries to {}", history.size(), destination);
            return ExportResult.success(destination, history.size());

        } catch (IOException e) {
            LOGGER.error("Failed to export history to {}: {}", destination, e.getMessage());
            return ExportResult.failure(e.getMessage());
        }
    }

    // ── Exportação de favoritos ────────────────────────────────────────

    public ExportResult exportFavorites(List<UsernameResult> favorites, Path destination) {
        if (favorites.isEmpty()) return ExportResult.empty("No favorites to export.");

        try (BufferedWriter bw = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            bw.write("username,status,platform,saved_at");
            bw.newLine();

            for (UsernameResult r : favorites) {
                bw.write(row(
                        r.getUsername(),
                        r.getStatus().getDisplayName(),
                        r.getPlatform().displayName,
                        r.getCheckedAtFormatted()
                ));
                bw.newLine();
            }

            LOGGER.info("Exported {} favorites to {}", favorites.size(), destination);
            return ExportResult.success(destination, favorites.size());

        } catch (IOException e) {
            LOGGER.error("Failed to export favorites to {}: {}", destination, e.getMessage());
            return ExportResult.failure(e.getMessage());
        }
    }

    // ── Exportação de logs ─────────────────────────────────────────────

    /**
     * Exporta erros, inconclusivos e rate limits (aba Logs).
     */
    public ExportResult exportLogs(List<UsernameResult> logs, Path destination) {
        if (logs.isEmpty()) return ExportResult.empty("No logs to export.");

        try (BufferedWriter bw = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            bw.write("time,type,username,api,detail");
            bw.newLine();

            for (UsernameResult r : logs) {
                bw.write(row(
                        r.getCheckedAtFormatted(),
                        r.getStatus().getDisplayName(),
                        r.getUsername(),
                        r.getPlatform().displayName,
                        r.getErrorDetail() != null ? r.getErrorDetail() : ""
                ));
                bw.newLine();
            }

            LOGGER.info("Exported {} log entries to {}", logs.size(), destination);
            return ExportResult.success(destination, logs.size());

        } catch (IOException e) {
            LOGGER.error("Failed to export logs to {}: {}", destination, e.getMessage());
            return ExportResult.failure(e.getMessage());
        }
    }

    // ── Helpers de formatação ──────────────────────────────────────────

    public String suggestFilename(ExportType type) {
        String ts = LocalDateTime.now().format(EXPORT_TS);
        return switch (type) {
            case RESULTS   -> "aliasforge_results_"   + ts + ".csv";
            case HISTORY   -> "aliasforge_history_"   + ts + ".csv";
            case FAVORITES -> "aliasforge_favorites_" + ts + ".csv";
            case LOGS      -> "aliasforge_logs_"      + ts + ".csv";
        };
    }

    private String row(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvCell(fields[i]));
        }
        return sb.toString();
    }

    private String csvCell(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ── Tipos e resultados ─────────────────────────────────────────────

    public enum ExportType { RESULTS, HISTORY, FAVORITES, LOGS }

    public record ExportResult(
            boolean success,
            boolean empty,
            Path    destination,
            int     rowCount,
            String  errorMessage
    ) {
        public static ExportResult success(Path dest, int rows) {
            return new ExportResult(true, false, dest, rows, null);
        }

        public static ExportResult empty(String message) {
            return new ExportResult(false, true, null, 0, message);
        }

        public static ExportResult failure(String message) {
            return new ExportResult(false, false, null, 0, message);
        }

        public String userMessage() {
            if (empty)    return errorMessage;
            if (!success) return "Export failed: " + errorMessage;
            return "Exported " + rowCount + " records to:\n" + destination;
        }
    }
}
