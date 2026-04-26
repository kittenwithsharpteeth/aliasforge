package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

import java.net.HttpURLConnection;

/**
 * Instagram — mais sensível que Roblox/Minecraft.
 * Delay: 1500ms para segurança sem ser lento demais.
 */
public class InstagramApi extends AbstractPlatformApi {

    private static final String ENDPOINT = "https://www.instagram.com/";

    @Override public Platform getPlatform()           { return Platform.INSTAGRAM; }
    @Override public int      getRecommendedDelayMs() { return 1500; }

    @Override
    protected String buildUrl(String username) { return ENDPOINT + username + "/"; }

    @Override
    protected HttpURLConnection openConnection(String endpoint) throws Exception {
        HttpURLConnection conn = super.openConnection(endpoint);
        conn.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0");
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    @Override
    protected CheckResult interpretResponse(int code, long ms) {
        return switch (code) {
            case 200      -> CheckResult.taken(ms);
            case 404      -> CheckResult.available(ms);
            case 301, 302 -> CheckResult.taken(ms);
            case 429      -> CheckResult.rateLimit();
            case 403      -> CheckResult.rateLimit();
            default       -> CheckResult.error("unexpected http " + code);
        };
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.INSTAGRAM.minLength || len > Platform.INSTAGRAM.maxLength) return false;
        if (username.startsWith(".") || username.endsWith(".")) return false;
        if (username.contains("..")) return false;
        return username.matches("[a-zA-Z0-9_.]+");
    }
}