package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.LookupResult;
import com.cedarxuesong.translate_allinone.utils.cache.ScoreboardTextCache;
import com.cedarxuesong.translate_allinone.utils.cache.TranslationStatus;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;

/** Coordinates the opt-in scoreboard Component V1 path and legacy dual-read fallback. */
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

        ComponentTranslationRuntime.Resolution<Component> resolution = ComponentTranslationRuntime.resolve(
                prepared.v1Document(),
                config.target_language,
                prepared.translationTemplateKey(),
                () -> lookupLegacy(prepared),
                prepared::renderV1Translated,
                "scoreboard_entry"
        );
        if (resolution.state() == ComponentTranslationRuntime.State.V1_HIT
                || resolution.state() == ComponentTranslationRuntime.State.LEGACY_HIT) {
            return resolution.value();
        }

        Component original = prepared.renderV1Original();
        if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
            String animationKey = resolution.cacheKey().isBlank()
                    ? prepared.translationTemplateKey()
                    : resolution.cacheKey();
            return AnimationManager.getAnimatedStyledText(original, animationKey, false);
        }
        return original;
    }

    public static Set<String> refreshIdentities(
            ScoreboardEntryTemplate.Prepared prepared,
            String targetLanguage
    ) {
        if (prepared == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        if (!prepared.translationTemplateKey().isBlank()) {
            result.add("legacy:" + prepared.translationTemplateKey());
        }
        if (prepared.v1Document() != null && targetLanguage != null && !targetLanguage.isBlank()) {
            result.add("v1:" + ComponentTranslationRuntime.cacheKey(prepared.v1Document(), targetLanguage));
        }
        return Set.copyOf(result);
    }

    public static int forceRefresh(
            Iterable<ScoreboardEntryTemplate.Prepared> preparedEntries,
            String targetLanguage
    ) {
        if (preparedEntries == null) {
            return 0;
        }
        Set<String> legacyKeys = new LinkedHashSet<>();
        int refreshed = 0;
        for (ScoreboardEntryTemplate.Prepared prepared : preparedEntries) {
            if (prepared == null) {
                continue;
            }
            if (!prepared.translationTemplateKey().isBlank()) {
                legacyKeys.add(prepared.translationTemplateKey());
            }
            if (prepared.v1Document() != null
                    && ComponentTranslationRuntime.forceRefresh(prepared.v1Document(), targetLanguage)) {
                refreshed++;
            }
        }
        return refreshed + ScoreboardTextCache.getInstance().forceRefresh(legacyKeys);
    }

    public static void beginObjective(Object objective) {
        PREPARED_DOCUMENTS.beginObjective(objective);
    }

    public static void reset() {
        PREPARED_DOCUMENTS.clear();
    }

    private static Component lookupLegacy(ScoreboardEntryTemplate.Prepared prepared) {
        if (prepared.translationTemplateKey().isBlank()) {
            return null;
        }
        LookupResult lookup = ScoreboardTextCache.getInstance().peek(prepared.translationTemplateKey());
        if (lookup.status() != TranslationStatus.TRANSLATED) {
            return null;
        }
        return prepared.renderTranslated(lookup.translation());
    }
}
