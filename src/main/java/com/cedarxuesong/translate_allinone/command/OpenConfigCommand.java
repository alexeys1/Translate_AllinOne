package com.cedarxuesong.translate_allinone.command;

import com.cedarxuesong.translate_allinone.gui.ModConfigScreen;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

public final class OpenConfigCommand {
    private OpenConfigCommand() {
    }

    public static int run(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(new ModConfigScreen(null)));
        return 1;
    }
}
