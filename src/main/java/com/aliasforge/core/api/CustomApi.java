package com.aliasforge.core.api;

import com.aliasforge.config.AppConfig;
import com.aliasforge.model.CustomApiSettings;
import com.aliasforge.model.CustomApiSettings.DetectionMode;
import com.aliasforge.model.Platform;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

/**
 * Custom API — configurável pelo usuário via aba "api settings".
 *
 * Suporta dois modos de detecção, configuráveis independentemente:
 *
 * 1. STATUS_CODE  → compara o HTTP status code com os valores configurados
 *                   (ex: 404 = available, 200 = taken)
 *
 * 2. BODY_SCRAPING → lê o body da resposta e procura pelas strings
 *                    configuradas (ex: "user not found" = available)
 *
 * 3. BOTH (padrão) → tenta STATUS_CODE primeiro; se inconclusivo
 *                    (o código não bate com nenhum configurado),
 *                    cai no BODY_SCRAPING como fallback.
 *
 * Headers customizados são suportados via JSON:
 *   {"Authorization": "Bearer TOKEN", "X-Api-Key": "abc123"}
 */
public class CustomApi extends AbstractPlatformApi {

    @Override public Platform getPlatform() { return Platform.CUSTOM; }

    @Override
    public int getRecommendedDelayMs() {
        return AppConfig.getInstance().getSettings().getCustomApi().getDelayMs();
    }

    @Override
    public boolean isAvailable() {
        CustomApiSettings cfg = AppConfig.getInstance().getSettings().getCustomApi();
        return cfg.isEnabled() && cfg.isConfigured();
    }

    @Override
    public String getUnavailableReason() {
        CustomApiSettings cfg = AppConfig.getInstance().getSettings().getCustomApi();
        if (!cfg.isEnabled()) {
            return "Custom API is disabled. Go to API Settings to configure and enable it.";
        }
        if (cfg.getEndpointUrl() == null || cfg.getEndpointUrl().isBlank()) {
            return "Custom API endpoint URL is not set.";
        }
        if (!cfg.isConfigured()) {
            return "Custom API is not fully configured. Check detection mode settings in API Settings.";
        }
        return "Custom API is not available.";
    }

    @Override
    protected String buildUrl(String username) {
        CustomApiSettings cfg = AppConfig.getInstance().getSettings().getCustomApi();
        String base = cfg.getEndpointUrl();
        if (base == null || base.isBlank()) return "";
        // Garante que a URL termina com "/" antes de adicionar o username
        if (!base.endsWith("/")) base = base + "/";
        return base + username;
    }

    @Override
    protected CheckResult interpretResponse(int httpCode, long ms) {
        // Usado apenas no fallback do AbstractPlatformApi — a lógica real está em check()
        CustomApiSettings cfg = AppConfig.getInstance().getSettings().getCustomApi();
        return interpretStatusCode(cfg, httpCode, ms);
    }

    // ── check() principal ──────────────────────────────────────────────

    @Override
    public CheckResult check(String username) {
        if (!isValidUsername(username)) return CheckResult.error("invalid username format");
        if (!isAvailable())             return CheckResult.unavailable(getUnavailableReason());

        CustomApiSettings cfg = AppConfig.getInstance().getSettings().getCustomApi();
        String url = buildUrl(username);

        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = openConnection(url, cfg.getTimeoutMs());
            applyCustomHeaders(conn, cfg.getCustomHeaders());

            int    code = conn.getResponseCode();
            long   ms   = System.currentTimeMillis() - start;

            LOGGER.debug("custom api username={} http={} time={}ms url={}",
                    username, code, ms, url);

            // Rate limit — verificar antes de qualquer outra coisa
            if (cfg.getRateLimitStatusCode() > 0 && code == cfg.getRateLimitStatusCode()) {
                conn.disconnect();
                return CheckResult.rateLimit();
            }

            DetectionMode mode = cfg.getDetectionMode();

            // ── Modo: somente status code ──────────────────────────────
            if (mode == DetectionMode.STATUS_CODE) {
                conn.disconnect();
                return interpretStatusCode(cfg, code, ms);
            }

            // ── Modo: somente body scraping ────────────────────────────
            if (mode == DetectionMode.BODY_SCRAPING) {
                String body = readBody(conn, cfg.getBodyReadLines());
                conn.disconnect();
                return interpretBody(cfg, body, ms);
            }

            // ── Modo: ambos (status code primeiro, scraping como fallback) ─
            // BOTH
            CheckResult statusResult = interpretStatusCode(cfg, code, ms);

            // Se o status code foi conclusivo, retorna imediatamente
            if (statusResult.status() != com.aliasforge.model.CheckStatus.ERROR) {
                conn.disconnect();
                return statusResult;
            }

            // Status code inconclusivo — tenta body scraping como fallback
            LOGGER.debug("custom api: status code {} inconclusive for '{}', trying body scraping",
                    code, username);
            String body = readBody(conn, cfg.getBodyReadLines());
            conn.disconnect();

            CheckResult bodyResult = interpretBody(cfg, body, ms);

            // Se o body também foi inconclusivo, retorna o erro original do status code
            if (bodyResult.status() == com.aliasforge.model.CheckStatus.ERROR) {
                return CheckResult.error(
                        "inconclusive: http " + code + " and body did not match any configured string");
            }

            return bodyResult;

        } catch (java.net.SocketTimeoutException e) {
            return CheckResult.rateLimit();
        } catch (java.net.UnknownHostException e) {
            return CheckResult.error("could not reach host — check endpoint URL");
        } catch (Exception e) {
            LOGGER.error("Custom API check failed for '{}': {}", username, e.getMessage());
            return CheckResult.error(e.getMessage());
        }
    }

    // ── Interpretação por status code ──────────────────────────────────

    private CheckResult interpretStatusCode(CustomApiSettings cfg, int code, long ms) {
        if (code == cfg.getAvailableStatusCode()) return CheckResult.available(ms);
        if (code == cfg.getTakenStatusCode())     return CheckResult.taken(ms);
        if (cfg.getRateLimitStatusCode() > 0 &&
                code == cfg.getRateLimitStatusCode())  return CheckResult.rateLimit();

        // Código não configurado — inconclusivo
        return CheckResult.error("unexpected http " + code +
                " (configured: available=" + cfg.getAvailableStatusCode() +
                ", taken=" + cfg.getTakenStatusCode() + ")");
    }

    // ── Interpretação por body scraping ────────────────────────────────

    private CheckResult interpretBody(CustomApiSettings cfg, String body, long ms) {
        if (body == null || body.isEmpty()) {
            return CheckResult.error("empty response body");
        }

        String lower            = body.toLowerCase();
        String availableSignal  = cfg.getAvailableBodyString();
        String takenSignal      = cfg.getTakenBodyString();

        boolean hasAvailableSignal = availableSignal != null && !availableSignal.isBlank();
        boolean hasTakenSignal     = takenSignal     != null && !takenSignal.isBlank();

        // Verifica sinal de "disponível" primeiro (mais seguro — evita false positives)
        if (hasAvailableSignal && lower.contains(availableSignal.toLowerCase().trim())) {
            LOGGER.debug("custom api body scraping: found available signal '{}'", availableSignal);
            return CheckResult.available(ms);
        }

        // Verifica sinal de "ocupado"
        if (hasTakenSignal && lower.contains(takenSignal.toLowerCase().trim())) {
            LOGGER.debug("custom api body scraping: found taken signal '{}'", takenSignal);
            return CheckResult.taken(ms);
        }

        // Nenhum sinal encontrado
        return CheckResult.error("body did not contain any configured detection string");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Lê as primeiras N linhas do body da resposta.
     * Se bodyReadLines == 0, lê tudo.
     */
    private String readBody(HttpURLConnection conn, int maxLines) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            if (maxLines <= 0) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
            return reader.lines().limit(maxLines).collect(Collectors.joining("\n"));
        } catch (Exception e) {
            // Alguns servidores retornam o body no error stream (ex: 404 com JSON)
            try {
                var errStream = conn.getErrorStream();
                if (errStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(errStream));
                    if (maxLines <= 0) {
                        return reader.lines().collect(Collectors.joining("\n"));
                    }
                    return reader.lines().limit(maxLines).collect(Collectors.joining("\n"));
                }
            } catch (Exception ignored) {}
            LOGGER.warn("custom api: could not read response body: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Aplica headers customizados a partir de uma string JSON.
     * Formato esperado: {"Header-Name": "value", ...}
     * Silenciosamente ignora se o JSON for inválido ou vazio.
     */
    private void applyCustomHeaders(HttpURLConnection conn, String headersJson) {
        if (headersJson == null || headersJson.isBlank()) return;
        try {
            JsonObject obj = JsonParser.parseString(headersJson.trim()).getAsJsonObject();
            obj.entrySet().forEach(entry ->
                    conn.setRequestProperty(
                            entry.getKey(),
                            entry.getValue().getAsString()
                    )
            );
            LOGGER.debug("custom api: applied {} custom headers", obj.size());
        } catch (Exception e) {
            LOGGER.warn("custom api: could not parse custom headers JSON: {}", e.getMessage());
        }
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null || username.isBlank()) return false;
        int len = username.length();
        return len >= Platform.CUSTOM.minLength && len <= Platform.CUSTOM.maxLength;
    }
}