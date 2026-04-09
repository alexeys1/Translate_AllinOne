package com.cedarxuesong.translate_allinone.mixin.mixinItem;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationContext;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationSupport;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

@Pseudo
@Mixin(targets = "com.wynnmod.feature.item.wynn.StatsTooltipFeature", remap = false)
public abstract class WynnmodStatsTooltipContextMixin {
    @Unique
    private static final String ITEM_STATUS_ANIMATION_KEY = "item-tooltip-status-wynnmod";

    @Unique
    private static final ThreadLocal<List<String>> translate_allinone$originalTooltipSnapshot = new ThreadLocal<>();

    @Inject(
            method = "onDrawItemTooltip(Lcom/wynnmod/mixin/events/ContainerEvents$DrawItemTooltip;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void translate_allinone$prepareWynnmodTooltip(@Coerce Object event, CallbackInfo ci) {
        TooltipTranslationContext.setSkipDrawContextTranslation(false);
        translate_allinone$originalTooltipSnapshot.set(translate_allinone$captureTooltipLines(event));
    }

    @Inject(
            method = "onDrawItemTooltip(Lcom/wynnmod/mixin/events/ContainerEvents$DrawItemTooltip;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void translate_allinone$translateDecoratedWynnmodTooltip(@Coerce Object event, CallbackInfo ci) {
        try {
            ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
            if (!config.enabled || !config.wynn_item_compatibility) {
                return;
            }

            boolean isKeyPressed = KeybindingManager.isPressed(config.keybinding.binding);
            if (TooltipTranslationSupport.shouldShowOriginal(config.keybinding.mode, isKeyPressed)) {
                return;
            }

            List<Text> currentTooltip = translate_allinone$getTooltipText(event);
            if (currentTooltip == null || currentTooltip.isEmpty()) {
                return;
            }

            List<Text> translatedTooltip = translate_allinone$translateDecoratedTooltip(currentTooltip, config);
            if (translatedTooltip == null || translatedTooltip == currentTooltip) {
                return;
            }

            if (translate_allinone$setTooltipText(event, translatedTooltip)) {
                TooltipTranslationContext.setSkipDrawContextTranslation(true);
            }
        } finally {
            translate_allinone$originalTooltipSnapshot.remove();
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static List<Text> translate_allinone$getTooltipText(Object event) {
        if (event == null) {
            return null;
        }

        try {
            Method method = event.getClass().getMethod("getText");
            Object result = method.invoke(event);
            if (result instanceof List<?> list) {
                return (List<Text>) list;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    @Unique
    private static List<String> translate_allinone$captureTooltipLines(Object event) {
        List<Text> tooltip = translate_allinone$getTooltipText(event);
        if (tooltip == null || tooltip.isEmpty()) {
            return List.of();
        }

        List<String> snapshot = new ArrayList<>(tooltip.size());
        for (Text line : tooltip) {
            snapshot.add(line == null ? null : line.getString());
        }
        return snapshot;
    }

    @Unique
    private static List<Text> translate_allinone$translateDecoratedTooltip(List<Text> currentTooltip, ItemTranslateConfig config) {
        List<String> originalSnapshot = translate_allinone$originalTooltipSnapshot.get();
        if (originalSnapshot == null) {
            originalSnapshot = List.of();
        }

        List<Text> translatedTooltip = new ArrayList<>(currentTooltip.size() + 1);
        boolean isFirstLine = true;
        boolean changed = false;
        boolean isCurrentItemStackPending = false;
        boolean hasMissingKeyIssue = false;

        for (int i = 0; i < currentTooltip.size(); i++) {
            Text line = currentTooltip.get(i);
            if (translate_allinone$isInternalStatusLine(line)) {
                changed = true;
                continue;
            }

            boolean shouldTranslate = false;
            if (line != null && !line.getString().trim().isEmpty()) {
                if (isFirstLine) {
                    shouldTranslate = config.enabled_translate_item_custom_name;
                    isFirstLine = false;
                } else {
                    shouldTranslate = config.enabled_translate_item_lore;
                }
            }

            if (!shouldTranslate || translate_allinone$isUnchangedOriginalLine(originalSnapshot, i, line)) {
                translatedTooltip.add(line);
                continue;
            }

            TooltipTranslationSupport.TooltipLineResult lineResult = TooltipTranslationSupport.translateLine(line, true);
            translatedTooltip.add(lineResult.translatedLine());
            changed = true;
            if (lineResult.pending()) {
                isCurrentItemStackPending = true;
            }
            if (lineResult.missingKeyIssue()) {
                hasMissingKeyIssue = true;
            }
        }

        ItemTemplateCache.CacheStats stats = ItemTemplateCache.getInstance().getCacheStats();
        boolean isAnythingPending = stats.total() > stats.translated();
        boolean shouldShowStatus = isCurrentItemStackPending || hasMissingKeyIssue || isAnythingPending;
        if (shouldShowStatus) {
            translatedTooltip.add(TooltipTranslationSupport.createStatusLine(
                    stats,
                    hasMissingKeyIssue,
                    ITEM_STATUS_ANIMATION_KEY
            ));
            changed = true;
        }

        return changed ? translatedTooltip : currentTooltip;
    }

    @Unique
    private static boolean translate_allinone$isUnchangedOriginalLine(List<String> originalSnapshot, int index, Text line) {
        if (index >= originalSnapshot.size()) {
            return false;
        }
        return Objects.equals(originalSnapshot.get(index), line == null ? null : line.getString());
    }

    @Unique
    private static boolean translate_allinone$isInternalStatusLine(Text line) {
        if (line == null) {
            return false;
        }

        String content = line.getString();
        return content.startsWith("Translating...")
                || content.startsWith("Item translation key mismatch, retrying...");
    }

    @Unique
    private static boolean translate_allinone$setTooltipText(Object event, List<Text> tooltip) {
        if (event == null) {
            return false;
        }

        try {
            Method method = event.getClass().getMethod("setText", List.class);
            method.invoke(event, tooltip);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
