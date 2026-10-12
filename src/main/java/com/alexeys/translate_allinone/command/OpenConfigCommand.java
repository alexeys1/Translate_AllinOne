package com.alexeys.translate_allinone.command;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.gui.ModConfigScreen;
import com.alexeys.translate_allinone.gui.PromptEditorScreen;
import com.alexeys.translate_allinone.versionapi.MinecraftScreens;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

public final class OpenConfigCommand {
    private OpenConfigCommand() {
    }

    public static int run(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> MinecraftScreens.INSTANCE.open(new ModConfigScreen(null)));
        return 1;
    }

    public static int runWithProvider(CommandContext<FabricClientCommandSource> context) {
        String providerId = StringArgumentType.getString(context, "providerId");
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> MinecraftScreens.INSTANCE.open(new ModConfigScreen(null, providerId)));
        return 1;
    }

    public static int runPromptEditor(CommandContext<FabricClientCommandSource> context) {
        String providerId = StringArgumentType.getString(context, "providerId");
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> MinecraftScreens.INSTANCE.open(
                providerExists(providerId) ? new PromptEditorScreen(null, providerId) : new ModConfigScreen(null, providerId)
        ));
        return 1;
    }

    private static boolean providerExists(String providerId) {
        return Translate_AllinOne.getConfig().providerManager.findById(providerId) != null;
    }
}
