package com.aliasforge.model;

/**
 * Representa todos os status possíveis de uma verificação de username.
 */
public enum CheckStatus {
    CHECKING,
    AVAILABLE,
    TAKEN,
    RATE_LIMIT,
    ERROR,
    PENDING;

    public String getDisplayName() {
        return switch (this) {
            case CHECKING   -> "checking";
            case AVAILABLE  -> "available";
            case TAKEN      -> "taken";
            case RATE_LIMIT -> "rate limit";
            case ERROR      -> "error";
            case PENDING    -> "pending";
        };
    }

    public String getColor() {
        return switch (this) {
            case CHECKING   -> "#2196f3";
            case AVAILABLE  -> "#4caf50";
            case TAKEN      -> "#f44336";
            case RATE_LIMIT -> "#ffc107";
            case ERROR      -> "#757575";
            case PENDING    -> "#aaaaaa";
        };
    }
}