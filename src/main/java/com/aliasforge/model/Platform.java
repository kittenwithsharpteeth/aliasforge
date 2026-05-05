package com.aliasforge.model;

/**
 * Plataformas suportadas para verificação de usernames.
 */
public enum Platform {

    MINECRAFT("minecraft", 3, 16, Group.RELIABLE_API),
    CUSTOM("custom",       1, 32, Group.RELIABLE_API),

    GITHUB("github",       1, 39, Group.RELIABLE_API),
    REDDIT("reddit",       3, 20, Group.RELIABLE_API),

    GUNS_LOL("guns_lol",   3, 32, Group.WEB_CHECK),
    CALIBER("caliber",     3, 32, Group.WEB_CHECK),
    TIKTOK("tiktok",       2, 24, Group.WEB_CHECK),
    INSTAGRAM("instagram", 1, 30, Group.WEB_CHECK);

    public final String displayName;
    public final int minLength;
    public final int maxLength;
    public final Group group;

    Platform(String displayName, int minLength, int maxLength, Group group) {
        this.displayName = displayName;
        this.minLength   = minLength;
        this.maxLength   = maxLength;
        this.group       = group;
    }

    public static Platform fromString(String name) {
        for (Platform p : values()) {
            if (p.displayName.equalsIgnoreCase(name) || p.name().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return MINECRAFT;
    }

    @Override
    public String toString() {
        return displayName;
    }

    // ── GROUPS ─────────────────────────────────────────

    public enum Group {
        RELIABLE_API("Reliable APIs"),
        WEB_CHECK("Web Check");

        public final String label;

        Group(String label) {
            this.label = label;
        }
    }
}