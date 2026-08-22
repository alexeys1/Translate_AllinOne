package com.alexeys.translate_allinone.versionapi;

public final class MinecraftVersionCapabilities {
    private static final VersionCapabilities CURRENT = new VersionCapabilities(true);

    private MinecraftVersionCapabilities() {
    }

    public static VersionCapabilities current() {
        return CURRENT;
    }
}
