package com.alexeys.translate_allinone.mixin.mixinWynnDialogue;

import com.alexeys.translate_allinone.utils.translate.WynnDialogueFontFallback;
import net.minecraft.client.font.GlyphProvider;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.StyleSpriteSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(TextRenderer.class)
public abstract class WynnDialogueTextRendererMixin {
    @Shadow
    @Final
    private TextRenderer.GlyphsProvider fonts;

    @Unique
    private final Map<GlyphProvider, GlyphProvider> translate_allinone$fallbackProviders = new IdentityHashMap<>();

    @Inject(method = "getGlyphs", at = @At("RETURN"), cancellable = true)
    private void translate_allinone$resolveDialogueGlyphs(
            StyleSpriteSource source,
            CallbackInfoReturnable<GlyphProvider> cir
    ) {
        if (!WynnDialogueFontFallback.isDialogueFont(source)) {
            return;
        }
        GlyphProvider target = cir.getReturnValue();
        GlyphProvider fallback = fonts.getGlyphs(StyleSpriteSource.DEFAULT);
        if (target == null || fallback == null || target == fallback) {
            return;
        }
        cir.setReturnValue(translate_allinone$fallbackProviders.computeIfAbsent(
                target,
                ignored -> WynnDialogueFontFallback.provider(fonts, net.minecraft.text.Style.EMPTY.withFont(source))
        ));
    }
}
