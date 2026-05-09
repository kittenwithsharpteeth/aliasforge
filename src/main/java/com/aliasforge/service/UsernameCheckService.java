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
            return StartResult.rejected(reason);
        }

        if (config.buildCharset().isEmpty()) {
            return StartResult.rejected(
                    "No character types selected. Enable at least one (letters, numbers, etc.).");
        }

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
    public void stop()    { checkerService.stop();    rateLimitService.clearTracking(); }
    public void stopAll() { checkerService.stopAll(); rateLimitService.clearTracking(); }

    // ── Manual verifier ────────────────────────────────────────────────

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
     * Fix: campo "accepted" — accessor gerado é accepted(), não isAccepted().
     * isRejected() é método extra que referencia accepted() corretamente.
     */
    public record StartResult(boolean accepted, String rejectionReason) {
        public static StartResult accepted()          { return new StartResult(true, null); }
        public static StartResult rejected(String r)  { return new StartResult(false, r); }

        // Método de conveniência — referencia o accessor accepted() do record
        public boolean isRejected() { return !accepted(); }
    }

    /**
     * Fix: campo "enqueued" — accessor gerado é enqueued(), não isEnqueued().
     * isInvalid() é método extra que referencia enqueued() corretamente.
     */
    public record ManualCheckResult(
            boolean  enqueued,
            String   username,
            Platform platform,
            String   rejectionReason) {

        public static ManualCheckResult enqueued(String u, Platform p) {
            return new ManualCheckResult(true, u, p, null);
        }
        public static ManualCheckResult invalid(String reason) {
            return new ManualCheckResult(false, null, null, reason);
        }

        // Método de conveniência — referencia o accessor enqueued() do record
        public boolean isInvalid() { return !enqueued(); }
    }

    public record BatchManualResult(int accepted, int rejected) {
        public int     total()       { return accepted + rejected; }
        public boolean allAccepted() { return rejected == 0; }
    }
}