package com.aliasforge.service;

import com.aliasforge.core.api.ApiFactory;
import com.aliasforge.core.api.PlatformApi;
import com.aliasforge.model.Platform;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Centraliza toda a lógica relacionada a plataformas.
 *
 * Responsabilidades extraídas de:
 * - ApiFactory (criação de APIs)
 * - Múltiplas classes *Api (validação duplicada)
 * - SidebarPanel / GroupedPlatformComboBox (resolução de platform por string)
 * - AppSettings (strings "lastPlatform" sem validação centralizada)
 *
 * Antes: Platform.fromString() existia no enum mas sem tratamento de
 * plataformas indisponíveis; validação de username duplicada em cada
 * *Api e novamente na UI antes de exibir erros.
 *
 * Depois: Um único ponto de entrada para tudo relacionado a plataformas.
 */
public class PlatformService {

    private static PlatformService instance;

    private PlatformService() {}

    public static synchronized PlatformService getInstance() {
        if (instance == null) instance = new PlatformService();
        return instance;
    }

    // ── Resolução ──────────────────────────────────────────────────────

    /**
     * Resolve uma plataforma pelo nome (displayName ou enum name).
     * Retorna Optional.empty() se não encontrada — sem fallback silencioso.
     *
     * Antes: Platform.fromString() fazia fallback silencioso para MINECRAFT,
     * mascarando bugs onde o nome vinha errado da UI.
     */
    public Optional<Platform> resolve(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        for (Platform p : Platform.values()) {
            if (p.displayName.equalsIgnoreCase(name) || p.name().equalsIgnoreCase(name)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve com fallback explícito — use apenas onde um default faz sentido
     * (ex: carregar configuração salva que pode estar em formato antigo).
     */
    public Platform resolveOrDefault(String name, Platform fallback) {
        return resolve(name).orElse(fallback);
    }

    // ── Disponibilidade ────────────────────────────────────────────────

    /**
     * Verifica se a plataforma está disponível para uso.
     * Antes: cada CheckerQueue.workerLoop() criava uma instância de API só
     * para chamar isAvailable() — overhead desnecessário para consultas de UI.
     */
    public boolean isAvailable(Platform platform) {
        return ApiFactory.create(platform).isAvailable();
    }

    /**
     * Retorna o motivo da indisponibilidade, ou empty se estiver disponível.
     */
    public Optional<String> getUnavailableReason(Platform platform) {
        PlatformApi api = ApiFactory.create(platform);
        if (api.isAvailable()) return Optional.empty();
        return Optional.of(api.getUnavailableReason());
    }

    // ── Validação de username ──────────────────────────────────────────

    /**
     * Valida um username para a plataforma especificada.
     *
     * Antes: a validação estava duplicada em:
     * - Cada classe *Api (isValidUsername)
     * - SidebarPanel (sem validação — enviava direto)
     * - ResultsPanel (nenhuma — só descobria o erro no resultado)
     *
     * Agora a UI pode validar antes de enfileirar via este único método.
     */
    public ValidationResult validate(String username, Platform platform) {
        if (username == null || username.isBlank()) {
            return ValidationResult.invalid("Username cannot be empty.");
        }

        int len = username.length();

        if (len < platform.minLength) {
            return ValidationResult.invalid(
                    "Too short for " + platform.displayName +
                            " (minimum " + platform.minLength + " characters).");
        }

        if (len > platform.maxLength) {
            return ValidationResult.invalid(
                    "Too long for " + platform.displayName +
                            " (maximum " + platform.maxLength + " characters).");
        }

        // Delega a regex específica da plataforma para a API
        PlatformApi api = ApiFactory.create(platform);
        if (!api.isValidUsername(username)) {
            return ValidationResult.invalid(
                    "Invalid characters for " + platform.displayName + ".");
        }

        return ValidationResult.valid();
    }

    // ── Metadados ──────────────────────────────────────────────────────

    /**
     * Retorna todas as plataformas disponíveis (não desabilitadas).
     * Usado pela UI para construir combos sem precisar filtrar manualmente.
     */
    public List<Platform> getAvailablePlatforms() {
        return Arrays.stream(Platform.values())
                .filter(this::isAvailable)
                .toList();
    }

    /**
     * Retorna todas as plataformas de um grupo.
     */
    public List<Platform> getByGroup(Platform.Group group) {
        return Arrays.stream(Platform.values())
                .filter(p -> p.group == group)
                .toList();
    }

    /**
     * Retorna o delay recomendado entre requests para a plataforma.
     * Antes: cada *Api expunha getRecommendedDelayMs() mas a UI não tinha
     * como consultar sem instanciar a API inteira.
     */
    public int getRecommendedDelayMs(Platform platform) {
        return ApiFactory.create(platform).getRecommendedDelayMs();
    }

    // ── ValidationResult ───────────────────────────────────────────────

    public record ValidationResult(boolean valid, String errorMessage) {

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isInvalid() { return !valid; }
    }
}