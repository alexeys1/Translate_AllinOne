package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.mixin.mixinScreenTranslate.UiTranslationDrawContextMixin;
import com.alexeys.translate_allinone.mixin.mixinScreenTranslate.UiTranslationChatComponentMixin;
import com.alexeys.translate_allinone.mixin.mixinScreenTranslate.UiTranslationCommandSuggestionsMixin;
import com.alexeys.translate_allinone.mixin.mixinScreenTranslate.UiTranslationFontMixin;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTranslationScopeBoundaryTest {
    @Test
    void genericTextEntrypointsLeaveScoreboardTextUntouchedOutsideExplicitScope() {
        assertFalse(UiTranslationScope.isActive());

        Text source = Text.literal("Scoreboard: 123");
        OrderedText sequence = source.asOrderedText();
        List<Text> components = List.of(source);

        assertSame(source, UiTranslationRuntime.translateComponent(source, UiTextRole.OPTION));
        assertSame(source, UiTranslationRuntime.translateFormattedText(source, UiTextRole.OPTION));
        assertSame(sequence, UiTranslationRuntime.translateFormattedCharSequence(sequence, UiTextRole.OPTION));
        assertSame(components, UiTranslationRuntime.translateComponents(components, UiTextRole.OPTION));
        assertFalse(UiTranslationScope.isActive());
    }

    @Test
    void catchAllMixinsDoNotFallbackToCurrentScreen() throws IOException {
        for (Class<?> mixinClass : List.of(
                UiTranslationDrawContextMixin.class,
                UiTranslationFontMixin.class
        )) {
            try (InputStream stream = mixinClass.getResourceAsStream(mixinClass.getSimpleName() + ".class")) {
                assertNotNull(stream);
                String bytecode = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(bytecode.contains("InCurrentScreen"), mixinClass.getName());
            }
        }
    }

    @Test
    void internalScopeClosesCleanly() {
        try (UiTranslationScope.Scope scope = UiTranslationScope.enterInternal()) {
            assertTrue(UiTranslationScope.isInternal());
        }
        assertFalse(UiTranslationScope.isInternal());
    }

    @Test
    void chatHistoryMixinsUseInternalScope() throws IOException {
        for (Class<?> mixinClass : List.of(
                UiTranslationChatComponentMixin.class,
                UiTranslationCommandSuggestionsMixin.class
        )) {
            try (InputStream stream = mixinClass.getResourceAsStream(mixinClass.getSimpleName() + ".class")) {
                assertNotNull(stream);
                String bytecode = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertTrue(bytecode.contains("enterInternal"), mixinClass.getName());
                assertFalse(bytecode.contains("InCurrentScreen"), mixinClass.getName());
            }
        }
    }
}
