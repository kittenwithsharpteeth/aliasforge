package com.aliasforge.core.checker;

import com.aliasforge.core.generator.NameGenerator;
import com.aliasforge.core.queue.CheckerQueue;
import com.aliasforge.model.GeneratorConfig;
import com.aliasforge.model.Platform;
import com.aliasforge.model.UsernameResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Serviço principal que orquestra NameGenerator + CheckerQueue.
 *
 * Correções no modo infinito:
 * - infinitePaused: impede injeção de novos batches enquanto pausado
 * - pause/resume/stop respeitam o estado infinito corretamente
 * - watcher verifica infinitePaused antes de injetar
 */
public class CheckerService {

    private static final Logger LOGGER         = LoggerFactory.getLogger(CheckerService.class);
    private static final int    INFINITE_BATCH = 50;

    private final NameGenerator generator;

    private final Consumer<UsernameResult>            onResult;
    private final Consumer<CheckerQueue.CheckerStats> onStats;
    private final Runnable                            onCompleted;

    private CheckerQueue             currentQueue;
    private ScheduledExecutorService infiniteScheduler;

    // Estado do modo infinito
    private final AtomicBoolean infiniteRunning = new AtomicBoolean(false);
    private final AtomicBoolean infinitePaused  = new AtomicBoolean(false);
    private GeneratorConfig     infiniteConfig;

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
        stopAndDiscard();

        if (config.isInfinite()) {
            startInfinite(config);
        } else {
            startFinite(config);
        }
    }

    private void startFinite(GeneratorConfig config) {
        List<String> usernames = generator.generate(config);
        if (usernames.isEmpty()) {
            LOGGER.warn("No usernames generated — check config filters.");
            return;
        }
        LOGGER.info("Starting finite checker: {} usernames on {}",
                usernames.size(), config.getPlatform());
        currentQueue = new CheckerQueue(onResult, onStats, () -> {
            if (onCompleted != null) onCompleted.run();
        });
        currentQueue.start(usernames, config.getPlatform());
    }

    private void startInfinite(GeneratorConfig config) {
        infiniteConfig = config;
        infiniteRunning.set(true);
        infinitePaused.set(false);

        LOGGER.info("Starting infinite checker on {}", config.getPlatform());

        // Queue com callback de ciclo concluído
        currentQueue = new CheckerQueue(onResult, onStats, () -> {
            // Só injeta próximo batch se não pausado e ainda rodando
            if (infiniteRunning.get() && !infinitePaused.get()) {
                LOGGER.debug("Infinite: batch complete, injecting next batch");
                injectNextBatch();
            }
        });

        // Injeta primeiro batch e inicia a queue
        List<String> firstBatch = generateBatch();
        if (!firstBatch.isEmpty()) {
            currentQueue.start(firstBatch, infiniteConfig.getPlatform());
        }

        // Watcher de segurança: detecta se a queue travou sem disparar o callback
        infiniteScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "aliasforge-infinite-watcher");
            t.setDaemon(true);
            return t;
        });

        infiniteScheduler.scheduleAtFixedRate(() -> {
            // Não injeta se pausado, parado ou já rodando normalmente
            if (!infiniteRunning.get() || infinitePaused.get()) return;
            if (currentQueue == null) return;
            if (currentQueue.isRunning() || currentQueue.isPaused()) return;

            // Queue ociosa sem ter sido sinalizada — injeta próximo batch
            LOGGER.debug("Infinite watcher: queue idle, injecting next batch");
            injectNextBatch();
        }, 3, 3, TimeUnit.SECONDS);
    }

    private void injectNextBatch() {
        if (!infiniteRunning.get() || infinitePaused.get() || currentQueue == null) return;
        List<String> batch = generateBatch();
        if (batch.isEmpty()) return;
        LOGGER.info("Infinite mode: injecting {} usernames", batch.size());
        for (String u : batch) {
            currentQueue.addManualTask(u, infiniteConfig.getPlatform());
        }
    }

    private List<String> generateBatch() {
        GeneratorConfig c = cloneWithQuantity(infiniteConfig, INFINITE_BATCH);
        return generator.generate(c);
    }

    // ── Pause / Resume / Stop ──────────────────────────────────────────

    public void pause() {
        if (infiniteRunning.get()) {
            // Modo infinito: marca infinitePaused para bloquear injeção de novos batches
            infinitePaused.set(true);
            LOGGER.info("Infinite mode paused — no new batches will be injected.");
        }
        if (currentQueue != null) currentQueue.pause();
    }

    public void resume() {
        if (infiniteRunning.get()) {
            infinitePaused.set(false);
            LOGGER.info("Infinite mode resumed.");
            // Se a queue esvaziou enquanto estava pausado, injeta novo batch
            if (currentQueue != null && !currentQueue.isRunning()) {
                injectNextBatch();
            }
        }
        if (currentQueue != null) currentQueue.resume();
    }

    public void stop() {
        stopAndDiscard();
    }

    // ── Manual ─────────────────────────────────────────────────────────

    public void startManual(List<String> usernames, Platform platform) {
        stopAndDiscard();
        LOGGER.info("Starting manual check: {} usernames on {}", usernames.size(), platform);
        currentQueue = new CheckerQueue(onResult, onStats, onCompleted);
        currentQueue.start(usernames, platform);
    }

    public void addManual(String username, Platform platform) {
        if (currentQueue == null || (!currentQueue.isRunning() && !currentQueue.isPaused())) {
            currentQueue = new CheckerQueue(onResult, onStats, null);
        }
        currentQueue.addManualTask(username, platform);
    }

    // ── Estado ─────────────────────────────────────────────────────────

    public boolean isRunning() {
        return currentQueue != null && currentQueue.isRunning();
    }

    public boolean isPaused() {
        return currentQueue != null && currentQueue.isPaused();
    }

    public boolean isInfiniteMode() {
        return infiniteRunning.get();
    }

    // ── Interno ────────────────────────────────────────────────────────

    private void stopAndDiscard() {
        // Para o modo infinito completamente
        infiniteRunning.set(false);
        infinitePaused.set(false);
        infiniteConfig = null;

        if (infiniteScheduler != null) {
            infiniteScheduler.shutdownNow();
            infiniteScheduler = null;
        }
        if (currentQueue != null) {
            currentQueue.stop();
            currentQueue = null;
        }
    }

    private GeneratorConfig cloneWithQuantity(GeneratorConfig src, int quantity) {
        GeneratorConfig c = new GeneratorConfig();
        c.setQuantity(quantity);
        c.setMinLength(src.getMinLength());
        c.setMaxLength(src.getMaxLength());
        c.setMode(src.getMode());
        c.setStartsWith(src.getStartsWith());
        c.setEndsWith(src.getEndsWith());
        c.setContains(src.getContains());
        c.setUseLetters(src.isUseLetters());
        c.setUseNumbers(src.isUseNumbers());
        c.setUseUnderscore(src.isUseUnderscore());
        c.setUsePeriod(src.isUsePeriod());
        c.setCustomChars(src.getCustomChars());
        c.setPlatform(src.getPlatform());
        return c;
    }
}