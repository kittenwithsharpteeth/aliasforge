package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

/**
 * Fábrica de implementações de PlatformApi.
 */
public class ApiFactory {

    private ApiFactory() {}

    public static PlatformApi create(Platform platform) {
        return switch (platform) {
            case MINECRAFT -> new MinecraftApi();
            case DISCORD   -> new DiscordApi();
            case ROBLOX    -> new RobloxApi();
            case INSTAGRAM -> new InstagramApi();
        };
    }
}