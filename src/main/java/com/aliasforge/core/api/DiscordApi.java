package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

/**
 * Discord não tem API pública para checar username sem autenticação.
 * Para implementar, seria necessário um Discord Bot com token válido.
 *
 * Status: DESABILITADO até integração com bot.
 */
public class DiscordApi extends AbstractPlatformApi {

    private static final String REASON =
            "Discord requires a Bot Token to check usernames. " +
                    "Please configure a bot in API Settings.";

    @Override public Platform getPlatform()          { return Platform.DISCORD; }
    @Override public int      getRecommendedDelayMs() { return 1000; }
    @Override public boolean  isAvailable()           { return false; }
    @Override public String   getUnavailableReason()  { return REASON; }

    @Override
    protected String buildUrl(String username) { return ""; }

    @Override
    protected CheckResult interpretResponse(int code, long ms) {
        return CheckResult.unavailable(REASON);
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.DISCORD.minLength || len > Platform.DISCORD.maxLength) return false;
        return username.matches("[a-zA-Z0-9_.]+");
    }
}