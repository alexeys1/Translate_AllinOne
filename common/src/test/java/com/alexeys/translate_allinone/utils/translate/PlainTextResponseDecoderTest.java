package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlainTextResponseDecoderTest {

    @Test
    void acceptsPlainTextWithoutRecovery() {
        PlainTextResponseDecoder.DecodeResult result = PlainTextResponseDecoder.decode("你好，世界！");

        assertEquals("你好，世界！", result.text());
        assertFalse(result.recovered());
    }

    @Test
    void decodesJsonStringPrimitive() {
        PlainTextResponseDecoder.DecodeResult result = PlainTextResponseDecoder.decode("\"你好\"");

        assertEquals("你好", result.text());
        assertTrue(result.recovered());
    }

    @Test
    void decodesSingleFieldTranslationEnvelope() {
        PlainTextResponseDecoder.DecodeResult result = PlainTextResponseDecoder.decode(
                "{\"translation\": \"你好\"}"
        );

        assertEquals("你好", result.text());
        assertTrue(result.recovered());
    }

    @Test
    void decodesSingleFieldTextEnvelope() {
        PlainTextResponseDecoder.DecodeResult result = PlainTextResponseDecoder.decode(
                "{\"text\":\"你好\"}"
        );

        assertEquals("你好", result.text());
        assertTrue(result.recovered());
    }

    @Test
    void rejectsUnknownJsonObject() {
        assertNull(PlainTextResponseDecoder.decode("{\"foo\": \"bar\"}"));
    }

    @Test
    void rejectsEnvelopeWithExtraFields() {
        assertNull(PlainTextResponseDecoder.decode(
                "{\"translation\": \"你好\", \"extra\": \"x\"}"
        ));
        assertNull(PlainTextResponseDecoder.decode(
                "{\"translation\": \"你好\", \"text\": \"x\"}"
        ));
    }

    @Test
    void rejectsJsonArray() {
        assertNull(PlainTextResponseDecoder.decode("[\"你好\"]"));
        assertNull(PlainTextResponseDecoder.decode("[1,2]"));
    }

    @Test
    void rejectsMalformedJson() {
        assertNull(PlainTextResponseDecoder.decode("{\"not\": json"));
        assertNull(PlainTextResponseDecoder.decode("{\"translation\": \"你好}"));
    }

    @Test
    void stripsSingleClosedMarkdownFenceBeforeReparse() {
        PlainTextResponseDecoder.DecodeResult result = PlainTextResponseDecoder.decode(
                "```json\n{\"translation\": \"你好\"}\n```"
        );

        assertEquals("你好", result.text());
        assertTrue(result.recovered());
    }

    @Test
    void stripsSingleClosedPlainFence() {
        PlainTextResponseDecoder.DecodeResult result = PlainTextResponseDecoder.decode(
                "```\n你好\n```"
        );

        assertEquals("你好", result.text());
        assertTrue(result.recovered());
    }

    @Test
    void rejectsFenceWithNonFenceFirstLine() {
        assertNull(PlainTextResponseDecoder.decode("prefix\n```\n你好\n```"));
    }

    @Test
    void rejectsMultipleFencesInBody() {
        assertNull(PlainTextResponseDecoder.decode("```\n```\n你好\n```"));
    }

    @Test
    void rejectsUnclosedFence() {
        assertNull(PlainTextResponseDecoder.decode("```json\n{\"translation\": \"你好\"}"));
    }

    @Test
    void rejectsFenceWithTrailingContent() {
        assertNull(PlainTextResponseDecoder.decode("```\n你好\n```\ntrailing"));
    }

    @Test
    void rejectsMixedPrefixAndJsonEnvelope() {
        assertNull(PlainTextResponseDecoder.decode("Here is the translation: {\"translation\": \"你好\"}"));
    }

    @Test
    void leavesExplanationTextAsPlainCandidateForGate() {
        PlainTextResponseDecoder.DecodeResult result = PlainTextResponseDecoder.decode(
                "Here is the translation: 你好"
        );

        assertEquals("Here is the translation: 你好", result.text());
        assertFalse(result.recovered());
    }

    @Test
    void returnsEmptyCandidateForBlankResponse() {
        PlainTextResponseDecoder.DecodeResult result = PlainTextResponseDecoder.decode("   ");

        assertEquals("", result.text());
        assertFalse(result.recovered());
    }
}