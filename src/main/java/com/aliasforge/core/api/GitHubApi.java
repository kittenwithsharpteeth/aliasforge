package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

/**
 * GitHub API — sem autenticação, limite de 60 req/hora por IP.
 * 200 = taken, 404 = available, 429/403 = rate limit.
 * Delay seguro: 1500ms para evitar o rate limit sem token.
 */
public class GitHubApi extends AbstractPlatformApi {

    private static final String ENDPOINT = "https://api.github.com/users/";

    @Override public Platform getPlatform()          { return Platform.GITHUB; }
    @Override public int      getRecommendedDelayMs(){ return 1500; }

    @Override
    protected String buildUrl(String username) { return ENDPOINT + username; }

    @Override
    protected CheckResult interpretResponse(int code, long ms) {
        return switch (code) {
            case 200       -> CheckResult.taken(ms);
            case 404       -> CheckResult.available(ms);
            case 429, 403  -> CheckResult.rateLimit();
            default        -> CheckResult.error("unexpected http " + code);
        };
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.GITHUB.minLength || len > Platform.GITHUB.maxLength) return false;
        // GitHub: letras, números e hífens (não pode começar ou terminar com hífen)
        if (!username.matches("[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?")) return false;
        // Sem hífens duplos consecutivos
        return !username.contains("--");
    }
}