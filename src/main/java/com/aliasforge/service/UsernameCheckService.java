package com.aliasforge.service;

import com.aliasforge.core.checker.CheckerService;
import com.aliasforge.core.queue.CheckerQueue;
import com.aliasforge.model.GeneratorConfig;
import com.aliasforge.model.Platform;
import com.aliasforge.model.UsernameResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Serviço de alto nível para verificação de usernames.
 *
 * Problema identificado no AppController:
 * Ele tinha duas responsabilidades não relacionadas:
 *
 * 1. Coordenar o ciclo de vida do checker (start/stop/pause/resume)
 *    e reagir aos seus eventos (onResult, onStats, onCompleted).
 *
 * 2. Atualizar o AppState com os resultados recebidos,
 *    persistir no histórico, sincronizar favoritos.
 *
 * Essa mistura tornava o AppController um "God Object" — ele precisava
 * conhecer CheckerService, HistoryRepository, FavoritesRepository,
 * AppState e SystemTrayService ao mesmo tempo.
 *
 * Separação proposta:
 * - UsernameCheckService → responsabilidade 1 (ciclo de vida + eventos brutos)
 * - AppController        → coordenação fina (delega para os serviços,
 *                          repassa resultados para o AppState)
 *
 * UsernameCheckService adiciona:
 * - Validação de username antes de enfileirar (via PlatformService)
 * - Tracking de rate limits (via RateLimitService)
 * - Estado de running/paused como fonte única de verdade
 */
public class UsernameCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsernameCheckService.class);

    private final CheckerService   checkerService;
    private final PlatformService  platformService;
    private final RateLimitService rateLimitService;

    // Callbacks para o AppController repassar ao AppState
    private final Consumer<UsernameResult>            onResult;
    private final Consumer<CheckerQueue.CheckerStats> onStats;
    private final Runnable                            onCompleted;

    public UsernameCheckService(
            Consumer<UsernameResult>            onResult,
            Consumer<CheckerQueue.CheckerStats> onStats,
            Runnable                            onCompleted) {

        this.onResult         = onResult;
        this.onStats          = onStats;
        this.onCompleted      = onCompleted;
        this.platformService  = PlatformService.getInstance();
        this.rateLimitService = RateLimitService.getInstance();

        // CheckerService recebe os callbacks augmentados com rastreamento
        this.checkerService = new CheckerService(
                this::handleResult,
                this::handleStats,
                this::handleCompleted
        );
    }

    // ── Algoritmo principal ────────────────────────────────────────────

    /**
     * Inicia o algoritmo gerador.
     *
     * Antes: AppController.start() delegava diretamente sem nenhuma
     * validação prévia (ex: plataforma disponível? charset vazio?).
     */
    public StartResult start(GeneratorConfig config) {
        // Valida disponibilidade da plataforma antes de gerar nomes
        if (!platformService.isAvailable(config.getPlatform())) {
            String reason = platformService
                    .getUnavailableReason(config.getPlatform())
                    .orElse("Platform unavailable.");
            LOGGER.warn("Start rejected: platform {} unavailable — {}",
                    config.getPlatform(), reason);
            return StartResult.rejected(reason);
        }

        // Valida que o charset não está vazio (causaria loop infinito no gerador)
        if (config.buildCharset().isEmpty()) {
            return StartResult.rejected(
                    "No character types selected. Enable at least one (letters, numbers, etc.).");
        }

        // Valida que minLength <= maxLength
        if (config.getMinLength() > config.getMaxLength()) {
            return StartResult.rejected(
                    "Minimum length cannot be greater than maximum length.");
        }

        rateLimitService.clearTracking();
        LOGGER.info("Starting checker: platform={}, qty={}, mode={}",
                config.getPlatform(), config.getQuantity(), config.getMode());

        checkerService.start(config);
        return StartResult.accepted();
    }

    public void pause()   { checkerService.pause(); }
    public void resume()  { checkerService.resume(); }
    public void stop()    {
        checkerService.stop();
        rateLimitService.clearTracking();
    }
    public void stopAll() {
        checkerService.stopAll();
        rateLimitService.clearTracking();
    }

    // ── Manual verifier ────────────────────────────────────────────────

    /**
     * Adiciona um único username para verificação manual.
     *
     * Novo: valida o username antes de enfileirar — antes a UI enviava
     * qualquer string sem validação, e o erro só aparecia como "error"
     * na tabela depois de uma request HTTP desnecessária.
     */
    public ManualCheckResult addManual(String username, Platform platform) {
        PlatformService.ValidationResult validation =
                platformService.validate(username, platform);

        if (validation.isInvalid()) {
            LOGGER.debug("Manual check rejected: {} — {}", username, validation.errorMessage());
            return ManualCheckResult.invalid(validation.errorMessage());
        }

        if (!platformService.isAvailable(platform)) {
            String reason = platformService.getUnavailableReason(platform)
                    .orElse("Platform unavailable.");
            return ManualCheckResult.invalid(reason);
        }

        checkerService.addManual(username, platform);
        LOGGER.debug("Manual check enqueued: {} on {}", username, platform);
        return ManualCheckResult.enqueued(username, platform);
    }

    /**
     * Adiciona uma lista de usernames para verificação manual.
     * Retorna quantos foram aceitos e quantos rejeitados.
     */
    public BatchManualResult addManualBatch(List<String> usernames, Platform platform) {
        int accepted = 0;
        int rejected = 0;

        for (String username : usernames) {
            ManualCheckResult r = addManual(username, platform);
            if (r.enqueued()) accepted++;
            else              rejected++;
        }

        LOGGER.info("Manual batch: {}/{} accepted on {}",
                accepted, usernames.size(), platform);
        return new BatchManualResult(accepted, rejected);
    }

    // ── Estado ─────────────────────────────────────────────────────────

    public boolean isRunning()      { return checkerService.isRunning(); }
    public boolean isPaused()       { return checkerService.isPaused(); }
    public boolean isInfiniteMode() { return checkerService.isInfiniteMode(); }

    // ── Handlers internos ──────────────────────────────────────────────

    /**
     * Intercepta resultados para rastrear rate limits antes de
     * repassar ao AppController.
     *
     * Antes: rate limits eram apenas contados como número no CheckerStats.
     * Agora o RateLimitService também recebe o evento com contexto completo.
     */
    private void handleResult(UsernameResult result) {
        if (result.getStatus() == com.aliasforge.model.CheckStatus.RATE_LIMIT) {
            rateLimitService.recordRateLimit(
                    result.getUsername(),
                    result.getPlatform(),
                    0 // attempt será refinado em iteração futura com o CheckTask
            );
        }
        onResult.accept(result);
    }

    private void handleStats(CheckerQueue.CheckerStats stats) {
        onStats.accept(stats);
    }

    private void handleCompleted() {
        LOGGER.info("Checker completed.");
        onCompleted.run();
    }

    // ── Records de retorno ─────────────────────────────────────────────

    public record StartResult(boolean accepted, String rejectionReason) {
        public static StartResult accepted()            { return new StartResult(true, null); }
        public static StartResult rejected(String r)   { return new StartResult(false, r); }
        public boolean isRejected()                    { return !accepted; }
    }

    public record ManualCheckResult(boolean enqueued, String username,
                                    Platform platform, String rejectionReason) {
        public static ManualCheckResult enqueued(String u, Platform p) {
            return new ManualCheckResult(true, u, p, null);
        }
        public static ManualCheckResult invalid(String reason) {
            return new ManualCheckResult(false, null, null, reason);
        }
        public boolean isInvalid() { return !enqueued; }
    }

    public record BatchManualResult(int accepted, int rejected) {
        public int total()          { return accepted + rejected; }
        public boolean allAccepted(){ return rejected == 0; }
    }
}