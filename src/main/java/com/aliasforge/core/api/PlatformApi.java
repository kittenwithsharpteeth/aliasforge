package com.aliasforge.core.api;

import com.aliasforge.model.CheckStatus;
import com.aliasforge.model.Platform;

/**
 * Interface base para todas as implementações de API de plataforma.
 */
public interface PlatformApi {

    CheckResult check(String username);
    Platform    getPlatform();
    boolean     isValidUsername(String username);

    /** Delay recomendado entre requests para esta plataforma (ms). */
    int getRecommendedDelayMs();

    /** Se false, plataforma está desabilitada (ex: requer bot/auth especial). */
    default boolean isAvailable() { return true; }

    /** Motivo de estar desabilitada. */
    default String getUnavailableReason() { return ""; }

    // ── CheckResult ────────────────────────────────────────────────────

    record CheckResult(CheckStatus status, long responseTimeMs, String errorDetail) {

        public static CheckResult available(long ms)     { return new CheckResult(CheckStatus.AVAILABLE,   ms, null); }
        public static CheckResult taken(long ms)         { return new CheckResult(CheckStatus.TAKEN,        ms, null); }
        public static CheckResult rateLimit()            { return new CheckResult(CheckStatus.RATE_LIMIT,   0,  "rate limited"); }
        public static CheckResult error(String detail)   { return new CheckResult(CheckStatus.ERROR,        0,  detail); }
        public static CheckResult unavailable(String r)  { return new CheckResult(CheckStatus.ERROR,        0,  "unavailable: " + r); }

        public boolean isRateLimit() { return status == CheckStatus.RATE_LIMIT; }
        public boolean isError()     { return status == CheckStatus.ERROR; }
    }
}