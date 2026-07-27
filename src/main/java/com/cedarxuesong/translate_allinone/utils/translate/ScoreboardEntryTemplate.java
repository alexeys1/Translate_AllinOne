package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentDocumentBuilder;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentJsonCodec;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTextUnit;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationApplier;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationPolicy;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationResponse;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Prepares both the legacy flattened scoreboard template and the structure-preserving
 * Component V1 document. The V1 source always keeps prefix, owner, and suffix in
 * separate, fixed-order branches.
 */
public final class ScoreboardEntryTemplate {
    public static final String PLAYER_NAME_TOKEN = "<taio-player-name>";
    public static final String V1_POLICY_VERSION = "scoreboard-v1";
    private static final String OWNER_POINTER_PREFIX = "/extra/1";
    private static final ComponentTranslationApplier V1_APPLIER = new ComponentTranslationApplier();

    public record Prepared(
            String translationTemplateKey,
            List<String> templateValues,
            String playerName,
            Component v1Original,
            ComponentTranslationDocument v1Document
    ) {
        public Prepared {
            translationTemplateKey = translationTemplateKey == null ? "" : translationTemplateKey;
            templateValues = templateValues == null ? List.of() : List.copyOf(templateValues);
            playerName = normalizedPlayerName(playerName);
            v1Original = v1Original == null ? Component.empty() : v1Original.copy();
        }

        /** Renders the original legacy representation for the legacy-only path. */
        public Component renderOriginal() {
            return renderLegacy(translationTemplateKey, templateValues, playerName);
        }

        /** Renders a translation read from scoreboard_translate_cache.json. */
        public Component renderTranslated(String translatedTemplate) {
            return renderLegacy(translatedTemplate, templateValues, playerName);
        }

        /** Returns the structure-preserving prefix -> owner -> suffix source. */
        public Component renderV1Original() {
            return v1Original.copy();
        }

        /** Applies only validated literal replacements to the V1 source document. */
        public Component renderV1Translated(ComponentTranslationResponse response) {
            if (v1Document == null) {
                throw new IllegalStateException("Scoreboard Component V1 document is unavailable.");
            }
            return V1_APPLIER.apply(v1Document, response);
        }
    }

    private ScoreboardEntryTemplate() {
    }

    /**
     * Legacy-compatible preparation entry point. This overload preserves the old
     * cache key and renderer behavior while also preparing an unprotected owner V1
     * document for callers that do not distinguish real players.
     */
    public static Prepared prepare(
            Component prefix,
            String playerName,
            boolean includePlayerName,
            Component suffix
    ) {
        LegacyPrepared legacy = prepareLegacy(
                copyOrEmpty(prefix),
                normalizedPlayerName(playerName),
                includePlayerName,
                copyOrEmpty(suffix)
        );
        if (legacy.translationTemplateKey().isBlank()) {
            return null;
        }
        return new Prepared(
                legacy.translationTemplateKey(),
                legacy.templateValues(),
                includePlayerName ? playerName : "",
                Component.empty(),
                null
        );
    }

    /**
     * Prepares the fixed three-segment V1 layout.
     *
     * @param owner the independently styled owner/player-name segment
     * @param translateOwner whether a non-protected owner may be sent as a literal unit
     * @param protectOwner whether the owner represents a real player or another protected token
     */
    public static Prepared prepare(
            Component prefix,
            Component owner,
            boolean translateOwner,
            boolean protectOwner,
            Component suffix
    ) {
        Component resolvedPrefix = copyOrEmpty(prefix);
        Component resolvedOwner = copyOrEmpty(owner);
        Component resolvedSuffix = copyOrEmpty(suffix);
        String playerName = resolvedOwner.getString();

        LegacyPrepared legacy = prepareLegacy(resolvedPrefix, playerName, translateOwner, resolvedSuffix);
        ComponentTranslationDocument document = prepareV1Document(
                resolvedPrefix,
                resolvedOwner,
                translateOwner,
                protectOwner,
                resolvedSuffix
        );
        Component v1Original = ComponentJsonCodec.decode(document.sourceJson());
        return new Prepared(
                legacy.translationTemplateKey(),
                legacy.templateValues(),
                playerName,
                v1Original,
                document
        );
    }

    public static boolean preservesPlayerNameToken(String sourceTemplate, String translatedTemplate) {
        return countPlayerNameTokens(sourceTemplate) == countPlayerNameTokens(translatedTemplate);
    }

    private static LegacyPrepared prepareLegacy(
            Component prefix,
            String playerName,
            boolean includePlayerName,
            Component suffix
    ) {
        String prefixLegacy = toLegacyText(prefix);
        String suffixLegacy = toLegacyText(suffix);
        if (prefixLegacy.isEmpty() && suffixLegacy.isEmpty()) {
            return new LegacyPrepared("", List.of());
        }

        StringBuilder source = new StringBuilder(prefixLegacy);
        if (includePlayerName) {
            source.append("\u00a7r").append(PLAYER_NAME_TOKEN);
        }
        if (!suffixLegacy.isEmpty()) {
            source.append("\u00a7r").append(suffixLegacy);
        }

        TemplateProcessor.TemplateExtractionResult extractionResult = TemplateProcessor.extract(source.toString());
        return new LegacyPrepared(
                extractionResult.template(),
                List.copyOf(extractionResult.values())
        );
    }

    private static ComponentTranslationDocument prepareV1Document(
            Component prefix,
            Component owner,
            boolean translateOwner,
            boolean protectOwner,
            Component suffix
    ) {
        JsonObject source = new JsonObject();
        source.addProperty("text", "");
        JsonArray segments = new JsonArray();
        segments.add(ComponentJsonCodec.encode(prefix));
        segments.add(ComponentJsonCodec.encode(owner));
        segments.add(ComponentJsonCodec.encode(suffix));
        source.add("extra", segments);

        ComponentTranslationPolicy policy = ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.SCOREBOARD)
                .withContext("scoreboard_entry")
                .withSemanticSetting("route_policy", V1_POLICY_VERSION)
                .withSemanticSetting("layout", "prefix-owner-suffix")
                .withSemanticSetting("translate_prefix_suffix", "true")
                .withSemanticSetting("translate_owner", Boolean.toString(translateOwner))
                .withSemanticSetting("owner_protected", Boolean.toString(protectOwner));
        ComponentTranslationDocument extracted = new ComponentDocumentBuilder().build(source, policy);
        if (translateOwner && !protectOwner) {
            return extracted;
        }

        List<ComponentTextUnit> filteredUnits = extracted.units().stream()
                .filter(unit -> !isOwnerPointer(unit.jsonPointer()))
                .toList();
        return new ComponentTranslationDocument(
                extracted.protocol(),
                extracted.policyVersion(),
                extracted.route(),
                extracted.sourceJson(),
                filteredUnits,
                extracted.semanticSettings()
        );
    }

    private static boolean isOwnerPointer(String pointer) {
        return OWNER_POINTER_PREFIX.equals(pointer)
                || (pointer != null && pointer.startsWith(OWNER_POINTER_PREFIX + "/"));
    }

    private static Component renderLegacy(String template, List<String> templateValues, String playerName) {
        String reassembled = TemplateProcessor.reassemble(
                template == null ? "" : template,
                templateValues == null ? List.of() : templateValues
        );
        return StylePreserver.fromLegacyText(reassembled.replace(PLAYER_NAME_TOKEN, normalizedPlayerName(playerName)));
    }

    private static String toLegacyText(Component text) {
        if (text == null || text.getString().trim().isEmpty()) {
            return "";
        }
        StylePreserver.ExtractionResult styleResult = StylePreserver.extractAndMark(text);
        return StylePreserver.toLegacyTemplate(styleResult.markedText, styleResult.styleMap);
    }

    private static int countPlayerNameTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = 0;
        int start = 0;
        while ((start = text.indexOf(PLAYER_NAME_TOKEN, start)) >= 0) {
            count++;
            start += PLAYER_NAME_TOKEN.length();
        }
        return count;
    }

    private static Component copyOrEmpty(Component component) {
        return component == null ? Component.empty() : component.copy();
    }

    private static String normalizedPlayerName(String playerName) {
        return playerName == null ? "" : playerName;
    }

    private record LegacyPrepared(String translationTemplateKey, List<String> templateValues) {
    }
}
