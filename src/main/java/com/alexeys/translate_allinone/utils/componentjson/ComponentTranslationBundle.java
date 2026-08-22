package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

public record ComponentTranslationBundle(
        ComponentTranslationDocument cacheDocument,
        List<ComponentTranslationDocument> documents,
        List<ComponentDynamicTemplate> templates,
        List<SemanticSlot> semanticSlots,
        List<AccentAnchor> accentAnchors
) {
    private static final String COHERENT_PARAGRAPH_ID = "paragraph";
    private static final Pattern STYLE_TAG_PATTERN = Pattern.compile("</?s\\d+>");
    private static final Pattern GLYPH_PLACEHOLDER_PATTERN = Pattern.compile("\\{g\\d+}");
    private static final Pattern TEMPLATE_MARKER_PATTERN = Pattern.compile("\\{[A-Za-z][A-Za-z0-9_.:-]*}");

    public ComponentTranslationBundle {
        if (cacheDocument == null || documents == null || templates == null || semanticSlots == null || accentAnchors == null) {
            throw new IllegalArgumentException("Component translation bundle is incomplete.");
        }
        documents = List.copyOf(documents);
        templates = List.copyOf(templates);
        semanticSlots = List.copyOf(semanticSlots);
        accentAnchors = List.copyOf(accentAnchors);
        if (documents.size() != templates.size()) {
            throw new IllegalArgumentException("Component translation bundle templates do not match its documents.");
        }
        if (documents.isEmpty() && cacheDocument.route() != ComponentTranslationRoute.TOOLTIP_PARAGRAPH) {
            throw new IllegalArgumentException("Component translation bundle has no local documents.");
        }
    }

    public record SemanticSlot(
            String id,
            int styleId,
            String sourceText
    ) {
        public SemanticSlot {
            if (id == null || !id.matches("slot\\d+") || styleId < 0 || sourceText == null || sourceText.isBlank()) {
                throw new IllegalArgumentException("Component semantic slot is invalid.");
            }
        }

        public String placeholder() {
            return "{" + id + "}";
        }
    }

    public record AccentAnchor(String id) {
        public AccentAnchor {
            if (id == null || !id.matches("accent\\d+")) {
                throw new IllegalArgumentException("Component accent anchor is invalid.");
            }
        }

        public String beginToken() {
            return "{" + id + ".begin}";
        }

        public String endToken() {
            return "{" + id + ".end}";
        }
    }

    public static ComponentTranslationBundle create(
            List<Component> components,
            ComponentTranslationRoute route,
            String context,
            String policyVersion
    ) {
        return create(components, route, context, policyVersion, false);
    }

    public static ComponentTranslationBundle create(
            List<Component> components,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            boolean isolateBatchContext
    ) {
        if (components == null || components.isEmpty() || route == null) {
            throw new IllegalArgumentException("Component translation bundle input is incomplete.");
        }

        ComponentDocumentBuilder builder = new ComponentDocumentBuilder();
        List<ComponentTranslationDocument> documents = new ArrayList<>(components.size());
        List<ComponentDynamicTemplate> templates = new ArrayList<>(components.size());
        JsonArray sourceLines = new JsonArray();
        List<ComponentTextUnit> bundleUnits = new ArrayList<>();
        int lineCount = components.size();

        for (Component component : components) {
            templates.add(ComponentDynamicTemplate.prepare(component));
        }

        for (int lineIndex = 0; lineIndex < components.size(); lineIndex++) {
            ComponentDynamicTemplate template = templates.get(lineIndex);
            String lineContext = buildLineContext(templates, lineIndex, context);
            ComponentTranslationPolicy policy = ComponentTranslationPolicy.forRoute(route)
                    .withContext(lineContext)
                    .withSemanticSetting("bundle_policy", policyVersion == null ? "1" : policyVersion);
            ComponentTranslationDocument document = builder.build(template.templateComponent(), policy);
            documents.add(document);
            sourceLines.add(document.sourceJson());

            for (ComponentTextUnit unit : document.units()) {
                bundleUnits.add(new ComponentTextUnit(
                        "l" + lineIndex + ":" + unit.id(),
                        "/" + lineIndex + unit.jsonPointer(),
                        unit.sourceText(),
                        unit.protectedTokens(),
                        lineContext
                ));
            }
            if (bundleUnits.size() > ComponentJsonLimits.DEFAULT.maxTextUnits()) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.LIMIT,
                        "Component translation bundle has too many text units."
                );
            }
        }

        if (sourceLines.toString().getBytes(StandardCharsets.UTF_8).length
                > ComponentJsonLimits.DEFAULT.maxDocumentUtf8Bytes()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.LIMIT,
                    "Component translation bundle exceeds the document size limit."
            );
        }

        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("bundle", "ordered-components");
        settings.put("bundle_policy", policyVersion == null ? "1" : policyVersion);
        if (isolateBatchContext) {
            settings.put("batch_context", "isolated");
        }
        settings.put("line_count", Integer.toString(lineCount));
        settings.put("line_boundaries", buildLineBoundaries(documents));
        ComponentTranslationDocument cacheDocument = new ComponentTranslationDocument(
                ComponentTranslationDocument.PROTOCOL,
                ComponentTranslationPolicy.CURRENT_VERSION,
                route,
                sourceLines,
                bundleUnits,
                settings
        );
        return new ComponentTranslationBundle(cacheDocument, documents, templates, List.of(), List.of());
    }

    public static ComponentTranslationBundle createCoherentParagraph(
            List<Component> components,
            String context,
            String policyVersion,
            String paragraphTemplate
    ) {
        return createCoherentParagraph(components, context, policyVersion, paragraphTemplate, List.of());
    }

    public static ComponentTranslationBundle createCoherentParagraph(
            List<Component> components,
            String context,
            String policyVersion,
            String paragraphTemplate,
            List<SemanticSlot> semanticSlots
    ) {
        if (components == null
                || components.isEmpty()
                || paragraphTemplate == null
                || semanticSlots == null
                || paragraphTemplate.isBlank()) {
            throw new IllegalArgumentException("Coherent paragraph translation input is incomplete.");
        }

        String resolvedContext = context == null || context.isBlank()
                ? "tooltip:paragraph:coherent"
                : context.trim();
        String resolvedPolicyVersion = policyVersion == null || policyVersion.isBlank()
                ? "paragraph-v4"
                : policyVersion.trim();
        ComponentTranslationPolicy policy = ComponentTranslationPolicy
                .forRoute(ComponentTranslationRoute.TOOLTIP_PARAGRAPH)
                .withContext(resolvedContext)
                .withSemanticSetting("bundle", "coherent-paragraph")
                .withSemanticSetting("bundle_policy", resolvedPolicyVersion);
        if (!semanticSlots.isEmpty()) {
            policy = policy.withSemanticSetting("style_binding", "semantic-slot-v1");
        }
        if (!decorativeGlyphSemanticAnchors(paragraphTemplate, semanticSlots).isEmpty()) {
            policy = policy.withSemanticSetting("glyph_binding", "semantic-anchor-v1");
        }
        if (!policy.allowsLiteral(paragraphTemplate)) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.DOCUMENT,
                    "Coherent paragraph contains text that is not eligible for translation."
            );
        }

        ComponentDocumentBuilder builder = new ComponentDocumentBuilder();
        List<ComponentTranslationDocument> documents = new ArrayList<>(components.size());
        List<ComponentDynamicTemplate> templates = new ArrayList<>(components.size());
        JsonArray sourceLines = new JsonArray();
        for (Component component : components) {
            if (component == null) {
                throw new IllegalArgumentException("Coherent paragraph contains a null Component.");
            }
            ComponentDynamicTemplate template = ComponentDynamicTemplate.prepare(component);
            ComponentTranslationDocument document = builder.build(template.templateComponent(), policy);
            documents.add(document);
            templates.add(template);
            sourceLines.add(document.sourceJson());
        }

        JsonObject sourceEnvelope = new JsonObject();
        sourceEnvelope.add("lines", sourceLines);
        sourceEnvelope.addProperty(COHERENT_PARAGRAPH_ID, paragraphTemplate);
        JsonArray sourceSlots = new JsonArray();
        for (SemanticSlot slot : semanticSlots) {
            if (slot == null || !paragraphTemplate.contains("<s" + slot.styleId() + ">" + slot.placeholder() + "</s" + slot.styleId() + ">")) {
                throw new IllegalArgumentException("Coherent paragraph semantic slot is not bound to its source style.");
            }
            JsonObject sourceSlot = new JsonObject();
            sourceSlot.addProperty("id", slot.id());
            sourceSlot.addProperty("style_id", slot.styleId());
            sourceSlot.addProperty("source", slot.sourceText());
            sourceSlots.add(sourceSlot);
        }
        sourceEnvelope.add("semantic_slots", sourceSlots);
        if (sourceEnvelope.toString().getBytes(StandardCharsets.UTF_8).length
                > ComponentJsonLimits.DEFAULT.maxDocumentUtf8Bytes()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.LIMIT,
                    "Coherent paragraph translation bundle exceeds the document size limit."
            );
        }

        List<ComponentTextUnit> paragraphUnits = new ArrayList<>();
        paragraphUnits.add(new ComponentTextUnit(
                COHERENT_PARAGRAPH_ID,
                "/" + COHERENT_PARAGRAPH_ID,
                paragraphTemplate,
                policy.protectedTokenMultiset(paragraphTemplate),
                resolvedContext + "; complete paragraph assembled from " + components.size() + " wrapped UI lines"
        ));
        for (int slotIndex = 0; slotIndex < semanticSlots.size(); slotIndex++) {
            SemanticSlot slot = semanticSlots.get(slotIndex);
            paragraphUnits.add(new ComponentTextUnit(
                    slot.id(),
                    "/semantic_slots/" + slotIndex + "/source",
                    slot.sourceText(),
                    policy.protectedTokenMultiset(slot.sourceText()),
                    resolvedContext + "; semantic style slot s" + slot.styleId()
            ));
        }
        Map<String, String> settings = new LinkedHashMap<>(policy.semanticSettings());
        settings.put("line_count", Integer.toString(components.size()));
        settings.put("line_boundaries", buildLineBoundaries(documents));
        ComponentTranslationDocument cacheDocument = new ComponentTranslationDocument(
                ComponentTranslationDocument.PROTOCOL,
                policy.version(),
                ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                sourceEnvelope,
                paragraphUnits,
                settings
        );
        return new ComponentTranslationBundle(cacheDocument, documents, templates, semanticSlots, List.of());
    }

    public static ComponentTranslationBundle createInlineAnchoredParagraph(
            String context,
            String policyVersion,
            String paragraphTemplate,
            List<String> hardTokenIds,
            List<String> accentIds
    ) {
        if (paragraphTemplate == null
                || paragraphTemplate.isBlank()
                || hardTokenIds == null
                || accentIds == null) {
            throw new IllegalArgumentException("Inline-anchored paragraph translation input is incomplete.");
        }
        String resolvedContext = context == null || context.isBlank()
                ? "tooltip:paragraph:coherent"
                : context.trim();
        String resolvedPolicyVersion = policyVersion == null || policyVersion.isBlank()
                ? "paragraph-v6"
                : policyVersion.trim();
        if (hardTokenIds.stream().distinct().count() != hardTokenIds.size()
                || accentIds.stream().distinct().count() != accentIds.size()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.DOCUMENT,
                    "Coherent paragraph token topology contains duplicate identifiers."
            );
        }
        ComponentTranslationPolicy policy = ComponentTranslationPolicy
                .forRoute(ComponentTranslationRoute.TOOLTIP_PARAGRAPH)
                .withContext(resolvedContext)
                .withSemanticSetting("bundle", "coherent-paragraph")
                .withSemanticSetting("bundle_policy", resolvedPolicyVersion)
                .withSemanticSetting("style_binding", "inline-anchor-v1")
                .withSemanticSetting("hard_token_topology", String.join(",", hardTokenIds))
                .withSemanticSetting("accent_topology", String.join(",", accentIds));
        if (!policy.allowsLiteral(paragraphTemplate)) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.DOCUMENT,
                    "Coherent paragraph contains text that is not eligible for translation."
            );
        }

        List<AccentAnchor> anchors = accentIds.stream().map(AccentAnchor::new).toList();
        for (String token : hardTokenIds) {
            if (token == null
                    || !token.matches("\\{(?:value|glyph)\\d+}")
                    || countOccurrences(paragraphTemplate, token) != 1) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.DOCUMENT,
                        "Coherent paragraph hard token topology is invalid: " + token
                );
            }
        }
        for (AccentAnchor anchor : anchors) {
            if (countOccurrences(paragraphTemplate, anchor.beginToken()) != 1
                    || countOccurrences(paragraphTemplate, anchor.endToken()) != 1) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.DOCUMENT,
                        "Coherent paragraph accent topology is invalid: " + anchor.id()
                );
            }
        }
        List<String> knownMarkers = new ArrayList<>(hardTokenIds);
        for (AccentAnchor anchor : anchors) {
            knownMarkers.add(anchor.beginToken());
            knownMarkers.add(anchor.endToken());
        }
        java.util.regex.Matcher markerMatcher = TEMPLATE_MARKER_PATTERN.matcher(paragraphTemplate);
        while (markerMatcher.find()) {
            if (!knownMarkers.contains(markerMatcher.group())) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.DOCUMENT,
                        "Coherent paragraph contains an unknown template marker: " + markerMatcher.group()
                );
            }
        }
        String markerStripped = TEMPLATE_MARKER_PATTERN.matcher(paragraphTemplate).replaceAll("");
        if (markerStripped.indexOf('{') >= 0 || markerStripped.indexOf('}') >= 0) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.DOCUMENT,
                    "Coherent paragraph contains an unparsed template delimiter."
            );
        }

        JsonObject sourceEnvelope = new JsonObject();
        sourceEnvelope.addProperty(COHERENT_PARAGRAPH_ID, paragraphTemplate);
        JsonArray hardTopology = new JsonArray();
        hardTokenIds.forEach(hardTopology::add);
        sourceEnvelope.add("hard_token_topology", hardTopology);
        JsonArray accentTopology = new JsonArray();
        accentIds.forEach(accentTopology::add);
        sourceEnvelope.add("accent_topology", accentTopology);
        if (sourceEnvelope.toString().getBytes(StandardCharsets.UTF_8).length
                > ComponentJsonLimits.DEFAULT.maxDocumentUtf8Bytes()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.LIMIT,
                    "Inline-anchored paragraph translation bundle exceeds the document size limit."
            );
        }

        ComponentTextUnit paragraphUnit = new ComponentTextUnit(
                COHERENT_PARAGRAPH_ID,
                "/" + COHERENT_PARAGRAPH_ID,
                paragraphTemplate,
                policy.protectedTokenMultiset(paragraphTemplate),
                resolvedContext + "; complete paragraph with local hard tokens and semantic accent anchors"
        );
        ComponentTranslationDocument cacheDocument = new ComponentTranslationDocument(
                ComponentTranslationDocument.PROTOCOL,
                policy.version(),
                ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                sourceEnvelope,
                List.of(paragraphUnit),
                policy.semanticSettings()
        );
        return new ComponentTranslationBundle(cacheDocument, List.of(), List.of(), List.of(), anchors);
    }

    public List<Component> apply(ComponentTranslationResponse response) {
        new ComponentTranslationValidator().validate(cacheDocument, response);
        ComponentTranslationApplier applier = new ComponentTranslationApplier();
        List<Component> result = new ArrayList<>(documents.size());
        for (int lineIndex = 0; lineIndex < documents.size(); lineIndex++) {
            ComponentTranslationDocument document = documents.get(lineIndex);
            Map<String, String> lineTranslations = new LinkedHashMap<>();
            for (ComponentTextUnit unit : document.units()) {
                String translation = response.translations().get("l" + lineIndex + ":" + unit.id());
                lineTranslations.put(unit.id(), translation);
            }
            Component translatedTemplate = applier.apply(
                    document,
                    new ComponentTranslationResponse(response.protocol(), lineTranslations)
            );
            result.add(templates.get(lineIndex).restore(translatedTemplate));
        }
        return List.copyOf(result);
    }

    public String coherentParagraphTranslation(ComponentTranslationResponse response) {
        new ComponentTranslationValidator().validate(cacheDocument, response);
        if (cacheDocument.units().isEmpty()
                || !COHERENT_PARAGRAPH_ID.equals(cacheDocument.units().getFirst().id())) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.APPLY,
                    "Translation bundle is not a coherent paragraph."
            );
        }
        String paragraph = response.translations().get(COHERENT_PARAGRAPH_ID);
        if (isInlineAnchorBundle()) {
            return paragraph;
        }
        validateDecorativeGlyphSemanticAnchors(
                cacheDocument.units().getFirst().sourceText(),
                paragraph,
                semanticSlots
        );
        for (SemanticSlot slot : semanticSlots) {
            String translatedSlot = response.translations().get(slot.id());
            if (translatedSlot == null || translatedSlot.isBlank()) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.APPLY,
                        "Semantic slot translation is empty: " + slot.id()
                );
            }
            String styledPlaceholder = "<s" + slot.styleId() + ">" + slot.placeholder() + "</s" + slot.styleId() + ">";
            if (countOccurrences(paragraph, styledPlaceholder) != 1) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.APPLY,
                        "Semantic slot style binding is invalid: slot=" + slot.id() + ", style=s" + slot.styleId()
                );
            }
            paragraph = paragraph.replace(styledPlaceholder, "<s" + slot.styleId() + ">" + translatedSlot.trim() + "</s" + slot.styleId() + ">");
        }
        return paragraph;
    }

    public String coherentSafeBodyParagraphTranslation(
            ComponentTranslationResponse response,
            Integer bodyStyleId
    ) {
        if (isInlineAnchorBundle()) {
            new ComponentTranslationValidator().validate(cacheDocument, response);
            String paragraph = response.translations().get(COHERENT_PARAGRAPH_ID);
            if (paragraph == null || paragraph.isBlank()) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.APPLY,
                        "Coherent paragraph safe-body candidate is empty."
                );
            }
            return paragraph;
        }
        if (response != null && !cacheDocument.units().isEmpty()) {
            validateDecorativeGlyphSemanticAnchors(
                    cacheDocument.units().getFirst().sourceText(),
                    response.translations().get(COHERENT_PARAGRAPH_ID),
                    semanticSlots
            );
        }
        ComponentTranslationResponse safeResponse = validateSafeBodyResponse(response);
        String paragraph = safeResponse.translations().get(COHERENT_PARAGRAPH_ID);
        if (paragraph == null || paragraph.isBlank()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.APPLY,
                    "Coherent paragraph safe-body candidate is empty."
            );
        }
        paragraph = paragraph.replaceAll("</?s\\d+>", "");
        for (SemanticSlot slot : semanticSlots) {
            String translatedSlot = safeResponse.translations().get(slot.id());
            if (translatedSlot == null || translatedSlot.isBlank() || countOccurrences(paragraph, slot.placeholder()) != 1) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.APPLY,
                        "Coherent paragraph safe-body semantic slot is invalid: " + slot.id()
                );
            }
            paragraph = paragraph.replace(slot.placeholder(), translatedSlot.trim());
        }
        if (bodyStyleId == null) {
            return paragraph;
        }
        return "<s" + bodyStyleId + ">" + paragraph + "</s" + bodyStyleId + ">";
    }

    private boolean isInlineAnchorBundle() {
        return "inline-anchor-v1".equals(cacheDocument.semanticSettings().get("style_binding"));
    }

    private ComponentTranslationResponse validateSafeBodyResponse(ComponentTranslationResponse response) {
        if (response == null) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.VALIDATION,
                    "Coherent paragraph safe-body response is missing."
            );
        }
        List<ComponentTextUnit> safeUnits = new ArrayList<>(cacheDocument.units().size());
        for (ComponentTextUnit unit : cacheDocument.units()) {
            safeUnits.add(new ComponentTextUnit(
                    unit.id(),
                    unit.jsonPointer(),
                    STYLE_TAG_PATTERN.matcher(unit.sourceText()).replaceAll(""),
                    unit.protectedTokens(),
                    unit.context()
            ));
        }
        ComponentTranslationDocument safeDocument = new ComponentTranslationDocument(
                cacheDocument.protocol(),
                cacheDocument.policyVersion(),
                cacheDocument.route(),
                cacheDocument.sourceJson(),
                safeUnits,
                cacheDocument.semanticSettings()
        );
        Map<String, String> safeTranslations = new LinkedHashMap<>(response.translations());
        String paragraph = safeTranslations.get(COHERENT_PARAGRAPH_ID);
        if (paragraph != null) {
            safeTranslations.put(COHERENT_PARAGRAPH_ID, STYLE_TAG_PATTERN.matcher(paragraph).replaceAll(""));
        }
        ComponentTranslationResponse safeResponse = new ComponentTranslationResponse(
                response.protocol(),
                safeTranslations
        );
        return new ComponentTranslationValidator().validate(safeDocument, safeResponse);
    }

    public ComponentTranslationResponse promoteLegacyCoherentParagraphResponse(
            ComponentTranslationBundle legacyBundle,
            ComponentTranslationResponse legacyResponse
    ) {
        if (legacyBundle == null || !legacyBundle.semanticSlots().isEmpty() || semanticSlots.isEmpty()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.APPLY,
                    "Legacy paragraph promotion input is incompatible."
            );
        }
        String paragraph = legacyBundle.coherentParagraphTranslation(legacyResponse);
        Map<Integer, Integer> slotCountsByStyle = new LinkedHashMap<>();
        for (SemanticSlot slot : semanticSlots) {
            slotCountsByStyle.merge(slot.styleId(), 1, Integer::sum);
        }
        if (slotCountsByStyle.values().stream().anyMatch(count -> count != 1)) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.APPLY,
                    "Legacy paragraph semantic styles are ambiguous."
            );
        }

        Map<String, String> promotedTranslations = new LinkedHashMap<>();
        for (SemanticSlot slot : semanticSlots) {
            java.util.regex.Pattern spanPattern = java.util.regex.Pattern.compile(
                    "<s" + slot.styleId() + ">(.*?)</s" + slot.styleId() + ">"
            );
            java.util.regex.Matcher matcher = spanPattern.matcher(paragraph);
            if (!matcher.find()) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.APPLY,
                        "Legacy paragraph semantic style cannot be promoted safely: s" + slot.styleId()
                );
            }
            String translatedSlot = matcher.group(1).trim();
            if (translatedSlot.isBlank() || matcher.find()) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.APPLY,
                        "Legacy paragraph semantic style cannot be promoted safely: s" + slot.styleId()
                );
            }
            paragraph = spanPattern.matcher(paragraph).replaceFirst(
                    java.util.regex.Matcher.quoteReplacement(
                            "<s" + slot.styleId() + ">" + slot.placeholder() + "</s" + slot.styleId() + ">"
                    )
            );
            promotedTranslations.put(slot.id(), translatedSlot);
        }
        promotedTranslations.put(COHERENT_PARAGRAPH_ID, paragraph);
        ComponentTranslationResponse promoted = new ComponentTranslationResponse(
                cacheDocument.protocol(),
                promotedTranslations
        );
        return new ComponentTranslationValidator().validate(cacheDocument, promoted);
    }

    private static int countOccurrences(String text, String value) {
        if (text == null || text.isEmpty() || value == null || value.isEmpty()) {
            return 0;
        }
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    private static void validateDecorativeGlyphSemanticAnchors(
            String sourceParagraph,
            String translatedParagraph,
            List<SemanticSlot> semanticSlots
    ) {
        if (translatedParagraph == null) {
            return;
        }
        String plainTranslation = STYLE_TAG_PATTERN.matcher(translatedParagraph).replaceAll("");
        for (DecorativeGlyphSemanticAnchor anchor : decorativeGlyphSemanticAnchors(sourceParagraph, semanticSlots)) {
            String first = anchor.slotBeforeGlyph() ? anchor.slotPlaceholder() : anchor.glyphPlaceholder();
            String second = anchor.slotBeforeGlyph() ? anchor.glyphPlaceholder() : anchor.slotPlaceholder();
            Pattern adjacency = Pattern.compile(Pattern.quote(first) + "\\s*" + Pattern.quote(second));
            if (!adjacency.matcher(plainTranslation).find()) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.APPLY,
                        "Decorative glyph semantic anchor changed: glyph=" + anchor.glyphPlaceholder()
                                + ", slot=" + anchor.slotPlaceholder()
                                + ", relation=" + (anchor.slotBeforeGlyph() ? "after-slot" : "before-slot")
                );
            }
        }
    }

    private static List<DecorativeGlyphSemanticAnchor> decorativeGlyphSemanticAnchors(
            String paragraph,
            List<SemanticSlot> semanticSlots
    ) {
        if (paragraph == null || paragraph.isBlank() || semanticSlots == null || semanticSlots.isEmpty()) {
            return List.of();
        }
        String plainParagraph = STYLE_TAG_PATTERN.matcher(paragraph).replaceAll("");
        java.util.regex.Matcher glyphMatcher = GLYPH_PLACEHOLDER_PATTERN.matcher(plainParagraph);
        List<DecorativeGlyphSemanticAnchor> anchors = new ArrayList<>();
        while (glyphMatcher.find()) {
            String glyphPlaceholder = glyphMatcher.group();
            String before = plainParagraph.substring(0, glyphMatcher.start()).stripTrailing();
            String after = plainParagraph.substring(glyphMatcher.end()).stripLeading();
            for (SemanticSlot slot : semanticSlots) {
                if (before.endsWith(slot.placeholder())) {
                    anchors.add(new DecorativeGlyphSemanticAnchor(slot.placeholder(), glyphPlaceholder, true));
                }
                if (after.startsWith(slot.placeholder())) {
                    anchors.add(new DecorativeGlyphSemanticAnchor(slot.placeholder(), glyphPlaceholder, false));
                }
            }
        }
        return List.copyOf(anchors);
    }

    private record DecorativeGlyphSemanticAnchor(
            String slotPlaceholder,
            String glyphPlaceholder,
            boolean slotBeforeGlyph
    ) {
    }

    private static String buildLineContext(List<ComponentDynamicTemplate> templates, int index, String context) {
        StringBuilder result = new StringBuilder(context == null || context.isBlank() ? "component_bundle" : context.trim());
        result.append(" line ").append(index + 1).append('/').append(templates.size());
        if (index > 0) {
            result.append(" previous=").append(templates.get(index - 1).templateComponent().getString());
        }
        if (index + 1 < templates.size()) {
            result.append(" next=").append(templates.get(index + 1).templateComponent().getString());
        }
        return result.toString();
    }

    private static String buildLineBoundaries(List<ComponentTranslationDocument> documents) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < documents.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(index).append(':').append(documents.get(index).units().size());
        }
        return result.toString();
    }
}
