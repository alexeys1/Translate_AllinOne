package com.cedarxuesong.translate_allinone.mixin.mixinItem;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTextMatcherSupport;
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

@Pseudo
@Mixin(targets = "com.wynnmod.feature.item.wynn.StatsTooltipFeature", remap = false)
public abstract class WynnmodStatsTooltipContextMixin {
    @Unique
    private static final String ITEM_STATUS_ANIMATION_KEY = "item-tooltip-status-wynnmod";

    @Inject(
            method = "onDrawItemTooltip(Lcom/wynnmod/mixin/events/ContainerEvents$DrawItemTooltip;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void translate_allinone$prepareWynnmodTooltip(@Coerce Object event, CallbackInfo ci) {
        TooltipTranslationContext.setSkipDrawContextTranslation(false);
    }

    @Inject(
            method = "onDrawItemTooltip(Lcom/wynnmod/mixin/events/ContainerEvents$DrawItemTooltip;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void translate_allinone$translateDecoratedWynnmodTooltip(@Coerce Object event, CallbackInfo ci) {
        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        if (!config.enabled || !config.wynn_item_compatibility) {
            return;
        }

        List<Text> currentTooltip = translate_allinone$getTooltipText(event);
        if (currentTooltip == null || currentTooltip.isEmpty()) {
            return;
        }

        List<Text> sanitizedTooltip = TooltipTranslationSupport.stripInternalGeneratedLines(currentTooltip);
        TooltipTranslationSupport.maybeForceRefreshCurrentTooltip(sanitizedTooltip, config);
        boolean showRefreshNotice = TooltipTranslationSupport.shouldShowRefreshNotice(sanitizedTooltip, config);

        boolean isKeyPressed = KeybindingManager.isPressed(config.keybinding.binding);
        if (TooltipTranslationSupport.shouldShowOriginal(config.keybinding.mode, isKeyPressed)) {
            List<Text> tooltipToDisplay = TooltipTranslationSupport.appendRefreshNoticeLine(sanitizedTooltip, showRefreshNotice);
            if (tooltipToDisplay != currentTooltip && translate_allinone$setTooltipText(event, tooltipToDisplay)) {
                TooltipTranslationContext.setSkipDrawContextTranslation(true);
            }
            return;
        }

        boolean emitDevLog = TooltipTextMatcherSupport.beginTooltipDevPass(config, "wynnmod", sanitizedTooltip);
        long tooltipStartedAtNanos = emitDevLog ? System.nanoTime() : 0L;
        List<Text> translatedTooltip = translate_allinone$translateDecoratedTooltip(
                sanitizedTooltip,
                config,
                showRefreshNotice,
                emitDevLog,
                tooltipStartedAtNanos
        );
        if (translate_allinone$setTooltipText(event, translatedTooltip)) {
            TooltipTranslationContext.setSkipDrawContextTranslation(true);
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
    private static List<Text> translate_allinone$translateDecoratedTooltip(
            List<Text> currentTooltip,
            ItemTranslateConfig config,
            boolean showRefreshNotice,
            boolean emitDevLog,
            long tooltipStartedAtNanos
    ) {
        List<Text> tooltip = TooltipTranslationSupport.stripInternalGeneratedLines(currentTooltip);
        TooltipTranslationSupport.TooltipProcessingResult processedTooltip = TooltipTranslationSupport.processTooltipLines(
                tooltip,
                config,
                true,
                emitDevLog,
                "wynnmod"
        );
        List<Text> translatedTooltip = new ArrayList<>(processedTooltip.translatedLines());

        ItemTemplateCache.CacheStats stats = ItemTemplateCache.getInstance().getCacheStats();
        boolean isAnythingPending = stats.total() > stats.translated();
        boolean shouldShowStatus = processedTooltip.pending() || processedTooltip.missingKeyIssue() || isAnythingPending;
        if (shouldShowStatus) {
            translatedTooltip.add(TooltipTranslationSupport.createStatusLine(
                    stats,
                    processedTooltip.missingKeyIssue(),
                    ITEM_STATUS_ANIMATION_KEY
            ));
        }

        if (showRefreshNotice) {
            translatedTooltip.add(TooltipTranslationSupport.createRefreshNoticeLine());
        }

        TooltipTextMatcherSupport.logTooltipPassIfDev(
                config,
                emitDevLog,
                "wynnmod",
                tooltip.size(),
                processedTooltip.translatableLines(),
                tooltipStartedAtNanos
        );
        return translatedTooltip;
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
