package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

/**
 * TikTok — verificação via endpoint interno + scraping robusto.
 *
 * Estratégia em camadas:
 * 1. GET https://www.tiktok.com/api/user/detail/?uniqueId={username}
 *    → JSON interno: statusCode 0 = exists, 10202 = not found
 * 2. Fallback: GET https://www.tiktok.com/@{username}
 *    → scraping do HTML procurando por "followers"/"following" (perfil real)
 *    ou indicadores de "não encontrado"
 *
 * TikTok bloqueia facilmente — delay alto (2500ms) e User-Agent realista.
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

        // ── Tentativa 1: endpoint JSON interno ─────────────────────────
        CheckResult apiResult = tryApiEndpoint(username);
        if (apiResult != null) return apiResult;

        // ── Tentativa 2: scraping do HTML ──────────────────────────────
        return tryHtmlScraping(username);
    }

    /**
     * Tenta o endpoint JSON interno do TikTok.
     * Retorna null se não for possível determinar com segurança.
     */
    private CheckResult tryApiEndpoint(String username) {
        long start = System.currentTimeMillis();
        try {
            URL url = new URL(API_ENDPOINT + username);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(12_000);
            conn.setInstanceFollowRedirects(true);

            // Headers que imitam o browser para não ser bloqueado
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

            // Se não retornou 200, não conseguimos usar este endpoint
            if (code != 200) { conn.disconnect(); return null; }

            String body = new BufferedReader(new InputStreamReader(conn.getInputStream()))
                    .lines().collect(Collectors.joining());
            conn.disconnect();

            // TikTok API interna: statusCode 0 = usuário existe, 10202 = não existe
            if (body.contains("\"statusCode\":0") || body.contains("\"status_code\":0")) {
                // Confirma que tem userInfo real no JSON
                if (body.contains("\"userInfo\"") || body.contains("\"uniqueId\"")) {
                    return CheckResult.taken(ms);
                }
            }
            if (body.contains("\"statusCode\":10202") ||
                    body.contains("\"status_code\":10202") ||
                    body.contains("\"statusCode\":10221")) {
                return CheckResult.available(ms);
            }

            // Não conseguiu determinar — cai no scraping HTML
            return null;

        } catch (java.net.SocketTimeoutException e) {
            return CheckResult.rateLimit();
        } catch (Exception e) {
            LOGGER.warn("TikTok API endpoint failed for '{}': {}", username, e.getMessage());
            return null; // Cai no fallback HTML
        }
    }

    /**
     * Fallback: faz scraping do HTML da página de perfil.
     * Procura por indicadores concretos de perfil real vs. página de erro.
     */
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

            // Lê as primeiras 120 linhas — suficiente para o <head> e meta tags
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

    /**
     * Classifica o HTML retornado.
     *
     * Indicadores POSITIVOS (perfil existe):
     * - "followers" ou "following" no head/meta (contagens reais)
     * - og:title contém o username ou "@"
     * - "userInfo" no JSON embutido
     * - "__UNIVERSAL_DATA_FOR_REHYDRATION__" com dados do usuário
     *
     * Indicadores NEGATIVOS (perfil não existe):
     * - "Couldn't find this account"
     * - "statusCode\":10202"
     * - og:title é só "TikTok" sem username
     * - "noindex" sem dados de usuário
     */
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
        // og:title com o username é o sinal mais confiável
        if (lower.contains("og:title") &&
                (lower.contains("@" + lowerUser) || lower.contains(lowerUser + " (@"))) {
            return CheckResult.taken(ms);
        }

        // JSON embutido com dados reais do usuário
        if (lower.contains("\"uniqueid\":\"" + lowerUser + "\"") ||
                lower.contains("\"nickname\"") && lower.contains("\"followercount\"")) {
            return CheckResult.taken(ms);
        }

        // "followers" e "following" no HTML = página de perfil real
        if (lower.contains("followers") && lower.contains("following")) {
            return CheckResult.taken(ms);
        }

        // ── Ambíguo — sem dados suficientes ───────────────────────────
        // Se o título é apenas "TikTok" sem o username, provavelmente não existe
        boolean hasGenericTitle =
                lower.contains("<title>tiktok</title>") ||
                        lower.contains("<title>tiktok -") ||
                        (lower.contains("og:title") && !lower.contains(lowerUser));

        if (hasGenericTitle) return CheckResult.available(ms);

        // Default conservador: se chegou até aqui sem sinal claro, marca como error
        // para não dar falso positivo "available"
        LOGGER.warn("TikTok: could not determine status for '{}' — marking as error", username);
        return CheckResult.error("could not determine — try manual check");
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < Platform.TIKTOK.minLength || len > Platform.TIKTOK.maxLength) return false;
        return username.matches("[a-zA-Z0-9_.]+");
    }
}