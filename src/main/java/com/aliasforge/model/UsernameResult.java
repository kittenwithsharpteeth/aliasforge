package com.aliasforge.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Representa o resultado de uma verificação de username.
 */
public class UsernameResult {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String        username;
    private final Platform      platform;
    private final CheckStatus   status;
    private final long          responseTimeMs;
    private final String        errorDetail;   // detalhe do erro para os logs futuros
    private final LocalDateTime checkedAt;
    private final boolean       favorited;

    public UsernameResult(String username, Platform platform, CheckStatus status,
                          long responseTimeMs, String errorDetail) {
        this.username       = username;
        this.platform       = platform;
        this.status         = status;
        this.responseTimeMs = responseTimeMs;
        this.errorDetail    = errorDetail;
        this.checkedAt      = LocalDateTime.now();
        this.favorited      = false;
    }

    public UsernameResult(String username, Platform platform, CheckStatus status,
                          long responseTimeMs, String errorDetail,
                          LocalDateTime checkedAt, boolean favorited) {
        this.username       = username;
        this.platform       = platform;
        this.status         = status;
        this.responseTimeMs = responseTimeMs;
        this.errorDetail    = errorDetail;
        this.checkedAt      = checkedAt;
        this.favorited      = favorited;
    }

    // ── Builders ───────────────────────────────────────────────────────

    public UsernameResult withStatus(CheckStatus newStatus, long responseTimeMs) {
        return new UsernameResult(username, platform, newStatus,
                responseTimeMs, errorDetail, checkedAt, favorited);
    }

    public UsernameResult withFavorited(boolean favorited) {
        return new UsernameResult(username, platform, status,
                responseTimeMs, errorDetail, checkedAt, favorited);
    }

    // ── Getters ────────────────────────────────────────────────────────

    public String        getUsername()       { return username; }
    public Platform      getPlatform()       { return platform; }
    public CheckStatus   getStatus()         { return status; }
    public long          getResponseTimeMs() { return responseTimeMs; }
    public boolean       isFavorited()       { return favorited; }
    public LocalDateTime getCheckedAt()      { return checkedAt; }
    public String        getErrorDetail()    { return errorDetail; }

    /**
     * Coluna "origin" na tabela:
     * - available / taken  → vazio
     * - rate limit         → "queue"
     * - error              → "logs"
     * - checking / pending → vazio
     */
    public String getOrigin() {
        return status.getOriginDisplay();
    }

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