package com.alexeys.translate_allinone.mixin.mixinChatScreen;

import com.alexeys.translate_allinone.utils.input.ChatInputInstructionFieldRegistry;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParentElement.class)
public interface ScreenCharTypedMixin {
    @Inject(method = "charTyped(CI)Z", at = @At("HEAD"), cancellable = true)
    default void translate_allinone$forwardInstructionCharTyped(
            char chr,
            int modifiers,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if ((Object) this instanceof ChatScreen
                && ChatInputInstructionFieldRegistry.typeIntoFocusedField(chr, modifiers)) {
            cir.setReturnValue(true);
        }
    }
}
