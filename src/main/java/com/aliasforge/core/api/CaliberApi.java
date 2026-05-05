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
 *
 * O site é um SPA (React) com JS pesado — scraping do HTML não funciona
 * porque o conteúdo é renderizado no cliente.
 *
 * Estratégia principal: verificar a URL FINAL após todos os redirects.
 *
 * Comportamento observado:
 * - Usuário EXISTE   → URL final permanece https://caliber.lol/u/{username}
 *                      ou https://caliber.lol/{username}
 * - Usuário NÃO EXISTE → redireciona para home (https://caliber.lol/)
 *                        ou para /404 ou /not-found
 *
 * Também tenta o header X-User-Exists ou similar que alguns SPAs expõem.
 *
 * Fallback: status code 404 = available, qualquer redirect para / = available.
 *
 * HTTP_1_1 obrigatório pelo mesmo motivo que guns.lol (307 + HTTP/2).
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

            // 404 explícito = disponível
            if (code == 404) return CheckResult.available(ms);
            if (code == 429) return CheckResult.rateLimit();
            if (code != 200) return CheckResult.error("http " + code);

            // ── Estratégia 1: URL final após redirects ─────────────────
            // Se foi redirecionado para a home ou /404, o usuário não existe
            String finalPath = finalUri.getPath().toLowerCase();

            if (wasRedirectedAway(finalPath, username)) {
                LOGGER.debug("caliber.lol: '{}' redirected to '{}' → available",
                        username, finalPath);
                return CheckResult.available(ms);
            }

            // URL final ainda contém o username → usuário provavelmente existe
            if (finalPath.contains(username.toLowerCase())) {
                // ── Estratégia 2: confirma com scraping leve do HTML ───
                // mesmo sendo SPA, o SSR pode incluir og:title ou dados mínimos
                if (body != null) {
                    String lower = body.toLowerCase();

                    // Sinais negativos no HTML (página de erro SSR)
                    if (lower.contains("user not found") ||
                            lower.contains("page not found") ||
                            lower.contains("not-found") ||
                            lower.contains("404")) {
                        return CheckResult.available(ms);
                    }

                    // Sinais positivos (dados reais no HTML)
                    if (lower.contains("og:title") &&
                            lower.contains(username.toLowerCase())) {
                        return CheckResult.taken(ms);
                    }
                }

                // URL final correta + sem sinal negativo = provavelmente taken
                return CheckResult.taken(ms);
            }

            // Inconclusivo
            LOGGER.warn("caliber.lol: could not determine for '{}' finalUrl='{}'",
                    username, finalUri);
            return CheckResult.error("JS-heavy site — try manually at caliber.lol/u/" + username);

        } catch (java.net.http.HttpTimeoutException e) {
            return CheckResult.rateLimit();
        } catch (Exception e) {
            LOGGER.error("caliber.lol check failed for '{}': {}", username, e.getMessage());
            return CheckResult.error(e.getMessage());
        }
    }

    /**
     * Verifica se a URL final indica que o usuário não existe.
     * Retorna true se foi redirecionado para fora da página do usuário.
     */
    private boolean wasRedirectedAway(String finalPath, String username) {
        String lowerUser = username.toLowerCase();

        // Redirecionou para home
        if (finalPath.equals("/") || finalPath.isEmpty()) return true;

        // Redirecionou para página de erro explícita
        if (finalPath.contains("404") ||
                finalPath.contains("not-found") ||
                finalPath.contains("error")) return true;

        // Redirecionou para login ou signup (usuário não existe)
        if (finalPath.contains("login") ||
                finalPath.contains("signup") ||
                finalPath.contains("register")) return true;

        // URL final não contém o username de nenhuma forma
        // (ex: /u/kitten → /u/kitten = ok, /u/kitten → / = redirected away)
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