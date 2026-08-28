package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatOutputLineIdentityTest {
    @Test
    void findsExactInstanceAmongEqualDuplicates() {
        ChatHudLine first = new ChatHudLine(0, Text.literal("same"), null, null);
        ChatHudLine target = new ChatHudLine(1, Text.literal("same"), null, null);
        List<ChatHudLine> messages = List.of(first, target);

        assertEquals(1, ChatOutputTranslateManager.indexOfLineByIdentity(messages, target));
    }

    @Test
    void returnsMinusOneWhenTargetNotPresent() {
        ChatHudLine present = new ChatHudLine(0, Text.literal("a"), null, null);
        ChatHudLine missing = new ChatHudLine(1, Text.literal("b"), null, null);

        assertEquals(-1, ChatOutputTranslateManager.indexOfLineByIdentity(List.of(present), missing));
    }

    @Test
    void returnsMinusOneForNullArguments() {
        ChatHudLine line = new ChatHudLine(0, Text.literal("a"), null, null);

        assertEquals(-1, ChatOutputTranslateManager.indexOfLineByIdentity(null, null));
        assertEquals(-1, ChatOutputTranslateManager.indexOfLineByIdentity(List.of(line), null));
        assertEquals(-1, ChatOutputTranslateManager.indexOfLineByIdentity(null, line));
    }
}
