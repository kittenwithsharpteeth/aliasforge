package com.aliasforge.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Representa o resultado de uma verificação de username.
 * Imutável após criação — status é atualizado via withStatus().
 */
public class UsernameResult {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String        username;
    private final Platform      platform;
    private final CheckStatus   status;
    private final long          responseTimeMs;
    private final String        origin;       // "queue", "logs", "manual"
    private final LocalDateTime checkedAt;
    private final boolean       favorited;

    // Construtor principal
    public UsernameResult(String username, Platform platform, CheckStatus status,
                          long responseTimeMs, String origin) {
        this.username       = username;
        this.platform       = platform;
        this.status         = status;
        this.responseTimeMs = responseTimeMs;
        this.origin         = origin;
        this.checkedAt      = LocalDateTime.now();
        this.favorited      = false;
    }

    // Construtor completo (usado ao carregar do disco)
    public UsernameResult(String username, Platform platform, CheckStatus status,
                          long responseTimeMs, String origin,
                          LocalDateTime checkedAt, boolean favorited) {
        this.username       = username;
        this.platform       = platform;
        this.status         = status;
        this.responseTimeMs = responseTimeMs;
        this.origin         = origin;
        this.checkedAt      = checkedAt;
        this.favorited      = favorited;
    }

    // ── Builders imutáveis ─────────────────────────────────────────────

    /** Retorna uma cópia com novo status. */
    public UsernameResult withStatus(CheckStatus newStatus, long responseTimeMs) {
        return new UsernameResult(username, platform, newStatus,
                responseTimeMs, origin, checkedAt, favorited);
    }

    /** Retorna uma cópia com favorited alternado. */
    public UsernameResult withFavorited(boolean favorited) {
        return new UsernameResult(username, platform, status,
                responseTimeMs, origin, checkedAt, favorited);
    }

    // ── Getters ────────────────────────────────────────────────────────

    public String        getUsername()       { return username; }
    public Platform      getPlatform()       { return platform; }
    public CheckStatus   getStatus()         { return status; }
    public long          getResponseTimeMs() { return responseTimeMs; }
    public String        getOrigin()         { return origin; }
    public LocalDateTime getCheckedAt()      { return checkedAt; }
    public boolean       isFavorited()       { return favorited; }

    public String getCheckedAtFormatted() {
        return checkedAt != null ? checkedAt.format(FORMATTER) : "";
    }

    public String getResponseTimeDisplay() {
        return responseTimeMs > 0 ? responseTimeMs + "ms" : "";
    }

    @Override
    public String toString() {
        return String.format("UsernameResult{username='%s', platform=%s, status=%s, time=%dms}",
                username, platform, status, responseTimeMs);
    }
}