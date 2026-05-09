package com.aliasforge.ui;

import com.aliasforge.core.state.AppState;
import com.aliasforge.model.*;
import com.aliasforge.service.ExportService;
import com.aliasforge.service.HistoryService;
import com.aliasforge.service.PlatformService;
import com.aliasforge.service.RateLimitService;
import com.aliasforge.service.UsernameCheckService;
import com.aliasforge.util.SystemTrayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * AppController — camada fina entre a UI e os serviços.
 */
public class AppController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppController.class);

    private final AppState             state;
    private final UsernameCheckService checkService;
    private final HistoryService       historyService;
    private final PlatformService      platformService;
    private final RateLimitService     rateLimitService;
    private final ExportService        exportService;

    public AppController() {
        this.state           = new AppState();
        this.historyService  = HistoryService.getInstance();
        this.platformService = PlatformService.getInstance();
        this.rateLimitService= RateLimitService.getInstance();
        this.exportService   = ExportService.getInstance();

        this.checkService = new UsernameCheckService(
                this::onResult,
                this::onStats,
                this::onCompleted
        );

        state.setHistory(historyService.getAll());
        state.setFavorites(historyService.getFavorites());
    }

    // ── Algoritmo principal ────────────────────────────────────────────

    /**
     * FIX: result.accepted() é o accessor do campo boolean do record —
     * não confundir com o antigo factory method accepted() que foi
     * renomeado para ofAccepted(). O accessor do campo funciona corretamente
     * como boolean aqui.
     */
    public UsernameCheckService.StartResult start(GeneratorConfig config) {
        LOGGER.info("Starting: platform={}, qty={}, mode={}",
                config.getPlatform(), config.getQuantity(), config.getMode());

        UsernameCheckService.StartResult result = checkService.start(config);

        if (result.accepted()) {
            state.setRunning(true);
            state.setPaused(false);
        } else {
            LOGGER.warn("Start rejected: {}", result.rejectionReason());
        }

        return result;
    }

    public void pause() {
        checkService.pause();
        state.setPaused(true);
    }

    public void resume() {
        checkService.resume();
        state.setPaused(false);
    }

    public void stop() {
        checkService.stop();
        state.setRunning(false);
        state.setPaused(false);
    }

    public void stopAll() {
        checkService.stopAll();
        state.setRunning(false);
        state.setPaused(false);
    }

    // ── Manual verifier ────────────────────────────────────────────────

    public UsernameCheckService.ManualCheckResult addManualTask(
            String username, Platform platform) {
        return checkService.addManual(username, platform);
    }

    public UsernameCheckService.BatchManualResult startManual(
            List<String> usernames, Platform platform) {
        return checkService.addManualBatch(usernames, platform);
    }

    // ── Results ────────────────────────────────────────────────────────

    public void clearResults() {
        state.clearResults();
    }

    // ── Favoritos ──────────────────────────────────────────────────────

    public void toggleFavorite(String username, Platform platform) {
        HistoryService.FavoriteToggleResult result =
                historyService.toggleFavorite(username, platform);

        state.setHistory(result.updatedHistory());
        state.setFavorites(result.updatedFavorites());
        updateFavoritedInResults(result.username(), result.platform(), result.nowFavorited());
    }

    public boolean isFavorited(String username, Platform platform) {
        return historyService.isFavorited(username, platform);
    }

    // ── Histórico ──────────────────────────────────────────────────────

    public void clearHistory() {
        HistoryService.ClearResult result = historyService.clearAll();
        state.setHistory(result.history());
        state.setFavorites(result.favorites());
    }

    // ── Validação ──────────────────────────────────────────────────────

    public PlatformService.ValidationResult validate(String username, Platform platform) {
        return platformService.validate(username, platform);
    }

    // ── Estado ─────────────────────────────────────────────────────────

    public AppState      getState()         { return state; }
    public boolean       isRunning()        { return checkService.isRunning(); }
    public boolean       isPaused()         { return checkService.isPaused(); }
    public ExportService getExportService() { return exportService; }

    // ── Handlers internos ──────────────────────────────────────────────

    private void onResult(UsernameResult result) {
        state.upsertResult(result);
        historyService.record(result);

        if (result.getStatus() != CheckStatus.CHECKING &&
                result.getStatus() != CheckStatus.PENDING  &&
                result.getStatus() != CheckStatus.RATE_LIMIT) {
            state.setHistory(historyService.getAll());
        }
    }

    private void onStats(com.aliasforge.core.queue.CheckerQueue.CheckerStats stats) {
        state.updateStats(stats);
        SystemTrayService.getInstance().setTooltip(
                "AliasForge — available: " + stats.available() +
                        " | checked: "   + stats.checked());
    }

    private void onCompleted() {
        state.signalCompleted();
        SystemTrayService.getInstance().setTooltip("AliasForge — idle");
    }

    // ── Privado ────────────────────────────────────────────────────────

    private void updateFavoritedInResults(String username, Platform platform,
                                          boolean nowFavorited) {
        List<UsernameResult> current = state.getResults();
        for (UsernameResult r : current) {
            if (r.getUsername().equalsIgnoreCase(username) &&
                    r.getPlatform() == platform) {
                state.upsertResult(r.withFavorited(nowFavorited));
                break;
            }
        }
    }
}