package com.aliasforge.core.queue;

import com.aliasforge.model.Platform;

/**
 * Representa uma tarefa de verificação na fila.
 * Imutável, identifica unicamente um username + plataforma.
 */
public class CheckTask {

    public enum Origin { GENERATOR, MANUAL }

    private final String   username;
    private final Platform platform;
    private final Origin   origin;
    private final int      retryCount;

    public CheckTask(String username, Platform platform, Origin origin) {
        this(username, platform, origin, 0);
    }

    public CheckTask(String username, Platform platform, Origin origin, int retryCount) {
        this.username   = username;
        this.platform   = platform;
        this.origin     = origin;
        this.retryCount = retryCount;
    }

    /** Retorna uma cópia desta task com retryCount incrementado. */
    public CheckTask withRetry() {
        return new CheckTask(username, platform, origin, retryCount + 1);
    }

    public String   getUsername()   { return username; }
    public Platform getPlatform()   { return platform; }
    public Origin   getOrigin()     { return origin; }
    public int      getRetryCount() { return retryCount; }

    public String getOriginDisplay() {
        return switch (origin) {
            case GENERATOR -> "queue";
            case MANUAL    -> "manual";
        };
    }

    @Override
    public String toString() {
        return String.format("CheckTask{username='%s', platform=%s, origin=%s, retry=%d}",
                username, platform, origin, retryCount);
    }
}