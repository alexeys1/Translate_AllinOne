package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ComponentJsonDocumentBuilder {
    private final ComponentJsonLimits limits;

    public ComponentJsonDocumentBuilder() {
        this(ComponentJsonLimits.DEFAULT);
    }

    public ComponentJsonDocumentBuilder(ComponentJsonLimits limits) {
        this.limits = limits == null ? ComponentJsonLimits.DEFAULT : limits;
    }

    public ComponentTranslationDocument build(JsonElement sourceJson, ComponentTranslationPolicy policy) {
        if (sourceJson == null || policy == null) {
            throw new ComponentJsonException(ComponentJsonException.Kind.DOCUMENT, "Component JSON and policy are required.");
        }

        byte[] sourceBytes = sourceJson.toString().getBytes(StandardCharsets.UTF_8);
        if (sourceBytes.length > limits.maxDocumentUtf8Bytes()) {
            throw new ComponentJsonException(ComponentJsonException.Kind.LIMIT, "Component JSON exceeds the document size limit.");
        }

        inspectLimits(sourceJson, 1, new int[]{0});
        List<ComponentTextUnit> units = new ArrayList<>();
        collectComponent(sourceJson, "", 1, policy, units);
        return new ComponentTranslationDocument(
                ComponentTranslationDocument.PROTOCOL,
                policy.version(),
                policy.route(),
                sourceJson,
                units,
                policy.semanticSettings()
        );
    }

    private void inspectLimits(JsonElement element, int depth, int[] nodeCount) {
        if (depth > limits.maxDocumentDepth()) {
            throw new ComponentJsonException(ComponentJsonException.Kind.LIMIT, "Component JSON exceeds the depth limit.");
        }
        nodeCount[0]++;
        if (nodeCount[0] > limits.maxDocumentNodes()) {
            throw new ComponentJsonException(ComponentJsonException.Kind.LIMIT, "Component JSON exceeds the node limit.");
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                inspectLimits(child, depth + 1, nodeCount);
            }
        } else if (element.isJsonObject()) {
            for (JsonElement child : element.getAsJsonObject().asMap().values()) {
                inspectLimits(child, depth + 1, nodeCount);
            }
        }
    }

    private void collectComponent(
            JsonElement element,
            String pointer,
            int depth,
            ComponentTranslationPolicy policy,
            List<ComponentTextUnit> units
    ) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                addUnit(pointer, primitive.getAsString(), policy, units);
            }
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                collectComponent(array.get(index), appendPointer(pointer, Integer.toString(index)), depth + 1, policy, units);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        JsonElement text = object.get("text");
        if (text != null) {
            if (!text.isJsonPrimitive() || !text.getAsJsonPrimitive().isString()) {
                throw new ComponentJsonException(ComponentJsonException.Kind.DOCUMENT, "Literal Component text is not a string.");
            }
            addUnit(appendPointer(pointer, "text"), text.getAsString(), policy, units);
        }

        JsonElement extra = object.get("extra");
        if (extra != null) {
            if (!extra.isJsonArray()) {
                throw new ComponentJsonException(ComponentJsonException.Kind.DOCUMENT, "Component extra field is not an array.");
            }
            JsonArray children = extra.getAsJsonArray();
            for (int index = 0; index < children.size(); index++) {
                collectComponent(
                        children.get(index),
                        appendPointer(appendPointer(pointer, "extra"), Integer.toString(index)),
                        depth + 1,
                        policy,
                        units
                );
            }
        }
    }

    private void addUnit(
            String pointer,
            String text,
            ComponentTranslationPolicy policy,
            List<ComponentTextUnit> units
    ) {
        if (!policy.allowsLiteral(text)) {
            return;
        }
        if (units.size() >= limits.maxTextUnits()) {
            throw new ComponentJsonException(ComponentJsonException.Kind.LIMIT, "Component has too many translatable text units.");
        }
        units.add(new ComponentTextUnit(
                "u" + units.size(),
                pointer,
                text,
                policy.protectedTokenMultiset(text),
                policy.context()
        ));
    }

    private static String appendPointer(String base, String segment) {
        return base + "/" + segment.replace("~", "~0").replace("/", "~1");
    }
}
