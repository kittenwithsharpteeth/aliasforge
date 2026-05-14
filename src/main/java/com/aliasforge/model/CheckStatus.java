package com.aliasforge.model;

/**
 * Representa todos os status possíveis de uma verificação de username.
 */
public enum CheckStatus {
    CHECKING,
    AVAILABLE,
    TAKEN,
    RATE_LIMIT,
    INCONCLUSIVE,
    ERROR,
    PENDING;

    public String getDisplayName() {
        return switch (this) {
            case CHECKING     -> "checking";
            case AVAILABLE    -> "available";
            case TAKEN        -> "taken";
            case RATE_LIMIT   -> "rate limit";
            case INCONCLUSIVE -> "inconclusive";
            case ERROR        -> "error";
            case PENDING      -> "pending";
        };
    }

    public String getColor() {
        return switch (this) {
            case CHECKING     -> "#2196f3";
            case AVAILABLE    -> "#4caf50";
            case TAKEN        -> "#f44336";
            case RATE_LIMIT   -> "#ffc107";
            case INCONCLUSIVE -> "#9c27b0";
            case ERROR        -> "#757575";
            case PENDING      -> "#aaaaaa";
        };
    }

    /**
     * Retorna o texto da coluna "origin" para cada status.
     * available/taken = vazio
     * rate limit = "queue"
     * inconclusive = "review"
     * error = "logs"
     * checking/pending = vazio
     */
    public String getOriginDisplay() {
        return switch (this) {
            case RATE_LIMIT   -> "queue";
            case INCONCLUSIVE -> "review";
            case ERROR        -> "logs";
            default           -> "";
        };
    }
}
