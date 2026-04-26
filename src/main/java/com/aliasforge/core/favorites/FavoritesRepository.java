package com.aliasforge.core.favorites;

import com.aliasforge.core.history.HistoryRepository;
import com.aliasforge.model.Platform;
import com.aliasforge.model.UsernameResult;

import java.util.List;

/**
 * Gerencia os favoritos — delega ao HistoryRepository,
 * já que favoritos são simplesmente resultados com favorited=true.
 *
 * Esta classe existe como fachada para a UI não precisar
 * conhecer o HistoryRepository diretamente.
 */
public class FavoritesRepository {

    private static FavoritesRepository instance;
    private final HistoryRepository history;

    private FavoritesRepository() {
        this.history = HistoryRepository.getInstance();
    }

    public static synchronized FavoritesRepository getInstance() {
        if (instance == null) instance = new FavoritesRepository();
        return instance;
    }

    // ── API pública ────────────────────────────────────────────────────

    public List<UsernameResult> getAll() {
        return history.getFavorites();
    }

    public void toggle(String username, Platform platform) {
        history.toggleFavorite(username, platform);
    }

    public boolean isFavorited(String username, Platform platform) {
        return history.getAll().stream().anyMatch(r ->
                r.getUsername().equalsIgnoreCase(username) &&
                        r.getPlatform() == platform &&
                        r.isFavorited());
    }

    public void clear() {
        history.getAll().stream()
                .filter(UsernameResult::isFavorited)
                .forEach(r -> history.toggleFavorite(r.getUsername(), r.getPlatform()));
    }

    public int size() {
        return getAll().size();
    }
}