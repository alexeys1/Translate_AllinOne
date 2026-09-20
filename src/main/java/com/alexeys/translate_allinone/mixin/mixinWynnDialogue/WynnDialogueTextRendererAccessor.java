package com.alexeys.translate_allinone.mixin.mixinWynnDialogue;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Font.class)
public interface WynnDialogueTextRendererAccessor {
    @Accessor("provider")
    Font.Provider translate_allinone$getGlyphsProvider();
}
