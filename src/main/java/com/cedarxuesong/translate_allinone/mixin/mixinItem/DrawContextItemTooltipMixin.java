package com.cedarxuesong.translate_allinone.mixin.mixinItem;

import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationContext;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationSupport;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Screen.class)
public abstract class DrawContextItemTooltipMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/DrawContextItemTooltipMixin");

    @Unique
    private static final String ITEM_STATUS_ANIMATION_KEY = "item-tooltip-status";

    @Unique
    private static final ThreadLocal<Boolean> translate_allinone$isBuildingTooltipMirror = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "getTooltipFromItem(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void translate_allinone$mirrorTooltipForRendering(
            MinecraftClient client,
            ItemStack stack,
            CallbackInfoReturnable<List<Text>> cir
    ) {
        List<Text> originalTooltip = cir.getReturnValue();
        if (TooltipTranslationContext.isInWynnmodTooltipRender()) {
            TooltipTranslationContext.setSkipDrawContextTranslation(false);
            TooltipTranslationContext.rememberScreenMirrorTooltip(null);
            return;
        }
        List<Text> mirroredTooltip = translate_allinone$buildTooltipMirror(originalTooltip);
        TooltipTranslationContext.rememberScreenMirrorTooltip(
                mirroredTooltip == originalTooltip
                        ? null
                        : TooltipTranslationSupport.stripInternalGeneratedLines(mirroredTooltip)
        );
        TooltipTranslationContext.setSkipDrawContextTranslation(mirroredTooltip != originalTooltip);
        cir.setReturnValue(mirroredTooltip);
    }

    @Unique
    private static List<Text> translate_allinone$buildTooltipMirror(List<Text> originalTooltip) {
        if (translate_allinone$isBuildingTooltipMirror.get()) {
            return originalTooltip;
        }

        try {
            translate_allinone$isBuildingTooltipMirror.set(true);
            return TooltipTranslationSupport.buildTranslatedTooltip(originalTooltip, ITEM_STATUS_ANIMATION_KEY);
        } catch (Exception e) {
            LOGGER.error("Failed to build translated tooltip mirror", e);
            return originalTooltip;
        } finally {
            translate_allinone$isBuildingTooltipMirror.set(false);
        }
    }
}
