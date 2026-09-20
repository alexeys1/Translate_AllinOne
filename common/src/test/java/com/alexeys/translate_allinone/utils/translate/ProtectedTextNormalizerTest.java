package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectedTextNormalizerTest {

    @Test
    void stripsAllDocumentedProtectedTokenFamiliesConsistently() {
        String source = "§aHello</s1>{d1}{g1}{value2}{glyph3}{accent4.begin}{c5}%s%d%f"
                + "__TAIO_PROTECTED_TOKEN_0__\\n\\t";

        String stripped = ProtectedTextNormalizer.stripProtectedContent(source);

        assertEquals("Hello", stripped.trim());
    }

    @Test
    void normalizesComparableTextAcrossTokenFamilies() {
        assertEquals(
                ProtectedTextNormalizer.normalizeComparable("<s1>Hello</s1>{d1}{g1}world"),
                ProtectedTextNormalizer.normalizeComparable("hello world")
        );
        assertEquals(
                ProtectedTextNormalizer.normalizeComparable("§aHello，world！"),
                ProtectedTextNormalizer.normalizeComparable("hello，world！")
        );
    }

    @Test
    void normalizeComparableIsLowercasingAndNfkc() {
        assertEquals("world你好", ProtectedTextNormalizer.normalizeComparable("ＷＯＲＬＤ你好"));
        assertEquals("hiworld", ProtectedTextNormalizer.normalizeComparable("<s1>Hi</s1>{d1}{g1}world"));
    }

    @Test
    void normalizesEmptyTemplateToEmptyString() {
        assertEquals("", ProtectedTextNormalizer.normalizeComparable("<s1></s1>"));
    }

    @Test
    void countsChineseSignal() {
        assertTrue(ProtectedTextNormalizer.containsCjk("你好世界"));
        assertFalse(ProtectedTextNormalizer.containsCjk("Hello world"));
        assertEquals(4, ProtectedTextNormalizer.countCjk("你好世界abc"));
    }

    @Test
    void countsAsciiLettersAfterStrip() {
        assertEquals(10, ProtectedTextNormalizer.countAsciiLetters(
                ProtectedTextNormalizer.stripProtectedContent("§aHello §bWorld")
        ));
        assertEquals(0, ProtectedTextNormalizer.countAsciiLetters(
                ProtectedTextNormalizer.stripProtectedContent("3/10")
        ));
    }

    @Test
    void detectsChineseTargetSynonyms() {
        assertTrue(ProtectedTextNormalizer.isChineseTarget("Chinese"));
        assertTrue(ProtectedTextNormalizer.isChineseTarget("简体中文"));
        assertTrue(ProtectedTextNormalizer.isChineseTarget("zh_cn"));
        assertFalse(ProtectedTextNormalizer.isChineseTarget("English"));
    }

    @Test
    void extractsHardProtectedTokensForMismatchComparison() {
        assertEquals(
                java.util.List.of("§a", "<s1>", "</s1>", "{d1}", "\\n"),
                ProtectedTextNormalizer.extractHardProtectedTokens("§a<s1>Hello</s1>{d1}\\n")
        );
    }

    @Test
    void ignoresSoftTokensInHardTokenComparison() {
        assertEquals(
                java.util.List.of(),
                ProtectedTextNormalizer.extractHardProtectedTokens("https://example.com/a/b /warp wynn 3/10 {c1}")
        );
    }

    @Test
    void detectsAbnormalRepetition() {
        assertTrue(ProtectedTextNormalizer.hasAbnormalRepetition("你好你好你好你好 你好你好你好你好"));
        assertFalse(ProtectedTextNormalizer.hasAbnormalRepetition("今天天气很好，适合出门散步。"));
    }

    @Test
    void truncationHeuristicRejectsVeryShortOutputForLongSource() {
        assertTrue(ProtectedTextNormalizer.looksTruncatedForChineseOutput(
                "This is an extremely long English source sentence that clearly deserves a complete translation",
                "你好"
        ));
        assertFalse(ProtectedTextNormalizer.looksTruncatedForChineseOutput("Hello", "你好"));
    }
}