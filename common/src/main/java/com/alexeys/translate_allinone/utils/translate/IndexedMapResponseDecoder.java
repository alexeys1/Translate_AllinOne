package com.alexeys.translate_allinone.utils.translate;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IndexedMapResponseDecoder {

    private IndexedMapResponseDecoder() {
    }

    public static List<String> decode(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IndexedMapResponseException(
                    IndexedMapRejectionCode.MALFORMED_PROTOCOL,
                    "Indexed map response is empty: expected=JSON_OBJECT with keys 1..N"
            );
        }
        Map<String, String> entries = new LinkedHashMap<>();
        try (JsonReader reader = new JsonReader(new StringReader(rawResponse))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new IndexedMapResponseException(
                        IndexedMapRejectionCode.MALFORMED_PROTOCOL,
                        "Indexed map response must be one JSON object: expected=BEGIN_OBJECT, actual=" + reader.peek()
                );
            }
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (entries.containsKey(key)) {
                    throw new IndexedMapResponseException(
                            IndexedMapRejectionCode.MALFORMED_PROTOCOL,
                            "Indexed map response contains a duplicate key: " + key
                    );
                }
                if (reader.peek() != JsonToken.STRING) {
                    throw new IndexedMapResponseException(
                            IndexedMapRejectionCode.MALFORMED_PROTOCOL,
                            "Indexed map value must be a string: key=" + key + ", actual=" + reader.peek()
                    );
                }
                entries.put(key, reader.nextString());
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IndexedMapResponseException(
                        IndexedMapRejectionCode.MALFORMED_PROTOCOL,
                        "Trailing content after indexed map response: expected=END_DOCUMENT, actual=" + reader.peek()
                );
            }
        } catch (IndexedMapResponseException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IndexedMapResponseException(
                    IndexedMapRejectionCode.MALFORMED_PROTOCOL,
                    "Malformed indexed map response: " + e.getClass().getSimpleName()
            );
        }
        if (entries.isEmpty()) {
            throw new IndexedMapResponseException(
                    IndexedMapRejectionCode.MALFORMED_PROTOCOL,
                    "Indexed map response must contain keys 1..N: expected=non-empty JSON_OBJECT"
            );
        }
        if (!hasExactIndexedKeys(entries)) {
            throw new IndexedMapResponseException(
                    IndexedMapRejectionCode.ID_MISMATCH,
                    "Indexed map response keys must be exactly 1..N: actual=" + entries.keySet()
            );
        }
        List<String> values = new ArrayList<>(entries.size());
        for (int index = 1; index <= entries.size(); index++) {
            values.add(entries.get(String.valueOf(index)));
        }
        return List.copyOf(values);
    }

    private static boolean hasExactIndexedKeys(Map<String, String> entries) {
        for (int index = 1; index <= entries.size(); index++) {
            if (!entries.containsKey(String.valueOf(index))) {
                return false;
            }
        }
        return entries.size() == entries.keySet().size();
    }
}