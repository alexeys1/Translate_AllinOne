package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatOutputLineIdentityTest {
    @Test
    void findsExactInstanceAmongValueEqualDuplicates() {
        ChatHudLine first = line(0, "same");
        ChatHudLine target = line(0, "same");
        List<ChatHudLine> messages = List.of(first, target);

        assertEquals(1, ChatOutputTranslateManager.indexOfLineByIdentity(messages, target));
    }

    @Test
    void returnsMinusOneWhenTargetNotPresent() {
        ChatHudLine present = line(0, "a");
        ChatHudLine missing = line(0, "b");

        assertEquals(-1, ChatOutputTranslateManager.indexOfLineByIdentity(List.of(present), missing));
    }

    @Test
    void returnsMinusOneForNullArguments() {
        ChatHudLine line = line(0, "a");

        assertEquals(-1, ChatOutputTranslateManager.indexOfLineByIdentity(null, null));
        assertEquals(-1, ChatOutputTranslateManager.indexOfLineByIdentity(List.of(line), null));
        assertEquals(-1, ChatOutputTranslateManager.indexOfLineByIdentity(null, line));
    }

    private static ChatHudLine line(int creationTick, String text) {
        return new ChatHudLine(creationTick, Text.literal(text), null, null);
    }
}
