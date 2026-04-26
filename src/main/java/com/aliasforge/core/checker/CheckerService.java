package com.aliasforge.core.checker;

import com.aliasforge.core.generator.NameGenerator;
import com.aliasforge.core.queue.CheckerQueue;
import com.aliasforge.model.GeneratorConfig;
import com.aliasforge.model.Platform;
import com.aliasforge.model.UsernameResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Serviço principal que orquestra NameGenerator + CheckerQueue.
 *
 * Fix crítico: cria uma nova CheckerQueue a cada start() para evitar
 * acúmulo de workers e listeners entre sessões.
 */
public class CheckerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckerService.class);

    private final NameGenerator generator;

    // Callbacks guardados para repassar ao criar nova queue
    private final Consumer<UsernameResult>        onResult;
    private final Consumer<CheckerQueue.CheckerStats> onStats;
    private final Runnable                        onCompleted;

    // Queue atual — recriada a cada start()
    private CheckerQueue currentQueue;

    public CheckerService(
            Consumer<UsernameResult>            onResult,
            Consumer<CheckerQueue.CheckerStats> onStats,
            Runnable                            onCompleted) {
        this.generator   = new NameGenerator();
        this.onResult    = onResult;
        this.onStats     = onStats;
        this.onCompleted = onCompleted;
    }

    // ── Controle ───────────────────────────────────────────────────────

    public void start(GeneratorConfig config) {
        // Para qualquer execução anterior completamente
        stopAndDiscard();

        List<String> usernames = generator.generate(config);
        if (usernames.isEmpty()) {
            LOGGER.warn("No usernames generated — check config filters.");
            return;
        }

        LOGGER.info("Starting checker: {} usernames on {}",
                usernames.size(), config.getPlatform());

        // Cria queue nova — sem estado residual da sessão anterior
        currentQueue = new CheckerQueue(onResult, onStats, onCompleted);
        currentQueue.start(usernames, config.getPlatform());
    }

    public void startManual(List<String> usernames, Platform platform) {
        stopAndDiscard();
        LOGGER.info("Starting manual check: {} usernames on {}", usernames.size(), platform);
        currentQueue = new CheckerQueue(onResult, onStats, onCompleted);
        currentQueue.start(usernames, platform);
    }

    public void addManual(String username, Platform platform) {
        if (currentQueue != null && !currentQueue.isRunning()) {
            // Se não está rodando, inicia uma nova sessão só para este username
            currentQueue = new CheckerQueue(onResult, onStats, onCompleted);
        }
        if (currentQueue != null) {
            currentQueue.addManualTask(username, platform);
        }
    }

    public void pause() {
        if (currentQueue != null) currentQueue.pause();
    }

    public void resume() {
        if (currentQueue != null) currentQueue.resume();
    }

    public void stop() {
        stopAndDiscard();
    }

    // ── Estado ─────────────────────────────────────────────────────────

    public boolean isRunning() {
        return currentQueue != null && currentQueue.isRunning();
    }

    public boolean isPaused() {
        return currentQueue != null && currentQueue.isPaused();
    }

    // ── Interno ────────────────────────────────────────────────────────

    /** Para a queue atual e descarta a referência. */
    private void stopAndDiscard() {
        if (currentQueue != null) {
            currentQueue.stop();
            currentQueue = null;
        }
    }
}