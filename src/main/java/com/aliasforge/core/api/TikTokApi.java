package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

/**
 * TikTok — verificação via endpoint interno + scraping robusto.
 */
public class TikTokApi extends AbstractPlatformApi {

    private static final String API_ENDPOINT  =
            "https://www.tiktok.com/api/user/detail/?uniqueId=";
    private static final String HTML_ENDPOINT = "https://www.tiktok.com/@";

    @Override public Platform getPlatform()          { return Platform.TIKTOK; }
    @Override public int      getRecommendedDelayMs(){ return 2500; }

    @Override
    protected String buildUrl(String username) {
        return HTML_ENDPOINT + username;
    }

    @Override
    protected CheckResult interpretResponse(int code, long ms) {
        return switch (code) {
            case 404      -> CheckResult.available(ms);
            case 429      -> CheckResult.rateLimit();
            default       -> CheckResult.error("unexpected http " + code);
        };
    }

    @Override
    public CheckResult check(String username) {
        if (!isValidUsername(username)) return CheckResult.error("invalid username format");

        CheckResult apiResult = tryApiEndpoint(username);
        if (apiResult != null) return apiResult;

        return tryHtmlScraping(username);
    }

    private CheckResult tryApiEndpoint(String username) {
        long start = System.currentTimeMillis();
        try {
            URL url = new URL(API_ENDPOINT + username);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(12_000);
            conn.setInstanceFollowRedirects(true);

            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/124.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            conn.setRequestProperty("Referer", "https://www.tiktok.com/");
            conn.setRequestProperty("sec-fetch-site", "same-origin");

            int  code = conn.getResponseCode();
            long ms   = System.currentTimeMillis() - start;

            if (code == 429) { conn.disconnect(); return CheckResult.rateLimit(); }
            if (code != 200) { conn.disconnect(); return null; }

            String body = new BufferedReader(new InputStreamReader(conn.getInputStream()))
                    .lines().collect(Collectors.joining());
            conn.disconnect();

            if (body.contains("\"statusCode\":0") || body.contains("\"status_code\":0")) {
                if (body.contains("\"userInfo\"") || body.contains("\"uniqueId\"")) {
                    return CheckResult.taken(ms);
                }
            }
            if (body.contains("\"statusCode\":10202") ||
                    body.contains("\"status_code\":10202") ||
                    body.contains("\"statusCode\":10221")) {
                return CheckResult.available(ms);
            }

            return null;

        } catch (java.net.SocketTimeoutException e) {
            return CheckResult.rateLimit();
        } catch (Exception e) {
            LOGGER.warn("TikTok API endpoint failed for '{}': {}", username, e.getMessage());
            return null;
        }
    }

    private CheckResult tryHtmlScraping(String username) {
        long start = System.currentTimeMillis();
        try {
            URL url = new URL(HTML_ENDPOINT + username);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(12_000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/124.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

            int  code = conn.getResponseCode();
            long ms   = System.currentTimeMillis() - start;

            if (code == 404) { conn.disconnect(); return CheckResult.available(ms); }
            if (code == 429) { conn.disconnect(); return CheckResult.rateLimit(); }
            if (code != 200) { conn.disconnect(); return CheckResult.error("http " + code); }

            String body = new BufferedReader(new InputStreamReader(conn.getInputStream()))
                    .lines().limit(120)
                    .collect(Collectors.joining("\n"));
            conn.disconnect();

            return classifyHtml(body, username, ms);

        } catch (java.net.SocketTimeoutException e) {
            return CheckResult.rateLimit();
        } catch (Exception e) {
            LOGGER.error("TikTok HTML scraping failed for '{}': {}", username, e.getMessage());
            return CheckResult.error(e.getMessage());
        }
    }

    private CheckResult classifyHtml(String html, String username, long ms) {
        if (html == null || html.isEmpty()) return CheckResult.error("empty response");

        String lower = html.toLowerCase();
        String lowerUser = username.toLowerCase();

        // ── Indicadores negativos fortes ───────────────────────────────
        if (lower.contains("couldn't find this account") ||
                lower.contains("could not find this account") ||
                lower.contains("\"statuscode\":10202") ||
                lower.contains("\"status_code\":10202") ||
                lower.contains("page not available") ||
                lower.contains("this account doesn't exist")) {
            return CheckResult.available(ms);
        }

        // ── Indicadores positivos fortes ───────────────────────────────
        if (lower.contains("og:title") &&
                (lower.contains("@" + lowerUser) || lower.contains(lowerUser + " (@"))) {
            return CheckResult.taken(ms);
        }

        if (lower.contains("\"uniqueid\":\"" + lowerUser + "\"") ||
                lower.contains("\"nickname\"") && lower.contains("\"followercount\"")) {
            return CheckResult.taken(ms);
        }

        if (lower.contains("followers") && lower.contains("following")) {
            return CheckResult.taken(ms);
        }

        // ── Ambíguo — sem dados suficientes ───────────────────────────
        boolean hasGenericTitle =
                lower.contains("<title>tiktok</title>") ||
                        lower.contains("<title>tiktok -") ||
                        (lower.contains("og:title") && !lower.contains(lowerUser));

        if (hasGenericTitle) return CheckResult.available(ms);

        // Default conservador: se chegou até aqui sem sinal claro, marca como inconclusivo
        // para não dar falso positivo "available" nem misturar ambiguidade com erro técnico
        LOGGER.warn("TikTok: could not determine status for '{}' — marking as inconclusive", username);
        return CheckResult.inconclusive("could not determine — try manual check", ms);
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.TIKTOK.minLength || len > Platform.TIKTOK.maxLength) return false;
        return username.matches("[a-zA-Z0-9_.]+");
    }
}
