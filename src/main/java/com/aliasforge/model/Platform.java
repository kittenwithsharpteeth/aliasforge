package com.aliasforge.model;

/**
 * Plataformas suportadas para verificação de usernames.
 * Apenas Minecraft está ativo. Custom permite endpoint configurável pelo usuário.
 */
public enum Platform {
    MINECRAFT("minecraft", 3, 16),
    CUSTOM("custom",       1, 32);

    public final String displayName;
    public final int    minLength;
    public final int    maxLength;

    Platform(String displayName, int minLength, int maxLength) {
        this.displayName = displayName;
        this.minLength   = minLength;
        this.maxLength   = maxLength;
    }

    public static Platform fromString(String name) {
        for (Platform p : values()) {
            if (p.displayName.equalsIgnoreCase(name)) return p;
        }
        return MINECRAFT;
    }

    @Override
    public String toString() { return displayName; }
}