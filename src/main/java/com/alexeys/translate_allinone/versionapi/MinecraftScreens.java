package com.alexeys.translate_allinone.versionapi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class MinecraftScreens implements Screens<Screen> {
    public static final MinecraftScreens INSTANCE = new MinecraftScreens();

    private MinecraftScreens() {
    }

    @Override
    public void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
