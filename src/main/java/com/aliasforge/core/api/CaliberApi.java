package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.stream.Collectors;

/**
 * caliber.lol — verificação via GET no perfil público.
 * GET https://caliber.lol/u/{username}
 * 200 = taken, 404 = available.
 * Fallback: scraping procura por indicadores de perfil inexistente no HTML.
 */
public class CaliberApi extends AbstractPlatformApi {

    private static final String ENDPOINT = "https://caliber.lol/u/";

    @Override public Platform getPlatform()          { return Platform.CALIBER; }
    @Override public int      getRecommendedDelayMs(){ return 800; }

    @Override
    protected String buildUrl(String username) { return ENDPOINT + username; }

    @Override
    protected CheckResult interpretResponse(int code, long ms) {
        return switch (code) {
            case 200      -> CheckResult.taken(ms);
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
            int  code = conn.getResponseCode();
            long ms   = System.currentTimeMillis() - start;

            if (code == 404) { conn.disconnect(); return CheckResult.available(ms); }
            if (code == 429) { conn.disconnect(); return CheckResult.rateLimit(); }
            if (code != 200) { conn.disconnect(); return CheckResult.error("http " + code); }

            // Fallback: scraping para confirmar existência do perfil
            String body = new BufferedReader(new InputStreamReader(conn.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));
            conn.disconnect();

            if (containsNotFound(body)) return CheckResult.available(ms);
            return CheckResult.taken(ms);

        } catch (java.net.SocketTimeoutException e) {
            return CheckResult.rateLimit();
        } catch (Exception e) {
            LOGGER.error("Error checking '{}' on caliber.lol: {}", username, e.getMessage());
            return CheckResult.error(e.getMessage());
        }
    }

    private boolean containsNotFound(String html) {
        if (html == null) return false;
        String lower = html.toLowerCase();
        return lower.contains("user not found")
                || lower.contains("page not found")
                || lower.contains("does not exist")
                || lower.contains("not found")
                || lower.contains("404");
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.CALIBER.minLength || len > Platform.CALIBER.maxLength) return false;
        return username.matches("[a-zA-Z0-9_\\-.]+");
    }
}