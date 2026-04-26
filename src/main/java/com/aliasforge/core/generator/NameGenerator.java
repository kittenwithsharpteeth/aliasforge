package com.aliasforge.core.generator;

import com.aliasforge.model.GeneratorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Gera usernames aleatórios ou pronunciáveis com base em um GeneratorConfig.
 * Thread-safe — usa Random por instância.
 */
public class NameGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NameGenerator.class);

    private static final String[] INICIOS = {
            "br", "cr", "dr", "fr", "gr", "pr", "tr",
            "bl", "cl", "fl", "gl", "pl", "sl",
            "ch", "sh", "th", "wh",
            "b", "c", "d", "f", "g", "h", "j", "k", "l", "m",
            "n", "p", "r", "s", "t", "v", "w", "x", "z"
    };

    private static final String[] VOGAIS = {
            "a", "e", "i", "o", "u",
            "ae", "ai", "ao", "au", "ei", "eu",
            "ia", "ie", "io", "oa", "oe", "ou", "ua", "ue", "ui"
    };

    private static final String[] FINAIS = {
            "", "r", "s", "l", "n", "m", "t", "k", "x", "z",
            "ck", "st", "sk", "nd", "nt", "mp", "lt", "rt", "rs", "ns", "ls"
    };

    private final Random random = new Random();

    // ── API pública ────────────────────────────────────────────────────

    /**
     * Gera uma lista de usernames com base no config.
     * Se config.isInfinite(), retorna um batch de 50 por vez.
     */
    public List<String> generate(GeneratorConfig config) {
        int qty = config.isInfinite() ? 50 : config.getQuantity();

        List<String> result = switch (config.getMode()) {
            case RANDOM      -> generateRandom(config, qty);
            case PRONOUNCEABLE -> generatePronounceable(config, qty);
        };

        LOGGER.info("Generated {} usernames (mode={}, platform={})",
                result.size(), config.getMode(), config.getPlatform());
        return result;
    }

    /**
     * Gera um único username aleatório — usado pelo checker em modo infinito.
     */
    public String generateOne(GeneratorConfig config) {
        return switch (config.getMode()) {
            case RANDOM      -> generateOneRandom(config);
            case PRONOUNCEABLE -> generateOnePronounce(config);
        };
    }

    // ── Geração aleatória ──────────────────────────────────────────────

    private List<String> generateRandom(GeneratorConfig config, int qty) {
        Set<String> result = new LinkedHashSet<>();
        int attempts = 0;
        int maxAttempts = qty * 150;

        while (result.size() < qty && attempts++ < maxAttempts) {
            String name = generateOneRandom(config);
            if (name != null) result.add(name);
        }

        if (result.size() < qty) {
            LOGGER.warn("Only generated {}/{} names after {} attempts — filters may be too strict",
                    result.size(), qty, attempts);
        }

        return new ArrayList<>(result);
    }

    private String generateOneRandom(GeneratorConfig config) {
        String charset  = config.buildCharset();
        String prefix   = config.getStartsWith().toLowerCase();
        String suffix   = config.getEndsWith().toLowerCase();
        String contains = config.getContains().toLowerCase();

        int minLen = config.getMinLength();
        int maxLen = config.getMaxLength();

        // Garante espaço mínimo
        if (minLen < prefix.length() + suffix.length()) return null;

        int target = minLen + random.nextInt(maxLen - minLen + 1);
        int middle = target - prefix.length() - suffix.length();
        if (middle < 0) return null;

        // Monta: prefixo + miolo aleatório + sufixo
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < middle; i++) {
            sb.append(charset.charAt(random.nextInt(charset.length())));
        }
        sb.append(suffix);
        String name = sb.toString();

        // Injeta "contains" se necessário
        if (!contains.isEmpty() && !name.contains(contains)) {
            int insertAt = prefix.length() +
                    random.nextInt(Math.max(1, middle - contains.length() + 1));
            if (middle < contains.length()) return null;
            name = name.substring(0, insertAt) + contains +
                    name.substring(insertAt + contains.length());
        }

        return validate(name, config) ? name : null;
    }

    // ── Geração pronunciável ───────────────────────────────────────────

    private List<String> generatePronounceable(GeneratorConfig config, int qty) {
        Set<String> result = new LinkedHashSet<>();
        int attempts = 0;
        int maxAttempts = qty * 200;

        while (result.size() < qty && attempts++ < maxAttempts) {
            String name = generateOnePronounce(config);
            if (name != null) result.add(name);
        }

        return new ArrayList<>(result);
    }

    private String generateOnePronounce(GeneratorConfig config) {
        String prefix   = config.getStartsWith().toLowerCase();
        String suffix   = config.getEndsWith().toLowerCase();
        String contains = config.getContains().toLowerCase();

        int target = config.getMinLength() +
                random.nextInt(config.getMaxLength() - config.getMinLength() + 1);

        if (target < prefix.length() + suffix.length()) return null;

        StringBuilder sb = new StringBuilder(prefix);
        int segment = 0;

        while (sb.length() < target - suffix.length() && segment++ < 40) {
            String ini = INICIOS[random.nextInt(INICIOS.length)];
            String vog = VOGAIS[random.nextInt(VOGAIS.length)];
            String fim = FINAIS[random.nextInt(FINAIS.length)];
            String syl = ini + vog + fim;

            int remaining = target - suffix.length() - sb.length();
            if (syl.length() > remaining) syl = ini + vog;
            if (syl.length() > remaining) syl = vog;
            if (syl.length() > remaining) break;

            sb.append(syl);
        }

        sb.append(suffix);
        String name = sb.toString();

        // Injeta "contains" se necessário
        if (!contains.isEmpty() && !name.contains(contains)) {
            int start = prefix.length();
            int end   = name.length() - suffix.length();
            int space = end - start;
            if (space < contains.length()) return null;
            int pos = start + random.nextInt(Math.max(1, space - contains.length() + 1));
            name = name.substring(0, pos) + contains + name.substring(pos + contains.length());
        }

        if (!hasVowel(name)) return null;
        return validate(name, config) ? name : null;
    }

    // ── Validação ──────────────────────────────────────────────────────

    private boolean validate(String name, GeneratorConfig config) {
        if (name == null || name.isEmpty()) return false;
        if (name.length() < config.getMinLength()) return false;
        if (name.length() > config.getMaxLength()) return false;

        // Valida contra limites da plataforma
        int platformMin = config.getPlatform().minLength;
        int platformMax = config.getPlatform().maxLength;
        if (name.length() < platformMin || name.length() > platformMax) return false;

        // Valida filtros de texto
        String prefix   = config.getStartsWith().toLowerCase();
        String suffix   = config.getEndsWith().toLowerCase();
        String contains = config.getContains().toLowerCase();

        if (!prefix.isEmpty()   && !name.startsWith(prefix))   return false;
        if (!suffix.isEmpty()   && !name.endsWith(suffix))     return false;
        if (!contains.isEmpty() && !name.contains(contains))   return false;

        // Valida caracteres permitidos
        String charset = config.buildCharset();
        for (char c : name.toCharArray()) {
            if (charset.indexOf(c) < 0) return false;
        }

        return true;
    }

    private boolean hasVowel(String s) {
        for (char c : s.toCharArray()) {
            if ("aeiou".indexOf(c) >= 0) return true;
        }
        return false;
    }
}