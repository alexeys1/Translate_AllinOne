package com.cedarxuesong.translate_allinone.mixin.mixinScreenTranslate;

import com.cedarxuesong.translate_allinone.utils.translate.UiTextRole;
import com.cedarxuesong.translate_allinone.utils.translate.UiTranslationRuntime;
import com.terraformersmc.modmenu.util.mod.Mod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.Locale;

@Pseudo
@Mixin(targets = "com.terraformersmc.modmenu.config.ModMenuConfig$Sorting", remap = false)
public abstract class UiTranslationModMenuSortingMixin {
    @Inject(method = "getComparator", at = @At("RETURN"), cancellable = true, require = 0)
    private void translate_allinone$sortByScreenTranslation(CallbackInfoReturnable<Comparator<Mod>> cir) {
        Comparator<Mod> original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        String mode = ((Enum<?>) (Object) this).name();
        if (!"ASCENDING".equals(mode) && !"DESCENDING".equals(mode)) {
            return;
        }

        Comparator<Mod> translatedComparator = Comparator.comparing(
                (Mod mod) -> translatedName(mod).toLowerCase(Locale.ROOT)
        );
        cir.setReturnValue("DESCENDING".equals(mode) ? translatedComparator.reversed() : translatedComparator);
    }

    private static String translatedName(Mod mod) {
        if (mod == null) {
            return "";
        }
        String base = mod.getTranslatedName();
        return base == null ? "" : UiTranslationRuntime.translateStringInCurrentScreen(base, UiTextRole.OPTION);
    }
}