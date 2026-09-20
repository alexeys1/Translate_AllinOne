package com.alexeys.translate_allinone.utils.translate;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlainTextResponseDecoder {

    private static final Pattern FENCE_OPEN_LINE = Pattern.compile("^```[A-Za-z0-9_-]*$");
    private static final Pattern FENCE_CLOSE_LINE = Pattern.compile("^```$");
    private static final Pattern EXPLANATION_PREFIX_PATTERN = Pattern.compile(
            "(?i)^(?:here(?:'s| is) (?:the |a )?translation|translated text|the translation|translation)\\s*[:：]"
    );

    private PlainTextResponseDecoder() {
    }

    public static DecodeResult decode(String rawResponse) {
        if (rawResponse == null) {
            return null;
        }
        String trimmed = rawResponse.trim();
        if (trimmed.isEmpty()) {
            return new DecodeResult("", false);
        }
        String envelope = decodeSingleFieldEnvelope(trimmed);
        if (envelope != null) {
            return new DecodeResult(envelope, true);
        }
        if (isCompleteJsonObject(trimmed) || isCompleteJsonArray(trimmed)) {
            return null;
        }
        if ((trimmed.startsWith("{") || trimmed.startsWith("[")) && containsJsonFieldSignature(trimmed)) {
            return null;
        }
        String stringPrimitive = decodeJsonStringPrimitive(trimmed);
        if (stringPrimitive != null) {
            return new DecodeResult(stringPrimitive, true);
        }
        if (trimmed.startsWith("\"") && containsJsonFieldSignature(trimmed)) {
            return null;
        }
        String fenced = decodeSingleClosedFence(trimmed);
        if (fenced != null) {
            if (fenced.isEmpty()) {
                return new DecodeResult("", true);
            }
            DecodeResult inner = decode(fenced);
            return inner == null ? null : new DecodeResult(inner.text(), true);
        }
        if (trimmed.startsWith("```") || trimmed.contains("```")) {
            return null;
        }
        if (EXPLANATION_PREFIX_PATTERN.matcher(trimmed).find() && containsJsonFieldSignature(trimmed)) {
            return null;
        }
        return new DecodeResult(trimmed, false);
    }

    private static String decodeJsonStringPrimitive(String value) {
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            return null;
        }
        try (JsonReader reader = new JsonReader(new StringReader(value))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.STRING) {
                return null;
            }
            String parsed = reader.nextString();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                return null;
            }
            return parsed;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String decodeSingleFieldEnvelope(String value) {
        try (JsonReader reader = new JsonReader(new StringReader(value))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                return null;
            }
            reader.beginObject();
            if (!reader.hasNext()) {
                return null;
            }
            String field = reader.nextName();
            if (!"translation".equals(field) && !"text".equals(field)) {
                return null;
            }
            if (reader.peek() != JsonToken.STRING) {
                return null;
            }
            String parsed = reader.nextString();
            if (reader.hasNext()) {
                return null;
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                return null;
            }
            return parsed;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isCompleteJsonObject(String value) {
        if (!value.startsWith("{")) {
            return false;
        }
        try (JsonReader reader = new JsonReader(new StringReader(value))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                return false;
            }
            reader.beginObject();
            while (reader.hasNext()) {
                reader.skipValue();
            }
            reader.endObject();
            return reader.peek() == JsonToken.END_DOCUMENT;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean isCompleteJsonArray(String value) {
        if (!value.startsWith("[")) {
            return false;
        }
        try (JsonReader reader = new JsonReader(new StringReader(value))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                return false;
            }
            reader.beginArray();
            while (reader.hasNext()) {
                reader.skipValue();
            }
            reader.endArray();
            return reader.peek() == JsonToken.END_DOCUMENT;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean containsJsonFieldSignature(String value) {
        return value.indexOf('"') >= 0 && value.indexOf('"', value.indexOf('"') + 1) >= 0
                && value.contains(":");
    }

    private static String decodeSingleClosedFence(String value) {
        int firstNewline = value.indexOf('\n');
        if (firstNewline < 0) {
            return null;
        }
        String openLine = value.substring(0, firstNewline).trim();
        Matcher openMatcher = FENCE_OPEN_LINE.matcher(openLine);
        if (!openMatcher.matches()) {
            return null;
        }
        String body = value.substring(firstNewline + 1);
        int lastNewline = body.lastIndexOf('\n');
        String closeLine = lastNewline < 0 ? body : body.substring(lastNewline + 1);
        Matcher closeMatcher = FENCE_CLOSE_LINE.matcher(closeLine.trim());
        if (!closeMatcher.matches()) {
            return null;
        }
        String content = lastNewline < 0 ? "" : body.substring(0, lastNewline);
        if (content.contains("```")) {
            return null;
        }
        return content;
    }

    public record DecodeResult(String text, boolean recovered) {
        public DecodeResult {
            text = text == null ? "" : text;
        }
    }
}