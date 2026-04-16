package com.cedarxuesong.translate_allinone.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class MainCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(createRoot("translate_allinone"));
        dispatcher.register(createRoot("taio"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> createRoot(String name) {
        return ClientCommandManager.literal(name)
                .executes(OpenConfigCommand::run)
                .then(ChatHudTranslateCommand.getArgumentBuilder());
    }
}
