package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.utils.text.TemplateProcessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.text.Text;

public final class ComponentDynamicTemplate {
    private static final String PRIVATE_PLACEHOLDER_PREFIX = "{taio.private.";

    private final Text templateComponent;
    private final Map<String, List<String>> numericValuesByPointer;
    private final Map<String, String> privateValuesByPlaceholder;

    private ComponentDynamicTemplate(
            Text templateComponent,
            Map<String, List<String>> numericValuesByPointer,
            Map<String, String> privateValuesByPlaceholder
    ) {
        this.templateComponent = templateComponent == null ? Text.empty() : templateComponent.copy();
        this.numericValuesByPointer = immutableValuesByPointer(numericValuesByPointer);
        this.privateValuesByPlaceholder = Map.copyOf(new LinkedHashMap<>(privateValuesByPlaceholder));
    }

    public static ComponentDynamicTemplate prepare(Text source) {
        return prepare(source, Set.of());
    }

    public static ComponentDynamicTemplate prepare(Text source, Set<String> privateTokens) {
        JsonElement sourceJson = ComponentJsonCodec.encode(source == null ? Text.empty() : source);
        Map<String, List<String>> numericValuesByPointer = new LinkedHashMap<>();
        Map<String, String> privateValuesByPlaceholder = new LinkedHashMap<>();
        List<String> normalizedPrivateTokens = normalizePrivateTokens(privateTokens);
        JsonElement templateJson = replaceDynamicLiterals(
                sourceJson.deepCopy(),
                "",
                numericValuesByPointer,
                privateValuesByPlaceholder,
                normalizedPrivateTokens
        );
        return new ComponentDynamicTemplate(
                ComponentJsonCodec.decode(templateJson),
                numericValuesByPointer,
                privateValuesByPlaceholder
        );
    }

    public Text templateComponent() {
        return templateComponent.copy();
    }

    public Set<String> privatePlaceholders() {
        return Set.copyOf(privateValuesByPlaceholder.keySet());
    }

    public boolean hasDynamicValues() {
        return !numericValuesByPointer.isEmpty() || !privateValuesByPlaceholder.isEmpty();
    }

    public Text restore(Text translatedTemplate) {
        if (translatedTemplate == null) {
            return Text.empty();
        }
        if (!hasDynamicValues()) {
            return translatedTemplate;
        }
        JsonElement restoredJson = restoreDynamicLiterals(
                ComponentJsonCodec.encode(translatedTemplate).deepCopy(),
                ""
        );
        return ComponentJsonCodec.decode(restoredJson);
    }

    private JsonElement restoreDynamicLiterals(JsonElement element, String pointer) {
        if (element == null || element.isJsonNull()) {
            return element;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            return primitive.isString()
                    ? new JsonPrimitive(restoreLiteral(primitive.getAsString(), pointer))
                    : element;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                array.set(index, restoreDynamicLiterals(array.get(index), appendPointer(pointer, Integer.toString(index))));
            }
            return array;
        }
        if (!element.isJsonObject()) {
            return element;
        }

        JsonObject object = element.getAsJsonObject();
        JsonElement text = object.get("text");
        if (text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
            object.addProperty("text", restoreLiteral(text.getAsString(), appendPointer(pointer, "text")));
        }
        JsonElement extra = object.get("extra");
        if (extra != null && extra.isJsonArray()) {
            JsonArray children = extra.getAsJsonArray();
            String extraPointer = appendPointer(pointer, "extra");
            for (int index = 0; index < children.size(); index++) {
                children.set(
                        index,
                        restoreDynamicLiterals(children.get(index), appendPointer(extraPointer, Integer.toString(index)))
                );
            }
        }
        return object;
    }

    private String restoreLiteral(String translated, String pointer) {
        List<String> numericValues = numericValuesByPointer.get(pointer);
        String restored = numericValues == null
                ? translated
                : TemplateProcessor.reassemble(translated, numericValues);
        for (Map.Entry<String, String> privateValue : privateValuesByPlaceholder.entrySet()) {
            restored = restored.replace(privateValue.getKey(), privateValue.getValue());
        }
        return restored;
    }

    private static JsonElement replaceDynamicLiterals(
            JsonElement element,
            String pointer,
            Map<String, List<String>> numericValuesByPointer,
            Map<String, String> privateValuesByPlaceholder,
            List<String> privateTokens
    ) {
        if (element == null || element.isJsonNull()) {
            return element;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            return primitive.isString()
                    ? replaceLiteral(
                    primitive.getAsString(),
                    pointer,
                    numericValuesByPointer,
                    privateValuesByPlaceholder,
                    privateTokens
            )
                    : element;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                array.set(
                        index,
                        replaceDynamicLiterals(
                                array.get(index),
                                appendPointer(pointer, Integer.toString(index)),
                                numericValuesByPointer,
                                privateValuesByPlaceholder,
                                privateTokens
                        )
                );
            }
            return array;
        }
        if (!element.isJsonObject()) {
            return element;
        }

        JsonObject object = element.getAsJsonObject();
        JsonElement text = object.get("text");
        if (text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
            object.add(
                    "text",
                    replaceLiteral(
                            text.getAsString(),
                            appendPointer(pointer, "text"),
                            numericValuesByPointer,
                            privateValuesByPlaceholder,
                            privateTokens
                    )
            );
        }
        JsonElement extra = object.get("extra");
        if (extra != null && extra.isJsonArray()) {
            JsonArray children = extra.getAsJsonArray();
            String extraPointer = appendPointer(pointer, "extra");
            for (int index = 0; index < children.size(); index++) {
                children.set(
                        index,
                        replaceDynamicLiterals(
                                children.get(index),
                                appendPointer(extraPointer, Integer.toString(index)),
                                numericValuesByPointer,
                                privateValuesByPlaceholder,
                                privateTokens
                        )
                );
            }
        }
        return object;
    }

    private static JsonPrimitive replaceLiteral(
            String text,
            String pointer,
            Map<String, List<String>> numericValuesByPointer,
            Map<String, String> privateValuesByPlaceholder,
            List<String> privateTokens
    ) {
        String protectedText = replacePrivateTokens(text, privateTokens, privateValuesByPlaceholder);
        TemplateProcessor.TemplateExtractionResult extracted = TemplateProcessor.extract(protectedText);
        if (!extracted.values().isEmpty()) {
            numericValuesByPointer.put(pointer, List.copyOf(extracted.values()));
        }
        return new JsonPrimitive(extracted.template());
    }

    private static String replacePrivateTokens(
            String text,
            List<String> privateTokens,
            Map<String, String> privateValuesByPlaceholder
    ) {
        if (text == null || text.isEmpty() || privateTokens.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length());
        int cursor = 0;
        while (cursor < text.length()) {
            String match = null;
            for (String privateToken : privateTokens) {
                if (text.startsWith(privateToken, cursor)) {
                    match = privateToken;
                    break;
                }
            }
            if (match == null) {
                result.append(text.charAt(cursor++));
                continue;
            }
            String placeholder = PRIVATE_PLACEHOLDER_PREFIX
                    + alphabeticIndex(privateValuesByPlaceholder.size())
                    + "}";
            privateValuesByPlaceholder.put(placeholder, match);
            result.append(placeholder);
            cursor += match.length();
        }
        return result.toString();
    }

    private static List<String> normalizePrivateTokens(Set<String> privateTokens) {
        if (privateTokens == null || privateTokens.isEmpty()) {
            return List.of();
        }
        return privateTokens.stream()
                .filter(value -> value != null && !value.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private static Map<String, List<String>> immutableValuesByPointer(Map<String, List<String>> valuesByPointer) {
        Map<String, List<String>> copied = new LinkedHashMap<>();
        if (valuesByPointer != null) {
            valuesByPointer.forEach((pointer, values) -> copied.put(
                    pointer,
                    values == null ? List.of() : List.copyOf(values)
            ));
        }
        return Map.copyOf(copied);
    }

    private static String alphabeticIndex(int index) {
        StringBuilder result = new StringBuilder();
        int remaining = Math.max(0, index);
        do {
            result.append((char) ('a' + (remaining % 26)));
            remaining = remaining / 26 - 1;
        } while (remaining >= 0);
        return result.reverse().toString();
    }

    private static String appendPointer(String base, String segment) {
        return base + "/" + segment.replace("~", "~0").replace("/", "~1");
    }
}
