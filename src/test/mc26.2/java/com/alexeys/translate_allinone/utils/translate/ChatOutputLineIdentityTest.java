package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatOutputLineIdentityTest {
    @Test
    void findsExactInstanceAmongValueEqualDuplicates() {
        GuiMessage first = line(0, "same");
        GuiMessage target = line(0, "same");
        List<GuiMessage> messages = List.of(first, target);

        assertEquals(1, ChatOutputTranslateManager.indexOfLineByIdentity(messages, target));
    }

    @Test
    void returnsMinusOneWhenTargetNotPresent() {
        GuiMessage present = line(0, "a");
        GuiMessage missing = line(0, "b");

        assertEquals(-1, ChatOutputTranslateManager.indexOfLineByIdentity(List.of(present), missing));
    }

    @Test
    void returnsMinusOneForNullArguments() {
        GuiMessage line = line(0, "a");

        assertEquals(-1, ChatOutputTranslateManager.indexOfLineByIdentity(null, null));
        assertEquals(-1, ChatOutputTranslateManager.indexOfLineByIdentity(List.of(line), null));
        assertEquals(-1, ChatOutputTranslateManager.indexOfLineByIdentity(null, line));
    }

    private static GuiMessage line(int addedTime, String text) {
        return new GuiMessage(addedTime, Component.literal(text), null, null, null);
    }
}
