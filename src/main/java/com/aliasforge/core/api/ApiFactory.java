package com.aliasforge.core.api;

import com.aliasforge.model.Platform;

/**
 * Fábrica de APIs das plataformas.
 */
public class ApiFactory {

    private ApiFactory() {}

    public static PlatformApi create(Platform platform) {
        return switch (platform) {

            case MINECRAFT -> new MinecraftApi();
            case CUSTOM    -> new CustomApi();

            case GITHUB    -> new GitHubApi();
            case REDDIT    -> new RedditApi();
            case GUNS_LOL  -> new GunsLolApi();
            case CALIBER   -> new CaliberApi();
            case TIKTOK    -> new TikTokApi();
            case INSTAGRAM -> new InstagramApi();
        };
    }
}