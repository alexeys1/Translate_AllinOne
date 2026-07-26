package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

public record ComponentTranslationBundle(
        ComponentTranslationDocument cacheDocument,
        List<ComponentTranslationDocument> documents
) {
    private static final String COHERENT_PARAGRAPH_ID = "paragraph";

    public ComponentTranslationBundle {
        if (cacheDocument == null || documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("Component translation bundle is incomplete.");
        }
        documents = List.copyOf(documents);
    }

    public static ComponentTranslationBundle create(
            List<Component> components,
            ComponentTranslationRoute route,
            String context,
            String policyVersion
    ) {
        if (components == null || components.isEmpty() || route == null) {
            throw new IllegalArgumentException("Component translation bundle input is incomplete.");
        }

        ComponentDocumentBuilder builder = new ComponentDocumentBuilder();
        List<ComponentTranslationDocument> documents = new ArrayList<>(components.size());
        JsonArray sourceLines = new JsonArray();
        List<ComponentTextUnit> bundleUnits = new ArrayList<>();
        int lineCount = components.size();

        for (int lineIndex = 0; lineIndex < components.size(); lineIndex++) {
            Component component = components.get(lineIndex);
            String lineContext = buildLineContext(components, lineIndex, context);
            ComponentTranslationPolicy policy = ComponentTranslationPolicy.forRoute(route)
                    .withContext(lineContext)
                    .withSemanticSetting("bundle_policy", policyVersion == null ? "1" : policyVersion);
            ComponentTranslationDocument document = builder.build(component, policy);
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
        return new ComponentTranslationBundle(cacheDocument, documents);
    }

    public static ComponentTranslationBundle createCoherentParagraph(
            List<Component> components,
            String context,
            String policyVersion,
            String paragraphTemplate
    ) {
        if (components == null
                || components.isEmpty()
                || paragraphTemplate == null
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
        if (!policy.allowsLiteral(paragraphTemplate)) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.DOCUMENT,
                    "Coherent paragraph contains text that is not eligible for translation."
            );
        }

        ComponentDocumentBuilder builder = new ComponentDocumentBuilder();
        List<ComponentTranslationDocument> documents = new ArrayList<>(components.size());
        JsonArray sourceLines = new JsonArray();
        for (Component component : components) {
            if (component == null) {
                throw new IllegalArgumentException("Coherent paragraph contains a null Component.");
            }
            ComponentTranslationDocument document = builder.build(component, policy);
            documents.add(document);
            sourceLines.add(document.sourceJson());
        }

        JsonObject sourceEnvelope = new JsonObject();
        sourceEnvelope.add("lines", sourceLines);
        sourceEnvelope.addProperty(COHERENT_PARAGRAPH_ID, paragraphTemplate);
        if (sourceEnvelope.toString().getBytes(StandardCharsets.UTF_8).length
                > ComponentJsonLimits.DEFAULT.maxDocumentUtf8Bytes()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.LIMIT,
                    "Coherent paragraph translation bundle exceeds the document size limit."
            );
        }

        ComponentTextUnit paragraphUnit = new ComponentTextUnit(
                COHERENT_PARAGRAPH_ID,
                "/" + COHERENT_PARAGRAPH_ID,
                paragraphTemplate,
                policy.protectedTokenMultiset(paragraphTemplate),
                resolvedContext + "; complete paragraph assembled from " + components.size() + " wrapped UI lines"
        );
        Map<String, String> settings = new LinkedHashMap<>(policy.semanticSettings());
        settings.put("line_count", Integer.toString(components.size()));
        settings.put("line_boundaries", buildLineBoundaries(documents));
        ComponentTranslationDocument cacheDocument = new ComponentTranslationDocument(
                ComponentTranslationDocument.PROTOCOL,
                policy.version(),
                ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                sourceEnvelope,
                List.of(paragraphUnit),
                settings
        );
        return new ComponentTranslationBundle(cacheDocument, documents);
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
            result.add(applier.apply(
                    document,
                    new ComponentTranslationResponse(response.protocol(), lineTranslations)
            ));
        }
        return List.copyOf(result);
    }

    public String coherentParagraphTranslation(ComponentTranslationResponse response) {
        new ComponentTranslationValidator().validate(cacheDocument, response);
        if (cacheDocument.units().size() != 1
                || !COHERENT_PARAGRAPH_ID.equals(cacheDocument.units().getFirst().id())) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.APPLY,
                    "Translation bundle is not a coherent paragraph."
            );
        }
        return response.translations().get(COHERENT_PARAGRAPH_ID);
    }

    private static String buildLineContext(List<Component> components, int index, String context) {
        StringBuilder result = new StringBuilder(context == null || context.isBlank() ? "component_bundle" : context.trim());
        result.append(" line ").append(index + 1).append('/').append(components.size());
        if (index > 0 && components.get(index - 1) != null) {
            result.append(" previous=").append(components.get(index - 1).getString());
        }
        if (index + 1 < components.size() && components.get(index + 1) != null) {
            result.append(" next=").append(components.get(index + 1).getString());
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
