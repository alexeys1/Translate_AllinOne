package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentJsonException;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationMetrics;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;

public final class ScoreboardComponentTranslationSupport {
    private static final ScoreboardPreparedDocumentCache PREPARED_DOCUMENTS =
            new ScoreboardPreparedDocumentCache();

    private ScoreboardComponentTranslationSupport() {
    }

    public static ScoreboardEntryTemplate.Prepared prepare(
            Object objective,
            Component prefix,
            Component owner,
            boolean translateOwner,
            boolean protectOwner,
            Component suffix
    ) {
        return PREPARED_DOCUMENTS.prepare(
                objective,
                prefix,
                owner,
                translateOwner,
                protectOwner,
                suffix
        );
    }

    public static Component resolve(
            ScoreboardEntryTemplate.Prepared prepared,
            ScoreboardConfig config
    ) {
        if (prepared == null || config == null) {
            return Component.empty();
        }
        if (!TranslationFeatureGate.isEnabled()
                || ComponentRenderTranslationSupport.isTranslationBlockedByScreen()) {
            return prepared.renderOriginal();
        }

        ComponentTranslationRuntime.Resolution<Component> resolution = ComponentTranslationRuntime.resolve(
                prepared.document(),
                config.target_language,
                null,
                () -> null,
                response -> {
                    long startedAt = System.nanoTime();
                    try {
                        return prepared.renderTranslated(response);
                    } catch (RuntimeException e) {
                        ComponentTranslationMetrics.Outcome outcome =
                                e instanceof ComponentJsonException componentError
                                        && componentError.kind() == ComponentJsonException.Kind.CODEC
                                        ? ComponentTranslationMetrics.Outcome.CODEC_DECODE_FAILURE
                                        : ComponentTranslationMetrics.Outcome.VALIDATION_STRUCTURE_FAILURE;
                        ComponentTranslationMetrics.record(
                                prepared.document(),
                                outcome
                        );
                        throw e;
                    } finally {
                        ComponentTranslationMetrics.recordNanos(
                                prepared.document(),
                                ComponentTranslationMetrics.Timing.APPLY,
                                System.nanoTime() - startedAt
                        );
                    }
                },
                "scoreboard_entry"
        );
        if (resolution.state() == ComponentTranslationRuntime.State.CACHE_HIT) {
            return resolution.value();
        }

        Component original = prepared.renderOriginal();
        if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
            String animationKey = resolution.cacheKey().isBlank()
                    ? ComponentTranslationRuntime.cacheKey(prepared.document(), config.target_language)
                    : resolution.cacheKey();
            return AnimationManager.getAnimatedStyledText(original, animationKey, false);
        }
        if (resolution.state() == ComponentTranslationRuntime.State.FAILED) {
            ComponentTranslationMetrics.record(
                    prepared.document(),
                    ComponentTranslationMetrics.Outcome.LOCAL_FALLBACK
            );
            ComponentTranslationMetrics.record(
                    prepared.document(),
                    ComponentTranslationMetrics.Outcome.FALLBACK_ORIGINAL
            );
        }
        return original;
    }

    public static Set<String> refreshIdentities(
            ScoreboardEntryTemplate.Prepared prepared,
            String targetLanguage
    ) {
        if (!TranslationFeatureGate.isEnabled() || prepared == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        if (prepared.document() != null && targetLanguage != null && !targetLanguage.isBlank()) {
            result.add("component:" + ComponentTranslationRuntime.cacheKey(prepared.document(), targetLanguage));
        }
        return Set.copyOf(result);
    }

    public static int forceRefresh(
            Iterable<ScoreboardEntryTemplate.Prepared> preparedEntries,
            String targetLanguage
    ) {
        if (!TranslationFeatureGate.isEnabled() || preparedEntries == null) {
            return 0;
        }
        int refreshed = 0;
        for (ScoreboardEntryTemplate.Prepared prepared : preparedEntries) {
            if (prepared == null) {
                continue;
            }
            if (prepared.document() != null
                    && ComponentTranslationRuntime.forceRefresh(prepared.document(), targetLanguage)) {
                refreshed++;
            }
        }
        return refreshed;
    }

    public static void beginObjective(Object objective) {
        PREPARED_DOCUMENTS.beginObjective(objective);
    }

    public static void reset() {
        PREPARED_DOCUMENTS.clear();
    }

}
