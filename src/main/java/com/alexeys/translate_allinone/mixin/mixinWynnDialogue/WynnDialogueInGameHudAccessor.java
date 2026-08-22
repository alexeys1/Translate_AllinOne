package com.alexeys.translate_allinone.mixin.mixinWynnDialogue;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Hud.class)
public interface WynnDialogueInGameHudAccessor {
    @Accessor("overlayMessageString")
    Component translate_allinone$getOverlayMessage();

    @Accessor("overlayMessageString")
    void translate_allinone$setOverlayMessage(Component text);

    @Accessor("overlayMessageTime")
    int translate_allinone$getOverlayRemaining();
}
