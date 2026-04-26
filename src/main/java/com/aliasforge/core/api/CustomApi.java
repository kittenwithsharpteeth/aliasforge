package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

/**
 * API customizável pelo usuário via aba "api settings".
 * Por enquanto desabilitada até o usuário configurar endpoint e regras.
 */
public class CustomApi extends AbstractPlatformApi {

    @Override public Platform getPlatform()           { return Platform.CUSTOM; }
    @Override public int      getRecommendedDelayMs() { return 1000; }
    @Override public boolean  isAvailable()           { return false; }

    @Override
    public String getUnavailableReason() {
        return "Custom API not configured yet. Go to API Settings to set up your endpoint.";
    }

    @Override
    protected String buildUrl(String username) { return ""; }

    @Override
    protected CheckResult interpretResponse(int code, long ms) {
        return CheckResult.unavailable(getUnavailableReason());
    }

    @Override
    public boolean isValidUsername(String username) {
        return username != null && !username.isEmpty();
    }
}