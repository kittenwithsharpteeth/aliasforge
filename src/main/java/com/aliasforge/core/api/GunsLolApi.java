package com.aliasforge.core.api;

import com.aliasforge.config.AppConfig;
import com.aliasforge.model.Platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * guns.lol — verificação via GET no perfil público.
 */
public class GunsLolApi extends AbstractPlatformApi {

    private static final String ENDPOINT = "https://guns.lol/";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    @Override public Platform getPlatform()          { return Platform.GUNS_LOL; }
    @Override public int      getRecommendedDelayMs(){ return 1000; }

    @Override
    protected String buildUrl(String username) { return ENDPOINT + username; }

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
                    .uri(URI.create(ENDPOINT + username))
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

            int    code = response.statusCode();
            long   ms   = System.currentTimeMillis() - start;
            String body = response.body();

            LOGGER.debug("guns.lol username={} http={} time={}ms finalUrl={}",
                    username, code, ms, response.uri());

            if (code == 404) return CheckResult.available(ms);
            if (code == 429) return CheckResult.rateLimit();

            if (code == 307 || code == 301 || code == 302) {
                LOGGER.warn("guns.lol: unexpected redirect {} for '{}' to {}",
                        code, username,
                        response.headers().firstValue("location").orElse("unknown"));
                return CheckResult.error("unexpected redirect " + code);
            }

            if (code != 200) return CheckResult.error("http " + code);

            if (isNotFoundPage(body)) return CheckResult.available(ms);
            if (isRealProfile(body, username)) return CheckResult.taken(ms);

            // Ambíguo — conservador, não marca como available
            LOGGER.warn("guns.lol: ambiguous response for '{}' ({}chars body)",
                    username, body == null ? 0 : body.length());
            return CheckResult.inconclusive("could not determine — try manual check", ms);

        } catch (java.net.http.HttpTimeoutException e) {
            return CheckResult.rateLimit();
        } catch (Exception e) {
            LOGGER.error("Error checking '{}' on guns.lol: {}", username, e.getMessage());
            return CheckResult.error(e.getMessage());
        }
    }

    private boolean isNotFoundPage(String html) {
        if (html == null) return false;
        String lower = html.toLowerCase();
        return lower.contains("user not found") ||
                lower.contains("page not found") ||
                lower.contains("does not exist") ||
                lower.contains("no user found") ||
                lower.contains("class=\"error\"") ||
                lower.contains("id=\"error\"");
    }

    private boolean isRealProfile(String html, String username) {
        if (html == null) return false;
        String lower = html.toLowerCase();
        return (lower.contains("og:title") && lower.contains(username.toLowerCase())) ||
                (lower.contains("og:image") && lower.contains("guns.lol")) ||
                lower.contains("\"username\"") ||
                (lower.contains("avatar") && lower.contains("bio"));
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.GUNS_LOL.minLength || len > Platform.GUNS_LOL.maxLength) return false;
        return username.matches("[a-zA-Z0-9_\\-.]+");
    }
}
