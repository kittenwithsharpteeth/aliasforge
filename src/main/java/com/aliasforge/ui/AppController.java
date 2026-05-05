package com.aliasforge.ui;

import com.aliasforge.core.checker.CheckerService;
import com.aliasforge.core.favorites.FavoritesRepository;
import com.aliasforge.core.history.HistoryRepository;
import com.aliasforge.core.queue.CheckerQueue;
import com.aliasforge.model.*;
import com.aliasforge.util.SystemTrayService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Controlador central da aplicação.
 *
 * Fix: results NÃO é limpo ao iniciar o algoritmo.
 * O clear só acontece quando o usuário clicar em "clear" explicitamente.
 * Isso preserva os resultados anteriores ao reiniciar o algoritmo.
 */
public class AppController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppController.class);

    private final CheckerService      checkerService;
    private final HistoryRepository   historyRepo;
    private final FavoritesRepository favoritesRepo;

    private final ObservableList<UsernameResult> results   = FXCollections.observableArrayList();
    private final ObservableList<UsernameResult> history   = FXCollections.observableArrayList();
    private final ObservableList<UsernameResult> favorites = FXCollections.observableArrayList();

    private Consumer<CheckerQueue.CheckerStats> onStatsUpdate;
    private Runnable                            onCompleted;

    public AppController() {
        this.historyRepo   = HistoryRepository.getInstance();
        this.favoritesRepo = FavoritesRepository.getInstance();

        this.checkerService = new CheckerService(
                this::handleResult,
                this::handleStats,
                this::handleCompleted
        );

        history.setAll(historyRepo.getAll());
        favorites.setAll(favoritesRepo.getAll());
    }

    // ── Controle ───────────────────────────────────────────────────────

    /**
     * Inicia o algoritmo principal.
     * Fix: NÃO limpa os results — o usuário decide quando limpar via botão "clear".
     */
    public void start(GeneratorConfig config) {
        // Sem results::clear aqui — preserva resultados anteriores
        LOGGER.info("Starting: platform={}, qty={}, mode={}",
                config.getPlatform(), config.getQuantity(), config.getMode());
        checkerService.start(config);
    }

    public void startManual(List<String> usernames, com.aliasforge.model.Platform platform) {
        checkerService.startManual(usernames, platform);
    }

    public void addManualTask(String username, com.aliasforge.model.Platform platform) {
        checkerService.addManual(username, platform);
    }

    public void pause()  { checkerService.pause(); }
    public void resume() { checkerService.resume(); }

    /**
     * Para o algoritmo principal.
     * A manualQueue continua funcionando.
     * Os results NÃO são limpos.
     */
    public void stop() { checkerService.stop(); }

    /**
     * Para tudo — usado apenas ao encerrar o app.
     */
    public void stopAll() { checkerService.stopAll(); }

    /**
     * Limpa os results da tabela principal.
     * Chamado apenas pelo botão "clear" do ResultsPanel.
     */
    public void clearResults() {
        Platform.runLater(results::clear);
    }

    public boolean isRunning() { return checkerService.isRunning(); }
    public boolean isPaused()  { return checkerService.isPaused(); }

    // ── Favoritos ──────────────────────────────────────────────────────

    public void toggleFavorite(String username, com.aliasforge.model.Platform platform) {
        historyRepo.toggleFavorite(username, platform);
        Platform.runLater(() -> {
            history.setAll(historyRepo.getAll());
            favorites.setAll(favoritesRepo.getAll());
            for (int i = 0; i < results.size(); i++) {
                UsernameResult r = results.get(i);
                if (r.getUsername().equalsIgnoreCase(username) &&
                        r.getPlatform() == platform) {
                    results.set(i, r.withFavorited(!r.isFavorited()));
                    break;
                }
            }
        });
    }

    public boolean isFavorited(String username, com.aliasforge.model.Platform platform) {
        return favoritesRepo.isFavorited(username, platform);
    }

    // ── Histórico ──────────────────────────────────────────────────────

    public void clearHistory() {
        historyRepo.clear();
        Platform.runLater(() -> { history.clear(); favorites.clear(); });
    }

    public void refreshHistory() {
        Platform.runLater(() -> {
            history.setAll(historyRepo.getAll());
            favorites.setAll(favoritesRepo.getAll());
        });
    }

    // ── Listas ─────────────────────────────────────────────────────────

    public ObservableList<UsernameResult> getResults()   { return results; }
    public ObservableList<UsernameResult> getHistory()   { return history; }
    public ObservableList<UsernameResult> getFavorites() { return favorites; }

    public void setOnStatsUpdate(Consumer<CheckerQueue.CheckerStats> cb) { this.onStatsUpdate = cb; }
    public void setOnCompleted(Runnable cb) { this.onCompleted = cb; }

    // ── Handlers ───────────────────────────────────────────────────────

    private void handleResult(UsernameResult result) {
        updateOrAddResult(result);

        if (result.getStatus() != CheckStatus.CHECKING &&
                result.getStatus() != CheckStatus.PENDING  &&
                result.getStatus() != CheckStatus.RATE_LIMIT) {
            historyRepo.add(result);
            history.setAll(historyRepo.getAll());
        }
    }

    private void handleStats(CheckerQueue.CheckerStats stats) {
        if (onStatsUpdate != null) onStatsUpdate.accept(stats);
        SystemTrayService.getInstance().setTooltip(
                "AliasForge — available: " + stats.available() +
                        " | checked: " + stats.checked()
        );
    }

    private void handleCompleted() {
        LOGGER.info("Checker completed.");
        Platform.runLater(() -> { if (onCompleted != null) onCompleted.run(); });
        SystemTrayService.getInstance().setTooltip("AliasForge — idle");
    }

    private void updateOrAddResult(UsernameResult result) {
        for (int i = 0; i < results.size(); i++) {
            UsernameResult e = results.get(i);
            if (e.getUsername().equalsIgnoreCase(result.getUsername()) &&
                    e.getPlatform() == result.getPlatform()) {
                results.set(i, result);
                return;
            }
        }
        results.add(result);
    }
}