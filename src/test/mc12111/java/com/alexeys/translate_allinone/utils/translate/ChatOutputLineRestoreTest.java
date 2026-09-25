package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.mixin.mixinChatHud.ChatHudAccessor;
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

class ChatOutputLineRestoreTest {
    @Test
    void ignoresLinesWithoutTrackedOriginal() throws Exception {
        UUID messageId = UUID.randomUUID();
        ChatHudLine placeholder = line(0, "pending");
        List<ChatHudLine> messages = new ArrayList<>(List.of(placeholder));

        boolean restored = restoreOriginalChatLine(messageId, placeholder, new FakeChatHudAccessor(messages), messages);

        assertFalse(restored);
        assertEquals("pending", messages.get(0).content().getString());
    }

    @Test
    void ignoresLinesMissingFromTheMessageList() throws Exception {
        UUID messageId = UUID.randomUUID();
        MessageUtils.putTrackedMessage(messageId, Text.literal("hello"));

        ChatHudLine absent = line(0, "pending");
        List<ChatHudLine> messages = new ArrayList<>();

        boolean restored = restoreOriginalChatLine(messageId, absent, new FakeChatHudAccessor(messages), messages);

        assertFalse(restored);
        assertEquals(0, messages.size());
    }

    private static boolean restoreOriginalChatLine(
            UUID messageId,
            ChatHudLine pendingLine,
            ChatHudAccessor chatHudAccessor,
            List<ChatHudLine> messages
    ) throws Exception {
        Method method = ChatOutputTranslateManager.class.getDeclaredMethod(
                "restoreOriginalChatLine",
                UUID.class,
                ChatHudLine.class,
                ChatHudAccessor.class,
                List.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(null, messageId, pendingLine, chatHudAccessor, messages);
    }

    private static ChatHudLine line(int creationTick, String text) {
        return new ChatHudLine(creationTick, Text.literal(text), null, null);
    }

    private static final class FakeChatHudAccessor implements ChatHudAccessor {
        private final List<ChatHudLine> messages;
        private final List<ChatHudLine.Visible> visibleMessages = new ArrayList<>();
        private int scrolledLines;

        private FakeChatHudAccessor(List<ChatHudLine> messages) {
            this.messages = messages;
        }

        @Override
        public List<ChatHudLine> getMessages() {
            return messages;
        }

        @Override
        public List<ChatHudLine.Visible> getVisibleMessages() {
            return visibleMessages;
        }

        @Override
        public int getScrolledLines() {
            return scrolledLines;
        }

        @Override
        public void setScrolledLines(int scrolledLines) {
            this.scrolledLines = scrolledLines;
        }

        @Override
        public void invokeRefresh() {
        }

        @Override
        public int invokeGetLineHeight() {
            return 9;
        }
    }
}