package com.cedarxuesong.translate_allinone.mixin.mixinWynnDialogue;

import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextRenderer.class)
public interface WynnDialogueTextRendererAccessor {
    @Accessor("fonts")
    TextRenderer.GlyphsProvider translate_allinone$getGlyphsProvider();
}
