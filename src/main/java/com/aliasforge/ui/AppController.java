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
 *
 * Responsabilidades após refatoração:
 * - Receber ações da UI e delegar para o serviço correto
 * - Aplicar os resultados dos serviços ao AppState
 * - Não conter lógica de negócio
 *
 * Comparação com a versão anterior:
 *
 * ANTES — AppController fazia diretamente:
 *   historyRepo.toggleFavorite(username, platform);  // acesso direto ao repo
 *   state.setHistory(historyRepo.getAll());           // 3 operações separadas
 *   state.setFavorites(favoritesRepo.getAll());       // sem atomicidade
 *   for (int i ...) { state.upsertResult(...) }       // loop manual
 *
 * DEPOIS — AppController delega:
 *   HistoryService.FavoriteToggleResult r = historyService.toggleFavorite(...);
 *   state.setHistory(r.updatedHistory());
 *   state.setFavorites(r.updatedFavorites());
 *   // sincronização dos results ativos via updateFavoritedInResults()
 *
 * O AppController agora conhece apenas: AppState + os 4 serviços.
 * Antes conhecia: CheckerService, HistoryRepository, FavoritesRepository,
 *                 AppState e SystemTrayService (5 dependências de camadas diferentes).
 */
public class AppController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppController.class);

    private final AppState             state;
    private final UsernameCheckService checkService;
    private final HistoryService       historyService;
    private final PlatformService      platformService;
    private final RateLimitService     rateLimitService;

    // ExportService é stateless — acessado diretamente pela UI quando precisar
    // Exposto via getter para que as views não instanciem diretamente
    private final ExportService exportService;

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

        // Carrega histórico e favoritos no estado inicial
        state.setHistory(historyService.getAll());
        state.setFavorites(historyService.getFavorites());
    }

    // ── Algoritmo principal ────────────────────────────────────────────

    /**
     * Inicia o algoritmo. Valida a configuração antes de iniciar.
     *
     * Antes: qualquer config inválida só era descoberta depois que o
     * CheckerService tentava gerar nomes e falhava silenciosamente.
     * Agora: StartResult.isRejected() permite que a UI exiba o motivo.
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

    /**
     * Adiciona um username para verificação manual com validação prévia.
     * Retorna o resultado para a UI exibir feedback imediato se inválido.
     *
     * Antes: addManualTask() não retornava nada — erros de validação só
     * apareciam como "error" na tabela depois de uma request HTTP.
     */
    public UsernameCheckService.ManualCheckResult addManualTask(String username, Platform platform) {
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

    /**
     * Alterna o estado de favorito de um username.
     *
     * Antes: 4 operações separadas no AppController sem atomicidade.
     * Agora: 1 delegação ao HistoryService + aplicação do resultado.
     */
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

    // ── Validação (exposta para UI usar antes de enfileirar) ───────────

    /**
     * Valida um username para a plataforma selecionada.
     * A UI chama isso para dar feedback inline no campo de texto,
     * sem precisar esperar o resultado da verificação.
     */
    public PlatformService.ValidationResult validate(String username, Platform platform) {
        return platformService.validate(username, platform);
    }

    // ── Estado ─────────────────────────────────────────────────────────

    public AppState     getState()        { return state; }
    public boolean      isRunning()       { return checkService.isRunning(); }
    public boolean      isPaused()        { return checkService.isPaused(); }
    public ExportService getExportService(){ return exportService; }

    // ── Handlers internos (CheckerService → AppState) ──────────────────

    private void onResult(UsernameResult result) {
        state.upsertResult(result);

        historyService.record(result);

        // Atualiza a lista de histórico no state após persistência
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

    /**
     * Sincroniza o isFavorited nos results ativos quando o favorito é alterado.
     *
     * Antes: esse loop estava embutido em toggleFavorite() misturado com
     * as chamadas ao repositório, dificultando o entendimento do fluxo.
     */
    private void updateFavoritedInResults(String username, Platform platform, boolean nowFavorited) {
        List<UsernameResult> current = state.getResults();
        for (UsernameResult r : current) {
            if (r.getUsername().equalsIgnoreCase(username) && r.getPlatform() == platform) {
                state.upsertResult(r.withFavorited(nowFavorited));
                break;
            }
        }
    }
}