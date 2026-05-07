package com.aliasforge.ui;

import com.aliasforge.core.checker.CheckerService;
import com.aliasforge.core.favorites.FavoritesRepository;
import com.aliasforge.core.history.HistoryRepository;
import com.aliasforge.core.state.AppState;
import com.aliasforge.model.*;
import com.aliasforge.util.SystemTrayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * AppController — camada fina entre a UI e o core.
 *
 * Responsabilidades:
 * - Receber ações da UI (start, stop, pause, toggleFavorite, etc.)
 * - Delegar para os serviços corretos
 * - Atualizar o AppState com os resultados
 *
 * O que NÃO faz mais:
 * - Sem ObservableList (JavaFX)
 * - Sem Platform.runLater (JavaFX)
 * - Sem lógica de negócio
 * - Sem decisões sobre histórico ou favoritos
 *
 * A UI observa o AppState diretamente via listeners.
 */
public class AppController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppController.class);

    private final AppState            state;
    private final CheckerService      checkerService;
    private final HistoryRepository   historyRepo;
    private final FavoritesRepository favoritesRepo;

    public AppController() {
        this.state         = new AppState();
        this.historyRepo   = HistoryRepository.getInstance();
        this.favoritesRepo = FavoritesRepository.getInstance();

        this.checkerService = new CheckerService(
                this::onResult,
                this::onStats,
                this::onCompleted
        );

        // Carrega histórico e favoritos no estado inicial
        state.setHistory(historyRepo.getAll());
        state.setFavorites(favoritesRepo.getAll());
    }

    // ── Ações do algoritmo principal ───────────────────────────────────

    /**
     * Inicia o algoritmo. Não limpa os results — isso é decisão da UI.
     */
    public void start(GeneratorConfig config) {
        LOGGER.info("Starting: platform={}, qty={}, mode={}",
                config.getPlatform(), config.getQuantity(), config.getMode());
        state.setRunning(true);
        state.setPaused(false);
        checkerService.start(config);
    }

    public void pause() {
        checkerService.pause();
        state.setPaused(true);
    }

    public void resume() {
        checkerService.resume();
        state.setPaused(false);
    }

    public void stop() {
        checkerService.stop();
        state.setRunning(false);
        state.setPaused(false);
    }

    public void stopAll() {
        checkerService.stopAll();
        state.setRunning(false);
        state.setPaused(false);
    }

    // ── Ações do manual verifier ───────────────────────────────────────

    public void addManualTask(String username, Platform platform) {
        checkerService.addManual(username, platform);
    }

    public void startManual(List<String> usernames, Platform platform) {
        checkerService.startManual(usernames, platform);
    }

    // ── Ações de results ───────────────────────────────────────────────

    /** Limpa os results. Chamado pelo botão "clear" da UI. */
    public void clearResults() {
        state.clearResults();
    }

    // ── Ações de favoritos ─────────────────────────────────────────────

    public void toggleFavorite(String username, Platform platform) {
        historyRepo.toggleFavorite(username, platform);
        state.setHistory(historyRepo.getAll());
        state.setFavorites(favoritesRepo.getAll());

        // Atualiza o resultado na lista se estiver visível
        List<UsernameResult> current = state.getResults();
        for (int i = 0; i < current.size(); i++) {
            UsernameResult r = current.get(i);
            if (r.getUsername().equalsIgnoreCase(username) && r.getPlatform() == platform) {
                state.upsertResult(r.withFavorited(!r.isFavorited()));
                break;
            }
        }
    }

    public boolean isFavorited(String username, Platform platform) {
        return favoritesRepo.isFavorited(username, platform);
    }

    // ── Ações de histórico ─────────────────────────────────────────────

    public void clearHistory() {
        historyRepo.clear();
        state.clearHistory();
        state.clearFavorites();
    }

    // ── Estado ─────────────────────────────────────────────────────────

    public AppState  getState()     { return state; }
    public boolean   isRunning()    { return checkerService.isRunning(); }
    public boolean   isPaused()     { return checkerService.isPaused(); }

    // ── Handlers internos (chamados pelo CheckerService) ───────────────

    private void onResult(UsernameResult result) {
        state.upsertResult(result);

        // Persiste no histórico (exceto estados transitórios)
        if (result.getStatus() != CheckStatus.CHECKING &&
                result.getStatus() != CheckStatus.PENDING  &&
                result.getStatus() != CheckStatus.RATE_LIMIT) {
            historyRepo.add(result);
            state.setHistory(historyRepo.getAll());
        }
    }

    private void onStats(com.aliasforge.core.queue.CheckerQueue.CheckerStats stats) {
        state.updateStats(stats);
        SystemTrayService.getInstance().setTooltip(
                "AliasForge — available: " + stats.available() +
                        " | checked: " + stats.checked());
    }

    private void onCompleted() {
        LOGGER.info("Checker completed.");
        state.signalCompleted();
        SystemTrayService.getInstance().setTooltip("AliasForge — idle");
    }
}