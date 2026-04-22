package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import com.cedarxuesong.translate_allinone.utils.textmatcher.FlatNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TooltipTemplateRuntime {
    private static final String STORED_LEGACY_PREFIX = "[taio:legacy]";
    private static final long CACHE_MIGRATION_LOG_THROTTLE_WINDOW_MILLIS = 5000L;
    private static final int CACHE_MIGRATION_LOG_THROTTLE_STATE_LIMIT = 4096;
    private static final long FORCE_REFRESH_COMPAT_BYPASS_MILLIS = 300_000L;
    private static final int FORCE_REFRESH_COMPAT_BYPASS_STATE_LIMIT = 4096;
    static final Pattern STYLE_TAG_ID_PATTERN = Pattern.compile("</?s(\\d+)>");
    private static final Pattern NUMERIC_PLACEHOLDER_ID_PATTERN = Pattern.compile("\\{d(\\d+)}");
    private static final Pattern GLYPH_PLACEHOLDER_ID_PATTERN = Pattern.compile("\\{g(\\d+)}");
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipTranslationSupport");
    private static final ConcurrentHashMap<CacheMigrationLogKey, CacheMigrationLogThrottleState> CACHE_MIGRATION_LOG_THROTTLE =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY =
            new ConcurrentHashMap<>();

    private TooltipTemplateRuntime() {
    }

    enum CachedTranslationFormat {
        TAGGED,
        LEGACY
    }

    record PreparedTooltipTemplate(
            Text sourceLine,
            boolean useTagStylePreservation,
            StylePreserver.ExtractionResult styleResult,
            TemplateProcessor.TemplateExtractionResult templateResult,
            TemplateProcessor.DecorativeGlyphExtractionResult glyphResult,
            String unicodeTemplate,
            String normalizedTemplate,
            String translationTemplateKey
    ) {
    }

    record PreparedParagraphTemplate(
            String translationTemplateKey,
            Map<Integer, Style> styleMap,
            List<String> templateValues,
            List<String> glyphValues,
            Integer bodyStyleId,
            int wrapWidth
    ) {
    }

    private record CompatibilityTemplateKey(String key, CachedTranslationFormat format) {
    }

    private record AdaptedCachedTranslation(String translation, CachedTranslationFormat format) {
    }

    private record DecodedStoredTranslation(String translation, CachedTranslationFormat format) {
    }

    private record ResolvedTemplateLookup(
            ItemTemplateCache.LookupResult lookupResult,
            CachedTranslationFormat format,
            Text renderedLineOverride
    ) {
    }

    private record CacheMigrationLogKey(
            String phase,
            CachedTranslationFormat format,
            String newKey,
            String compatibilityKey
    ) {
    }

    private static final class CacheMigrationLogThrottleState {
        private long lastLoggedAtMillis = 0L;
        private int suppressedCount = 0;
    }

    static TooltipTranslationSupport.TooltipLineResult translateLine(Text line, boolean useTagStylePreservation) {
        return translatePreparedTemplate(prepareTemplate(line, useTagStylePreservation));
    }

    static TooltipTranslationSupport.TooltipLineResult translatePreparedTemplate(PreparedTooltipTemplate preparedTemplate) {
        ResolvedTemplateLookup resolvedLookup = resolveLookup(preparedTemplate);
        ItemTemplateCache.LookupResult lookupResult = resolvedLookup.lookupResult();
        ItemTemplateCache.TranslationStatus status = lookupResult.status();
        boolean pending = status == ItemTemplateCache.TranslationStatus.PENDING
                || status == ItemTemplateCache.TranslationStatus.IN_PROGRESS;
        boolean missingKeyIssue = false;

        String translatedTemplate = lookupResult.translation();
        String reassembledOriginal = TemplateProcessor.reassembleDecorativeGlyphs(
                TemplateProcessor.reassemble(preparedTemplate.normalizedTemplate(), preparedTemplate.templateResult().values()),
                preparedTemplate.glyphResult().values()
        );
        Text originalTextObject = preparedTemplate.useTagStylePreservation()
                ? StylePreserver.reapplyStylesFromTags(reassembledOriginal, preparedTemplate.styleResult().styleMap)
                : StylePreserver.reapplyStyles(reassembledOriginal, preparedTemplate.styleResult().styleMap);

        Text finalTooltipLine;
        if (status == ItemTemplateCache.TranslationStatus.TRANSLATED && resolvedLookup.renderedLineOverride() != null) {
            finalTooltipLine = resolvedLookup.renderedLineOverride();
        } else if (status == ItemTemplateCache.TranslationStatus.TRANSLATED) {
            String reassembledTranslated = TemplateProcessor.reassembleDecorativeGlyphs(
                    TemplateProcessor.reassemble(translatedTemplate, preparedTemplate.templateResult().values()),
                    preparedTemplate.glyphResult().values(),
                    true
            );
            finalTooltipLine = resolvedLookup.format() == CachedTranslationFormat.TAGGED
                    ? StylePreserver.reapplyStylesFromTags(reassembledTranslated, preparedTemplate.styleResult().styleMap, true)
                    : StylePreserver.fromLegacyText(reassembledTranslated);
        } else if (status == ItemTemplateCache.TranslationStatus.ERROR) {
            String errorMessage = lookupResult.errorMessage();
            if (TooltipInternalLineSupport.isMissingKeyIssue(errorMessage)) {
                pending = true;
                missingKeyIssue = true;
                finalTooltipLine = originalTextObject;
            } else {
                finalTooltipLine = Text.literal("Error: " + errorMessage).formatted(Formatting.RED);
            }
        } else {
            finalTooltipLine = AnimationManager.getAnimatedStyledText(
                    originalTextObject,
                    preparedTemplate.translationTemplateKey(),
                    false
            );
        }

        return new TooltipTranslationSupport.TooltipLineResult(finalTooltipLine, pending, missingKeyIssue);
    }

    static String extractTemplateKeyForLine(Text line, boolean useTagStylePreservation) {
        return prepareTemplate(line, useTagStylePreservation).translationTemplateKey();
    }

    static PreparedTooltipTemplate prepareTemplate(Text line, boolean useTagStylePreservation) {
        boolean resolvedUseTagStylePreservation = shouldUseTagStylePreservation(line, useTagStylePreservation);
        StylePreserver.ExtractionResult styleResult = resolvedUseTagStylePreservation
                ? StylePreserver.extractAndMarkWithTags(line)
                : StylePreserver.extractAndMark(line);
        TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
        String unicodeTemplate = templateResult.template();
        TemplateProcessor.DecorativeGlyphExtractionResult glyphResult = resolvedUseTagStylePreservation
                ? TemplateProcessor.extractDecorativeGlyphTags(
                unicodeTemplate,
                styleId -> {
                    Style style = styleResult.styleMap.get(styleId);
                    return style != null && style.getFont() != null;
                }
        )
                : new TemplateProcessor.DecorativeGlyphExtractionResult(unicodeTemplate, List.of());
        String normalizedTemplate = resolvedUseTagStylePreservation
                ? TemplateProcessor.normalizeWynnInlineSpacerGlyphsInTaggedText(glyphResult.template())
                : glyphResult.template();
        String translationTemplateKey = resolvedUseTagStylePreservation
                ? normalizedTemplate
                : StylePreserver.toLegacyTemplate(unicodeTemplate, styleResult.styleMap);
        return new PreparedTooltipTemplate(
                line,
                resolvedUseTagStylePreservation,
                styleResult,
                templateResult,
                glyphResult,
                unicodeTemplate,
                normalizedTemplate,
                translationTemplateKey
        );
    }

    static PreparedParagraphTemplate prepareParagraphTemplate(List<PreparedTooltipTemplate> preparedLines) {
        if (preparedLines == null || preparedLines.isEmpty()) {
            return null;
        }

        Map<Integer, Style> combinedStyleMap = new HashMap<>();
        List<String> combinedTemplateValues = new ArrayList<>();
        List<String> combinedGlyphValues = new ArrayList<>();
        StringBuilder combinedTemplateKey = new StringBuilder();
        int nextStyleId = 0;
        int nextNumericId = 0;
        int nextGlyphId = 0;

        for (PreparedTooltipTemplate preparedLine : preparedLines) {
            if (preparedLine == null || preparedLine.normalizedTemplate() == null || preparedLine.normalizedTemplate().isBlank()) {
                continue;
            }

            if (combinedTemplateKey.length() > 0) {
                combinedTemplateKey.append(' ');
            }
            combinedTemplateKey.append(remapParagraphTemplateIds(
                    preparedLine.normalizedTemplate(),
                    nextStyleId,
                    nextNumericId,
                    nextGlyphId
            ));

            for (Map.Entry<Integer, Style> entry : preparedLine.styleResult().styleMap.entrySet()) {
                combinedStyleMap.put(entry.getKey() + nextStyleId, entry.getValue());
            }
            combinedTemplateValues.addAll(preparedLine.templateResult().values());
            combinedGlyphValues.addAll(preparedLine.glyphResult().values());

            nextStyleId += countStyleIds(preparedLine.styleResult().styleMap);
            nextNumericId += preparedLine.templateResult().values().size();
            nextGlyphId += preparedLine.glyphResult().values().size();
        }

        if (combinedTemplateKey.isEmpty()) {
            return null;
        }

        return new PreparedParagraphTemplate(
                combinedTemplateKey.toString(),
                combinedStyleMap,
                combinedTemplateValues,
                combinedGlyphValues,
                TooltipParagraphSupport.findDominantParagraphBodyStyleId(combinedTemplateKey.toString(), combinedStyleMap),
                computeParagraphWrapWidth(preparedLines)
        );
    }

    static Text renderOriginalPreparedLine(PreparedTooltipTemplate preparedTemplate) {
        String reassembledOriginal = TemplateProcessor.reassembleDecorativeGlyphs(
                TemplateProcessor.reassemble(preparedTemplate.normalizedTemplate(), preparedTemplate.templateResult().values()),
                preparedTemplate.glyphResult().values()
        );
        return preparedTemplate.useTagStylePreservation()
                ? StylePreserver.reapplyStylesFromTags(reassembledOriginal, preparedTemplate.styleResult().styleMap)
                : StylePreserver.reapplyStyles(reassembledOriginal, preparedTemplate.styleResult().styleMap);
    }

    static void registerForceRefreshCompatBypass(Iterable<String> translationTemplateKeys) {
        if (translationTemplateKeys == null) {
            return;
        }

        cleanupForceRefreshCompatBypassState();
        long expiresAtMillis = System.currentTimeMillis() + FORCE_REFRESH_COMPAT_BYPASS_MILLIS;
        for (String translationTemplateKey : translationTemplateKeys) {
            if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
                continue;
            }
            FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.put(translationTemplateKey, expiresAtMillis);
        }
    }

    static boolean containsNumericPlaceholder(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("{d1}") || text.matches(".*\\{d\\d+}.*");
    }

    static boolean containsDecorativeGlyph(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (isDecorativeGlyphCodePoint(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    static String stripDecorativeGlyphsForHeuristics(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(raw.length());
        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            if (isDecorativeGlyphCodePoint(codePoint)) {
                builder.append(' ');
            } else {
                builder.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private static ResolvedTemplateLookup resolveLookup(PreparedTooltipTemplate preparedTemplate) {
        long resolveStartedAtNanos = System.nanoTime();
        ItemTemplateCache cache = ItemTemplateCache.getInstance();
        CachedTranslationFormat currentFormat = preparedTemplate.useTagStylePreservation()
                ? CachedTranslationFormat.TAGGED
                : CachedTranslationFormat.LEGACY;
        boolean invalidCurrentTranslation = false;
        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;

        ItemTemplateCache.LookupResult currentLookup = cache.peek(preparedTemplate.translationTemplateKey());
        DecodedStoredTranslation decodedCurrentTranslation = decodeStoredTranslation(currentLookup.translation(), currentFormat);
        if (currentLookup.status() == ItemTemplateCache.TranslationStatus.TRANSLATED
                && isUsableCachedTranslation(
                preparedTemplate,
                decodedCurrentTranslation.translation(),
                decodedCurrentTranslation.format()
        )) {
            clearForceRefreshCompatBypass(preparedTemplate.translationTemplateKey());
            return new ResolvedTemplateLookup(
                    translatedLookup(decodedCurrentTranslation.translation()),
                    decodedCurrentTranslation.format(),
                    null
            );
        } else if (currentLookup.status() == ItemTemplateCache.TranslationStatus.TRANSLATED) {
            invalidCurrentTranslation = true;
            logCacheMigrationIfDev(
                    config,
                    "reject-new-key",
                    preparedTemplate.translationTemplateKey(),
                    null,
                    decodedCurrentTranslation.format(),
                    false,
                    "Current newKey cache entry still renders unresolved placeholders; forcing refresh."
            );
        }

        if (shouldBypassCompatibilityFallback(preparedTemplate.translationTemplateKey())) {
            if (invalidCurrentTranslation) {
                cache.forceRefresh(List.of(preparedTemplate.translationTemplateKey()));
                registerForceRefreshCompatBypass(List.of(preparedTemplate.translationTemplateKey()));
                return new ResolvedTemplateLookup(
                        new ItemTemplateCache.LookupResult(ItemTemplateCache.TranslationStatus.PENDING, "", null),
                        currentFormat,
                        null
                );
            }
            return new ResolvedTemplateLookup(
                    cache.lookupOrQueue(preparedTemplate.translationTemplateKey()),
                    currentFormat,
                    null
            );
        }

        long collectCompatibilityKeysStartedAtNanos = System.nanoTime();
        List<CompatibilityTemplateKey> compatibilityKeys = collectCompatibilityKeys(preparedTemplate);
        long collectCompatibilityKeysElapsedNanos = System.nanoTime() - collectCompatibilityKeysStartedAtNanos;
        long compatibilityScanStartedAtNanos = System.nanoTime();
        for (CompatibilityTemplateKey compatibilityKey : compatibilityKeys) {
            ItemTemplateCache.LookupResult compatibilityLookup = cache.peek(compatibilityKey.key());
            if (compatibilityLookup.status() != ItemTemplateCache.TranslationStatus.TRANSLATED) {
                continue;
            }

            DecodedStoredTranslation decodedCompatibilityTranslation = decodeStoredTranslation(
                    compatibilityLookup.translation(),
                    compatibilityKey.format()
            );
            if (!isUsableCachedTranslation(
                    preparedTemplate,
                    decodedCompatibilityTranslation.translation(),
                    decodedCompatibilityTranslation.format()
            )) {
                continue;
            }

            Text compatibilityRenderedText = renderCompatibilityText(
                    preparedTemplate,
                    decodedCompatibilityTranslation.translation(),
                    decodedCompatibilityTranslation.format()
            );
            AdaptedCachedTranslation adaptedTranslation = adaptCachedTranslation(
                    preparedTemplate,
                    decodedCompatibilityTranslation.translation(),
                    decodedCompatibilityTranslation.format()
            );
            if (adaptedTranslation != null
                    && adaptedTranslation.translation() != null
                    && !adaptedTranslation.translation().isBlank()
                    && isSafeAdaptedTranslation(preparedTemplate, adaptedTranslation, compatibilityRenderedText)) {
                long promoteStartedAtNanos = System.nanoTime();
                cache.promoteTranslation(
                        preparedTemplate.translationTemplateKey(),
                        encodeStoredTranslation(adaptedTranslation.translation(), adaptedTranslation.format())
                );
                long promoteElapsedNanos = System.nanoTime() - promoteStartedAtNanos;
                logCacheMigrationIfDev(
                        config,
                        "promote",
                        preparedTemplate.translationTemplateKey(),
                        compatibilityKey.key(),
                        decodedCompatibilityTranslation.format(),
                        true,
                        adaptedTranslation.format() == decodedCompatibilityTranslation.format()
                                ? "Reused compatibility cache entry and wrote it into newKey."
                                : "Reused compatibility cache entry, adapted it, and wrote it into newKey."
                );
                logCacheMigrationTimingIfDev(
                        config,
                        "promote",
                        preparedTemplate.translationTemplateKey(),
                        compatibilityKey.key(),
                        decodedCompatibilityTranslation.format(),
                        collectCompatibilityKeysElapsedNanos,
                        System.nanoTime() - compatibilityScanStartedAtNanos,
                        promoteElapsedNanos,
                        System.nanoTime() - resolveStartedAtNanos,
                        "compatibilityKeyCount=" + compatibilityKeys.size()
                );
                return new ResolvedTemplateLookup(
                        translatedLookup(adaptedTranslation.translation()),
                        adaptedTranslation.format(),
                        null
                );
            }

            long promoteStartedAtNanos = System.nanoTime();
            cache.promoteTranslation(
                    preparedTemplate.translationTemplateKey(),
                    encodeStoredTranslation(
                            decodedCompatibilityTranslation.translation(),
                            decodedCompatibilityTranslation.format()
                    )
            );
            long promoteElapsedNanos = System.nanoTime() - promoteStartedAtNanos;
            logCacheMigrationIfDev(
                    config,
                    decodedCompatibilityTranslation.format() == CachedTranslationFormat.LEGACY
                            ? "promote-legacy"
                            : "promote-compatible-format",
                    preparedTemplate.translationTemplateKey(),
                    compatibilityKey.key(),
                    decodedCompatibilityTranslation.format(),
                    true,
                    decodedCompatibilityTranslation.format() == CachedTranslationFormat.LEGACY
                            ? "Reused compatibility cache entry and wrote legacy-compatible content into newKey."
                            : "Reused compatibility cache entry and wrote compatible content into newKey."
            );
            logCacheMigrationTimingIfDev(
                    config,
                    decodedCompatibilityTranslation.format() == CachedTranslationFormat.LEGACY
                            ? "promote-legacy"
                            : "promote-compatible-format",
                    preparedTemplate.translationTemplateKey(),
                    compatibilityKey.key(),
                    decodedCompatibilityTranslation.format(),
                    collectCompatibilityKeysElapsedNanos,
                    System.nanoTime() - compatibilityScanStartedAtNanos,
                    promoteElapsedNanos,
                    System.nanoTime() - resolveStartedAtNanos,
                    "compatibilityKeyCount=" + compatibilityKeys.size()
            );
            return new ResolvedTemplateLookup(
                    translatedLookup(decodedCompatibilityTranslation.translation()),
                    decodedCompatibilityTranslation.format(),
                    null
            );
        }

        if (invalidCurrentTranslation) {
            cache.forceRefresh(List.of(preparedTemplate.translationTemplateKey()));
            return new ResolvedTemplateLookup(
                    new ItemTemplateCache.LookupResult(ItemTemplateCache.TranslationStatus.PENDING, "", null),
                    currentFormat,
                    null
            );
        }

        return new ResolvedTemplateLookup(cache.lookupOrQueue(preparedTemplate.translationTemplateKey()), currentFormat, null);
    }

    private static ItemTemplateCache.LookupResult translatedLookup(String translation) {
        return new ItemTemplateCache.LookupResult(
                ItemTemplateCache.TranslationStatus.TRANSLATED,
                translation,
                null
        );
    }

    private static List<CompatibilityTemplateKey> collectCompatibilityKeys(PreparedTooltipTemplate preparedTemplate) {
        if (!preparedTemplate.useTagStylePreservation()) {
            return List.of();
        }

        List<CompatibilityTemplateKey> compatibilityKeys = new ArrayList<>(2);
        addCompatibilityKey(
                compatibilityKeys,
                TemplateProcessor.normalizeWynnInlineSpacerGlyphsInTaggedText(
                        TemplateProcessor.extractDecorativeGlyphTags(preparedTemplate.unicodeTemplate()).template()
                ),
                CachedTranslationFormat.TAGGED
        );
        addCompatibilityKey(
                compatibilityKeys,
                buildLegacyCompatibilityKey(preparedTemplate.sourceLine()),
                CachedTranslationFormat.LEGACY
        );
        return compatibilityKeys;
    }

    private static void addCompatibilityKey(
            List<CompatibilityTemplateKey> compatibilityKeys,
            String key,
            CachedTranslationFormat format
    ) {
        if (key == null || key.isBlank()) {
            return;
        }

        for (CompatibilityTemplateKey existing : compatibilityKeys) {
            if (existing.key().equals(key)) {
                return;
            }
        }

        compatibilityKeys.add(new CompatibilityTemplateKey(key, format));
    }

    private static AdaptedCachedTranslation adaptCachedTranslation(
            PreparedTooltipTemplate preparedTemplate,
            String cachedTranslation,
            CachedTranslationFormat format
    ) {
        if (cachedTranslation == null || cachedTranslation.isBlank()) {
            return null;
        }

        if (format == CachedTranslationFormat.LEGACY && preparedTemplate.useTagStylePreservation()) {
            String converted = StylePreserver.convertLegacyTranslationToTaggedTemplate(
                    cachedTranslation,
                    preparedTemplate.styleResult().styleMap
            );
            if (converted == null || converted.isBlank()) {
                return null;
            }
            return new AdaptedCachedTranslation(converted, CachedTranslationFormat.TAGGED);
        }

        return new AdaptedCachedTranslation(cachedTranslation, format);
    }

    private static DecodedStoredTranslation decodeStoredTranslation(String storedTranslation, CachedTranslationFormat defaultFormat) {
        if (storedTranslation == null) {
            return new DecodedStoredTranslation("", defaultFormat);
        }

        if (storedTranslation.startsWith(STORED_LEGACY_PREFIX)) {
            return new DecodedStoredTranslation(
                    storedTranslation.substring(STORED_LEGACY_PREFIX.length()),
                    CachedTranslationFormat.LEGACY
            );
        }

        return new DecodedStoredTranslation(storedTranslation, defaultFormat);
    }

    private static String encodeStoredTranslation(String translation, CachedTranslationFormat format) {
        if (translation == null || translation.isBlank()) {
            return translation;
        }

        if (format == CachedTranslationFormat.LEGACY && !translation.startsWith(STORED_LEGACY_PREFIX)) {
            return STORED_LEGACY_PREFIX + translation;
        }

        return translation;
    }

    private static Text renderCompatibilityText(
            PreparedTooltipTemplate preparedTemplate,
            String cachedTranslation,
            CachedTranslationFormat format
    ) {
        if (cachedTranslation == null || cachedTranslation.isBlank()) {
            return null;
        }

        String reassembledTranslated = TemplateProcessor.reassembleDecorativeGlyphs(
                TemplateProcessor.reassemble(cachedTranslation, preparedTemplate.templateResult().values()),
                preparedTemplate.glyphResult().values(),
                true
        );
        return format == CachedTranslationFormat.TAGGED
                ? StylePreserver.reapplyStylesFromTags(reassembledTranslated, preparedTemplate.styleResult().styleMap, true)
                : StylePreserver.fromLegacyText(reassembledTranslated);
    }

    private static boolean isSafeAdaptedTranslation(
            PreparedTooltipTemplate preparedTemplate,
            AdaptedCachedTranslation adaptedTranslation,
            Text compatibilityRenderedText
    ) {
        if (adaptedTranslation == null
                || adaptedTranslation.format() != CachedTranslationFormat.TAGGED
                || adaptedTranslation.translation() == null
                || adaptedTranslation.translation().isBlank()) {
            return false;
        }

        String reassembledTranslated = TemplateProcessor.reassembleDecorativeGlyphs(
                TemplateProcessor.reassemble(adaptedTranslation.translation(), preparedTemplate.templateResult().values()),
                preparedTemplate.glyphResult().values(),
                true
        );
        if (containsNumericPlaceholder(reassembledTranslated)) {
            return false;
        }

        Text adaptedRenderedText = StylePreserver.reapplyStylesFromTags(
                reassembledTranslated,
                preparedTemplate.styleResult().styleMap,
                true
        );
        if (adaptedRenderedText == null) {
            return false;
        }
        if (compatibilityRenderedText == null) {
            return true;
        }
        return adaptedRenderedText.getString().equals(compatibilityRenderedText.getString());
    }

    private static boolean isUsableCachedTranslation(
            PreparedTooltipTemplate preparedTemplate,
            String cachedTranslation,
            CachedTranslationFormat format
    ) {
        Text renderedText = renderCompatibilityText(preparedTemplate, cachedTranslation, format);
        return renderedText != null && !containsNumericPlaceholder(renderedText.getString());
    }

    private static void logCacheMigrationIfDev(
            ItemTranslateConfig config,
            String phase,
            String newKey,
            String compatibilityKey,
            CachedTranslationFormat compatibilityFormat,
            boolean promoted,
            String detail
    ) {
        if (!TooltipTextMatcherSupport.shouldLogItemCacheMigration(config)) {
            return;
        }

        int suppressedCount = acquireCacheMigrationLogSlot(phase, compatibilityFormat, newKey, compatibilityKey);
        if (suppressedCount < 0) {
            return;
        }

        LOGGER.info(
                "[ItemDev:cache-migration] phase={} promoted={} format={} repeatsSuppressed={} newKey=\"{}\" compatibilityKey=\"{}\" detail=\"{}\"",
                phase,
                promoted,
                compatibilityFormat == null ? "" : compatibilityFormat.name(),
                suppressedCount,
                truncateForLog(newKey, 220),
                truncateForLog(compatibilityKey, 220),
                truncateForLog(detail, 220)
        );
    }

    private static void logCacheMigrationTimingIfDev(
            ItemTranslateConfig config,
            String phase,
            String newKey,
            String compatibilityKey,
            CachedTranslationFormat compatibilityFormat,
            long collectCompatibilityKeysElapsedNanos,
            long compatibilityScanElapsedNanos,
            long promoteElapsedNanos,
            long totalElapsedNanos,
            String detail
    ) {
        if (!TooltipTextMatcherSupport.shouldLogItemCacheMigration(config)) {
            return;
        }

        LOGGER.info(
                "[ItemDev:cache-hotspot] phase={} thread=\"{}\" renderThread={} format={} totalMs={} collectKeysMs={} scanMs={} promoteMs={} newKey=\"{}\" compatibilityKey=\"{}\" detail=\"{}\"",
                phase,
                Thread.currentThread().getName(),
                Thread.currentThread().getName().contains("Render thread"),
                compatibilityFormat == null ? "" : compatibilityFormat.name(),
                formatDevDurationMillis(totalElapsedNanos),
                formatDevDurationMillis(collectCompatibilityKeysElapsedNanos),
                formatDevDurationMillis(compatibilityScanElapsedNanos),
                formatDevDurationMillis(promoteElapsedNanos),
                truncateForLog(newKey, 220),
                truncateForLog(compatibilityKey, 220),
                truncateForLog(detail, 220)
        );
    }

    private static String buildLegacyCompatibilityKey(Text line) {
        if (line == null) {
            return null;
        }

        StylePreserver.ExtractionResult legacyStyleResult = StylePreserver.extractAndMark(line);
        TemplateProcessor.TemplateExtractionResult legacyTemplateResult = TemplateProcessor.extract(legacyStyleResult.markedText);
        return StylePreserver.toLegacyTemplate(legacyTemplateResult.template(), legacyStyleResult.styleMap);
    }

    private static boolean shouldUseTagStylePreservation(Text line, boolean useTagStylePreservation) {
        return useTagStylePreservation || requiresRichStylePreservation(line);
    }

    private static boolean requiresRichStylePreservation(Text line) {
        if (line == null) {
            return false;
        }

        for (FlatNode node : FlatNode.flatten(line)) {
            if (node.style() != null && node.style().getFont() != null) {
                return true;
            }

            String extracted = node.extractString();
            if (containsDecorativeGlyph(extracted)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDecorativeGlyphCodePoint(int codePoint) {
        int unicodeType = Character.getType(codePoint);
        return unicodeType == Character.PRIVATE_USE
                || unicodeType == Character.UNASSIGNED
                || (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
    }

    private static String remapParagraphTemplateIds(
            String template,
            int styleOffset,
            int numericOffset,
            int glyphOffset
    ) {
        String remapped = remapPatternIds(template, STYLE_TAG_ID_PATTERN, "s", styleOffset, true);
        remapped = remapPatternIds(remapped, NUMERIC_PLACEHOLDER_ID_PATTERN, "d", numericOffset, false);
        return remapPatternIds(remapped, GLYPH_PLACEHOLDER_ID_PATTERN, "g", glyphOffset, false);
    }

    private static String remapPatternIds(
            String input,
            Pattern pattern,
            String prefix,
            int offset,
            boolean styleTag
    ) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            int currentId = Integer.parseInt(matcher.group(1));
            int remappedId = currentId + offset;
            String replacement;
            if (styleTag) {
                boolean closingTag = input.charAt(matcher.start() + 1) == '/';
                replacement = closingTag
                        ? "</" + prefix + remappedId + ">"
                        : "<" + prefix + remappedId + ">";
            } else {
                replacement = "{" + prefix + remappedId + "}";
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static int countStyleIds(Map<Integer, Style> styleMap) {
        if (styleMap == null || styleMap.isEmpty()) {
            return 0;
        }

        int maxStyleId = -1;
        for (Integer styleId : styleMap.keySet()) {
            if (styleId != null && styleId > maxStyleId) {
                maxStyleId = styleId;
            }
        }
        return maxStyleId + 1;
    }

    private static int computeParagraphWrapWidth(List<PreparedTooltipTemplate> preparedLines) {
        TextRenderer textRenderer = getTooltipTextRenderer();
        if (textRenderer == null || preparedLines == null || preparedLines.isEmpty()) {
            return -1;
        }

        int maxWidth = 0;
        for (PreparedTooltipTemplate preparedLine : preparedLines) {
            if (preparedLine == null || preparedLine.sourceLine() == null) {
                continue;
            }
            maxWidth = Math.max(maxWidth, textRenderer.getWidth(preparedLine.sourceLine()));
        }
        return maxWidth;
    }

    private static TextRenderer getTooltipTextRenderer() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : client.textRenderer;
    }

    private static boolean shouldBypassCompatibilityFallback(String translationTemplateKey) {
        if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
            return false;
        }

        Long expiresAtMillis = FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.get(translationTemplateKey);
        if (expiresAtMillis == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (expiresAtMillis <= now) {
            FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.remove(translationTemplateKey, expiresAtMillis);
            return false;
        }
        return true;
    }

    private static void clearForceRefreshCompatBypass(String translationTemplateKey) {
        if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
            return;
        }
        FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.remove(translationTemplateKey);
    }

    private static void cleanupForceRefreshCompatBypassState() {
        if (FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (var entry : FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.entrySet()) {
            Long expiresAtMillis = entry.getValue();
            if (expiresAtMillis == null || expiresAtMillis <= now) {
                FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.remove(entry.getKey(), expiresAtMillis);
            }
        }

        if (FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.size() > FORCE_REFRESH_COMPAT_BYPASS_STATE_LIMIT) {
            FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.clear();
        }
    }

    private static String formatDevDurationMillis(long elapsedNanos) {
        return String.format(Locale.ROOT, "%.2f", elapsedNanos / 1_000_000.0);
    }

    private static int acquireCacheMigrationLogSlot(
            String phase,
            CachedTranslationFormat compatibilityFormat,
            String newKey,
            String compatibilityKey
    ) {
        if ("promote".equals(phase)) {
            return 0;
        }

        if (CACHE_MIGRATION_LOG_THROTTLE.size() > CACHE_MIGRATION_LOG_THROTTLE_STATE_LIMIT) {
            CACHE_MIGRATION_LOG_THROTTLE.clear();
        }

        CacheMigrationLogKey logKey = new CacheMigrationLogKey(phase, compatibilityFormat, newKey, compatibilityKey);
        CacheMigrationLogThrottleState state = CACHE_MIGRATION_LOG_THROTTLE.computeIfAbsent(
                logKey,
                unused -> new CacheMigrationLogThrottleState()
        );
        long now = System.currentTimeMillis();
        synchronized (state) {
            if (state.lastLoggedAtMillis > 0
                    && now - state.lastLoggedAtMillis < CACHE_MIGRATION_LOG_THROTTLE_WINDOW_MILLIS) {
                state.suppressedCount++;
                return -1;
            }

            int suppressedCount = state.suppressedCount;
            state.suppressedCount = 0;
            state.lastLoggedAtMillis = now;
            return suppressedCount;
        }
    }

    static String truncateForLog(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String normalized = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
