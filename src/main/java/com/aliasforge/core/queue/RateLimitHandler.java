package com.aliasforge.core.queue;

import com.aliasforge.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Gerencia rate limits com backoff exponencial.
 *
 * Backoff: delay * 2^(tentativa-1)
 * Ex: tentativa 1 = 60s, tentativa 2 = 120s, tentativa 3 = 240s
 */
public class RateLimitHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitHandler.class);

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "aliasforge-retry");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, CheckTask> retryQueue = new LinkedHashMap<>();
    private final Consumer<CheckTask> onRetryReady;
    private final Consumer<CheckTask> onRetryExhausted;

    public RateLimitHandler(Consumer<CheckTask> onRetryReady,
                            Consumer<CheckTask> onRetryExhausted) {
        this.onRetryReady     = onRetryReady;
        this.onRetryExhausted = onRetryExhausted;
    }

    public synchronized void handleRateLimit(CheckTask task) {
        int maxRetries = AppConfig.getInstance().getSettings().getMaxRetries();

        if (task.getRetryCount() >= maxRetries) {
            LOGGER.warn("Exhausted retries for '{}' after {} attempts",
                    task.getUsername(), maxRetries);
            retryQueue.remove(task.getUsername());
            onRetryExhausted.accept(task);
            return;
        }

        CheckTask retryTask = task.withRetry();
        retryQueue.put(task.getUsername(), retryTask);

        // Backoff exponencial: baseDelay * 2^(retry-1)
        long baseDelay = AppConfig.getInstance().getSettings().getRetryDelaySeconds();
        long delay     = baseDelay * (1L << (retryTask.getRetryCount() - 1));
        delay          = Math.min(delay, 300); // máximo 300s

        LOGGER.info("Rate limit '{}' — retry {}/{} in {}s (exponential backoff)",
                task.getUsername(), retryTask.getRetryCount(), maxRetries, delay);

        scheduler.schedule(() -> {
            synchronized (this) { retryQueue.remove(task.getUsername()); }
            LOGGER.info("Retrying '{}' (attempt {}/{})",
                    task.getUsername(), retryTask.getRetryCount(), maxRetries);
            onRetryReady.accept(retryTask);
        }, delay, TimeUnit.SECONDS);
    }

    public synchronized int     getPendingRetryCount() { return retryQueue.size(); }
    public synchronized boolean hasPendingRetries()    { return !retryQueue.isEmpty(); }
    public void                 shutdown()             { scheduler.shutdownNow(); }
}