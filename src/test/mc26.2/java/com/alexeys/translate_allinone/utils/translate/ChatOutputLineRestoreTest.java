package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.mixin.mixinChatHud.ChatHudAccessor;
import com.alexeys.translate_allinone.utils.MessageUtils;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatOutputLineRestoreTest {
    @Test
    void restoresTheLocatedLineWithoutTouchingValueEqualDuplicates() throws Exception {
        UUID messageId = UUID.randomUUID();
        MessageUtils.putTrackedMessage(messageId, Component.literal("hello"));

        GuiMessage duplicate = line(7, "same");
        GuiMessage placeholder = line(7, "same");
        List<GuiMessage> messages = new ArrayList<>(List.of(duplicate, placeholder));

        boolean restored = restoreOriginalChatLine(messageId, placeholder, new FakeChatHudAccessor(messages), messages);

        assertFalse(restored, "without trimmed rows nothing can be rendered");
        assertEquals("same", messages.get(0).content().getString(), "the value-equal twin must stay untouched");
        assertEquals("hello", messages.get(1).content().getString(), "the located line gets the tracked original");
    }

    @Test
    void ignoresLinesWithoutTrackedOriginal() throws Exception {
        UUID messageId = UUID.randomUUID();
        GuiMessage placeholder = line(0, "pending");
        List<GuiMessage> messages = new ArrayList<>(List.of(placeholder));

        boolean restored = restoreOriginalChatLine(messageId, placeholder, new FakeChatHudAccessor(messages), messages);

        assertFalse(restored);
        assertEquals("pending", messages.get(0).content().getString());
    }

    private static boolean restoreOriginalChatLine(
            UUID messageId,
            GuiMessage pendingLine,
            ChatHudAccessor chatHudAccessor,
            List<GuiMessage> messages
    ) throws Exception {
        Method method = ChatOutputTranslateManager.class.getDeclaredMethod(
                "restoreOriginalChatLine",
                UUID.class,
                GuiMessage.class,
                ChatHudAccessor.class,
                List.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(null, messageId, pendingLine, chatHudAccessor, messages);
    }

    private static GuiMessage line(int addedTime, String text) {
        return new GuiMessage(addedTime, Component.literal(text), null, null, null);
    }

    private static final class FakeChatHudAccessor implements ChatHudAccessor {
        private final List<GuiMessage> messages;
        private final List<GuiMessage.Line> trimmedMessages = new ArrayList<>();
        private int scrolledLines;

        private FakeChatHudAccessor(List<GuiMessage> messages) {
            this.messages = messages;
        }

        @Override
        public List<GuiMessage> getMessages() {
            return messages;
        }

        @Override
        public List<GuiMessage.Line> getTrimmedMessages() {
            return trimmedMessages;
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
