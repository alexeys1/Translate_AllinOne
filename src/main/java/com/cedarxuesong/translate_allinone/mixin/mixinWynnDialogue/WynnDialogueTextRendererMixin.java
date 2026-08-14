package com.cedarxuesong.translate_allinone.mixin.mixinWynnDialogue;

import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueFontFallback;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

@Mixin(TextRenderer.class)
public abstract class WynnDialogueTextRendererMixin {
    @Shadow
    @Final
    private Function<Identifier, FontStorage> fontStorageAccessor;

    @Unique
    private final Map<FontStorage, FontStorage> translate_allinone$fallbackStorages = new IdentityHashMap<>();

    @Inject(
            method = "getFontStorage(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/font/FontStorage;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void translate_allinone$resolveDialogueGlyphs(
            Identifier source,
            CallbackInfoReturnable<FontStorage> cir
    ) {
        if (!WynnDialogueFontFallback.isDialogueFont(source)) {
            return;
        }
        FontStorage target = cir.getReturnValue();
        FontStorage fallback = fontStorageAccessor.apply(Style.DEFAULT_FONT_ID);
        if (target == null || fallback == null || target == fallback) {
            return;
        }
        cir.setReturnValue(translate_allinone$fallbackStorages.computeIfAbsent(
                target,
                ignored -> WynnDialogueFontFallback.fallbackStorage(target, fallback)
        ));
    }
}
