package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class ComponentTranslationApplier {
    private final ComponentTranslationValidator validator;

    public ComponentTranslationApplier() {
        this(new ComponentTranslationValidator());
    }

    public ComponentTranslationApplier(ComponentTranslationValidator validator) {
        this.validator = validator == null ? new ComponentTranslationValidator() : validator;
    }

    public Text apply(
            ComponentTranslationDocument document,
            ComponentTranslationResponse response
    ) {
        JsonElement translatedJson = applyToJson(document, response);
        return ComponentJsonCodec.decode(translatedJson);
    }

    public JsonElement applyToJson(
            ComponentTranslationDocument document,
            ComponentTranslationResponse response
    ) {
        validator.validate(document, response);
        JsonElement source = document.sourceJson();
        JsonElement translated = source.deepCopy();

        for (ComponentTextUnit unit : document.units()) {
            JsonElement current = getAtPointer(translated, unit.jsonPointer());
            if (!current.isJsonPrimitive()
                    || !current.getAsJsonPrimitive().isString()
                    || !unit.sourceText().equals(current.getAsString())) {
                throw applyError("Text path no longer points to the expected source string: " + unit.id());
            }
            translated = replaceAtPointer(translated, unit.jsonPointer(), new JsonPrimitive(response.translations().get(unit.id())));
        }

        JsonElement sourceSkeleton = maskedCopy(source, document.units());
        JsonElement translatedSkeleton = maskedCopy(translated, document.units());
        if (!sourceSkeleton.equals(translatedSkeleton)) {
            throw applyError("Applying translations changed the Component JSON structure.");
        }
        return translated;
    }

    static JsonElement maskedCopy(JsonElement source, List<ComponentTextUnit> units) {
        JsonElement masked = source.deepCopy();
        for (ComponentTextUnit unit : units) {
            masked = replaceAtPointer(masked, unit.jsonPointer(), new JsonPrimitive("__taio_" + unit.id() + "__"));
        }
        return masked;
    }

    static JsonElement getAtPointer(JsonElement root, String pointer) {
        JsonElement current = root;
        for (String segment : decodePointer(pointer)) {
            if (current.isJsonObject()) {
                JsonObject object = current.getAsJsonObject();
                if (!object.has(segment)) {
                    throw applyError("JSON Pointer field does not exist: " + pointer);
                }
                current = object.get(segment);
            } else if (current.isJsonArray()) {
                int index = parseArrayIndex(segment, pointer);
                JsonArray array = current.getAsJsonArray();
                if (index >= array.size()) {
                    throw applyError("JSON Pointer array index is out of bounds: " + pointer);
                }
                current = array.get(index);
            } else {
                throw applyError("JSON Pointer traverses a primitive value: " + pointer);
            }
        }
        return current;
    }

    private static JsonElement replaceAtPointer(JsonElement root, String pointer, JsonElement replacement) {
        List<String> segments = decodePointer(pointer);
        if (segments.isEmpty()) {
            return replacement;
        }

        JsonElement parent = root;
        for (int index = 0; index < segments.size() - 1; index++) {
            String segment = segments.get(index);
            if (parent.isJsonObject()) {
                JsonObject object = parent.getAsJsonObject();
                if (!object.has(segment)) {
                    throw applyError("JSON Pointer field does not exist: " + pointer);
                }
                parent = object.get(segment);
            } else if (parent.isJsonArray()) {
                JsonArray array = parent.getAsJsonArray();
                int arrayIndex = parseArrayIndex(segment, pointer);
                if (arrayIndex >= array.size()) {
                    throw applyError("JSON Pointer array index is out of bounds: " + pointer);
                }
                parent = array.get(arrayIndex);
            } else {
                throw applyError("JSON Pointer traverses a primitive value: " + pointer);
            }
        }

        String last = segments.get(segments.size() - 1);
        if (parent.isJsonObject()) {
            JsonObject object = parent.getAsJsonObject();
            if (!object.has(last)) {
                throw applyError("JSON Pointer field does not exist: " + pointer);
            }
            object.add(last, replacement);
        } else if (parent.isJsonArray()) {
            JsonArray array = parent.getAsJsonArray();
            int arrayIndex = parseArrayIndex(last, pointer);
            if (arrayIndex >= array.size()) {
                throw applyError("JSON Pointer array index is out of bounds: " + pointer);
            }
            array.set(arrayIndex, replacement);
        } else {
            throw applyError("JSON Pointer parent is not a container: " + pointer);
        }
        return root;
    }

    private static List<String> decodePointer(String pointer) {
        if (pointer == null) {
            throw applyError("JSON Pointer cannot be null.");
        }
        if (pointer.isEmpty()) {
            return List.of();
        }
        if (!pointer.startsWith("/")) {
            throw applyError("Invalid JSON Pointer: " + pointer);
        }

        String[] rawSegments = pointer.substring(1).split("/", -1);
        List<String> segments = new ArrayList<>(rawSegments.length);
        for (String rawSegment : rawSegments) {
            StringBuilder decoded = new StringBuilder();
            for (int index = 0; index < rawSegment.length(); index++) {
                char current = rawSegment.charAt(index);
                if (current != '~') {
                    decoded.append(current);
                    continue;
                }
                if (index + 1 >= rawSegment.length()) {
                    throw applyError("Invalid JSON Pointer escape: " + pointer);
                }
                char escaped = rawSegment.charAt(++index);
                if (escaped == '0') {
                    decoded.append('~');
                } else if (escaped == '1') {
                    decoded.append('/');
                } else {
                    throw applyError("Invalid JSON Pointer escape: " + pointer);
                }
            }
            segments.add(decoded.toString());
        }
        return segments;
    }

    private static int parseArrayIndex(String value, String pointer) {
        if (value.isEmpty() || (value.length() > 1 && value.charAt(0) == '0')) {
            throw applyError("Invalid JSON Pointer array index: " + pointer);
        }
        try {
            int index = Integer.parseInt(value);
            if (index < 0) {
                throw applyError("Negative JSON Pointer array index: " + pointer);
            }
            return index;
        } catch (NumberFormatException e) {
            throw new ComponentJsonException(ComponentJsonException.Kind.APPLY, "Invalid JSON Pointer array index: " + pointer, e);
        }
    }

    private static ComponentJsonException applyError(String message) {
        return new ComponentJsonException(ComponentJsonException.Kind.APPLY, message);
    }
}
