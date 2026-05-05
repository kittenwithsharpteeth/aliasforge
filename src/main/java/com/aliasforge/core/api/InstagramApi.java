package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.stream.Collectors;

/**
 * Instagram — verificação via GET no perfil público.
 * GET https://www.instagram.com/{username}/
 *
 * Instagram pode retornar 200 para perfis inexistentes (SPA),
 * então scraping é usado como fallback.
 * Indicadores: og:title genérico, "Sorry, this page isn't available".
 *
 * Delay alto (2500ms) por bot detection agressivo do Instagram.
 */
public class InstagramApi extends AbstractPlatformApi {

    private static final String ENDPOINT = "https://www.instagram.com/";

    @Override public Platform getPlatform()          { return Platform.INSTAGRAM; }
    @Override public int      getRecommendedDelayMs(){ return 2500; }

    @Override
    protected String buildUrl(String username) { return ENDPOINT + username + "/"; }

    @Override
    protected CheckResult interpretResponse(int code, long ms) {
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
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

            int  code = conn.getResponseCode();
            long ms   = System.currentTimeMillis() - start;

            if (code == 404) { conn.disconnect(); return CheckResult.available(ms); }
            if (code == 429) { conn.disconnect(); return CheckResult.rateLimit(); }
            if (code != 200) { conn.disconnect(); return CheckResult.error("http " + code); }

            // Scraping: lê só o head do HTML para detectar página de erro
            String body = new BufferedReader(new InputStreamReader(conn.getInputStream()))
                    .lines().limit(60)
                    .collect(Collectors.joining("\n"));
            conn.disconnect();

            if (profileNotFound(body)) return CheckResult.available(ms);
            return CheckResult.taken(ms);

        } catch (java.net.SocketTimeoutException e) {
            return CheckResult.rateLimit();
        } catch (Exception e) {
            LOGGER.error("Error checking '{}' on Instagram: {}", username, e.getMessage());
            return CheckResult.error(e.getMessage());
        }
    }

    private boolean profileNotFound(String html) {
        if (html == null) return false;
        String lower = html.toLowerCase();
        return lower.contains("sorry, this page isn't available")
                || lower.contains("page not found")
                || lower.contains("wasn't found")
                || lower.contains("content=\"instagram\"")  // título genérico sem username
                || lower.contains("\"loginRequired\"");     // requer login = provável inexistente
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.INSTAGRAM.minLength || len > Platform.INSTAGRAM.maxLength) return false;
        // Instagram: letras, números, underscores e pontos
        return username.matches("[a-zA-Z0-9_.]+");
    }
}