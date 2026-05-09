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

        PlatformApi api = ApiFactory.create(platform);
        if (!api.isValidUsername(username)) {
            return ValidationResult.invalid(
                    "Invalid characters for " + platform.displayName + ".");
        }

        return ValidationResult.valid();
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
     * Fix: record fields são "valid" e "errorMessage".
     * Os accessors gerados pelo Java são valid() e errorMessage() — não isValid() nem isInvalid().
     * Métodos de conveniência com nomes diferentes precisam ser declarados explicitamente
     * e referenciar os accessors corretos do record.
     */
    public record ValidationResult(boolean valid, String errorMessage) {

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        // Método de conveniência — referencia o accessor valid() do record
        public boolean isInvalid() { return !valid(); }
    }
}