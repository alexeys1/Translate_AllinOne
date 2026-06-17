package com.cedarxuesong.translate_allinone.command;

import com.cedarxuesong.translate_allinone.utils.MessageUtils;
import com.cedarxuesong.translate_allinone.utils.translate.ChatOutputTranslateManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.util.UUID;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class ChatHudTranslateCommand {
    private static final String INVALID_MESSAGE_ID_KEY = "text.translate_allinone.command.error.invalid_message_id";
    private static final String MESSAGE_NOT_FOUND_KEY = "text.translate_allinone.command.error.message_not_found";

    public static LiteralArgumentBuilder<FabricClientCommandSource> getArgumentBuilder() {
        return literal("translatechatline")
                .then(argument("messageId", StringArgumentType.string())
                        .executes(ChatHudTranslateCommand::run)
                        .then(argument("action", StringArgumentType.word())
                                .executes(ChatHudTranslateCommand::run)));
    }

    private static int run(CommandContext<FabricClientCommandSource> context) {
        String messageIdStr = StringArgumentType.getString(context, "messageId");
        String action = "translate";
        try {
            action = StringArgumentType.getString(context, "action");
        } catch (IllegalArgumentException ignored) {
        }
        UUID messageId;
        try {
            messageId = UUID.fromString(messageIdStr);
        } catch (IllegalArgumentException e) {
            context.getSource().sendError(Text.translatable(INVALID_MESSAGE_ID_KEY, messageIdStr));
            return 0;
        }

        Text originalMessage = MessageUtils.getTrackedMessage(messageId);
        if (originalMessage == null) {
            context.getSource().sendError(Text.translatable(MESSAGE_NOT_FOUND_KEY, messageIdStr));
            return 0;
        }

        if ("restore".equalsIgnoreCase(action)) {
            ChatOutputTranslateManager.restoreOriginal(messageId);
            return 1;
        }

        ChatOutputTranslateManager.translate(messageId, originalMessage);

        return 1;
    }
}
