package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

import java.net.HttpURLConnection;

/**
 * Roblox — usa endpoint de perfil direto.
 *
 * GET https://www.roblox.com/users/profile?username=X
 *   Segue redirects:
 *   - Se redireciona para /users/{ID}/profile = username existe (taken)
 *   - Se retorna 200 na página genérica = não existe (available)
 *   - 404 = disponível
 *
 * Lemos o Location do redirect para decidir.
 * setInstanceFollowRedirects(false) para capturar o redirect manualmente.
 */
public class RobloxApi extends AbstractPlatformApi {

    private static final String ENDPOINT =
            "https://www.roblox.com/users/profile?username=";

    @Override public Platform getPlatform()           { return Platform.ROBLOX; }
    @Override public int      getRecommendedDelayMs() { return 700; }

    @Override
    protected String buildUrl(String username) {
        return ENDPOINT + username;
    }

    @Override
    protected HttpURLConnection openConnection(String endpoint) throws Exception {
        HttpURLConnection conn = super.openConnection(endpoint);
        conn.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0");
        // NÃO segue redirects — queremos ver o 302 manualmente
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    @Override
    protected CheckResult interpretResponse(int code, long ms) {
        return switch (code) {
            // 302 redirect para /users/{id}/profile = username existe
            case 301, 302 -> CheckResult.taken(ms);
            // 404 = username não existe
            case 404      -> CheckResult.available(ms);
            // 200 na página genérica também significa não encontrado
            case 200      -> CheckResult.available(ms);
            case 429      -> CheckResult.rateLimit();
            case 403      -> CheckResult.rateLimit();
            default       -> CheckResult.error("http " + code);
        };
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