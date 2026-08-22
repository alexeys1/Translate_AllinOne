package com.alexeys.translate_allinone.utils.componentjson;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class ComponentTranslationDebugLoggerTest {
    @Test
    void splitsEmptyAndShortText() {
        assertIterableEquals(List.of(""), ComponentTranslationDebugLogger.splitTextForLog(null));
        assertIterableEquals(List.of("tooltip text"), ComponentTranslationDebugLogger.splitTextForLog("tooltip text"));
    }

    @Test
    void preservesSurrogatePairsAtChunkBoundaries() {
        String source = "a".repeat(255) + "\uD83D\uDE00" + "tail";

        List<String> chunks = ComponentTranslationDebugLogger.splitTextForLog(source);

        assertEquals(source, String.join("", chunks));
        assertEquals(255, chunks.get(0).length());
        assertEquals("\uD83D\uDE00tail", chunks.get(1));
    }
}
