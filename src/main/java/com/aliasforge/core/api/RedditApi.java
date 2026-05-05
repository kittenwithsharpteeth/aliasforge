package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

/**
 * Reddit API — endpoint público /about.json, sem auth.
 * 200 = taken, 404 = available, 429 = rate limit.
 * Delay seguro: 1000ms.
 */
public class RedditApi extends AbstractPlatformApi {

    private static final String ENDPOINT = "https://www.reddit.com/user/";

    @Override public Platform getPlatform()          { return Platform.REDDIT; }
    @Override public int      getRecommendedDelayMs(){ return 1000; }

    @Override
    protected String buildUrl(String username) { return ENDPOINT + username + "/about.json"; }

    @Override
    protected CheckResult interpretResponse(int code, long ms) {
        return switch (code) {
            case 200      -> CheckResult.taken(ms);
            case 404      -> CheckResult.available(ms);
            case 429      -> CheckResult.rateLimit();
            // Reddit redireciona usuários deletados/suspensos para /user/page
            case 302, 301 -> CheckResult.taken(ms);
            default       -> CheckResult.error("unexpected http " + code);
        };
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.REDDIT.minLength || len > Platform.REDDIT.maxLength) return false;
        // Reddit: letras, números, underscores e hífens
        return username.matches("[a-zA-Z0-9_\\-]+");
    }
}