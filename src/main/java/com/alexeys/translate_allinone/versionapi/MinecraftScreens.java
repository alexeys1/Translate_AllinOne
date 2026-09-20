package com.alexeys.translate_allinone.versionapi;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public final class MinecraftScreens implements Screens<Screen> {
    public static final MinecraftScreens INSTANCE = new MinecraftScreens();

    private MinecraftScreens() {
    }

    @Override
    public void open(Screen screen) {
        MinecraftClient.getInstance().setScreen(screen);
    }
}
