package com.aliasforge.service;

import com.aliasforge.config.AppConfig;
import com.aliasforge.model.AppSettings;
import com.aliasforge.model.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Centraliza a política de rate limit da aplicação.
 *
 * Problema identificado: a lógica de rate limit estava fragmentada:
 *
 * 1. RateLimitHandler — mecanismo de retry (delay, backoff, max retries)
 *    → Correto, mas completamente opaco para a UI e para o AppState.
 *    → A UI nunca sabia *quantos* itens estavam em retry, nem *quando*
 *      o próximo retry aconteceria.
 *
 * 2. CheckerQueue.workerLoop() — capturava o isRateLimit() do CheckResult
 *    e delegava ao RateLimitHandler. Estatísticas de rate limit eram
 *    contadas ali mesmo (totalRateLimit.incrementAndGet()) sem semântica clara.
 *
 * 3. AppSettings — guardava maxRetries e retryDelaySeconds mas qualquer
 *    leitura dos valores efetivos requeria instanciar AppConfig + getSettings().
 *
 * Este serviço expõe:
 * - A política de rate limit de forma legível (getPolicy())
 * - O estado atual dos retries pendentes para a UI (getPendingCount())
 * - Eventos para a UI reagir (onRateLimitDetected, onRetryScheduled)
 * - Cálculo centralizado do delay real (plataforma vs. configuração do usuário)
 *
 * Nota: o mecanismo de retry em si (scheduling) permanece no RateLimitHandler,
 * que é de nível de infra. Este serviço fica na camada de aplicação.
 */
public class RateLimitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitService.class);

    private static RateLimitService instance;

    // Mapa: "username@platform" → instante do último rate limit detectado
    private final Map<String, Instant> recentlyLimited = new ConcurrentHashMap<>();

    // Listeners para eventos de rate limit (usados pela UI para alertas, badges, etc.)
    private final CopyOnWriteArrayList<Consumer<RateLimitEvent>> listeners =
            new CopyOnWriteArrayList<>();

    private RateLimitService() {}

    public static synchronized RateLimitService getInstance() {
        if (instance == null) instance = new RateLimitService();
        return instance;
    }

    // ── Política ───────────────────────────────────────────────────────

    /**
     * Retorna a política de rate limit atual (lida de AppSettings).
     *
     * Antes: cada componente chamava AppConfig.getInstance().getSettings()
     * e acessava getMaxRetries() / getRetryDelaySeconds() diretamente,
     * sem uma abstração que desse nome semântico a esses valores.
     */
    public RateLimitPolicy getPolicy() {
        AppSettings s = AppConfig.getInstance().getSettings();
        return new RateLimitPolicy(s.getMaxRetries(), s.getRetryDelaySeconds());
    }

    /**
     * Calcula o delay efetivo entre requests para uma plataforma.
     *
     * Antes: esse cálculo estava inline no workerLoop() do CheckerQueue:
     *   Math.max(userDelay, platformDelay)
     * — sem nome, sem contexto, sem log.
     *
     * Agora é explícito e pode ser consultado pela UI (ex: para exibir
     * "estimated time remaining" na StatusBar).
     */
    public int effectiveDelayMs(Platform platform) {
        AppSettings s           = AppConfig.getInstance().getSettings();
        int         userDelay   = s.getDelayBetweenRequestsMs();
        int         platformMin = PlatformService.getInstance().getRecommendedDelayMs(platform);
        int         effective   = Math.max(userDelay, platformMin);

        if (effective > userDelay) {
            LOGGER.debug("Platform {} enforces minimum delay: {}ms (user set {}ms)",
                    platform, effective, userDelay);
        }

        return effective;
    }

    /**
     * Calcula o delay de retry para uma tentativa específica.
     * Implementa o backoff linear: base × tentativa.
     *
     * Antes: essa fórmula estava hardcoded em RateLimitHandler.handleRateLimit():
     *   int delaySeconds = baseDelaySeconds * nextRetry;
     * — agora está nomeada e testável.
     */
    public Duration retryDelay(int attemptNumber) {
        int baseSeconds = AppConfig.getInstance().getSettings().getRetryDelaySeconds();
        long totalSeconds = (long) baseSeconds * attemptNumber;
        return Duration.ofSeconds(totalSeconds);
    }

    // ── Rastreamento de rate limits ────────────────────────────────────

    /**
     * Registra que um rate limit foi detectado para um username/platform.
     * Notifica os listeners e mantém o histórico recente.
     *
     * Chamado pelo CheckerQueue quando recebe um CheckResult.isRateLimit().
     */
    public void recordRateLimit(String username, Platform platform, int currentAttempt) {
        String key = key(username, platform);
        recentlyLimited.put(key, Instant.now());

        RateLimitEvent event = new RateLimitEvent(
                username, platform, currentAttempt,
                getPolicy().maxRetries(),
                retryDelay(currentAttempt + 1)
        );

        LOGGER.info("Rate limit recorded: {} on {} (attempt {}/{})",
                username, platform, currentAttempt, getPolicy().maxRetries());

        listeners.forEach(l -> l.accept(event));
    }

    /**
     * Verifica se um username foi rate-limited recentemente.
     * "Recentemente" = dentro da janela de backoff esperada.
     *
     * Útil para a UI decidir se deve avisar o usuário antes de enfileirar
     * o mesmo username novamente.
     */
    public boolean wasRecentlyLimited(String username, Platform platform) {
        Instant ts = recentlyLimited.get(key(username, platform));
        if (ts == null) return false;

        Duration maxWindow = retryDelay(getPolicy().maxRetries());
        return Duration.between(ts, Instant.now()).compareTo(maxWindow) < 0;
    }

    /**
     * Retorna um snapshot somente-leitura dos itens recentemente limitados.
     * Usado pela StatusBar para exibir "X itens aguardando retry".
     */
    public Map<String, Instant> getRecentlyLimited() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(recentlyLimited));
    }

    /**
     * Limpa o rastreamento de rate limits (chamado ao parar o checker).
     */
    public void clearTracking() {
        recentlyLimited.clear();
        LOGGER.debug("Rate limit tracking cleared.");
    }

    // ── Listeners ──────────────────────────────────────────────────────

    /**
     * Registra um listener para eventos de rate limit.
     * A UI pode usar isso para exibir avisos, toasts, badges na aba, etc.
     *
     * Antes: não havia como a UI ser notificada de rate limits específicos —
     * ela só via o número total no CheckerStats.
     */
    public void addListener(Consumer<RateLimitEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<RateLimitEvent> listener) {
        listeners.remove(listener);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private String key(String username, Platform platform) {
        return username.toLowerCase() + "@" + platform.name();
    }

    // ── Records ────────────────────────────────────────────────────────

    /**
     * Política de rate limit — snapshot imutável dos settings atuais.
     * Facilita passar a política para componentes sem depender de AppConfig.
     */
    public record RateLimitPolicy(int maxRetries, int retryDelaySeconds) {

        /** Delay total máximo possível antes de desistir. */
        public Duration maxBackoffDuration() {
            // soma: base×1 + base×2 + ... + base×maxRetries = base × (n×(n+1)/2)
            long totalSeconds = (long) retryDelaySeconds * maxRetries * (maxRetries + 1) / 2;
            return Duration.ofSeconds(totalSeconds);
        }

        /** Descrição legível para exibir na UI (ex: tooltip do Settings). */
        public String describe() {
            if (maxRetries == 0) return "No retries — rate limited items are marked as error.";
            return "Up to " + maxRetries + " retries with " + retryDelaySeconds +
                    "s base delay (max backoff: " +
                    maxBackoffDuration().toSeconds() + "s).";
        }
    }

    /**
     * Evento emitido quando um rate limit é detectado.
     * Carrega contexto suficiente para a UI decidir como reagir.
     */
    public record RateLimitEvent(
            String   username,
            Platform platform,
            int      attempt,
            int      maxAttempts,
            Duration nextRetryIn
    ) {
        public boolean isLastAttempt() { return attempt >= maxAttempts; }

        public String describe() {
            if (isLastAttempt()) {
                return username + " on " + platform.displayName +
                        " — rate limited, no more retries.";
            }
            return username + " on " + platform.displayName +
                    " — rate limited, retry in " + nextRetryIn.toSeconds() + "s " +
                    "(" + attempt + "/" + maxAttempts + ").";
        }
    }
}