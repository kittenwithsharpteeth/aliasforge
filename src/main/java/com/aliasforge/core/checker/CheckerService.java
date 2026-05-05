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
 * Fix: manualQueue é completamente separada da mainQueue.
 * O manual verifier funciona independentemente do estado do algoritmo principal
 * (rodando, pausado ou parado).
 */
public class CheckerService {

    private static final Logger LOGGER         = LoggerFactory.getLogger(CheckerService.class);
    private static final int    INFINITE_BATCH = 50;

    private final NameGenerator generator;

    private final Consumer<UsernameResult>            onResult;
    private final Consumer<CheckerQueue.CheckerStats> onStats;
    private final Runnable                            onCompleted;

    // ── Queue principal (algoritmo) ────────────────────────────────────
    private CheckerQueue             mainQueue;
    private ScheduledExecutorService infiniteScheduler;

    // ── Queue exclusiva do manual verifier ─────────────────────────────
    // Completamente independente — não é afetada por pause/stop do algoritmo
    private CheckerQueue manualQueue;

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

        // A manualQueue é criada uma vez e nunca destruída
        // — persiste durante toda a vida do serviço
        this.manualQueue = new CheckerQueue(onResult, onStats, null);
    }

    // ── Controle do algoritmo principal ───────────────────────────────

    public void start(GeneratorConfig config) {
        stopMainQueue(); // Para apenas o algoritmo, NÃO a manualQueue

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
        mainQueue = new CheckerQueue(onResult, onStats, () -> {
            if (onCompleted != null) onCompleted.run();
        });
        mainQueue.start(usernames, config.getPlatform());
    }

    private void startInfinite(GeneratorConfig config) {
        infiniteConfig = config;
        infiniteRunning.set(true);
        infinitePaused.set(false);

        LOGGER.info("Starting infinite checker on {}", config.getPlatform());

        mainQueue = new CheckerQueue(onResult, onStats, () -> {
            if (infiniteRunning.get() && !infinitePaused.get()) {
                LOGGER.debug("Infinite: batch complete, injecting next batch");
                injectNextBatch();
            }
        });

        List<String> firstBatch = generateBatch();
        if (!firstBatch.isEmpty()) {
            mainQueue.start(firstBatch, infiniteConfig.getPlatform());
        }

        infiniteScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "aliasforge-infinite-watcher");
            t.setDaemon(true);
            return t;
        });

        infiniteScheduler.scheduleAtFixedRate(() -> {
            if (!infiniteRunning.get() || infinitePaused.get()) return;
            if (mainQueue == null) return;
            if (mainQueue.isRunning() || mainQueue.isPaused()) return;
            LOGGER.debug("Infinite watcher: queue idle, injecting next batch");
            injectNextBatch();
        }, 3, 3, TimeUnit.SECONDS);
    }

    private void injectNextBatch() {
        if (!infiniteRunning.get() || infinitePaused.get() || mainQueue == null) return;
        List<String> batch = generateBatch();
        if (batch.isEmpty()) return;
        LOGGER.info("Infinite mode: injecting {} usernames", batch.size());
        for (String u : batch) {
            mainQueue.addManualTask(u, infiniteConfig.getPlatform());
        }
    }

    private List<String> generateBatch() {
        GeneratorConfig c = cloneWithQuantity(infiniteConfig, INFINITE_BATCH);
        return generator.generate(c);
    }

    // ── Pause / Resume / Stop do algoritmo ────────────────────────────
    // Nenhum desses métodos toca a manualQueue

    public void pause() {
        if (infiniteRunning.get()) {
            infinitePaused.set(true);
            LOGGER.info("Infinite mode paused — no new batches will be injected.");
        }
        if (mainQueue != null) mainQueue.pause();
    }

    public void resume() {
        if (infiniteRunning.get()) {
            infinitePaused.set(false);
            LOGGER.info("Infinite mode resumed.");
            if (mainQueue != null && !mainQueue.isRunning()) {
                injectNextBatch();
            }
        }
        if (mainQueue != null) mainQueue.resume();
    }

    public void stop() {
        stopMainQueue();
    }

    // ── Manual verifier — queue independente ──────────────────────────

    /**
     * Adiciona uma task ao manual verifier.
     * Funciona independentemente do estado do algoritmo principal.
     * A manualQueue nunca é pausada ou parada pelo algoritmo.
     */
    public void addManual(String username, Platform platform) {
        manualQueue.addManualTask(username, platform);
        LOGGER.debug("Manual task added: {} on {}", username, platform);
    }

    /**
     * Inicia uma verificação manual em lote.
     * Também usa a manualQueue — não interfere com o algoritmo.
     */
    public void startManual(List<String> usernames, Platform platform) {
        LOGGER.info("Starting manual batch: {} usernames on {}", usernames.size(), platform);
        for (String u : usernames) {
            manualQueue.addManualTask(u, platform);
        }
    }

    // ── Estado ─────────────────────────────────────────────────────────

    public boolean isRunning() {
        return mainQueue != null && mainQueue.isRunning();
    }

    public boolean isPaused() {
        return mainQueue != null && mainQueue.isPaused();
    }

    public boolean isInfiniteMode() {
        return infiniteRunning.get();
    }

    // ── Interno ────────────────────────────────────────────────────────

    /**
     * Para apenas o algoritmo principal.
     * A manualQueue continua rodando normalmente.
     */
    private void stopMainQueue() {
        infiniteRunning.set(false);
        infinitePaused.set(false);
        infiniteConfig = null;

        if (infiniteScheduler != null) {
            infiniteScheduler.shutdownNow();
            infiniteScheduler = null;
        }
        if (mainQueue != null) {
            mainQueue.stop();
            mainQueue = null;
        }
    }

    /**
     * Para tudo — incluindo a manualQueue.
     * Chamado apenas ao encerrar o app.
     */
    public void stopAll() {
        stopMainQueue();
        if (manualQueue != null) {
            manualQueue.stop();
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