package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

/**
 * Roblox API — lê o JSON da API de busca e verifica username exato.
 * Sobrescreve check() diretamente para ler o body da resposta.
 */
public class RobloxApi extends AbstractPlatformApi {

    private static final String SEARCH_URL =
            "https://users.roblox.com/v1/users/search?keyword=";

    @Override public Platform getPlatform()           { return Platform.ROBLOX; }
    @Override public int      getRecommendedDelayMs() { return 600; }

    @Override
    public CheckResult check(String username) {
        if (!isValidUsername(username)) return CheckResult.error("invalid username");

        long start = System.currentTimeMillis();
        try {
            String url = SEARCH_URL + username + "&limit=10";
            HttpURLConnection conn = openConnection(url);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Origin", "https://www.roblox.com");
            conn.setRequestProperty("Referer", "https://www.roblox.com/");
            conn.setInstanceFollowRedirects(true);

            int  code = conn.getResponseCode();
            long ms   = System.currentTimeMillis() - start;

            if (code == 429) { conn.disconnect(); return CheckResult.rateLimit(); }
            if (code == 403) { conn.disconnect(); return CheckResult.rateLimit(); }
            if (code != 200) { conn.disconnect(); return CheckResult.error("http " + code); }

            // Lê o JSON
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            conn.disconnect();

            // Procura pelo username exato no JSON
            // Formato: {"data":[{"id":...,"name":"username",...},...]}
            String body      = sb.toString();
            String lowerUser = username.toLowerCase();
            boolean exactMatch = body.toLowerCase()
                    .contains("\"name\":\"" + lowerUser + "\"");

            return exactMatch ? CheckResult.taken(ms) : CheckResult.available(ms);

        } catch (java.net.SocketTimeoutException e) {
            return CheckResult.rateLimit();
        } catch (Exception e) {
            LOGGER.error("Error checking Roblox '{}': {}", username, e.getMessage());
            return CheckResult.error(e.getMessage());
        }
    }

    // Necessário pela classe abstrata mas não usado (check() é sobrescrito)
    @Override
    protected String buildUrl(String username) {
        return SEARCH_URL + username + "&limit=10";
    }

    @Override
    protected CheckResult interpretResponse(int httpCode, long ms) {
        return CheckResult.error("not used");
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.ROBLOX.minLength || len > Platform.ROBLOX.maxLength) return false;
        if (username.startsWith("_") || username.endsWith("_")) return false;
        if (username.contains("__")) return false;
        return username.matches("[a-zA-Z0-9_]+");
    }
}