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
 * Fluxo de rate limit:
 * 1. Username recebe rate limit → RATE_LIMIT (origin = "queue")
 * 2. RateLimitHandler agenda retry (30s / 60s / 120s)
 * 3. Fila principal continua processando outros usernames normalmente
 * 4. Após 3 falhas → ERROR (origin = "logs")
 *
 * Origin display (via CheckStatus.getOriginDisplay()):
 *   available/taken/checking → ""
 *   rate limit               → "queue"
 *   error                    → "logs"
 */
public class CheckerQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckerQueue.class);

    private final AtomicBoolean running  = new AtomicBoolean(false);
    private final AtomicBoolean paused   = new AtomicBoolean(false);
    private final AtomicBoolean stopped  = new AtomicBoolean(false);

    private final AtomicInteger totalChecked    = new AtomicInteger(0);
    private final AtomicInteger totalAvailable  = new AtomicInteger(0);
    private final AtomicInteger totalTaken      = new AtomicInteger(0);
    private final AtomicInteger totalRateLimit  = new AtomicInteger(0);
    private final AtomicInteger totalError      = new AtomicInteger(0);
    private final AtomicInteger currentChecking = new AtomicInteger(0);

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

        for (String u : usernames) {
            taskQueue.add(new CheckTask(u, platform, CheckTask.Origin.GENERATOR));
        }

        rateLimitHandler = new RateLimitHandler(
                // Retry pronto → volta para a fila
                retryTask -> {
                    if (!stopped.get()) taskQueue.add(retryTask);
                },
                // Esgotou retries → publica como ERROR
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
        if (!stopped.get()) {
            taskQueue.add(new CheckTask(username, platform, CheckTask.Origin.MANUAL));
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
        PlatformApi api          = null;
        Platform    lastPlatform = null;

        while (!stopped.get()) {
            // Pausa
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

            try {
                CheckTask task = taskQueue.poll(500, TimeUnit.MILLISECONDS);

                if (task == null) {
                    if (!rateLimitHandler.hasPendingRetries()) {
                        running.set(false);
                        if (onCompleted != null) onCompleted.run();
                    }
                    continue;
                }

                // Recria API se plataforma mudou
                if (api == null || task.getPlatform() != lastPlatform) {
                    api = ApiFactory.create(task.getPlatform());
                    lastPlatform = task.getPlatform();
                }

                // Plataforma indisponível
                if (!api.isAvailable()) {
                    totalError.incrementAndGet();
                    totalChecked.incrementAndGet();
                    publishResult(new UsernameResult(
                            task.getUsername(), task.getPlatform(),
                            CheckStatus.ERROR, 0, api.getUnavailableReason()));
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
                    // Primeira ocorrência de rate limit para este username
                    if (task.getRetryCount() == 0) {
                        totalRateLimit.incrementAndGet();
                    }

                    // Delega ao handler — vai recolocar na fila ou virar error
                    rateLimitHandler.handleRateLimit(task);

                    // Publica RATE_LIMIT para atualizar a tabela
                    publishResult(new UsernameResult(
                            task.getUsername(), task.getPlatform(),
                            CheckStatus.RATE_LIMIT, 0, null));

                } else {
                    // Resultado final — se era retry, remove do contador de rate limit
                    if (task.getRetryCount() > 0) {
                        totalRateLimit.decrementAndGet();
                    }

                    totalChecked.incrementAndGet();
                    switch (result.status()) {
                        case AVAILABLE -> totalAvailable.incrementAndGet();
                        case TAKEN     -> totalTaken.incrementAndGet();
                        default        -> totalError.incrementAndGet();
                    }

                    publishResult(new UsernameResult(
                            task.getUsername(), task.getPlatform(),
                            result.status(), result.responseTimeMs(),
                            result.errorDetail()));
                }

                publishStats();

                // Delay por plataforma (usa o maior entre config e mínimo da plataforma)
                int userDelay     = AppConfig.getInstance().getSettings().getDelayBetweenRequestsMs();
                int platformDelay = api.getRecommendedDelayMs();
                Thread.sleep(Math.max(userDelay, platformDelay));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("Unexpected error in worker", e);
            }
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
            CheckerStats stats = new CheckerStats(
                    totalChecked.get(), totalAvailable.get(), totalTaken.get(),
                    totalRateLimit.get(), totalError.get(), currentChecking.get(),
                    taskQueue.size() + rateLimitHandler.getPendingRetryCount()
            );
            javafx.application.Platform.runLater(() -> onStatsUpdate.accept(stats));
        }
    }

    private void reset() {
        taskQueue.clear();
        totalChecked.set(0); totalAvailable.set(0); totalTaken.set(0);
        totalRateLimit.set(0); totalError.set(0); currentChecking.set(0);
    }

    public boolean isRunning()    { return running.get(); }
    public boolean isPaused()     { return paused.get(); }
    public int     getQueueSize() { return taskQueue.size(); }

    public record CheckerStats(
            int checked, int available, int taken,
            int rateLimit, int error, int currentlyChecking, int remaining) {}
}