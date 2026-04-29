package com.cedarxuesong.translate_allinone.mixin.mixinItem;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipInternalLineSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRecentRenderGuardSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRefreshNoticeSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTextDebugCopySupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTextMatcherSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationContext;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationSupport;
import net.fabricmc.loader.api.FabricLoader;
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
@Mixin(
        targets = {
                "com.wynnmod.feature.item.wynn.StatsTooltipFeature",
                "com.wynnmod.feature.item.wynn.DecorateTooltipFeature"
        },
        remap = false
)
public abstract class WynnmodStatsTooltipContextMixin {
    @Unique
    private static final String ITEM_STATUS_ANIMATION_KEY = "item-tooltip-status-wynnmod";

    @Unique
    private record DecoratedTooltipTranslationResult(
            List<Text> tooltip,
            boolean locallyStableForRecentGuard
    ) {
    }

    @Inject(
            method = "onDrawItemTooltip(Lcom/wynnmod/mixin/events/ContainerEvents$DrawItemTooltip;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void translate_allinone$prepareWynnmodTooltip(@Coerce Object event, CallbackInfo ci) {
        if (!translate_allinone$shouldProcessWynnmodTooltip()) {
            TooltipTextMatcherSupport.logTooltipGuardIfDev(
                    Translate_AllinOne.getConfig().itemTranslate,
                    "wynnmod",
                    "skip-tracking-disabled-head",
                    null,
                    "Wynnmod tooltip tracking is disabled or the feature is unavailable."
            );
            return;
        }
        TooltipTranslationContext.pushWynnmodTooltipRender();
        TooltipTranslationContext.setSkipDrawContextTranslation(false);
    }

    @Inject(
            method = "onDrawItemTooltip(Lcom/wynnmod/mixin/events/ContainerEvents$DrawItemTooltip;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void translate_allinone$translateDecoratedWynnmodTooltip(@Coerce Object event, CallbackInfo ci) {
        boolean processingWynnmodTooltip = translate_allinone$shouldProcessWynnmodTooltip();
        boolean usingWynnmodTooltipTracking = translate_allinone$shouldUseWynnmodTooltipTracking();
        try {
            if (!processingWynnmodTooltip) {
                TooltipTextMatcherSupport.logTooltipGuardIfDev(
                        Translate_AllinOne.getConfig().itemTranslate,
                        "wynnmod",
                        "skip-tracking-disabled-return",
                        null,
                        "Wynnmod tooltip tracking is disabled or the feature is unavailable."
                );
                return;
            }

            ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
            List<Text> currentTooltip = translate_allinone$getTooltipText(event);
            if (currentTooltip == null || currentTooltip.isEmpty()) {
                TooltipTextMatcherSupport.logTooltipGuardIfDev(
                        config,
                        "wynnmod",
                        "skip-empty-tooltip",
                        currentTooltip,
                        "Wynnmod event did not expose any tooltip text."
                );
                return;
            }

            List<Text> sanitizedTooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(currentTooltip);
            TooltipTextDebugCopySupport.maybeCopyCurrentTooltip(sanitizedTooltip);
            if (!usingWynnmodTooltipTracking) {
                return;
            }
            TooltipRefreshNoticeSupport.maybeForceRefreshCurrentTooltip(sanitizedTooltip, config);
            boolean showRefreshNotice = TooltipRefreshNoticeSupport.shouldShowRefreshNotice(sanitizedTooltip, config);

            boolean isKeyPressed = KeybindingManager.isPressed(config.keybinding.binding);
            if (TooltipTranslationSupport.shouldShowOriginal(config.keybinding.mode, isKeyPressed)) {
                TooltipTextMatcherSupport.logTooltipGuardIfDev(
                        config,
                        "wynnmod",
                        "skip-show-original",
                        sanitizedTooltip,
                        "Configured keybinding mode is currently showing the original tooltip. showRefreshNotice="
                                + showRefreshNotice
                );
                List<Text> tooltipToDisplay = TooltipRefreshNoticeSupport.appendRefreshNoticeLine(sanitizedTooltip, showRefreshNotice);
                if (tooltipToDisplay != currentTooltip && translate_allinone$setTooltipText(event, tooltipToDisplay)) {
                    TooltipRecentRenderGuardSupport.clearRememberedTooltip();
                    TooltipTranslationContext.rememberExpectedDrawContextTooltip(tooltipToDisplay);
                    TooltipTranslationContext.setSkipDrawContextTranslation(true);
                }
                return;
            }

            TooltipRefreshNoticeSupport.queueRemoteTranslationForCurrentTooltip(sanitizedTooltip, config);
            if (TooltipRecentRenderGuardSupport.shouldSkipDuplicateRender(sanitizedTooltip, showRefreshNotice)) {
                TooltipTextMatcherSupport.logTooltipGuardIfDev(
                        config,
                        "wynnmod",
                        "skip-duplicate-render",
                        sanitizedTooltip,
                        "Recent translated tooltip guard matched. showRefreshNotice=" + showRefreshNotice
                );
                TooltipTranslationContext.rememberExpectedDrawContextTooltip(currentTooltip);
                TooltipTranslationContext.setSkipDrawContextTranslation(true);
                return;
            }

            boolean emitDevLog = TooltipTextMatcherSupport.beginTooltipDevPass(config, "wynnmod", sanitizedTooltip);
            long tooltipStartedAtNanos = emitDevLog ? System.nanoTime() : 0L;
            DecoratedTooltipTranslationResult translatedTooltipResult = translate_allinone$translateDecoratedTooltip(
                    sanitizedTooltip,
                    config,
                    showRefreshNotice,
                    emitDevLog,
                    tooltipStartedAtNanos
            );
            if (translate_allinone$setTooltipText(event, translatedTooltipResult.tooltip())) {
                TooltipRecentRenderGuardSupport.rememberTooltipIfStable(
                        translatedTooltipResult.tooltip(),
                        translatedTooltipResult.locallyStableForRecentGuard()
                );
                TooltipTranslationContext.rememberExpectedDrawContextTooltip(translatedTooltipResult.tooltip());
                TooltipTranslationContext.setSkipDrawContextTranslation(true);
            }
        } finally {
            if (processingWynnmodTooltip) {
                TooltipTranslationContext.popWynnmodTooltipRender();
            }
        }
    }

    @Unique
    private static boolean translate_allinone$shouldProcessWynnmodTooltip() {
        return FabricLoader.getInstance().isModLoaded("wynnmod")
                && (translate_allinone$shouldUseWynnmodTooltipTracking() || TooltipTextDebugCopySupport.isEnabled());
    }

    @Unique
    private static boolean translate_allinone$shouldUseWynnmodTooltipTracking() {
        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        return config != null
                && config.enabled
                && FabricLoader.getInstance().isModLoaded("wynnmod");
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
    private static DecoratedTooltipTranslationResult translate_allinone$translateDecoratedTooltip(
            List<Text> currentTooltip,
            ItemTranslateConfig config,
            boolean showRefreshNotice,
            boolean emitDevLog,
            long tooltipStartedAtNanos
    ) {
        List<Text> tooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(currentTooltip);
        TooltipTranslationSupport.TooltipProcessingResult processedTooltip = TooltipTranslationSupport.processTooltipLines(
                tooltip,
                config,
                true,
                emitDevLog,
                "wynnmod"
        );
        List<Text> translatedTooltip = TooltipInternalLineSupport.appendStatusLineIfNeeded(
                new ArrayList<>(processedTooltip.translatedLines()),
                processedTooltip,
                ITEM_STATUS_ANIMATION_KEY
        );
        translatedTooltip = TooltipRefreshNoticeSupport.appendRefreshNoticeLine(translatedTooltip, showRefreshNotice);
        boolean locallyStableForRecentGuard = !processedTooltip.pending() && !processedTooltip.missingKeyIssue();

        TooltipTextMatcherSupport.logTooltipPassIfDev(
                config,
                emitDevLog,
                "wynnmod",
                tooltip.size(),
                processedTooltip.translatableLines(),
                tooltipStartedAtNanos
        );
        return new DecoratedTooltipTranslationResult(translatedTooltip, locallyStableForRecentGuard);
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
