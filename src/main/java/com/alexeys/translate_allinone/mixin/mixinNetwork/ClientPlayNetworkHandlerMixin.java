package com.alexeys.translate_allinone.mixin.mixinNetwork;

import com.alexeys.translate_allinone.utils.MessageUtils;
import com.alexeys.translate_allinone.utils.translate.ChatOutputTranslateManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "runClickEventCommand", at = @At("HEAD"), cancellable = true)
    private void onRunClickEventCommand(String command, Screen screen, CallbackInfo ci) {
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        String[] parts = command.split("\\s+");
        if (parts.length < 4 || !"translate_allinone".equals(parts[0]) || !"translatechatline".equals(parts[1])) {
            return;
        }

        UUID messageId;
        try {
            messageId = UUID.fromString(parts[2]);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        String action = parts[3];
        if ("restore".equalsIgnoreCase(action)) {
            ChatOutputTranslateManager.restoreOriginal(messageId);
            ci.cancel();
            return;
        }
        if ("translate".equalsIgnoreCase(action)) {
            Text originalMessage = MessageUtils.getTrackedMessage(messageId);
            if (originalMessage == null) {
                if (ChatOutputTranslateManager.handleToggleCommandWithMissingTracking(messageId, action)) {
                    ci.cancel();
                }
                return;
            }
            ChatOutputTranslateManager.translate(messageId, originalMessage);
            ci.cancel();
        }
    }
}
