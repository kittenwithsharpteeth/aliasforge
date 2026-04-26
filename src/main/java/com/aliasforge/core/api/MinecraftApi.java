package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

/**
 * Mojang API — funciona perfeitamente sem autenticação.
 * 200 = ocupado, 404 = disponível, 429 = rate limit.
 * Delay seguro: 600ms.
 */
public class MinecraftApi extends AbstractPlatformApi {

    private static final String ENDPOINT =
            "https://api.mojang.com/users/profiles/minecraft/";

    @Override public Platform getPlatform()         { return Platform.MINECRAFT; }
    @Override public int      getRecommendedDelayMs(){ return 600; }

    @Override
    protected String buildUrl(String username) { return ENDPOINT + username; }

    @Override
    protected CheckResult interpretResponse(int code, long ms) {
        return switch (code) {
            case 200 -> CheckResult.taken(ms);
            case 204, 404 -> CheckResult.available(ms);
            case 429 -> CheckResult.rateLimit();
            default  -> CheckResult.error("unexpected http " + code);
        };
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.MINECRAFT.minLength || len > Platform.MINECRAFT.maxLength) return false;
        return username.matches("[a-zA-Z0-9_]+");
    }
}