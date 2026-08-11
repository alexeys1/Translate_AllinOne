package com.cedarxuesong.translate_allinone.mixin.mixinWynnDialogue;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InGameHud.class)
public interface WynnDialogueInGameHudAccessor {
    @Accessor("overlayMessage")
    Text translate_allinone$getOverlayMessage();

    @Accessor("overlayMessage")
    void translate_allinone$setOverlayMessage(Text text);

    @Accessor("overlayRemaining")
    int translate_allinone$getOverlayRemaining();
}
