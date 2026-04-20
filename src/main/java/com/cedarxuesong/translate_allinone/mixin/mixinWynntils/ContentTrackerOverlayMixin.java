package com.cedarxuesong.translate_allinone.mixin.mixinWynntils;

import com.cedarxuesong.translate_allinone.utils.translate.WynntilsTaskTrackerTranslationSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.wynntils.overlays.ContentTrackerOverlay", remap = false)
public abstract class ContentTrackerOverlayMixin {
    private static final String TITLE_WITH_WYNN_FONT =
            "with_font(with_color(styled_text(concat(activity_type; \" - \"; activity_name));activity_color);\"language/wynncraft\")";
    private static final String TITLE_WITH_DEFAULT_FONT =
            "with_color(styled_text(concat(activity_type; \" - \"; activity_name));activity_color)";

    @Inject(method = "getTemplate", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void translate_allinone$stripWynnFontForTranslatedTitles(CallbackInfoReturnable<String> cir) {
        if (!WynntilsTaskTrackerTranslationSupport.shouldUsePlainTitleFont()) {
            return;
        }

        String template = cir.getReturnValue();
        if (template == null || template.isEmpty()) {
            return;
        }

        cir.setReturnValue(template.replace(TITLE_WITH_WYNN_FONT, TITLE_WITH_DEFAULT_FONT));
    }
}
