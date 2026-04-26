package com.aliasforge.core.queue;

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
 * - Retry 1: espera 30s
 * - Retry 2: espera 60s
 * - Retry 3: espera 120s
 * - Após 3 falhas → vira error com origin "logs" e passa pro próximo
 *
 * O mesmo username NÃO acumula múltiplas entradas na fila.
 */
public class RateLimitHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitHandler.class);

    // Delays para cada retry (em segundos)
    private static final int[] RETRY_DELAYS = {30, 60, 120};
    private static final int   MAX_RETRIES  = 3;

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
        int currentRetry = retryQueue.getOrDefault(task.getUsername(), 0);

        if (currentRetry >= MAX_RETRIES) {
            // Esgotou — remove da fila e notifica como error
            retryQueue.remove(task.getUsername());
            LOGGER.warn("'{}' exhausted {} retries → marking as error",
                    task.getUsername(), MAX_RETRIES);
            onRetryExhausted.accept(task);
            return;
        }

        // Incrementa contador e agenda retry
        int nextRetry = currentRetry + 1;
        retryQueue.put(task.getUsername(), nextRetry);

        int delaySeconds = RETRY_DELAYS[currentRetry]; // 30s, 60s, 120s
        LOGGER.info("Rate limit '{}' → retry {}/{} in {}s",
                task.getUsername(), nextRetry, MAX_RETRIES, delaySeconds);

        CheckTask retryTask = task.withRetry();

        scheduler.schedule(() -> {
            synchronized (this) {
                // Só remove se não foi atualizado para um retry ainda maior
                if (retryQueue.getOrDefault(task.getUsername(), 0) == nextRetry) {
                    retryQueue.remove(task.getUsername());
                }
            }
            LOGGER.info("Retrying '{}' (attempt {}/{})",
                    task.getUsername(), nextRetry, MAX_RETRIES);
            onRetryReady.accept(retryTask);
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public synchronized int     getPendingRetryCount() { return retryQueue.size(); }
    public synchronized boolean hasPendingRetries()    { return !retryQueue.isEmpty(); }
    public void                 shutdown()             { scheduler.shutdownNow(); }
}