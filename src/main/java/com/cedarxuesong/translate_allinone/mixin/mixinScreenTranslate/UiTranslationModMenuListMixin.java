package com.cedarxuesong.translate_allinone.mixin.mixinScreenTranslate;

import com.cedarxuesong.translate_allinone.utils.translate.UiTranslationRuntime;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.terraformersmc.modmenu.gui.widget.ModListWidget", remap = false)
public abstract class UiTranslationModMenuListMixin {
    @Unique
    private int translate_allinone$lastScreenTranslationVersion = -1;

    @Inject(
            method = "extractListItems(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$scheduleReloadAfterTranslation(CallbackInfo ci) {
        int version = UiTranslationRuntime.screenTranslationVersion();
        if (version == translate_allinone$lastScreenTranslationVersion) {
            return;
        }
        translate_allinone$lastScreenTranslationVersion = version;

        Object list = this;
        Minecraft.getInstance().execute(() -> {
            try {
                list.getClass().getMethod("reloadFilters").invoke(list);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        });
    }
}