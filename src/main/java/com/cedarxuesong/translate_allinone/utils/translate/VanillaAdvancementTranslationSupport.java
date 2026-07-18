package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.cache.LookupResult;
import com.cedarxuesong.translate_allinone.utils.cache.TranslationStatus;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;

public final class VanillaAdvancementTranslationSupport {
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final AtomicBoolean UNEXPECTED_FAILURE_LOGGED = new AtomicBoolean(false);
    private static final long REFRESH_HOLD_RELEASE_GRACE_MILLIS = 250L;
    private static final Set<String> refreshedKeysThisHold = new HashSet<>();
    private static volatile boolean refreshHoldActive = false;
    private static volatile long refreshHoldGraceExpiresAtMillis = 0L;

    private VanillaAdvancementTranslationSupport() {
    }

    public static boolean isVanillaAdvancement(AdvancementHolder holder) {
        return holder != null
                && holder.id() != null
                && VANILLA_NAMESPACE.equals(holder.id().getNamespace());
    }

    public static Component translateTitle(AdvancementHolder holder, Component originalTitle) {
        return translateComponent(holder, originalTitle, "title", false);
    }

    public static Component translateQueuedTitle(AdvancementHolder holder, Component originalTitle) {
        return translateComponent(holder, originalTitle, "title", true);
    }

    public static Component translateHoveredTitle(AdvancementHolder holder, Component originalTitle) {
        return translateQueuedTitle(holder, originalTitle);
    }

    public static Component translateDescription(
            AdvancementHolder holder,
            DisplayInfo display,
            Component originalDescription
    ) {
        Component styledDescription = originalDescription;
        if (display != null && originalDescription != null && display.getType() != null) {
            styledDescription = ComponentUtils.mergeStyles(
                    originalDescription,
                    Style.EMPTY.withColor(display.getType().getChatColor())
            );
        }
        return translateComponent(holder, styledDescription, "description", false);
    }

    public static Component translateHoveredDescription(
            AdvancementHolder holder,
            DisplayInfo display,
            Component originalDescription
    ) {
        Component styledDescription = originalDescription;
        if (display != null && originalDescription != null && display.getType() != null) {
            styledDescription = ComponentUtils.mergeStyles(
                    originalDescription,
                    Style.EMPTY.withColor(display.getType().getChatColor())
            );
        }
        return translateComponent(holder, styledDescription, "description", true);
    }

    private static Component translateComponent(
            AdvancementHolder holder,
            Component originalText,
            String fieldName,
            boolean queueIfMissing
    ) {
        if (originalText == null || originalText.getString().trim().isEmpty()) {
            return originalText;
        }
        if (!isVanillaAdvancement(holder)) {
            return originalText;
        }

        ItemTranslateConfig config = currentItemConfig();
        if (!shouldRenderTranslatedAdvancement(config)) {
            return originalText;
        }

        try {
            StylePreserver.ExtractionResult styleResult = StylePreserver.extractAndMarkWithTags(originalText);
            TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
            String translationTemplateKey = templateResult.template();
            if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
                return originalText;
            }

            if (queueIfMissing && shouldForceRefreshAdvancement(config, translationTemplateKey)) {
                ItemTemplateCache.getInstance().forceRefresh(List.of(translationTemplateKey));
            }

            LookupResult lookupResult = queueIfMissing
                    ? ItemTemplateCache.getInstance().lookupOrQueue(translationTemplateKey)
                    : ItemTemplateCache.getInstance().peek(translationTemplateKey);
            TranslationStatus status = lookupResult.status();
            if (status == TranslationStatus.TRANSLATED) {
                String translatedTemplate = lookupResult.translation();
                if (translatedTemplate == null || translatedTemplate.isBlank()) {
                    return originalText;
                }
                String reassembledTranslated = TemplateProcessor.reassemble(translatedTemplate, templateResult.values());
                return StylePreserver.reapplyStylesFromTags(reassembledTranslated, styleResult.styleMap, true);
            }

            String animationKey = "advancement:" + holder.id() + ":" + fieldName;
            if (status == TranslationStatus.PENDING || status == TranslationStatus.IN_PROGRESS) {
                return AnimationManager.getAnimatedStyledText(originalText, animationKey, false);
            }
            if (status == TranslationStatus.ERROR) {
                return AnimationManager.getAnimatedStyledText(originalText, animationKey, true);
            }
        } catch (RuntimeException e) {
            if (UNEXPECTED_FAILURE_LOGGED.compareAndSet(false, true)) {
                Translate_AllinOne.LOGGER.error("Failed to translate vanilla advancement text.", e);
            }
        }

        return originalText;
    }

    private static boolean shouldForceRefreshAdvancement(ItemTranslateConfig config, String translationTemplateKey) {
        if (config == null || config.keybinding == null || config.keybinding.refreshBinding == null) {
            return false;
        }

        boolean isRefreshPressed = KeybindingManager.isPressed(config.keybinding.refreshBinding);
        long now = System.currentTimeMillis();

        if (isRefreshPressed) {
            refreshHoldActive = true;
            refreshHoldGraceExpiresAtMillis = now + REFRESH_HOLD_RELEASE_GRACE_MILLIS;
        } else {
            if (!refreshHoldActive || now > refreshHoldGraceExpiresAtMillis) {
                refreshHoldActive = false;
                refreshHoldGraceExpiresAtMillis = 0L;
                synchronized (refreshedKeysThisHold) {
                    refreshedKeysThisHold.clear();
                }
                return false;
            }
        }

        synchronized (refreshedKeysThisHold) {
            if (!refreshedKeysThisHold.add(translationTemplateKey)) {
                return false;
            }
        }

        LookupResult peekResult = ItemTemplateCache.getInstance().peek(translationTemplateKey);
        return peekResult.status() == TranslationStatus.TRANSLATED;
    }

    private static ItemTranslateConfig currentItemConfig() {
        ModConfig config = Translate_AllinOne.getConfig();
        return config == null ? null : config.itemTranslate;
    }

    private static boolean shouldRenderTranslatedAdvancement(ItemTranslateConfig config) {
        if (config == null || !config.enabled_translate_vanilla_advancements) {
            return false;
        }

        ItemTranslateConfig.KeybindingMode mode = config.keybinding == null || config.keybinding.mode == null
                ? ItemTranslateConfig.KeybindingMode.DISABLED
                : config.keybinding.mode;
        boolean keyPressed = config.keybinding != null
                && KeybindingManager.isPressed(config.keybinding.binding);
        return !TooltipTranslationSupport.shouldShowOriginal(mode, keyPressed);
    }
}
