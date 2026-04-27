package com.cedarxuesong.translate_allinone.mixin.mixinItem;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRecentRenderGuardSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTextDebugCopySupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTextMatcherSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationContext;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationSupport;
import net.fabricmc.loader.api.FabricLoader;
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

import java.lang.reflect.Method;
import java.util.List;

@Mixin(Screen.class)
public abstract class DrawContextItemTooltipMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/DrawContextItemTooltipMixin");

    @Unique
    private static final String ITEM_STATUS_ANIMATION_KEY = "item-tooltip-status";

    @Unique
    private static final ThreadLocal<Boolean> translate_allinone$isBuildingTooltipMirror = ThreadLocal.withInitial(() -> false);

    @Unique
    private static boolean translate_allinone$wynnmodFeatureReflectionInitialized = false;

    @Unique
    private static boolean translate_allinone$wynnmodFeatureReflectionAvailable = false;

    @Unique
    private static Class<?> translate_allinone$wynnmodDecorateTooltipFeatureClass;

    @Unique
    private static Method translate_allinone$wynnmodFeatureGetInstanceMethod;

    @Unique
    private static Method translate_allinone$wynnmodFeatureShouldFunctionMethod;

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
        TooltipTextDebugCopySupport.maybeCopyCurrentTooltip(originalTooltip);
        boolean useWynnmodTooltipTracking = translate_allinone$shouldUseWynnmodTooltipTracking();
        if (useWynnmodTooltipTracking && TooltipTranslationContext.isInWynnmodTooltipRender()) {
            TooltipTextMatcherSupport.logTooltipGuardIfDev(
                    Translate_AllinOne.getConfig().itemTranslate,
                    "screen-mirror",
                    "skip-inside-wynnmod-render",
                    originalTooltip,
                    "Draw-context mirror skipped because Wynnmod tooltip render is already active."
            );
            TooltipTranslationContext.setSkipDrawContextTranslation(false);
            return;
        }
        if (useWynnmodTooltipTracking) {
            ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
            // Only skip screen-mirror when Wynnmod has actually handed off this tooltip.
            java.util.Set<String> translationTemplateKeys = TooltipTranslationSupport.collectTranslationTemplateKeys(originalTooltip, config);
            if (TooltipTranslationContext.consumeSkipScreenMirrorTranslation(translationTemplateKeys)) {
                TooltipTextMatcherSupport.logTooltipGuardIfDev(
                        config,
                        "screen-mirror",
                        "skip-consume-wynnmod-handoff",
                        originalTooltip,
                        "TooltipTranslationContext.consumeSkipScreenMirrorTranslation(translationTemplateKeys) matched the expected tooltip."
                );
                return;
            }
        }
        TooltipTranslationSupport.TranslatedTooltipBuildResult mirrorResult =
                translate_allinone$buildTooltipMirror(originalTooltip);
        List<Text> mirroredTooltip = mirrorResult.translatedTooltip();
        if (useWynnmodTooltipTracking) {
            TooltipRecentRenderGuardSupport.rememberMirroredTooltip(
                    originalTooltip,
                    mirroredTooltip,
                    mirrorResult.locallyStableForRecentGuard()
            );
        }
        if (!translate_allinone$sameTooltipContent(originalTooltip, mirroredTooltip)) {
            TooltipTranslationContext.rememberExpectedDrawContextTooltip(mirroredTooltip);
            TooltipTranslationContext.setSkipDrawContextTranslation(true);
        }
        cir.setReturnValue(mirroredTooltip);
    }

    @Unique
    private static TooltipTranslationSupport.TranslatedTooltipBuildResult translate_allinone$buildTooltipMirror(List<Text> originalTooltip) {
        if (translate_allinone$isBuildingTooltipMirror.get()) {
            return new TooltipTranslationSupport.TranslatedTooltipBuildResult(originalTooltip, false);
        }

        try {
            translate_allinone$isBuildingTooltipMirror.set(true);
            return TooltipTranslationSupport.buildTranslatedTooltipResult(originalTooltip, ITEM_STATUS_ANIMATION_KEY);
        } catch (Exception e) {
            LOGGER.error("Failed to build translated tooltip mirror", e);
            return new TooltipTranslationSupport.TranslatedTooltipBuildResult(originalTooltip, false);
        } finally {
            translate_allinone$isBuildingTooltipMirror.set(false);
        }
    }

    @Unique
    private static boolean translate_allinone$shouldUseWynnmodTooltipTracking() {
        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        return config != null
                && config.enabled
                && FabricLoader.getInstance().isModLoaded("wynnmod")
                && translate_allinone$isWynnmodDecorateTooltipFeatureEnabled();
    }

    @Unique
    private static boolean translate_allinone$isWynnmodDecorateTooltipFeatureEnabled() {
        if (!FabricLoader.getInstance().isModLoaded("wynnmod")) {
            return false;
        }

        translate_allinone$initializeWynnmodFeatureReflection();
        if (!translate_allinone$wynnmodFeatureReflectionAvailable) {
            return false;
        }

        try {
            Object featureInstance = translate_allinone$wynnmodFeatureGetInstanceMethod.invoke(
                    null,
                    translate_allinone$wynnmodDecorateTooltipFeatureClass
            );
            if (featureInstance == null) {
                return false;
            }
            return Boolean.TRUE.equals(translate_allinone$wynnmodFeatureShouldFunctionMethod.invoke(featureInstance));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @Unique
    private static void translate_allinone$initializeWynnmodFeatureReflection() {
        if (translate_allinone$wynnmodFeatureReflectionInitialized) {
            return;
        }

        translate_allinone$wynnmodFeatureReflectionInitialized = true;
        try {
            Class<?> featureClass = Class.forName("com.wynnmod.feature.Feature");
            translate_allinone$wynnmodDecorateTooltipFeatureClass =
                    Class.forName("com.wynnmod.feature.item.wynn.DecorateTooltipFeature");
            translate_allinone$wynnmodFeatureGetInstanceMethod = featureClass.getMethod("getInstance", Class.class);
            translate_allinone$wynnmodFeatureShouldFunctionMethod = featureClass.getMethod("shouldFunction");
            translate_allinone$wynnmodFeatureReflectionAvailable = true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            translate_allinone$wynnmodFeatureReflectionAvailable = false;
        }
    }

    @Unique
    private static boolean translate_allinone$sameTooltipContent(List<Text> left, List<Text> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }

        for (int i = 0; i < left.size(); i++) {
            Text leftLine = left.get(i);
            Text rightLine = right.get(i);
            String leftValue = leftLine == null ? "" : leftLine.getString();
            String rightValue = rightLine == null ? "" : rightLine.getString();
            if (!leftValue.equals(rightValue)) {
                return false;
            }
        }
        return true;
    }
}
