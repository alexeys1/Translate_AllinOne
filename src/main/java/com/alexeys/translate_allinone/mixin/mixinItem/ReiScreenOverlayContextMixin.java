package com.alexeys.translate_allinone.mixin.mixinItem;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.alexeys.translate_allinone.utils.translate.TooltipRecentRenderGuardSupport;
import com.alexeys.translate_allinone.utils.translate.TooltipTextDebugCopySupport;
import com.alexeys.translate_allinone.utils.translate.TooltipTranslationContext;
import com.alexeys.translate_allinone.utils.translate.TooltipTranslationSupport;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "me.shedaniel.rei.impl.client.gui.ScreenOverlayImpl", remap = false)
public abstract class ReiScreenOverlayContextMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/ReiScreenOverlayContextMixin");

    @Unique
    private static final String ITEM_STATUS_ANIMATION_KEY = "item-tooltip-status-rei";

    @Unique
    private static final ThreadLocal<Boolean> translate_allinone$isProcessingReiTooltip =
            ThreadLocal.withInitial(() -> false);

    @Unique
    private static volatile Method translate_allinone$tooltipEntryTextFactoryMethod;

    @Unique
    private static volatile boolean translate_allinone$tooltipEntryTextFactoryInitialized = false;

    @Unique
    private static volatile boolean translate_allinone$loggedReiTooltipHook = false;

    @Inject(
            method = {
                    "renderTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lme/shedaniel/rei/api/client/gui/widgets/Tooltip;)V",
                    "renderTooltip(Lme/shedaniel/rei/api/client/gui/compat/GuiGraphics;Lme/shedaniel/rei/api/client/gui/widgets/Tooltip;)V",
                    "renderTooltip(Lnet/minecraft/client/gui/DrawContext;Lme/shedaniel/rei/api/client/gui/widgets/Tooltip;)V",
                    "renderTooltip(Lnet/minecraft/class_332;Lme/shedaniel/rei/api/client/gui/widgets/Tooltip;)V"
            },
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void translate_allinone$pushReiTooltipContext(
            @Coerce Object graphics,
            @Coerce Object tooltip,
            CallbackInfo ci
    ) {
        TooltipTranslationContext.pushReiTooltipRender();
        translate_allinone$translateReiTooltipEntries(tooltip);
    }

    @Inject(
            method = {
                    "renderTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lme/shedaniel/rei/api/client/gui/widgets/Tooltip;)V",
                    "renderTooltip(Lme/shedaniel/rei/api/client/gui/compat/GuiGraphics;Lme/shedaniel/rei/api/client/gui/widgets/Tooltip;)V",
                    "renderTooltip(Lnet/minecraft/client/gui/DrawContext;Lme/shedaniel/rei/api/client/gui/widgets/Tooltip;)V",
                    "renderTooltip(Lnet/minecraft/class_332;Lme/shedaniel/rei/api/client/gui/widgets/Tooltip;)V"
            },
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void translate_allinone$popReiTooltipContext(
            @Coerce Object graphics,
            @Coerce Object tooltip,
            CallbackInfo ci
    ) {
        TooltipTranslationContext.popReiTooltipRender();
    }

    @Unique
    private static void translate_allinone$translateReiTooltipEntries(Object tooltip) {
        if (tooltip == null || translate_allinone$isProcessingReiTooltip.get()) {
            return;
        }

        try {
            translate_allinone$isProcessingReiTooltip.set(true);
            List<Component> tooltipLines = translate_allinone$getReiTooltipTextLines(tooltip);
            translate_allinone$logReiTooltipHookIfDev(tooltip, tooltipLines);
            TooltipTextDebugCopySupport.maybeCopyCurrentTooltip(tooltipLines);
            if (tooltipLines == null || tooltipLines.isEmpty()) {
                return;
            }

            TooltipTranslationSupport.TranslatedTooltipBuildResult translatedTooltipResult =
                    TooltipTranslationSupport.buildTranslatedTooltipResult(tooltipLines, ITEM_STATUS_ANIMATION_KEY);
            List<Component> translatedTooltip = translatedTooltipResult.translatedTooltip();
            if (translate_allinone$sameTooltipContent(tooltipLines, translatedTooltip)) {
                return;
            }

            if (translate_allinone$replaceReiTooltipTextEntries(tooltip, translatedTooltip)) {
                TooltipRecentRenderGuardSupport.rememberTooltipIfStable(
                        translatedTooltip,
                        translatedTooltipResult.locallyStableForRecentGuard()
                );
                TooltipTranslationContext.rememberExpectedDrawContextTooltip(translatedTooltip);
                TooltipTranslationContext.setSkipDrawContextTranslation(true);
            }
        } catch (RuntimeException e) {
            LOGGER.debug("Failed to translate REI tooltip entries", e);
        } finally {
            translate_allinone$isProcessingReiTooltip.set(false);
        }
    }

    @Unique
    private static void translate_allinone$logReiTooltipHookIfDev(Object tooltip, List<Component> tooltipLines) {
        if (translate_allinone$loggedReiTooltipHook) {
            return;
        }

        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        if (config == null || config.debug == null || !config.debug.enabled) {
            return;
        }

        translate_allinone$loggedReiTooltipHook = true;
        LOGGER.info(
                "[ItemDev:rei] tooltip-hook tooltipClass=\"{}\" textLines={}",
                tooltip == null ? "null" : tooltip.getClass().getName(),
                tooltipLines == null ? 0 : tooltipLines.size()
        );
    }

    @Unique
    private static List<Component> translate_allinone$getReiTooltipTextLines(Object tooltip) {
        List<?> entries = translate_allinone$getReiTooltipEntries(tooltip);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<Component> tooltipLines = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            Component text = translate_allinone$getReiTooltipEntryText(entry);
            if (text != null) {
                tooltipLines.add(text);
            }
        }
        return tooltipLines;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static List<Object> translate_allinone$getReiTooltipEntries(Object tooltip) {
        if (tooltip == null) {
            return null;
        }

        try {
            Method method = tooltip.getClass().getMethod("entries");
            Object result = method.invoke(tooltip);
            if (result instanceof List<?> list) {
                return (List<Object>) list;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }

        return null;
    }

    @Unique
    private static Component translate_allinone$getReiTooltipEntryText(Object entry) {
        if (entry == null) {
            return null;
        }

        try {
            Method isTextMethod = entry.getClass().getMethod("isText");
            if (!Boolean.TRUE.equals(isTextMethod.invoke(entry))) {
                return null;
            }

            Method getAsTextMethod = entry.getClass().getMethod("getAsText");
            Object result = getAsTextMethod.invoke(entry);
            return result instanceof Component text ? text : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    @Unique
    private static boolean translate_allinone$replaceReiTooltipTextEntries(Object tooltip, List<Component> translatedTooltip) {
        List<Object> entries = translate_allinone$getReiTooltipEntries(tooltip);
        if (entries == null || entries.isEmpty() || translatedTooltip == null) {
            return false;
        }

        List<Object> originalEntries = new ArrayList<>(entries);
        List<Object> replacementEntries = new ArrayList<>(originalEntries.size() + translatedTooltip.size());
        int translatedIndex = 0;
        for (Object entry : originalEntries) {
            if (translate_allinone$getReiTooltipEntryText(entry) == null) {
                replacementEntries.add(entry);
                continue;
            }

            if (translatedIndex >= translatedTooltip.size()) {
                replacementEntries.add(entry);
                continue;
            }

            Object replacementEntry = translate_allinone$createReiTooltipTextEntry(translatedTooltip.get(translatedIndex));
            if (replacementEntry == null) {
                return false;
            }
            replacementEntries.add(replacementEntry);
            translatedIndex++;
        }

        while (translatedIndex < translatedTooltip.size()) {
            Object replacementEntry = translate_allinone$createReiTooltipTextEntry(translatedTooltip.get(translatedIndex));
            if (replacementEntry == null) {
                return false;
            }
            replacementEntries.add(replacementEntry);
            translatedIndex++;
        }

        entries.clear();
        entries.addAll(replacementEntries);
        return true;
    }

    @Unique
    private static Object translate_allinone$createReiTooltipTextEntry(Component text) {
        if (text == null) {
            return null;
        }

        Method factoryMethod = translate_allinone$getTooltipEntryTextFactoryMethod();
        if (factoryMethod == null) {
            return null;
        }

        try {
            return factoryMethod.invoke(null, text);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    @Unique
    private static Method translate_allinone$getTooltipEntryTextFactoryMethod() {
        if (translate_allinone$tooltipEntryTextFactoryInitialized) {
            return translate_allinone$tooltipEntryTextFactoryMethod;
        }

        translate_allinone$tooltipEntryTextFactoryInitialized = true;
        try {
            Class<?> tooltipClass = Class.forName("me.shedaniel.rei.api.client.gui.widgets.Tooltip");
            Method method = tooltipClass.getMethod("entry", Component.class);
            translate_allinone$tooltipEntryTextFactoryMethod = method;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            translate_allinone$tooltipEntryTextFactoryMethod = null;
        }
        return translate_allinone$tooltipEntryTextFactoryMethod;
    }

    @Unique
    private static boolean translate_allinone$sameTooltipContent(List<Component> left, List<Component> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }

        for (int i = 0; i < left.size(); i++) {
            Component leftLine = left.get(i);
            Component rightLine = right.get(i);
            String leftValue = leftLine == null ? "" : leftLine.getString();
            String rightValue = rightLine == null ? "" : rightLine.getString();
            if (!leftValue.equals(rightValue)) {
                return false;
            }
        }
        return true;
    }
}
