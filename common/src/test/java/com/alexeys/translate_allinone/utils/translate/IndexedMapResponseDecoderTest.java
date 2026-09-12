package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndexedMapResponseDecoderTest {

    @Test
    void decodesExactIndexedMapInOrder() {
        assertEquals(
                List.of("你好", "世界", "！"),
                IndexedMapResponseDecoder.decode("{\"1\":\"你好\",\"2\":\"世界\",\"3\":\"！\"}")
        );
    }

    @Test
    void rejectsMissingKey() {
        IndexedMapResponseException error = assertThrows(
                IndexedMapResponseException.class,
                () -> IndexedMapResponseDecoder.decode("{\"1\":\"你好\",\"3\":\"！\"}")
        );

        assertEquals(TranslationRejectionCode.ID_MISMATCH, error.code());
    }

    @Test
    void rejectsExtraKey() {
        IndexedMapResponseException error = assertThrows(
                IndexedMapResponseException.class,
                () -> IndexedMapResponseDecoder.decode("{\"1\":\"你好\",\"2\":\"世界\",\"x\":\"y\"}")
        );

        assertEquals(TranslationRejectionCode.ID_MISMATCH, error.code());
    }

    @Test
    void rejectsDuplicateKey() {
        IndexedMapResponseException error = assertThrows(
                IndexedMapResponseException.class,
                () -> IndexedMapResponseDecoder.decode("{\"1\":\"你好\",\"1\":\"世界\"}")
        );

        assertEquals(TranslationRejectionCode.MALFORMED_PROTOCOL, error.code());
    }

    @Test
    void rejectsNonStringValue() {
        IndexedMapResponseException error = assertThrows(
                IndexedMapResponseException.class,
                () -> IndexedMapResponseDecoder.decode("{\"1\":{\"translation\":\"你好\"}}")
        );

        assertEquals(TranslationRejectionCode.MALFORMED_PROTOCOL, error.code());
    }

    @Test
    void rejectsJsonArrayRoot() {
        IndexedMapResponseException error = assertThrows(
                IndexedMapResponseException.class,
                () -> IndexedMapResponseDecoder.decode("[\"你好\"]")
        );

        assertEquals(TranslationRejectionCode.MALFORMED_PROTOCOL, error.code());
    }

    @Test
    void rejectsTrailingContent() {
        IndexedMapResponseException error = assertThrows(
                IndexedMapResponseException.class,
                () -> IndexedMapResponseDecoder.decode("{\"1\":\"你好\"} trailing")
        );

        assertEquals(TranslationRejectionCode.MALFORMED_PROTOCOL, error.code());
    }

    @Test
    void rejectsTextWithOuterExplanations() {
        IndexedMapResponseException error = assertThrows(
                IndexedMapResponseException.class,
                () -> IndexedMapResponseDecoder.decode("Here is the map: {\"1\":\"你好\"}")
        );

        assertEquals(TranslationRejectionCode.MALFORMED_PROTOCOL, error.code());
    }

    @Test
    void rejectsEmptyDocument() {
        IndexedMapResponseException error = assertThrows(
                IndexedMapResponseException.class,
                () -> IndexedMapResponseDecoder.decode("{}")
        );

        assertEquals(TranslationRejectionCode.MALFORMED_PROTOCOL, error.code());
    }

    @Test
    void rejectsBlankResponse() {
        IndexedMapResponseException error = assertThrows(
                IndexedMapResponseException.class,
                () -> IndexedMapResponseDecoder.decode("   ")
        );

        assertEquals(TranslationRejectionCode.MALFORMED_PROTOCOL, error.code());
    }

    @Test
    void acceptsSingleValueMap() {
        assertEquals(List.of("你好"), IndexedMapResponseDecoder.decode("{\"1\":\"你好\"}"));
    }
}