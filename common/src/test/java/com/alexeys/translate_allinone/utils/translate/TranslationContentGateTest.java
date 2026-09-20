package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationContentGateTest {

    @Test
    void acceptsNormalChineseTranslation() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world, this is a test",
                "你好世界，这是一个测试",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void rejectsEmptyCandidate() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world",
                "   ",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.EMPTY, verdict.reason());
    }

    @Test
    void acceptsTemplateOnlyCandidateAfterTokenStrip() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "<s1>Hello world</s1>",
                "<s1></s1>",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void rejectsTruncatedCompletion() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world",
                "你好",
                "Chinese",
                true
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.TRUNCATED, verdict.reason());
    }

    @Test
    void rejectsStructuredJsonArtifactCandidate() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world",
                "{\"translation\": \"你好\"}",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.STRUCTURED_ARTIFACT, verdict.reason());
    }

    @Test
    void rejectsMarkdownFenceArtifactCandidate() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world",
                "```json\n{\"translation\": \"你好\"}\n```",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.STRUCTURED_ARTIFACT, verdict.reason());
    }

    @Test
    void rejectsExplanationPrefix() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world",
                "Here is the translation: 你好世界",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.STRUCTURED_ARTIFACT, verdict.reason());
    }

    @Test
    void rejectsTranslationPrefix() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world",
                "Translation: 你好世界",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.STRUCTURED_ARTIFACT, verdict.reason());
    }

    @Test
    void leavesChinesePrefixUntouched() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world",
                "翻译：你好世界",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void rejectsSourceEchoInTranslateMode() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world, this is a test",
                "Hello world, this is a test",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.SOURCE_COPY, verdict.reason());
    }

    @Test
    void skipsSourceEchoCheckWhenTargetLanguageIsUnknown() {
        assertTrue(TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world, this is a test",
                "Hello world, this is a test",
                null
        ).accepted());
        assertTrue(TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world, this is a test",
                "Hello world, this is a test",
                "   "
        ).accepted());
    }

    @Test
    void stillRejectsSourceEchoWhenTargetLanguageIsKnown() {
        assertFalse(TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world, this is a test",
                "Hello world, this is a test",
                "Chinese"
        ).accepted());
    }

    @Test
    void rejectsSourceEchoWithFormattingDifferences() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello World, THIS is a Test",
                "hello world this is a test",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.SOURCE_COPY, verdict.reason());
    }

    @Test
    void acceptsLegitimateRewriteThatKeepsSource() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.REWRITE,
                "This sentence is already professional and precise.",
                "This sentence is already professional and precise.",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void rejectsSourceEchoOnlyInTranslateModeNotRewrite() {
        TranslationContentVerdict translateVerdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "This sentence is already professional.",
                "This sentence is already professional.",
                "Chinese"
        );
        TranslationContentVerdict rewriteVerdict = TranslationContentGate.evaluate(
                TranslationMode.REWRITE,
                "This sentence is already professional.",
                "This sentence is already professional.",
                "Chinese"
        );

        assertFalse(translateVerdict.accepted());
        assertTrue(rewriteVerdict.accepted());
    }

    @Test
    void rejectsMissingChineseSignalForLatinSource() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world, this is a test",
                "Hello world",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.MISSING_TARGET_LANGUAGE_SIGNAL, verdict.reason());
    }

    @Test
    void skipsSignalRuleForShortSource() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Ragni",
                "Ragni",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void acceptsShortAbbreviationCopy() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "LFG",
                "LFG",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void acceptsPureUrlCopy() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Visit https://example.com/wynn for details",
                "https://example.com/wynn",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void acceptsTemplateEchoLikeCounter() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "§a[Wynntils] 3/10",
                "§a[Wynntils] 3/10",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void skipsSourceCopyWhenSourceAlreadyContainsChinese() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "你好世界，这是一个测试",
                "你好世界，这是一个测试",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void rejectsNestedJsonStringValue() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world, this is a test",
                "{\"translation\":\"目标译文\"}",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.STRUCTURED_ARTIFACT, verdict.reason());
    }

    @Test
    void rejectsAbnormalRepetition() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Hello world, this is a test",
                "你好你好你好你好 你好你好你好你好",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.ABNORMAL_REPETITION, verdict.reason());
    }

    @Test
    void rejectsAbnormalRepetitionOfLongerChunk() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "This is a very long source sentence for repetition testing",
                "重复内容重复内容重复内容重复内容重复内容重复内容",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.ABNORMAL_REPETITION, verdict.reason());
    }

    @Test
    void rejectsProtectedTokenLoss() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "§aHello §bworld",
                "你好 世界",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.PROTECTED_TOKEN_MISMATCH, verdict.reason());
    }

    @Test
    void acceptsProtectedTokenPreservation() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "§aHello",
                "§a你好",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void acceptsTokenReorderedButPreserved() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "<s1>Hello</s1> {d1} world",
                "{d1} <s1>你好</s1> 世界",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void rejectsStyleTagIdChange() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "<s1>Hello</s1> world",
                "<s2>你好</s2> 世界",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.PROTECTED_TOKEN_MISMATCH, verdict.reason());
    }

    @Test
    void acceptsMixedContentWithUrlAndCommandOnly() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Run /warp wynn and open https://example.com for more",
                "/warp wynn https://example.com",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void rejectsTruncatedChineseOutputForLongLatinSource() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "This is an extremely long English source sentence that clearly deserves a complete Chinese translation",
                "这是一小段",
                "Chinese"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.TRUNCATED, verdict.reason());
    }

    @Test
    void acceptsNormalTranslationForNonChineseTarget() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Bonjour le monde, ceci est un test",
                "Hello world, this is a test",
                "English"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void rejectsSourceEchoEvenForNonChineseTarget() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "Bonjour le monde, ceci est un test",
                "Bonjour le monde, ceci est un test",
                "English"
        );

        assertFalse(verdict.accepted());
        assertEquals(TranslationContentVerdict.SOURCE_COPY, verdict.reason());
    }

    @Test
    void acceptsShortLegitimacyWithSlots() {
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                "3/10",
                "3/10",
                "Chinese"
        );

        assertTrue(verdict.accepted());
    }

    @Test
    void alreadyInTargetLanguageIsTrueForChineseText() {
        assertTrue(TranslationContentGate.alreadyInTargetLanguage("你好世界，这是一段中文。", "Chinese"));
        assertTrue(TranslationContentGate.alreadyInTargetLanguage("无法从 墨汁发光药水 中获取。", "Chinese"));
        assertTrue(TranslationContentGate.alreadyInTargetLanguage("Mixin 提供一种附魔，可添加至 ", "Chinese"));
        assertTrue(TranslationContentGate.alreadyInTargetLanguage("你好世界，这是一段中文。", "zh_cn"));
    }

    @Test
    void alreadyInTargetLanguageIgnoresProtectedTokens() {
        assertTrue(TranslationContentGate.alreadyInTargetLanguage(
                "<s0>Mixin 提供一种附魔，可添加至 </s0>",
                "Chinese"
        ));
        assertTrue(TranslationContentGate.alreadyInTargetLanguage(
                "效果：{value0} {d1} 点",
                "Chinese"
        ));
    }

    @Test
    void alreadyInTargetLanguageRejectsDominantLatinFragments() {
        assertFalse(TranslationContentGate.alreadyInTargetLanguage(
                "God Potions 神药水效果",
                "Chinese"
        ));
        assertFalse(TranslationContentGate.alreadyInTargetLanguage(
                "This is still mostly English with 中文",
                "Chinese"
        ));
    }

    @Test
    void alreadyInTargetLanguageIsFalseForOtherTargetsAndEmptyInput() {
        assertFalse(TranslationContentGate.alreadyInTargetLanguage("你好世界", "English"));
        assertFalse(TranslationContentGate.alreadyInTargetLanguage("", "Chinese"));
        assertFalse(TranslationContentGate.alreadyInTargetLanguage(null, "Chinese"));
        assertFalse(TranslationContentGate.alreadyInTargetLanguage("单", "Chinese"));
    }

    @Test
    void alreadyInTargetLanguageRequiresAtLeastTwoCjkLetters() {
        assertFalse(TranslationContentGate.alreadyInTargetLanguage("s中", "Chinese"));
        assertTrue(TranslationContentGate.alreadyInTargetLanguage("中文", "Chinese"));
    }

    @Test
    void alreadyInTargetLanguageKeepsJapaneseAndKoreanSourcesTranslatable() {
        assertFalse(TranslationContentGate.alreadyInTargetLanguage("こんにちは世界", "Chinese"));
        assertFalse(TranslationContentGate.alreadyInTargetLanguage("안녕하세요 세계", "Chinese"));
        assertFalse(TranslationContentGate.alreadyInTargetLanguage("世界のアイテム", "Chinese"));
    }
}