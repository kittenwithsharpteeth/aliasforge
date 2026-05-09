package com.aliasforge.service;

import com.aliasforge.core.api.ApiFactory;
import com.aliasforge.core.api.PlatformApi;
import com.aliasforge.model.Platform;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Centraliza toda a lógica relacionada a plataformas.
 */
public class PlatformService {

    private static PlatformService instance;

    private PlatformService() {}

    public static synchronized PlatformService getInstance() {
        if (instance == null) instance = new PlatformService();
        return instance;
    }

    // ── Resolução ──────────────────────────────────────────────────────

    public Optional<Platform> resolve(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        for (Platform p : Platform.values()) {
            if (p.displayName.equalsIgnoreCase(name) || p.name().equalsIgnoreCase(name)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public Platform resolveOrDefault(String name, Platform fallback) {
        return resolve(name).orElse(fallback);
    }

    // ── Disponibilidade ────────────────────────────────────────────────

    public boolean isAvailable(Platform platform) {
        return ApiFactory.create(platform).isAvailable();
    }

    public Optional<String> getUnavailableReason(Platform platform) {
        PlatformApi api = ApiFactory.create(platform);
        if (api.isAvailable()) return Optional.empty();
        return Optional.of(api.getUnavailableReason());
    }

    // ── Validação de username ──────────────────────────────────────────

    public ValidationResult validate(String username, Platform platform) {
        if (username == null || username.isBlank()) {
            return ValidationResult.ofInvalid("Username cannot be empty.");
        }

        int len = username.length();

        if (len < platform.minLength) {
            return ValidationResult.ofInvalid(
                    "Too short for " + platform.displayName +
                            " (minimum " + platform.minLength + " characters).");
        }

        if (len > platform.maxLength) {
            return ValidationResult.ofInvalid(
                    "Too long for " + platform.displayName +
                            " (maximum " + platform.maxLength + " characters).");
        }

        PlatformApi api = ApiFactory.create(platform);
        if (!api.isValidUsername(username)) {
            return ValidationResult.ofInvalid(
                    "Invalid characters for " + platform.displayName + ".");
        }

        return ValidationResult.ofValid();
    }

    // ── Metadados ──────────────────────────────────────────────────────

    public List<Platform> getAvailablePlatforms() {
        return Arrays.stream(Platform.values())
                .filter(this::isAvailable)
                .toList();
    }

    public List<Platform> getByGroup(Platform.Group group) {
        return Arrays.stream(Platform.values())
                .filter(p -> p.group == group)
                .toList();
    }

    public int getRecommendedDelayMs(Platform platform) {
        return ApiFactory.create(platform).getRecommendedDelayMs();
    }

    // ── ValidationResult ───────────────────────────────────────────────

    /**
     * FIX 1: 'static record' — obrigatório para permitir membros estáticos
     * dentro de uma inner class.
     *
     * FIX 2: factory methods renomeados de valid()/invalid() para
     * ofValid()/ofInvalid() — evita colisão de nome com o accessor
     * gerado automaticamente pelo record para o campo 'valid'.
     *
     * Sem o rename, dentro do record 'valid()' é ambíguo: o compilador
     * não sabe se é o accessor do campo boolean ou uma chamada recursiva
     * ao factory method estático. Isso causava em cascata:
     *   - "Modifier 'static' not allowed here"
     *   - "Operator '!' cannot be applied to ValidationResult"
     *
     * FIX 3: isInvalid() usa '!valid' (campo direto) em vez de '!valid()'
     * (chamada de método) — elimina qualquer ambiguidade residual.
     *
     * Uso em outros arquivos — atualize as chamadas:
     *   ValidationResult.valid()   → ValidationResult.ofValid()
     *   ValidationResult.invalid() → ValidationResult.ofInvalid("msg")
     */
    public static record ValidationResult(boolean valid, String errorMessage) {

        public static ValidationResult ofValid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult ofInvalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isInvalid() { return !valid; }
    }
}