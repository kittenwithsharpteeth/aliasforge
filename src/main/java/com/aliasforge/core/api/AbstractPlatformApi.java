package com.aliasforge.core.api;

import com.aliasforge.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Lógica HTTP compartilhada por todas as plataformas.
 *
 * Timeout mínimo de 12s para evitar errors falsos.
 * Follow redirects habilitado (instanceFollowRedirects=true).
 */
public abstract class AbstractPlatformApi implements PlatformApi {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final int MIN_TIMEOUT_MS = 12_000;

    @Override
    public CheckResult check(String username) {
        if (!isValidUsername(username)) {
            return CheckResult.error("invalid username format");
        }
        if (!isAvailable()) {
            return CheckResult.unavailable(getUnavailableReason());
        }

        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = openConnection(buildUrl(username));
            int  code = conn.getResponseCode();
            long ms   = System.currentTimeMillis() - start;
            conn.disconnect();

            LOGGER.debug("platform={} username={} http={} time={}ms",
                    getPlatform(), username, code, ms);

            return interpretResponse(code, ms);

        } catch (java.net.SocketTimeoutException e) {
            long ms = System.currentTimeMillis() - start;
            LOGGER.warn("Timeout ({}ms) checking '{}' on {}", ms, username, getPlatform());
            return CheckResult.rateLimit();
        } catch (java.net.UnknownHostException e) {
            LOGGER.error("No internet connection checking '{}': {}", username, e.getMessage());
            return CheckResult.error("no internet connection");
        } catch (Exception e) {
            LOGGER.error("Error checking '{}' on {}: {}", username, getPlatform(), e.getMessage());
            return CheckResult.error(e.getMessage());
        }
    }

    protected abstract String      buildUrl(String username);
    protected abstract CheckResult interpretResponse(int httpCode, long ms);

    protected HttpURLConnection openConnection(String endpoint) throws Exception {
        int configTimeout = AppConfig.getInstance().getSettings().getRequestTimeoutMs();
        int timeout = Math.max(configTimeout, MIN_TIMEOUT_MS);

        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setRequestProperty("User-Agent", "AliasForge/1.0");
        // Fix: segue redirects (307, 301, 302, 308) automaticamente
        conn.setInstanceFollowRedirects(true);
        return conn;
    }
}