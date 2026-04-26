package com.aliasforge.model;

/**
 * Plataformas suportadas para verificação de usernames.
 */
public enum Platform {
    DISCORD("discord",   3, 32),
    MINECRAFT("minecraft", 3, 16),
    ROBLOX("roblox",    3, 20),
    INSTAGRAM("instagram", 1, 30);

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