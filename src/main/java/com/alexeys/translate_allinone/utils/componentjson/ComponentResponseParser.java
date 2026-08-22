package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ComponentResponseParser {
    private final ComponentJsonLimits limits;

    public ComponentResponseParser() {
        this(ComponentJsonLimits.DEFAULT);
    }

    public ComponentResponseParser(ComponentJsonLimits limits) {
        this.limits = limits == null ? ComponentJsonLimits.DEFAULT : limits;
    }

    public ComponentTranslationResponse parse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw responseError(
                    "Component translation response is empty: expected=JSON_OBJECT"
                            + ", actual=empty"
                            + ", missing=[protocol, translations]"
                            + ", unexpected token=EOF"
            );
        }
        validateUnicode(rawResponse, "response");
        if (rawResponse.getBytes(StandardCharsets.UTF_8).length > limits.maxResponseUtf8Bytes()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.LIMIT,
                    "Component translation response exceeds the size limit: expectedBytes<="
                            + limits.maxResponseUtf8Bytes()
                            + ", actualBytes=" + rawResponse.getBytes(StandardCharsets.UTF_8).length
                            + ", missing=[]"
                            + ", unexpected token=oversize"
            );
        }

        try (JsonReader reader = new JsonReader(new StringReader(rawResponse))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw responseError(
                        "Component translation response must be one JSON object: expected=BEGIN_OBJECT"
                                + ", actual=" + reader.peek()
                                + ", missing=object"
                                + ", unexpected token=" + reader.peek()
                );
            }

            String protocol = null;
            Map<String, String> translations = null;
            Set<String> seenFields = new LinkedHashSet<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (!seenFields.add(field)) {
                    throw responseError(
                            "Duplicate top-level field: expected=unique top-level field"
                                    + ", actual=" + field
                                    + ", missing=[]"
                                    + ", unexpected token=" + field
                    );
                }
                switch (field) {
                    case "protocol" -> protocol = readRequiredString(reader, "protocol");
                    case "translations" -> translations = readTranslations(reader);
                    default -> throw responseError(
                            "Unknown top-level field: expected=[protocol, translations]"
                                    + ", actual=" + field
                                    + ", missing=" + formatMissingFields(seenFields)
                                    + ", unexpected token=" + field
                    );
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw responseError(
                        "Trailing content after component translation response: expected=END_DOCUMENT"
                                + ", actual=" + reader.peek()
                                + ", missing=[]"
                                + ", unexpected token=" + reader.peek()
                );
            }
            if (protocol == null || translations == null) {
                throw responseError(
                        "Component translation response is missing required fields: expected=[protocol, translations]"
                                + ", actual=" + formatSeenFields(protocol, translations)
                                + ", missing=" + formatMissingFields(seenFields)
                                + ", unexpected token=[]"
                );
            }
            return new ComponentTranslationResponse(protocol, translations);
        } catch (ComponentJsonException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.RESPONSE,
                    "Malformed component translation response: expected=valid JSON object"
                            + ", actual=" + e.getClass().getSimpleName()
                            + ", missing=[]"
                            + ", unexpected token=" + e.getClass().getSimpleName(),
                    e
            );
        }
    }

    private Map<String, String> readTranslations(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw responseError(
                    "translations must be a JSON object: expected=BEGIN_OBJECT"
                            + ", actual=" + reader.peek()
                            + ", missing=translations object"
                            + ", unexpected token=" + reader.peek()
            );
        }
        if (limits.maxResponseDepth() < 2) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.LIMIT,
                    "Response depth limit is too small: expected>=2"
                            + ", actual=" + limits.maxResponseDepth()
                            + ", missing=translations object"
                            + ", unexpected token=depth-limit"
            );
        }

        Map<String, String> translations = new LinkedHashMap<>();
        int totalChars = 0;
        reader.beginObject();
        while (reader.hasNext()) {
            String id = reader.nextName();
            if (translations.containsKey(id)) {
                throw responseError(
                        "Duplicate translation id: expected=unique translation id"
                                + ", actual=" + id
                                + ", missing=[]"
                                + ", unexpected token=" + id
                );
            }
            if (translations.size() >= limits.maxTextUnits()) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.LIMIT,
                        "Response has too many translation units: expected<=" + limits.maxTextUnits()
                                + ", actual=" + (translations.size() + 1)
                                + ", missing=[]"
                                + ", unexpected token=translation-id"
                );
            }
            String translation = readRequiredString(reader, "translations." + id);
            if (translation.length() > limits.maxTranslationChars()) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.LIMIT,
                        "Translation exceeds the per-unit length limit: " + id
                                + ": expectedChars<=" + limits.maxTranslationChars()
                                + ", actualChars=" + translation.length()
                                + ", missing=[]"
                                + ", unexpected token=oversize-translation"
                );
            }
            totalChars += translation.length();
            if (totalChars > limits.maxTotalTranslationChars()) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.LIMIT,
                        "Translations exceed the total length limit: expectedChars<=" + limits.maxTotalTranslationChars()
                                + ", actualChars=" + totalChars
                                + ", missing=[]"
                                + ", unexpected token=oversize-total"
                );
            }
            translations.put(id, translation);
        }
        reader.endObject();
        return translations;
    }

    private String readRequiredString(JsonReader reader, String field) throws IOException {
        if (reader.peek() != JsonToken.STRING) {
            throw responseError(
                    field + " must be a string: expected=STRING"
                            + ", actual=" + reader.peek()
                            + ", missing=" + field
                            + ", unexpected token=" + reader.peek()
            );
        }
        String value = reader.nextString();
        validateUnicode(value, field);
        return value;
    }

    private static void validateUnicode(String value, String field) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw responseError(
                            "Invalid Unicode in " + field
                                    + ": expected=valid UTF-16, actual=unpaired high surrogate"
                                    + ", missing=[]"
                                    + ", unexpected token=surrogate"
                    );
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw responseError(
                        "Invalid Unicode in " + field
                                + ": expected=valid UTF-16, actual=unpaired low surrogate"
                                + ", missing=[]"
                                + ", unexpected token=surrogate"
                );
            }
        }
    }

    private static ComponentJsonException responseError(String message) {
        return new ComponentJsonException(ComponentJsonException.Kind.RESPONSE, message);
    }

    private static String formatMissingFields(Set<String> seenFields) {
        Set<String> missing = new LinkedHashSet<>(Arrays.asList("protocol", "translations"));
        if (seenFields != null) {
            missing.removeAll(seenFields);
        }
        return missing.toString();
    }

    private static String formatSeenFields(String protocol, Map<String, String> translations) {
        Set<String> actual = new LinkedHashSet<>();
        if (protocol != null) {
            actual.add("protocol");
        }
        if (translations != null) {
            actual.add("translations");
        }
        return actual.toString();
    }
}
