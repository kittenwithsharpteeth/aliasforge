package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.stream.Collectors;

/**
 * TikTok — verificação via GET no perfil público.
 * GET https://www.tiktok.com/@{username}
 *
 * TikTok sempre retorna 200 (SPA), então scraping é obrigatório.
 * Indicadores de "não existe": título genérico, meta tags de erro,
 * ou ausência do username no og:title.
 *
 * Delay alto (2000ms) para evitar bloqueio por bot detection.
 */
public class TikTokApi extends AbstractPlatformApi {

    private static final String ENDPOINT = "https://www.tiktok.com/@";

    @Override public Platform getPlatform()          { return Platform.TIKTOK; }
    @Override public int      getRecommendedDelayMs(){ return 2000; }

    @Override
    protected String buildUrl(String username) { return ENDPOINT + username; }

    @Override
    protected CheckResult interpretResponse(int code, long ms) {
        // TikTok raramente retorna 404 — usado só como fallback rápido
        return switch (code) {
            case 404      -> CheckResult.available(ms);
            case 429      -> CheckResult.rateLimit();
            default       -> CheckResult.error("unexpected http " + code);
        };
    }

    @Override
    public CheckResult check(String username) {
        if (!isValidUsername(username)) return CheckResult.error("invalid username format");

        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = openConnection(buildUrl(username));
            // TikTok exige User-Agent mais realista para não bloquear
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

            int  code = conn.getResponseCode();
            long ms   = System.currentTimeMillis() - start;

            if (code == 404) { conn.disconnect(); return CheckResult.available(ms); }
            if (code == 429) { conn.disconnect(); return CheckResult.rateLimit(); }

            // TikTok retorna 200 mesmo para perfis inexistentes — scraping obrigatório
            if (code != 200) { conn.disconnect(); return CheckResult.error("http " + code); }

            String body = new BufferedReader(new InputStreamReader(conn.getInputStream()))
                    .lines().limit(80) // lê só as primeiras 80 linhas (head do HTML)
                    .collect(Collectors.joining("\n"));
            conn.disconnect();

            if (profileNotFound(body, username)) return CheckResult.available(ms);
            return CheckResult.taken(ms);

        } catch (java.net.SocketTimeoutException e) {
            return CheckResult.rateLimit();
        } catch (Exception e) {
            LOGGER.error("Error checking '{}' on TikTok: {}", username, e.getMessage());
            return CheckResult.error(e.getMessage());
        }
    }

    private boolean profileNotFound(String html, String username) {
        if (html == null) return false;
        String lower = html.toLowerCase();

        // Página de erro: título genérico sem o username
        boolean hasUsername = lower.contains("@" + username.toLowerCase())
                || lower.contains("\"" + username.toLowerCase() + "\"");

        // Indicadores de "não encontrado"
        boolean hasNotFound = lower.contains("couldn't find this account")
                || lower.contains("page not found")
                || lower.contains("user not found")
                || lower.contains("\"statuscode\":10202") // API interna de erro
                || lower.contains("noindex");             // perfis inexistentes recebem noindex

        return hasNotFound || !hasUsername;
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.TIKTOK.minLength || len > Platform.TIKTOK.maxLength) return false;
        // TikTok: letras, números, underscores e pontos
        return username.matches("[a-zA-Z0-9_.]+");
    }
}