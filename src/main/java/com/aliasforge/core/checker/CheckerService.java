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
 * Suporta modo infinito: gera batches de 50 continuamente até o usuário parar.
 */
public class CheckerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckerService.class);
    private static final int    INFINITE_BATCH = 50;

    private final NameGenerator generator;

    private final Consumer<UsernameResult>            onResult;
    private final Consumer<CheckerQueue.CheckerStats> onStats;
    private final Runnable                            onCompleted;

    private CheckerQueue              currentQueue;
    private ScheduledExecutorService  infiniteScheduler;
    private final AtomicBoolean       infiniteRunning = new AtomicBoolean(false);
    private GeneratorConfig           infiniteConfig;

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
        currentQueue = new CheckerQueue(onResult, onStats, onCompleted);
        currentQueue.start(usernames, config.getPlatform());
    }

    /**
     * Modo infinito: gera um batch, verifica, quando a fila esvazia gera outro.
     * Usa um scheduler para monitorar a fila e injetar novos batches.
     */
    private void startInfinite(GeneratorConfig config) {
        infiniteConfig  = config;
        infiniteRunning.set(true);

        LOGGER.info("Starting infinite checker on {}", config.getPlatform());

        // Cria a queue e injeta primeiro batch
        currentQueue = new CheckerQueue(onResult, onStats, () -> {
            // Callback de "batch concluído" — injeta próximo se ainda infinito
            if (infiniteRunning.get()) {
                injectNextBatch();
            } else {
                if (onCompleted != null) onCompleted.run();
            }
        });

        injectFirstBatch();

        // Scheduler de segurança: verifica a cada 2s se a fila travou
        infiniteScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "aliasforge-infinite-watcher");
            t.setDaemon(true);
            return t;
        });
        infiniteScheduler.scheduleAtFixedRate(() -> {
            if (infiniteRunning.get() && currentQueue != null
                    && !currentQueue.isRunning() && !currentQueue.isPaused()) {
                LOGGER.debug("Infinite watcher: queue idle, injecting next batch");
                injectNextBatch();
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    private void injectFirstBatch() {
        List<String> batch = generateBatch();
        if (batch.isEmpty()) return;
        currentQueue.start(batch, infiniteConfig.getPlatform());
    }

    private void injectNextBatch() {
        if (!infiniteRunning.get() || currentQueue == null) return;
        List<String> batch = generateBatch();
        if (batch.isEmpty()) return;
        LOGGER.info("Infinite mode: injecting {} more usernames", batch.size());
        for (String u : batch) {
            currentQueue.addManualTask(u, infiniteConfig.getPlatform());
        }
    }

    private List<String> generateBatch() {
        // Cria config temporário com quantity = INFINITE_BATCH
        GeneratorConfig batchConfig = cloneWithQuantity(infiniteConfig, INFINITE_BATCH);
        return generator.generate(batchConfig);
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

    public void startManual(List<String> usernames, Platform platform) {
        stopAndDiscard();
        LOGGER.info("Starting manual check: {} usernames on {}", usernames.size(), platform);
        currentQueue = new CheckerQueue(onResult, onStats, onCompleted);
        currentQueue.start(usernames, platform);
    }

    public void addManual(String username, Platform platform) {
        if (currentQueue == null || !currentQueue.isRunning()) {
            // Cria uma fila leve só para o manual
            currentQueue = new CheckerQueue(onResult, onStats, null);
        }
        currentQueue.addManualTask(username, platform);
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

    public boolean isRunning() {
        return currentQueue != null && currentQueue.isRunning();
    }

    public boolean isPaused() {
        return currentQueue != null && currentQueue.isPaused();
    }

    // ── Interno ────────────────────────────────────────────────────────

    private void stopAndDiscard() {
        infiniteRunning.set(false);
        if (infiniteScheduler != null) {
            infiniteScheduler.shutdownNow();
            infiniteScheduler = null;
        }
        if (currentQueue != null) {
            currentQueue.stop();
            currentQueue = null;
        }
        infiniteConfig = null;
    }
}