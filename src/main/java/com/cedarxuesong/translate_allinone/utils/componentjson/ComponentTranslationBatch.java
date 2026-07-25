package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.utils.TranslateStringUtils;
import com.google.gson.JsonArray;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregates same-route V1 documents for one provider request without changing their cache keys. */
public record ComponentTranslationBatch(
        ComponentTranslationDocument requestDocument,
        List<ComponentTranslationDocument> documents
) {
    public ComponentTranslationBatch {
        if (requestDocument == null || documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("Component translation batch is incomplete.");
        }
        documents = List.copyOf(documents);
    }

    public static ComponentTranslationBatch create(List<ComponentTranslationDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("Component translation batch documents are required.");
        }

        ComponentTranslationDocument first = documents.getFirst();
        if (first == null) {
            throw new IllegalArgumentException("Component translation batch contains a null document.");
        }
        if (documents.size() > 1 && first.route() == ComponentTranslationRoute.TOOLTIP_PARAGRAPH) {
            throw new IllegalArgumentException("Tooltip paragraphs must be translated as individual documents.");
        }

        JsonArray sources = new JsonArray();
        List<ComponentTextUnit> units = new ArrayList<>();
        for (int documentIndex = 0; documentIndex < documents.size(); documentIndex++) {
            ComponentTranslationDocument document = documents.get(documentIndex);
            if (document == null
                    || document.route() != first.route()
                    || document.policyVersion() != first.policyVersion()) {
                throw new IllegalArgumentException("Component translation batch documents must use one route and policy version.");
            }
            sources.add(document.sourceJson());
            for (ComponentTextUnit unit : document.units()) {
                String id = batchUnitId(documentIndex, unit.id());
                units.add(new ComponentTextUnit(
                        id,
                        "/" + documentIndex + unit.jsonPointer(),
                        unit.sourceText(),
                        unit.protectedTokens(),
                        buildUnitContext(documents, documentIndex, unit)
                ));
            }
        }

        if (units.size() > ComponentJsonLimits.DEFAULT.maxTextUnits()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.LIMIT,
                    "Component translation batch has too many text units."
            );
        }

        if (sources.toString().getBytes(StandardCharsets.UTF_8).length
                > ComponentJsonLimits.DEFAULT.maxDocumentUtf8Bytes()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.LIMIT,
                    "Component translation batch exceeds the document size limit."
            );
        }

        Map<String, String> settings = new LinkedHashMap<>(first.semanticSettings());
        settings.put("batch", "same-route-tooltip-context");
        settings.put("batch_size", Integer.toString(documents.size()));
        settings.put("batch_boundaries", buildBoundaries(documents));
        ComponentTranslationDocument requestDocument = new ComponentTranslationDocument(
                ComponentTranslationDocument.PROTOCOL,
                first.policyVersion(),
                first.route(),
                sources,
                units,
                settings
        );
        return new ComponentTranslationBatch(requestDocument, documents);
    }

    /** Returns whether adding a document would keep the provider request within V1 limits. */
    static boolean canAppend(
            List<ComponentTranslationDocument> documents,
            ComponentTranslationDocument candidate
    ) {
        if (documents == null || documents.isEmpty() || candidate == null) {
            return false;
        }
        List<ComponentTranslationDocument> combined = new ArrayList<>(documents.size() + 1);
        combined.addAll(documents);
        combined.add(candidate);
        try {
            create(combined);
            return true;
        } catch (ComponentJsonException e) {
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public List<ComponentTranslationResponse> splitResponse(ComponentTranslationResponse response) {
        if (response == null || !requestDocument.protocol().equals(response.protocol())) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.RESPONSE,
                    "Component translation batch response protocol is invalid."
            );
        }

        List<ComponentTranslationResponse> split = new ArrayList<>(documents.size());
        for (int documentIndex = 0; documentIndex < documents.size(); documentIndex++) {
            ComponentTranslationDocument document = documents.get(documentIndex);
            Map<String, String> translations = new LinkedHashMap<>();
            for (ComponentTextUnit unit : document.units()) {
                String batchId = batchUnitId(documentIndex, unit.id());
                String translation = response.translations().get(batchId);
                if (translation == null) {
                    throw new ComponentJsonException(
                            ComponentJsonException.Kind.RESPONSE,
                            "Component translation batch is missing " + batchId + "."
                    );
                }
                translations.put(unit.id(), translation);
            }
            split.add(new ComponentTranslationResponse(response.protocol(), translations));
        }
        if (response.translations().size() != requestDocument.units().size()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.RESPONSE,
                    "Component translation batch returned an unexpected number of units."
            );
        }
        return List.copyOf(split);
    }

    static String batchUnitId(int documentIndex, String unitId) {
        return "b" + documentIndex + ":" + unitId;
    }

    private static String buildUnitContext(
            List<ComponentTranslationDocument> documents,
            int documentIndex,
            ComponentTextUnit unit
    ) {
        StringBuilder context = new StringBuilder(unit.context());
        context.append("; batch_item=").append(documentIndex + 1).append('/').append(documents.size());
        if (documentIndex > 0) {
            context.append("; previous_item=")
                    .append(firstVisibleText(documents.get(documentIndex - 1)));
        }
        if (documentIndex + 1 < documents.size()) {
            context.append("; next_item=")
                    .append(firstVisibleText(documents.get(documentIndex + 1)));
        }
        return context.toString();
    }

    private static String firstVisibleText(ComponentTranslationDocument document) {
        if (document == null || document.units().isEmpty()) {
            return "";
        }
        String joined = document.units().stream()
                .map(ComponentTextUnit::sourceText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
        return TranslateStringUtils.truncate(TranslateStringUtils.normalizeWhitespace(joined), 240);
    }

    private static String buildBoundaries(List<ComponentTranslationDocument> documents) {
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
