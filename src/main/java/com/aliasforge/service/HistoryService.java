package com.aliasforge.service;

import com.aliasforge.core.favorites.FavoritesRepository;
import com.aliasforge.core.history.HistoryRepository;
import com.aliasforge.model.CheckStatus;
import com.aliasforge.model.Platform;
import com.aliasforge.model.UsernameResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Fachada para operações de histórico e favoritos.
 *
 * Responsabilidades extraídas de:
 * - AppController (lógica de toggleFavorite espalhada em 3 lugares ao mesmo tempo:
 *   historyRepo, state, e a lista de results ativos)
 * - HistoryRepository (acesso direto da UI via AppController.getHistory())
 * - FavoritesRepository (duplicação da lógica de "já favoritado?")
 *
 * Problema identificado no AppController.toggleFavorite():
 *   historyRepo.toggleFavorite(...)       // persiste
 *   state.setHistory(historyRepo.getAll())// atualiza estado 1
 *   state.setFavorites(...)               // atualiza estado 2
 *   // loop manual pelos results ativos para sincronizar o isFavorited
 *   // — 4 operações para uma ação simples, propensas a dessincronizar
 *
 * Agora: AppController delega para HistoryService, que coordena tudo
 * atomicamente e retorna o resultado atualizado para o state.
 */
public class HistoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoryService.class);

    private static HistoryService instance;

    private final HistoryRepository   historyRepo;
    private final FavoritesRepository favoritesRepo;

    private HistoryService() {
        this.historyRepo   = HistoryRepository.getInstance();
        this.favoritesRepo = FavoritesRepository.getInstance();
    }

    public static synchronized HistoryService getInstance() {
        if (instance == null) instance = new HistoryService();
        return instance;
    }

    // ── Persistência ───────────────────────────────────────────────────

    /**
     * Persiste um resultado no histórico.
     * Estados transitórios (CHECKING, PENDING, RATE_LIMIT) são ignorados.
     *
     * Antes: essa lógica estava embutida no AppController.onResult(),
     * misturada com a atualização de estado da UI.
     */
    public void record(UsernameResult result) {
        if (shouldSkip(result.getStatus())) {
            LOGGER.debug("Skipping record for status={} username={}",
                    result.getStatus(), result.getUsername());
            return;
        }
        historyRepo.add(result);
        LOGGER.debug("Recorded: {} on {} → {}",
                result.getUsername(), result.getPlatform(), result.getStatus());
    }

    /**
     * Alterna o estado de favoritado de um resultado.
     * Retorna o resultado atualizado (com isFavorited refletindo o novo estado).
     *
     * Antes: AppController fazia 4 operações separadas propensas a race condition.
     * Agora: operação atômica com retorno do novo estado — o AppController
     * só precisa aplicar o resultado retornado ao AppState.
     */
    public FavoriteToggleResult toggleFavorite(String username, Platform platform) {
        historyRepo.toggleFavorite(username, platform);

        boolean nowFavorited = favoritesRepo.isFavorited(username, platform);

        LOGGER.info("Favorite toggled: {} on {} → favorited={}",
                username, platform, nowFavorited);

        return new FavoriteToggleResult(
                username,
                platform,
                nowFavorited,
                historyRepo.getAll(),
                favoritesRepo.getAll()
        );
    }

    // ── Consultas ──────────────────────────────────────────────────────

    public List<UsernameResult> getAll() {
        return historyRepo.getAll();
    }

    public List<UsernameResult> getFavorites() {
        return favoritesRepo.getAll();
    }

    public List<UsernameResult> getByStatus(CheckStatus status) {
        return historyRepo.getByStatus(status);
    }

    public List<UsernameResult> getByPlatform(Platform platform) {
        return historyRepo.getByPlatform(platform);
    }

    public boolean isFavorited(String username, Platform platform) {
        return favoritesRepo.isFavorited(username, platform);
    }

    /**
     * Verifica se um username já foi verificado nesta plataforma.
     * Útil para evitar duplicatas na fila.
     *
     * Antes: não existia — a UI permitia enfileirar o mesmo username
     * múltiplas vezes sem aviso.
     */
    public boolean wasAlreadyChecked(String username, Platform platform) {
        return historyRepo.alreadyChecked(username, platform);
    }

    /**
     * Busca o resultado mais recente para um username/platform.
     * Útil para o "re-check" exibir o estado anterior enquanto o novo carrega.
     */
    public Optional<UsernameResult> findLatest(String username, Platform platform) {
        return historyRepo.getAll().stream()
                .filter(r -> r.getUsername().equalsIgnoreCase(username)
                        && r.getPlatform() == platform)
                .findFirst(); // getAll() já é ordenado por mais recente primeiro
    }

    // ── Limpeza ────────────────────────────────────────────────────────

    /**
     * Limpa todo o histórico (inclui favoritos, pois dependem do histórico).
     * Retorna o estado vazio para o AppController atualizar o AppState.
     */
    public ClearResult clearAll() {
        historyRepo.clear();
        LOGGER.info("History cleared.");
        return new ClearResult(List.of(), List.of());
    }

    // ── Privado ────────────────────────────────────────────────────────

    /**
     * Estados que não devem ser persistidos no histórico.
     */
    private boolean shouldSkip(CheckStatus status) {
        return switch (status) {
            case CHECKING, PENDING, RATE_LIMIT -> true;
            default -> false;
        };
    }

    // ── Records de retorno ─────────────────────────────────────────────

    /**
     * Resultado do toggleFavorite — carrega tudo que o AppController
     * precisa repassar ao AppState em uma única operação coesa.
     */
    public record FavoriteToggleResult(
            String              username,
            Platform            platform,
            boolean             nowFavorited,
            List<UsernameResult> updatedHistory,
            List<UsernameResult> updatedFavorites
    ) {}

    /**
     * Resultado do clearAll — listas vazias prontas para aplicar ao AppState.
     */
    public record ClearResult(
            List<UsernameResult> history,
            List<UsernameResult> favorites
    ) {}
}