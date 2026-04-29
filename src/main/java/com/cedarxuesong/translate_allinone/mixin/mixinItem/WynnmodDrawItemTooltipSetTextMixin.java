package com.cedarxuesong.translate_allinone.mixin.mixinItem;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipInternalLineSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRecentRenderGuardSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRefreshNoticeSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTextDebugCopySupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTextMatcherSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationContext;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationSupport;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Pseudo
@Mixin(targets = "com.wynnmod.mixin.events.ContainerEvents$DrawItemTooltip", remap = false)
public abstract class WynnmodDrawItemTooltipSetTextMixin {
    @Unique
    private static final String ITEM_STATUS_ANIMATION_KEY = "item-tooltip-status-wynnmod-settext";

    @ModifyVariable(
            method = "setText(Ljava/util/List;)Lcom/wynnmod/mixin/events/ContainerEvents$DrawItemTooltip;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private List<Text> translate_allinone$translateDecoratedTooltipAtSetText(List<Text> tooltip) {
        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        if (tooltip == null || tooltip.isEmpty()) {
            return tooltip;
        }

        List<Text> sanitizedTooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(tooltip);
        if (sanitizedTooltip == null || sanitizedTooltip.isEmpty()) {
            return tooltip;
        }
        TooltipTextDebugCopySupport.maybeCopyCurrentTooltip(sanitizedTooltip);
        if (config == null || !config.enabled) {
            return tooltip;
        }
        Set<String> screenMirrorTranslationKeys = TooltipTranslationSupport.collectTranslationTemplateKeys(sanitizedTooltip, config);
        if (!screenMirrorTranslationKeys.isEmpty()) {
            TooltipTranslationContext.rememberExpectedScreenMirrorTooltip(screenMirrorTranslationKeys);
            TooltipTranslationContext.setSkipScreenMirrorTranslation(true);
        }

        TooltipRefreshNoticeSupport.maybeForceRefreshCurrentTooltip(sanitizedTooltip, config);
        boolean showRefreshNotice = TooltipRefreshNoticeSupport.shouldShowRefreshNotice(sanitizedTooltip, config);

        boolean isKeyPressed = KeybindingManager.isPressed(config.keybinding.binding);
        if (TooltipTranslationSupport.shouldShowOriginal(config.keybinding.mode, isKeyPressed)) {
            return TooltipRefreshNoticeSupport.appendRefreshNoticeLine(tooltip, showRefreshNotice);
        }

        if (!TooltipRecentRenderGuardSupport.shouldSkipDuplicateRender(sanitizedTooltip, showRefreshNotice)) {
            TooltipRefreshNoticeSupport.queueRemoteTranslationForCurrentTooltip(sanitizedTooltip, config);
        }

        boolean emitDevLog = TooltipTextMatcherSupport.beginTooltipDevPass(config, "wynnmod-setText", sanitizedTooltip);
        long tooltipStartedAtNanos = emitDevLog ? System.nanoTime() : 0L;
        TooltipTranslationSupport.TooltipProcessingResult processedTooltip = TooltipTranslationSupport.processTooltipLines(
                sanitizedTooltip,
                config,
                true,
                emitDevLog,
                "wynnmod-setText"
        );

        List<Text> translatedTooltip = TooltipInternalLineSupport.appendStatusLineIfNeeded(
                new ArrayList<>(processedTooltip.translatedLines()),
                processedTooltip,
                ITEM_STATUS_ANIMATION_KEY
        );
        translatedTooltip = TooltipRefreshNoticeSupport.appendRefreshNoticeLine(translatedTooltip, showRefreshNotice);

        TooltipTextMatcherSupport.logTooltipPassIfDev(
                config,
                emitDevLog,
                "wynnmod-setText",
                sanitizedTooltip.size(),
                processedTooltip.translatableLines(),
                tooltipStartedAtNanos
        );

        boolean locallyStableForRecentGuard = !processedTooltip.pending() && !processedTooltip.missingKeyIssue();
        TooltipRecentRenderGuardSupport.rememberTooltipIfStable(sanitizedTooltip, locallyStableForRecentGuard);
        TooltipRecentRenderGuardSupport.rememberTooltipIfStable(translatedTooltip, locallyStableForRecentGuard);
        if (!translate_allinone$sameTooltipContent(tooltip, translatedTooltip)) {
            TooltipTranslationContext.rememberExpectedDrawContextTooltip(translatedTooltip);
            TooltipTranslationContext.setSkipDrawContextTranslation(true);
        }
        return translatedTooltip;
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
