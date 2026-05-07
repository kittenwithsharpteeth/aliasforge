package com.aliasforge.core.state;

import com.aliasforge.core.queue.CheckerQueue;
import com.aliasforge.model.UsernameResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Estado central da aplicação — completamente livre de JavaFX.
 *
 * Contém as listas de resultados, histórico e favoritos, e notifica
 * listeners quando qualquer coisa muda. A UI registra seus listeners
 * aqui e reage às mudanças, mas o AppState não sabe nada sobre UI.
 *
 * Padrão: Observable sem depender de nenhuma classe de UI framework.
 */
public class AppState {

    // ── Listas de dados ────────────────────────────────────────────────
    private final CopyOnWriteArrayList<UsernameResult> results   = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<UsernameResult> history   = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<UsernameResult> favorites = new CopyOnWriteArrayList<>();

    // ── Estado do checker ──────────────────────────────────────────────
    private volatile boolean running = false;
    private volatile boolean paused  = false;
    private volatile CheckerQueue.CheckerStats lastStats = null;

    // ── Listeners — notificados quando algo muda ───────────────────────
    private final List<Runnable>                              onResultsChanged  = new CopyOnWriteArrayList<>();
    private final List<Runnable>                              onHistoryChanged  = new CopyOnWriteArrayList<>();
    private final List<Runnable>                              onFavoritesChanged= new CopyOnWriteArrayList<>();
    private final List<Consumer<CheckerQueue.CheckerStats>>   onStatsChanged    = new CopyOnWriteArrayList<>();
    private final List<Runnable>                              onRunningChanged  = new CopyOnWriteArrayList<>();
    private final List<Runnable>                              onCompleted       = new CopyOnWriteArrayList<>();

    // ── Registro de listeners ──────────────────────────────────────────

    public void addOnResultsChanged (Runnable cb)                            { onResultsChanged.add(cb); }
    public void addOnHistoryChanged (Runnable cb)                            { onHistoryChanged.add(cb); }
    public void addOnFavoritesChanged(Runnable cb)                           { onFavoritesChanged.add(cb); }
    public void addOnStatsChanged   (Consumer<CheckerQueue.CheckerStats> cb) { onStatsChanged.add(cb); }
    public void addOnRunningChanged (Runnable cb)                            { onRunningChanged.add(cb); }
    public void addOnCompleted      (Runnable cb)                            { onCompleted.add(cb); }

    // ── Mutações de results ────────────────────────────────────────────

    /**
     * Atualiza um resultado existente ou adiciona novo.
     * Chamado pelo checker a cada verificação.
     */
    public synchronized void upsertResult(UsernameResult result) {
        for (int i = 0; i < results.size(); i++) {
            UsernameResult e = results.get(i);
            if (e.getUsername().equalsIgnoreCase(result.getUsername()) &&
                    e.getPlatform() == result.getPlatform()) {
                results.set(i, result);
                fireResultsChanged();
                return;
            }
        }
        results.add(result);
        fireResultsChanged();
    }

    /** Limpa todos os results. Chamado pelo botão "clear" da UI. */
    public synchronized void clearResults() {
        results.clear();
        fireResultsChanged();
    }

    // ── Mutações de history ────────────────────────────────────────────

    public synchronized void setHistory(List<UsernameResult> newHistory) {
        history.clear();
        history.addAll(newHistory);
        fireHistoryChanged();
    }

    public synchronized void clearHistory() {
        history.clear();
        fireHistoryChanged();
    }

    // ── Mutações de favorites ──────────────────────────────────────────

    public synchronized void setFavorites(List<UsernameResult> newFavorites) {
        favorites.clear();
        favorites.addAll(newFavorites);
        fireFavoritesChanged();
    }

    public synchronized void clearFavorites() {
        favorites.clear();
        fireFavoritesChanged();
    }

    // ── Estado do checker ──────────────────────────────────────────────

    public void setRunning(boolean running) {
        this.running = running;
        fireRunningChanged();
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        fireRunningChanged();
    }

    public void updateStats(CheckerQueue.CheckerStats stats) {
        this.lastStats = stats;
        fireStatsChanged(stats);
    }

    public void signalCompleted() {
        this.running = false;
        this.paused  = false;
        fireRunningChanged();
        onCompleted.forEach(Runnable::run);
    }

    // ── Getters (listas imutáveis) ─────────────────────────────────────

    public List<UsernameResult> getResults()   { return Collections.unmodifiableList(results); }
    public List<UsernameResult> getHistory()   { return Collections.unmodifiableList(history); }
    public List<UsernameResult> getFavorites() { return Collections.unmodifiableList(favorites); }

    public boolean                     isRunning()  { return running; }
    public boolean                     isPaused()   { return paused; }
    public CheckerQueue.CheckerStats   getLastStats(){ return lastStats; }

    // ── Disparo de eventos ─────────────────────────────────────────────

    private void fireResultsChanged()  { onResultsChanged.forEach(Runnable::run); }
    private void fireHistoryChanged()  { onHistoryChanged.forEach(Runnable::run); }
    private void fireFavoritesChanged(){ onFavoritesChanged.forEach(Runnable::run); }
    private void fireRunningChanged()  { onRunningChanged.forEach(Runnable::run); }
    private void fireStatsChanged(CheckerQueue.CheckerStats s) {
        onStatsChanged.forEach(cb -> cb.accept(s));
    }
}