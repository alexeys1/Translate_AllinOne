package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatOutputLineLocatorTest {
    @Test
    void locatesAutomaticMessageInsideChatPatchesWrapper() {
        Text original = Text.literal("Automatic translation");
        Text decorated = Text.empty()
                .append(Text.literal("[12:34:56] "))
                .append(original.copy())
                .append(Text.empty());
        List<ChatHudLine> messages = List.of(
                new ChatHudLine(11, Text.literal("Other message"), null, null),
                new ChatHudLine(10, decorated, null, null)
        );

        assertEquals(1, ChatOutputTranslateManager.findTargetLineIndex(messages, original));
    }

    @Test
    void locatesNestedManualToggleByMessageId() {
        UUID messageId = UUID.randomUUID();
        Text original = Text.literal("Manual translation");
        Text toggle = Text.literal(" [T]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/translate_allinone translatechatline " + messageId + " translate"
        )));
        Text decorated = Text.empty()
                .append(Text.literal("[12:34:56] "))
                .append(Text.empty().append(original.copy()).append(toggle));
        List<ChatHudLine> messages = List.of(new ChatHudLine(10, decorated, null, null));

        assertEquals(0, ChatOutputTranslateManager.findTargetLineIndex(messages, messageId, "translate"));
    }
}
