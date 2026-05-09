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
 */
public class UsernameCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsernameCheckService.class);

    private final CheckerService   checkerService;
    private final PlatformService  platformService;
    private final RateLimitService rateLimitService;

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

        this.checkerService = new CheckerService(
                this::handleResult,
                this::handleStats,
                this::handleCompleted
        );
    }

    // ── Algoritmo principal ────────────────────────────────────────────

    public StartResult start(GeneratorConfig config) {
        if (!platformService.isAvailable(config.getPlatform())) {
            String reason = platformService
                    .getUnavailableReason(config.getPlatform())
                    .orElse("Platform unavailable.");
            LOGGER.warn("Start rejected: platform {} unavailable — {}",
                    config.getPlatform(), reason);
            return StartResult.ofRejected(reason);
        }

        if (config.buildCharset().isEmpty()) {
            return StartResult.ofRejected(
                    "No character types selected. Enable at least one (letters, numbers, etc.).");
        }

        if (config.getMinLength() > config.getMaxLength()) {
            return StartResult.ofRejected(
                    "Minimum length cannot be greater than maximum length.");
        }

        rateLimitService.clearTracking();
        LOGGER.info("Starting checker: platform={}, qty={}, mode={}",
                config.getPlatform(), config.getQuantity(), config.getMode());

        checkerService.start(config);
        return StartResult.ofAccepted();
    }

    public void pause()   { checkerService.pause(); }
    public void resume()  { checkerService.resume(); }
    public void stop()    { checkerService.stop();    rateLimitService.clearTracking(); }
    public void stopAll() { checkerService.stopAll(); rateLimitService.clearTracking(); }

    // ── Manual verifier ────────────────────────────────────────────────

    public ManualCheckResult addManual(String username, Platform platform) {
        PlatformService.ValidationResult validation =
                platformService.validate(username, platform);

        if (validation.isInvalid()) {
            LOGGER.debug("Manual check rejected: {} — {}", username, validation.errorMessage());
            return ManualCheckResult.ofInvalid(validation.errorMessage());
        }

        if (!platformService.isAvailable(platform)) {
            String reason = platformService.getUnavailableReason(platform)
                    .orElse("Platform unavailable.");
            return ManualCheckResult.ofInvalid(reason);
        }

        checkerService.addManual(username, platform);
        LOGGER.debug("Manual check enqueued: {} on {}", username, platform);
        return ManualCheckResult.ofEnqueued(username, platform);
    }

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

    private void handleResult(UsernameResult result) {
        if (result.getStatus() == com.aliasforge.model.CheckStatus.RATE_LIMIT) {
            rateLimitService.recordRateLimit(
                    result.getUsername(),
                    result.getPlatform(),
                    0
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

    /**
     * FIX: factory methods renomeados de accepted()/rejected() para
     * ofAccepted()/ofRejected() — evita colisão com o accessor do campo
     * 'accepted' gerado automaticamente pelo record.
     *
     * Sem o rename, 'accepted()' dentro do record é ambíguo entre o
     * accessor do campo boolean e o factory method estático, causando:
     *   - "invalid accessor method in record"
     *   - "bad operand type StartResult for unary operator '!'"
     *   - "incompatible types: StartResult cannot be converted to boolean"
     *
     * isRejected() usa '!accepted' (campo direto) para evitar ambiguidade.
     */
    public static record StartResult(boolean accepted, String rejectionReason) {

        public static StartResult ofAccepted() {
            return new StartResult(true, null);
        }

        public static StartResult ofRejected(String reason) {
            return new StartResult(false, reason);
        }

        public boolean isRejected() { return !accepted; }
    }

    /**
     * FIX: mesmo problema — factory methods renomeados de enqueued()/invalid()
     * para ofEnqueued()/ofInvalid() para evitar colisão com o accessor do campo
     * 'enqueued' e com o nome do record ValidationResult.
     *
     * isInvalid() usa '!enqueued' (campo direto).
     */
    public static record ManualCheckResult(
            boolean  enqueued,
            String   username,
            Platform platform,
            String   rejectionReason) {

        public static ManualCheckResult ofEnqueued(String u, Platform p) {
            return new ManualCheckResult(true, u, p, null);
        }

        public static ManualCheckResult ofInvalid(String reason) {
            return new ManualCheckResult(false, null, null, reason);
        }

        public boolean isInvalid() { return !enqueued; }
    }

    public static record BatchManualResult(int accepted, int rejected) {
        public int     total()       { return accepted + rejected; }
        public boolean allAccepted() { return rejected == 0; }
    }
}