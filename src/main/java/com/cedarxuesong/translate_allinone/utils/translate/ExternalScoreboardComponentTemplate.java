package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentJsonCodec;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
final class ExternalScoreboardComponentTemplate {
    private ExternalScoreboardComponentTemplate() {
    }

    static Prepared prepare(Component source, Set<String> privateTokens) {
        JsonElement sourceJson = ComponentJsonCodec.encode(source == null ? Component.empty() : source);
        Map<String, List<String>> valuesByPointer = new LinkedHashMap<>();
        List<String> protectedNames = new ArrayList<>(
                privateTokens == null ? Set.of() : privateTokens
        );
        protectedNames.removeIf(name -> name == null || name.isEmpty());
        protectedNames.sort(Comparator.comparingInt(String::length).reversed());
        JsonElement templateJson = replaceNumbers(
                sourceJson.deepCopy(),
                "",
                valuesByPointer,
                List.copyOf(protectedNames)
        );
        return new Prepared(ComponentJsonCodec.decode(templateJson), valuesByPointer);
    }

    private static JsonElement replaceNumbers(
            JsonElement element,
            String pointer,
            Map<String, List<String>> valuesByPointer,
            List<String> privateTokens
    ) {
        if (element == null || element.isJsonNull()) {
            return element;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            return primitive.isString()
                    ? replaceLiteral(primitive.getAsString(), pointer, valuesByPointer, privateTokens)
                    : element;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                array.set(
                        index,
                        replaceNumbers(
                                array.get(index),
                                appendPointer(pointer, Integer.toString(index)),
                                valuesByPointer,
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
            String textPointer = appendPointer(pointer, "text");
            object.add(
                    "text",
                    replaceLiteral(text.getAsString(), textPointer, valuesByPointer, privateTokens)
            );
        }
        JsonElement extra = object.get("extra");
        if (extra != null && extra.isJsonArray()) {
            JsonArray children = extra.getAsJsonArray();
            String extraPointer = appendPointer(pointer, "extra");
            for (int index = 0; index < children.size(); index++) {
                children.set(
                        index,
                        replaceNumbers(
                                children.get(index),
                                appendPointer(extraPointer, Integer.toString(index)),
                                valuesByPointer,
                                privateTokens
                        )
                );
            }
        }
        return object;
    }

    private static JsonElement replaceLiteral(
            String text,
            String pointer,
            Map<String, List<String>> valuesByPointer,
            List<String> privateTokens
    ) {
        Map<String, String> privatePlaceholders = new LinkedHashMap<>();
        String protectedText = text;
        int privateIndex = 0;
        for (String privateToken : privateTokens) {
            if (!protectedText.contains(privateToken)) {
                continue;
            }
            String placeholder = "{taio.private." + alphabeticIndex(privateIndex++) + "}";
            privatePlaceholders.put(placeholder, privateToken);
            protectedText = protectedText.replace(privateToken, placeholder);
        }

        TemplateProcessor.TemplateExtractionResult extraction = TemplateProcessor.extract(protectedText);
        String template = extraction.template();
        for (Map.Entry<String, String> privatePlaceholder : privatePlaceholders.entrySet()) {
            template = template.replace(privatePlaceholder.getKey(), privatePlaceholder.getValue());
        }
        if (!extraction.values().isEmpty()) {
            valuesByPointer.put(pointer, List.copyOf(extraction.values()));
        }
        return new JsonPrimitive(template);
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

    private static JsonElement restoreNumbers(
            JsonElement element,
            String pointer,
            Map<String, List<String>> valuesByPointer
    ) {
        if (element == null || element.isJsonNull()) {
            return element;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (!primitive.isString()) {
                return element;
            }
            List<String> values = valuesByPointer.get(pointer);
            return values == null
                    ? element
                    : new JsonPrimitive(TemplateProcessor.reassemble(primitive.getAsString(), values));
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                array.set(
                        index,
                        restoreNumbers(
                                array.get(index),
                                appendPointer(pointer, Integer.toString(index)),
                                valuesByPointer
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
            String textPointer = appendPointer(pointer, "text");
            List<String> values = valuesByPointer.get(textPointer);
            if (values != null) {
                object.addProperty(
                        "text",
                        TemplateProcessor.reassemble(text.getAsString(), values)
                );
            }
        }
        JsonElement extra = object.get("extra");
        if (extra != null && extra.isJsonArray()) {
            JsonArray children = extra.getAsJsonArray();
            String extraPointer = appendPointer(pointer, "extra");
            for (int index = 0; index < children.size(); index++) {
                children.set(
                        index,
                        restoreNumbers(
                                children.get(index),
                                appendPointer(extraPointer, Integer.toString(index)),
                                valuesByPointer
                        )
                );
            }
        }
        return object;
    }

    private static String appendPointer(String base, String segment) {
        return base + "/" + segment.replace("~", "~0").replace("/", "~1");
    }

    record Prepared(Component templateComponent, Map<String, List<String>> valuesByPointer) {
        Prepared {
            templateComponent = templateComponent == null ? Component.empty() : templateComponent;
            Map<String, List<String>> copiedValues = new LinkedHashMap<>();
            if (valuesByPointer != null) {
                valuesByPointer.forEach((pointer, values) ->
                        copiedValues.put(pointer, values == null ? List.of() : List.copyOf(values))
                );
            }
            valuesByPointer = Map.copyOf(copiedValues);
        }

        Component restore(Component translatedTemplate) {
            if (translatedTemplate == null || valuesByPointer.isEmpty()) {
                return translatedTemplate == null ? Component.empty() : translatedTemplate;
            }
            JsonElement translatedJson = ComponentJsonCodec.encode(translatedTemplate);
            return ComponentJsonCodec.decode(
                    restoreNumbers(translatedJson.deepCopy(), "", valuesByPointer)
            );
        }
    }
}
