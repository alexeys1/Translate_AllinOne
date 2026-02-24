package com.cedarxuesong.translate_allinone.mixin.mixinItem;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
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

import java.util.ArrayList;
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
        List<Text> mirroredTooltip = translate_allinone$buildTooltipMirror(originalTooltip);
        TooltipTranslationContext.setSkipDrawContextTranslation(mirroredTooltip != originalTooltip);
        cir.setReturnValue(mirroredTooltip);
    }

    @Unique
    private static List<Text> translate_allinone$buildTooltipMirror(List<Text> originalTooltip) {
        if (translate_allinone$isBuildingTooltipMirror.get()) {
            return originalTooltip;
        }

        if (originalTooltip == null || originalTooltip.isEmpty()) {
            return originalTooltip;
        }

        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        if (!config.enabled) {
            return originalTooltip;
        }

        boolean isKeyPressed = translate_allinone$isTranslateKeyPressed();
        if (TooltipTranslationSupport.shouldShowOriginal(config.keybinding.mode, isKeyPressed)) {
            return originalTooltip;
        }

        try {
            translate_allinone$isBuildingTooltipMirror.set(true);

            List<Text> mirroredTooltip = new ArrayList<>();
            boolean isFirstLine = true;
            int translatableLines = 0;
            boolean isCurrentItemStackPending = false;
            boolean hasMissingKeyIssue = false;

            for (Text line : originalTooltip) {
                if (line.getString().trim().isEmpty()) {
                    mirroredTooltip.add(line);
                    continue;
                }

                boolean shouldTranslate = false;
                if (isFirstLine) {
                    if (config.enabled_translate_item_custom_name) {
                        shouldTranslate = true;
                    }
                    isFirstLine = false;
                } else {
                    if (config.enabled_translate_item_lore) {
                        shouldTranslate = true;
                    }
                }

                if (!shouldTranslate) {
                    mirroredTooltip.add(line);
                    continue;
                }

                translatableLines++;

                TooltipTranslationSupport.TooltipLineResult lineResult = TooltipTranslationSupport.translateLine(line);
                if (lineResult.pending()) {
                    isCurrentItemStackPending = true;
                }
                if (lineResult.missingKeyIssue()) {
                    hasMissingKeyIssue = true;
                }

                mirroredTooltip.add(lineResult.translatedLine());
            }

            if (translatableLines > 0) {
                ItemTemplateCache.CacheStats stats = ItemTemplateCache.getInstance().getCacheStats();
                boolean isAnythingPending = stats.total() > stats.translated();
                boolean shouldShowStatus = isCurrentItemStackPending || hasMissingKeyIssue || isAnythingPending;

                if (shouldShowStatus) {
                    mirroredTooltip.add(TooltipTranslationSupport.createStatusLine(
                            stats,
                            hasMissingKeyIssue,
                            ITEM_STATUS_ANIMATION_KEY
                    ));
                }
            }

            return mirroredTooltip;
        } catch (Exception e) {
            LOGGER.error("Failed to build translated tooltip mirror", e);
            return originalTooltip;
        } finally {
            translate_allinone$isBuildingTooltipMirror.set(false);
        }
    }

    @Unique
    private static boolean translate_allinone$isTranslateKeyPressed() {
        return KeybindingManager.isPressed(Translate_AllinOne.getConfig().itemTranslate.keybinding.binding);
    }
}
