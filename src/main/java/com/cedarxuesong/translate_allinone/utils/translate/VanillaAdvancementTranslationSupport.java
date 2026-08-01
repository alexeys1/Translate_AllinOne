package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationApplier;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationMetrics;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class VanillaAdvancementTranslationSupport {
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final String ADVANCEMENT_STATUS_ANIMATION_KEY = "advancement-tooltip-status";
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

    public record HoveredAdvancementText(
            Component title,
            Component description,
            Component statusLine,
            Component errorStatusLine,
            boolean showRefreshNotice
    ) {
    }

    public static HoveredAdvancementText translateHoveredText(AdvancementHolder holder, DisplayInfo display) {
        Component originalTitle = display == null ? null : display.getTitle();
        Component styledDescription = styleDescription(display, display == null ? null : display.getDescription());
        ComponentAttempt title = translateComponentAttempt(holder, originalTitle, "title", true);
        ComponentAttempt description = translateComponentAttempt(holder, styledDescription, "description", true);
        String errorMessage = !title.errorMessage().isBlank() ? title.errorMessage() : description.errorMessage();
        return new HoveredAdvancementText(
                title.component(),
                description.component(),
                title.pending() || description.pending()
                        ? TooltipInternalLineSupport.createAnimatedPendingStatusLine(ADVANCEMENT_STATUS_ANIMATION_KEY)
                        : null,
                errorMessage.isBlank() ? null : TooltipInternalLineSupport.createErrorStatusLine(errorMessage),
                false
        );
    }

    public static Component createRefreshNoticeLine() {
        return TooltipRefreshNoticeSupport.createRefreshNoticeLine();
    }

    public static Component translateDescription(
            AdvancementHolder holder,
            DisplayInfo display,
            Component originalDescription
    ) {
        return translateComponent(holder, styleDescription(display, originalDescription), "description", false);
    }

    public static Component translateHoveredDescription(
            AdvancementHolder holder,
            DisplayInfo display,
            Component originalDescription
    ) {
        return translateComponent(holder, styleDescription(display, originalDescription), "description", true);
    }

    private static Component translateComponent(
            AdvancementHolder holder,
            Component originalText,
            String fieldName,
            boolean queueIfMissing
    ) {
        return translateComponentAttempt(holder, originalText, fieldName, queueIfMissing).component();
    }

    private static ComponentAttempt translateComponentAttempt(
            AdvancementHolder holder,
            Component originalText,
            String fieldName,
            boolean queueIfMissing
    ) {
        if (originalText == null || originalText.getString().trim().isEmpty() || !isVanillaAdvancement(holder)) {
            return new ComponentAttempt(originalText, false, "");
        }

        OtherTranslationsConfig config = currentOtherTranslationsConfig();
        if (!isAdvancementTranslationFeatureEnabled(config)) {
            return new ComponentAttempt(originalText, false, "");
        }

        try {
            ComponentTranslationDocument document = ComponentTranslationRuntime.prepare(
                    materializeComponentText(originalText),
                    ComponentTranslationRoute.ADVANCEMENT,
                    "advancement:" + fieldName,
                    "advancement"
            );
            if (document.units().isEmpty()) {
                return new ComponentAttempt(originalText, false, "");
            }

            boolean refreshRequested = queueIfMissing && maybeForceRefreshAdvancementDocument(config, document);
            if (!shouldRenderTranslatedAdvancement(config)) {
                if (refreshRequested) {
                    queueRefreshedAdvancementDocument(document, config, "advancement:" + holder.id() + ":" + fieldName);
                }
                return new ComponentAttempt(originalText, false, "");
            }

            ComponentTranslationApplier applier = new ComponentTranslationApplier();
            ComponentTranslationRuntime.Resolution<Component> resolution = ComponentTranslationRuntime.resolve(
                    document,
                    config.target_language,
                    null,
                    () -> null,
                    response -> {
                        long startedAt = System.nanoTime();
                        Component translated = applier.apply(document, response);
                        ComponentTranslationMetrics.recordNanos(
                                ComponentTranslationRoute.ADVANCEMENT,
                                ComponentTranslationMetrics.Timing.APPLY,
                                System.nanoTime() - startedAt
                        );
                        return translated;
                    },
                    "advancement:" + holder.id() + ":" + fieldName,
                    queueIfMissing
            );
            return new ComponentAttempt(
                    resolution.value() == null ? originalText : resolution.value(),
                    resolution.state() == ComponentTranslationRuntime.State.PENDING,
                    resolution.state() == ComponentTranslationRuntime.State.FAILED ? resolution.errorMessage() : ""
            );
        } catch (RuntimeException e) {
            if (UNEXPECTED_FAILURE_LOGGED.compareAndSet(false, true)) {
                Translate_AllinOne.LOGGER.error("Failed to translate vanilla advancement Component text.", e);
            }
            return new ComponentAttempt(originalText, false, e.getMessage());
        }
    }

    private record ComponentAttempt(Component component, boolean pending, String errorMessage) {
        private ComponentAttempt {
            errorMessage = errorMessage == null ? "" : errorMessage;
        }
    }

    static Component materializeComponentText(Component originalText) {
        MutableComponent materialized = Component.empty();
        originalText.visit((style, text) -> {
            if (text != null && !text.isEmpty()) {
                materialized.append(Component.literal(text).setStyle(style == null ? Style.EMPTY : style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return materialized;
    }

    private static boolean maybeForceRefreshAdvancementDocument(
            OtherTranslationsConfig config,
            ComponentTranslationDocument document
    ) {
        if (config == null || config.keybinding == null || config.keybinding.refreshBinding == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (!updateRefreshHoldState(KeybindingManager.isPressed(config.keybinding.refreshBinding), now)) {
            return false;
        }

        String cacheKey = ComponentTranslationRuntime.cacheKey(document, config.target_language);
        synchronized (refreshedKeysThisHold) {
            if (!refreshedKeysThisHold.add(cacheKey)) {
                return false;
            }
        }
        return ComponentTranslationRuntime.forceRefresh(document, config.target_language);
    }

    private static void queueRefreshedAdvancementDocument(
            ComponentTranslationDocument document,
            OtherTranslationsConfig config,
            String requestContext
    ) {
        ComponentTranslationRuntime.resolve(
                document,
                config.target_language,
                null,
                () -> null,
                response -> null,
                requestContext,
                true
        );
    }

    private static boolean updateRefreshHoldState(boolean isRefreshPressed, long now) {
        if (isRefreshPressed) {
            refreshHoldActive = true;
            refreshHoldGraceExpiresAtMillis = now + REFRESH_HOLD_RELEASE_GRACE_MILLIS;
            return true;
        }
        if (refreshHoldActive && now <= refreshHoldGraceExpiresAtMillis) {
            return true;
        }
        refreshHoldActive = false;
        refreshHoldGraceExpiresAtMillis = 0L;
        synchronized (refreshedKeysThisHold) {
            refreshedKeysThisHold.clear();
        }
        return false;
    }

    private static Component styleDescription(DisplayInfo display, Component originalDescription) {
        if (display != null && originalDescription != null && display.getType() != null) {
            return ComponentUtils.mergeStyles(
                    originalDescription,
                    Style.EMPTY.withColor(display.getType().getChatColor())
            );
        }
        return originalDescription;
    }

    private static boolean isAdvancementTranslationFeatureEnabled(OtherTranslationsConfig config) {
        return config != null && config.enabled && config.enabled_translate_vanilla_advancements;
    }

    private static OtherTranslationsConfig currentOtherTranslationsConfig() {
        ModConfig config = Translate_AllinOne.getConfig();
        return config == null ? null : config.otherTranslations;
    }

    private static boolean shouldRenderTranslatedAdvancement(OtherTranslationsConfig config) {
        return isAdvancementTranslationFeatureEnabled(config)
                && ComponentRenderTranslationSupport.shouldRenderTranslated(config);
    }
}
