package com.aliasforge.core.api;

import com.aliasforge.config.AppConfig;
import com.aliasforge.model.Platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * caliber.lol — verificação de username.
 */
public class CaliberApi extends AbstractPlatformApi {

    private static final String BASE_URL      = "https://caliber.lol";
    private static final String HTML_ENDPOINT = BASE_URL + "/u/";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    @Override public Platform getPlatform()          { return Platform.CALIBER; }
    @Override public int      getRecommendedDelayMs(){ return 1200; }

    @Override
    protected String buildUrl(String username) { return HTML_ENDPOINT + username; }

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

        int timeout = Math.max(
                AppConfig.getInstance().getSettings().getRequestTimeoutMs(), 12_000);

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(HTML_ENDPOINT + username))
                    .timeout(Duration.ofMillis(timeout))
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                    "Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());

            int    code    = response.statusCode();
            long   ms      = System.currentTimeMillis() - start;
            URI    finalUri = response.uri();
            String body    = response.body();

            LOGGER.debug("caliber.lol username={} http={} time={}ms finalUrl={}",
                    username, code, ms, finalUri);

            if (code == 404) return CheckResult.available(ms);
            if (code == 429) return CheckResult.rateLimit();
            if (code != 200) return CheckResult.error("http " + code);

            // ── Estratégia 1: URL final após redirects ─────────────────
            String finalPath = finalUri.getPath().toLowerCase();

            if (wasRedirectedAway(finalPath, username)) {
                LOGGER.debug("caliber.lol: '{}' redirected to '{}' → available",
                        username, finalPath);
                return CheckResult.available(ms);
            }

            if (finalPath.contains(username.toLowerCase())) {
                if (body != null) {
                    String lower = body.toLowerCase();

                    if (lower.contains("user not found") ||
                            lower.contains("page not found") ||
                            lower.contains("not-found") ||
                            lower.contains("404")) {
                        return CheckResult.available(ms);
                    }

                    if (lower.contains("og:title") &&
                            lower.contains(username.toLowerCase())) {
                        return CheckResult.taken(ms);
                    }
                }

                return CheckResult.taken(ms);
            }

            // Inconclusivo
            LOGGER.warn("caliber.lol: could not determine for '{}' finalUrl='{}'",
                    username, finalUri);
            return CheckResult.inconclusive("JS-heavy site — try manually at caliber.lol/u/" + username, ms);

        } catch (java.net.http.HttpTimeoutException e) {
            return CheckResult.rateLimit();
        } catch (Exception e) {
            LOGGER.error("caliber.lol check failed for '{}': {}", username, e.getMessage());
            return CheckResult.error(e.getMessage());
        }
    }

    private boolean wasRedirectedAway(String finalPath, String username) {
        String lowerUser = username.toLowerCase();

        if (finalPath.equals("/") || finalPath.isEmpty()) return true;

        if (finalPath.contains("404") ||
                finalPath.contains("not-found") ||
                finalPath.contains("error")) return true;

        if (finalPath.contains("login") ||
                finalPath.contains("signup") ||
                finalPath.contains("register")) return true;

        if (!finalPath.contains(lowerUser)) return true;

        return false;
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.CALIBER.minLength || len > Platform.CALIBER.maxLength) return false;
        return username.matches("[a-zA-Z0-9_\\-.]+");
    }
}
