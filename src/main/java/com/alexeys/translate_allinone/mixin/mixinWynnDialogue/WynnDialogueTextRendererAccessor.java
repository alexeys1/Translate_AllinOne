package com.alexeys.translate_allinone.mixin.mixinWynnDialogue;

import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

@Mixin(TextRenderer.class)
public interface WynnDialogueTextRendererAccessor {
    @Accessor("fontStorageAccessor")
    Function<Identifier, FontStorage> translate_allinone$getFontStorageAccessor();

    @Accessor("validateAdvance")
    boolean translate_allinone$isValidateAdvance();
}
