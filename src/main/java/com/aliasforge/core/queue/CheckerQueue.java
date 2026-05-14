package com.aliasforge.core.queue;

import com.aliasforge.config.AppConfig;
import com.aliasforge.core.api.ApiFactory;
import com.aliasforge.core.api.PlatformApi;
import com.aliasforge.model.CheckStatus;
import com.aliasforge.model.Platform;
import com.aliasforge.model.UsernameResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Fila principal de verificação.
 *
 * Correções aplicadas:
 * 1. completionSignaled — callback de "done" dispara apenas uma vez por ciclo,
 *    evita disparo concorrente quando múltiplas threads esvaziam a fila ao mesmo tempo.
 * 2. running=true explícito em addManualTask — garante consistência de estado
 *    quando novas tarefas chegam depois da fila esvaziar (manual/retry/infinito).
 * 3. Disponibilidade da plataforma verificada uma vez por instância de worker,
 *    não a cada username — elimina overhead repetitivo no modo manual.
 * 4. Fila de rate limit respeita paused — não injeta retry se worker está pausado.
 */
public class CheckerQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckerQueue.class);

    private final AtomicBoolean running            = new AtomicBoolean(false);
    private final AtomicBoolean paused             = new AtomicBoolean(false);
    private final AtomicBoolean stopped            = new AtomicBoolean(false);
    // Fix 1: garante que o callback onCompleted dispara apenas uma vez por ciclo
    private final AtomicBoolean completionSignaled = new AtomicBoolean(false);

    private final AtomicInteger totalChecked       = new AtomicInteger(0);
    private final AtomicInteger totalAvailable     = new AtomicInteger(0);
    private final AtomicInteger totalTaken         = new AtomicInteger(0);
    private final AtomicInteger totalRateLimit     = new AtomicInteger(0);
    private final AtomicInteger totalInconclusive  = new AtomicInteger(0);
    private final AtomicInteger totalError         = new AtomicInteger(0);
    private final AtomicInteger currentChecking    = new AtomicInteger(0);
    private final AtomicInteger activeWorkers      = new AtomicInteger(0);

    private final BlockingQueue<CheckTask> taskQueue = new LinkedBlockingQueue<>();
    private ExecutorService  executor;
    private RateLimitHandler rateLimitHandler;

    private final Consumer<UsernameResult> onResult;
    private final Consumer<CheckerStats>   onStatsUpdate;
    private final Runnable                 onCompleted;

    public CheckerQueue(Consumer<UsernameResult> onResult,
                        Consumer<CheckerStats> onStatsUpdate,
                        Runnable onCompleted) {
        this.onResult      = onResult;
        this.onStatsUpdate = onStatsUpdate;
        this.onCompleted   = onCompleted;
    }

    // ── Controle ───────────────────────────────────────────────────────

    public void start(List<String> usernames, Platform platform) {
        if (running.get()) return;
        reset();
        running.set(true);
        stopped.set(false);
        paused.set(false);
        completionSignaled.set(false);

        for (String u : usernames) {
            taskQueue.add(new CheckTask(u, platform, CheckTask.Origin.GENERATOR));
        }

        rateLimitHandler = new RateLimitHandler(
                retryTask -> {
                    if (!stopped.get()) {
                        running.set(true);
                        completionSignaled.set(false);
                        taskQueue.add(retryTask);
                    }
                },
                exhausted -> {
                    totalRateLimit.decrementAndGet();
                    totalError.incrementAndGet();
                    totalChecked.incrementAndGet();
                    publishResult(new UsernameResult(
                            exhausted.getUsername(), exhausted.getPlatform(),
                            CheckStatus.ERROR, 0,
                            "rate limit exhausted after " + exhausted.getRetryCount() + " retries"));
                    publishStats();
                }
        );

        int threads = AppConfig.getInstance().getSettings().getParallelThreads();
        executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "aliasforge-checker");
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < threads; i++) executor.submit(this::workerLoop);

        LOGGER.info("Checker started — {} usernames, {} threads, platform={}",
                usernames.size(), threads, platform);
    }

    public void addManualTask(String username, Platform platform) {
        if (stopped.get()) return;

        running.set(true);
        completionSignaled.set(false);

        taskQueue.add(new CheckTask(username, platform, CheckTask.Origin.MANUAL));

        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(1, r -> {
                Thread t = new Thread(r, "aliasforge-manual");
                t.setDaemon(true);
                return t;
            });
            rateLimitHandler = new RateLimitHandler(
                    retryTask -> { if (!stopped.get()) { running.set(true); taskQueue.add(retryTask); }},
                    exhausted  -> publishResult(new UsernameResult(
                            exhausted.getUsername(), exhausted.getPlatform(),
                            CheckStatus.ERROR, 0, "rate limit exhausted"))
            );
            executor.submit(this::workerLoop);
        }
    }

    public void pause() {
        paused.set(true);
        LOGGER.info("Checker paused.");
    }

    public void resume() {
        paused.set(false);
        synchronized (paused) { paused.notifyAll(); }
        LOGGER.info("Checker resumed.");
    }

    public void stop() {
        stopped.set(true);
        running.set(false);
        paused.set(false);
        taskQueue.clear();
        synchronized (paused) { paused.notifyAll(); }
        if (executor != null) executor.shutdownNow();
        if (rateLimitHandler != null) rateLimitHandler.shutdown();
        LOGGER.info("Checker stopped.");
    }

    // ── Worker ─────────────────────────────────────────────────────────

    private void workerLoop() {
        activeWorkers.incrementAndGet();

        PlatformApi api          = null;
        Platform    lastPlatform = null;
        boolean     platformAvailable = true;
        String      unavailableReason = "";

        try {
            while (!stopped.get()) {
                while (paused.get() && !stopped.get()) {
                    synchronized (paused) {
                        try { paused.wait(200); }
                        catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
                if (stopped.get()) break;

                CheckTask task = taskQueue.poll(500, TimeUnit.MILLISECONDS);

                if (task == null) {
                    if (running.get() &&
                            taskQueue.isEmpty() &&
                            rateLimitHandler != null &&
                            !rateLimitHandler.hasPendingRetries() &&
                            completionSignaled.compareAndSet(false, true)) {
                        running.set(false);
                        LOGGER.info("Checker queue empty — signaling completion.");
                        if (onCompleted != null) onCompleted.run();
                    }
                    continue;
                }

                if (api == null || task.getPlatform() != lastPlatform) {
                    api = ApiFactory.create(task.getPlatform());
                    lastPlatform = task.getPlatform();
                    platformAvailable  = api.isAvailable();
                    unavailableReason  = api.getUnavailableReason();
                    if (!platformAvailable) {
                        LOGGER.warn("Platform {} unavailable: {}", task.getPlatform(), unavailableReason);
                    }
                }

                if (!platformAvailable) {
                    totalError.incrementAndGet();
                    totalChecked.incrementAndGet();
                    publishResult(new UsernameResult(
                            task.getUsername(), task.getPlatform(),
                            CheckStatus.ERROR, 0, unavailableReason));
                    publishStats();
                    continue;
                }

                // Publica CHECKING
                publishResult(new UsernameResult(
                        task.getUsername(), task.getPlatform(),
                        CheckStatus.CHECKING, 0, null));

                currentChecking.incrementAndGet();
                PlatformApi.CheckResult result = api.check(task.getUsername());
                currentChecking.decrementAndGet();

                if (result.isRateLimit()) {
                    if (task.getRetryCount() == 0) totalRateLimit.incrementAndGet();
                    rateLimitHandler.handleRateLimit(task);
                    publishResult(new UsernameResult(
                            task.getUsername(), task.getPlatform(),
                            CheckStatus.RATE_LIMIT, 0, null));
                } else {
                    if (task.getRetryCount() > 0) totalRateLimit.decrementAndGet();
                    totalChecked.incrementAndGet();
                    switch (result.status()) {
                        case AVAILABLE    -> totalAvailable.incrementAndGet();
                        case TAKEN        -> totalTaken.incrementAndGet();
                        case INCONCLUSIVE -> totalInconclusive.incrementAndGet();
                        default           -> totalError.incrementAndGet();
                    }
                    publishResult(new UsernameResult(
                            task.getUsername(), task.getPlatform(),
                            result.status(), result.responseTimeMs(),
                            result.errorDetail()));
                }

                publishStats();

                // Delay por plataforma
                int userDelay     = AppConfig.getInstance().getSettings().getDelayBetweenRequestsMs();
                int platformDelay = api.getRecommendedDelayMs();
                Thread.sleep(Math.max(userDelay, platformDelay));

            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.error("Unexpected error in worker", e);
        } finally {
            activeWorkers.decrementAndGet();
        }
    }

    // ── Publish ────────────────────────────────────────────────────────

    private void publishResult(UsernameResult result) {
        if (onResult != null) {
            javafx.application.Platform.runLater(() -> onResult.accept(result));
        }
    }

    private void publishStats() {
        if (onStatsUpdate != null) {
            int pending = rateLimitHandler != null ? rateLimitHandler.getPendingRetryCount() : 0;
            CheckerStats stats = new CheckerStats(
                    totalChecked.get(), totalAvailable.get(), totalTaken.get(),
                    totalRateLimit.get(), totalInconclusive.get(), totalError.get(),
                    currentChecking.get(),
                    taskQueue.size() + pending
            );
            javafx.application.Platform.runLater(() -> onStatsUpdate.accept(stats));
        }
    }

    private void reset() {
        taskQueue.clear();
        totalChecked.set(0); totalAvailable.set(0); totalTaken.set(0);
        totalRateLimit.set(0); totalInconclusive.set(0); totalError.set(0);
        currentChecking.set(0);
        activeWorkers.set(0);
        completionSignaled.set(false);
    }

    public boolean isRunning()    { return running.get(); }
    public boolean isPaused()     { return paused.get(); }
    public int     getQueueSize() { return taskQueue.size(); }

    public record CheckerStats(
            int checked, int available, int taken,
            int rateLimit, int inconclusive, int error,
            int currentlyChecking, int remaining) {}
}
