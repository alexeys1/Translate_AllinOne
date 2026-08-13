package com.cedarxuesong.translate_allinone.mixin.mixinWynnDialogue;

import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueFontFallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(Font.class)
public abstract class WynnDialogueTextRendererMixin {
    @Shadow
    @Final
    private Font.Provider provider;

    @Unique
    private final Map<GlyphSource, GlyphSource> translate_allinone$fallbackProviders = new IdentityHashMap<>();

    @Inject(method = "getGlyphSource", at = @At("RETURN"), cancellable = true)
    private void translate_allinone$resolveDialogueGlyphs(
            FontDescription source,
            CallbackInfoReturnable<GlyphSource> cir
    ) {
        if (!WynnDialogueFontFallback.isDialogueFont(source)) {
            return;
        }
        GlyphSource target = cir.getReturnValue();
        GlyphSource fallback = provider.glyphs(FontDescription.DEFAULT);
        if (target == null || fallback == null || target == fallback) {
            return;
        }
        cir.setReturnValue(translate_allinone$fallbackProviders.computeIfAbsent(
                target,
                ignored -> WynnDialogueFontFallback.provider(provider, Style.EMPTY.withFont(source))
        ));
    }
}
