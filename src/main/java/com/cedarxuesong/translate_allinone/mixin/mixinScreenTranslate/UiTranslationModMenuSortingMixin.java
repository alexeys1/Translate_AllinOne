package com.cedarxuesong.translate_allinone.mixin.mixinScreenTranslate;

import com.cedarxuesong.translate_allinone.utils.translate.UiTextRole;
import com.cedarxuesong.translate_allinone.utils.translate.UiTranslationRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Locale;

@Pseudo
@Mixin(targets = "com.terraformersmc.modmenu.config.ModMenuConfig$Sorting", remap = false)
public abstract class UiTranslationModMenuSortingMixin {
    @Inject(method = "getComparator", at = @At("RETURN"), cancellable = true, require = 0)
    private void translate_allinone$sortByScreenTranslation(CallbackInfoReturnable<Comparator> cir) {
        Comparator<?> original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        String mode = enumName((Object) this);
        if (!"ASCENDING".equals(mode) && !"DESCENDING".equals(mode)) {
            return;
        }

        Comparator<Object> translatedComparator = Comparator.comparing(
                (Object mod) -> translatedName(mod).toLowerCase(Locale.ROOT)
        );
        cir.setReturnValue("DESCENDING".equals(mode) ? translatedComparator.reversed() : translatedComparator);
    }

    private static String enumName(Object sorting) {
        try {
            Method name = sorting.getClass().getMethod("name");
            Object value = name.invoke(sorting);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException | RuntimeException error) {
            return "";
        }
    }

    private static String translatedName(Object mod) {
        try {
            Method getTranslatedName = mod.getClass().getMethod("getTranslatedName");
            Object value = getTranslatedName.invoke(mod);
            String base = value == null ? "" : value.toString();
            return UiTranslationRuntime.translateStringInCurrentScreen(base, UiTextRole.OPTION);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return "";
        }
    }
}