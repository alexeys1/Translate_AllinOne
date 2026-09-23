package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.MessageUtils;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatOutputLineRestoreTest {
    @Test
    void restoresTheLocatedLineWithoutTouchingValueEqualDuplicates() throws Exception {
        UUID messageId = UUID.randomUUID();
        MessageUtils.putTrackedMessage(messageId, Text.literal("hello"));

        ChatHudLine duplicate = line(7, "same");
        ChatHudLine placeholder = line(7, "same");
        List<ChatHudLine> messages = new ArrayList<>(List.of(duplicate, placeholder));

        boolean restored = restoreOriginalChatLine(messageId, placeholder, messages);

        assertTrue(restored);
        assertEquals("same", messages.get(0).content().getString(), "the value-equal twin must stay untouched");
        assertEquals("hello", messages.get(1).content().getString(), "the located line gets the tracked original");
    }

    @Test
    void ignoresLinesWithoutTrackedOriginal() throws Exception {
        UUID messageId = UUID.randomUUID();
        ChatHudLine placeholder = line(0, "pending");
        List<ChatHudLine> messages = new ArrayList<>(List.of(placeholder));

        boolean restored = restoreOriginalChatLine(messageId, placeholder, messages);

        assertFalse(restored);
        assertEquals("pending", messages.get(0).content().getString());
    }

    @Test
    void ignoresLinesMissingFromTheChatHud() throws Exception {
        UUID messageId = UUID.randomUUID();
        MessageUtils.putTrackedMessage(messageId, Text.literal("hello"));

        ChatHudLine absent = line(3, "gone");
        List<ChatHudLine> messages = new ArrayList<>(List.of(line(3, "other")));

        assertFalse(restoreOriginalChatLine(messageId, absent, messages));
        assertEquals("other", messages.get(0).content().getString());
    }

    private static boolean restoreOriginalChatLine(
            UUID messageId,
            ChatHudLine pendingLine,
            List<ChatHudLine> messages
    ) throws Exception {
        Method method = ChatOutputTranslateManager.class.getDeclaredMethod(
                "restoreOriginalChatLine",
                UUID.class,
                ChatHudLine.class,
                List.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(null, messageId, pendingLine, messages);
    }

    private static ChatHudLine line(int creationTick, String text) {
        return new ChatHudLine(creationTick, Text.literal(text), null, null);
    }
}
