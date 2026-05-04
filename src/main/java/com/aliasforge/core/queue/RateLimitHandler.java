package com.aliasforge.core.queue;

import com.aliasforge.config.AppConfig;
import com.aliasforge.model.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Gerencia rate limits com backoff crescente.
 *
 * Comportamento:
 * - Rate limit → entra na fila, agenda retry
 * - Delay por tentativa: base, base×2, base×3, ...
 * - Max retries e delay base lidos de AppSettings em runtime
 * - Após esgotar retries → vira error
 *
 * O mesmo username NÃO acumula múltiplas entradas na fila.
 */
public class RateLimitHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitHandler.class);

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "aliasforge-retry");
                t.setDaemon(true);
                return t;
            });

    // username → retry count atual
    private final Map<String, Integer> retryQueue = new LinkedHashMap<>();

    private final Consumer<CheckTask> onRetryReady;
    private final Consumer<CheckTask> onRetryExhausted;

    public RateLimitHandler(Consumer<CheckTask> onRetryReady,
                            Consumer<CheckTask> onRetryExhausted) {
        this.onRetryReady     = onRetryReady;
        this.onRetryExhausted = onRetryExhausted;
    }

    /**
     * Chamado quando um username recebe rate limit.
     * - Se ainda tem retries disponíveis: agenda retry com delay crescente
     * - Se esgotou: chama onRetryExhausted (vira error)
     */
    public synchronized void handleRateLimit(CheckTask task) {
        AppSettings settings  = AppConfig.getInstance().getSettings();
        int maxRetries        = settings.getMaxRetries();
        int baseDelaySeconds  = settings.getRetryDelaySeconds();

        int currentRetry = retryQueue.getOrDefault(task.getUsername(), 0);

        if (currentRetry >= maxRetries) {
            retryQueue.remove(task.getUsername());
            LOGGER.warn("'{}' exhausted {} retries → marking as error",
                    task.getUsername(), maxRetries);
            onRetryExhausted.accept(task);
            return;
        }

        int nextRetry    = currentRetry + 1;
        // Delay cresce linearmente: base, base×2, base×3, ...
        int delaySeconds = baseDelaySeconds * nextRetry;

        retryQueue.put(task.getUsername(), nextRetry);

        LOGGER.info("Rate limit '{}' → retry {}/{} in {}s",
                task.getUsername(), nextRetry, maxRetries, delaySeconds);

        CheckTask retryTask = task.withRetry();

        scheduler.schedule(() -> {
            synchronized (this) {
                if (retryQueue.getOrDefault(task.getUsername(), 0) == nextRetry) {
                    retryQueue.remove(task.getUsername());
                }
            }
            LOGGER.info("Retrying '{}' (attempt {}/{})",
                    task.getUsername(), nextRetry, maxRetries);
            onRetryReady.accept(retryTask);
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public synchronized int     getPendingRetryCount() { return retryQueue.size(); }
    public synchronized boolean hasPendingRetries()    { return !retryQueue.isEmpty(); }
    public void                 shutdown()             { scheduler.shutdownNow(); }
}