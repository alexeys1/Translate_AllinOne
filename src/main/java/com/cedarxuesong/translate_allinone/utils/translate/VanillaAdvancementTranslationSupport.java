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
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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

    public static boolean isVanillaAdvancement(AdvancementEntry holder) {
        return holder != null
                && holder.id() != null
                && VANILLA_NAMESPACE.equals(holder.id().getNamespace());
    }

    public static Text translateTitle(AdvancementEntry holder, Text originalTitle) {
        return translateComponent(holder, originalTitle, "title", false);
    }

    public static Text translateQueuedTitle(AdvancementEntry holder, Text originalTitle) {
        return translateComponent(holder, originalTitle, "title", true);
    }

    public static Text translateHoveredTitle(AdvancementEntry holder, Text originalTitle) {
        return translateQueuedTitle(holder, originalTitle);
    }

    public record HoveredAdvancementText(
            Text title,
            Text description,
            Text statusLine,
            Text errorStatusLine,
            boolean showRefreshNotice
    ) {
    }

    public static HoveredAdvancementText translateHoveredText(AdvancementEntry holder, AdvancementDisplay display) {
        Text originalTitle = display == null ? null : display.getTitle();
        Text styledDescription = styleDescription(display, display == null ? null : display.getDescription());
        ComponentAttempt title = translateComponentAttempt(holder, originalTitle, "title", true);
        ComponentAttempt description = translateComponentAttempt(holder, styledDescription, "description", true);
        String errorMessage = !title.errorMessage().isBlank() ? title.errorMessage() : description.errorMessage();
        return new HoveredAdvancementText(
                displayComponentAttempt(holder, title, "title"),
                displayComponentAttempt(holder, description, "description"),
                title.pending() || description.pending()
                        ? TooltipInternalLineSupport.createAnimatedPendingStatusLine(ADVANCEMENT_STATUS_ANIMATION_KEY)
                        : null,
                errorMessage.isBlank() ? null : TooltipInternalLineSupport.createErrorStatusLine(errorMessage),
                false
        );
    }

    public static Text createRefreshNoticeLine() {
        return TooltipRefreshNoticeSupport.createRefreshNoticeLine();
    }

    public static Text translateDescription(
            AdvancementEntry holder,
            AdvancementDisplay display,
            Text originalDescription
    ) {
        return translateComponent(holder, styleDescription(display, originalDescription), "description", false);
    }

    public static Text translateHoveredDescription(
            AdvancementEntry holder,
            AdvancementDisplay display,
            Text originalDescription
    ) {
        return translateComponent(holder, styleDescription(display, originalDescription), "description", true);
    }

    private static Text translateComponent(
            AdvancementEntry holder,
            Text originalText,
            String fieldName,
            boolean queueIfMissing
    ) {
        return displayComponentAttempt(
                holder,
                translateComponentAttempt(holder, originalText, fieldName, queueIfMissing),
                fieldName
        );
    }

    private static Text displayComponentAttempt(
            AdvancementEntry holder,
            ComponentAttempt attempt,
            String fieldName
    ) {
        if (attempt == null || !attempt.pending()) {
            return attempt == null ? null : attempt.text();
        }
        String holderId = holder == null || holder.id() == null ? "unknown" : holder.id().toString();
        return ComponentRenderTranslationSupport.animatePending(
                attempt.text(),
                "advancement:" + holderId + ":" + fieldName
        );
    }

    private static ComponentAttempt translateComponentAttempt(
            AdvancementEntry holder,
            Text originalText,
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
            ComponentTranslationRuntime.Resolution<Text> resolution = ComponentTranslationRuntime.resolve(
                    document,
                    config.target_language,
                    null,
                    () -> null,
                    response -> {
                        long startedAt = System.nanoTime();
                        try {
                            return applier.apply(document, response);
                        } finally {
                            ComponentTranslationMetrics.recordNanos(
                                    ComponentTranslationRoute.ADVANCEMENT,
                                    ComponentTranslationMetrics.Timing.APPLY,
                                    System.nanoTime() - startedAt
                            );
                        }
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
                Translate_AllinOne.LOGGER.error("Failed to translate vanilla advancement Text.", e);
            }
            return new ComponentAttempt(originalText, false, e.getMessage());
        }
    }

    private record ComponentAttempt(Text text, boolean pending, String errorMessage) {
        private ComponentAttempt {
            errorMessage = errorMessage == null ? "" : errorMessage;
        }
    }

    static Text materializeComponentText(Text originalText) {
        MutableText materialized = Text.empty();
        originalText.visit((style, text) -> {
            if (text != null && !text.isEmpty()) {
                materialized.append(Text.literal(text).setStyle(style == null ? Style.EMPTY : style));
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

    private static Text styleDescription(AdvancementDisplay display, Text originalDescription) {
        if (display != null && originalDescription != null && display.getFrame() != null) {
            return Texts.withStyle(
                    originalDescription,
                    Style.EMPTY.withColor(display.getFrame().getTitleFormat())
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
