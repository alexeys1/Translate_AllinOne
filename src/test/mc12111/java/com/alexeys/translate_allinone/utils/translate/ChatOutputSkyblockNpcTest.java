package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatOutputSkyblockNpcTest {

    @Test
    void recognizesFormattedSkyblockNpcDialogue() {
        assertTrue(ChatOutputTranslateManager.isSkyblockNpcMessage(npcMessage()));
    }

    @Test
    void recognizesSkyblockNpcInsideTimestampWrapper() {
        Text wrapped = Text.empty()
                .append(Text.literal("[12:34:56] ").formatted(Formatting.GRAY))
                .append(npcMessage());

        assertTrue(ChatOutputTranslateManager.isSkyblockNpcMessage(wrapped));
    }

    @Test
    void recognizesSkyblockNpcInsideAvatarWrapper() {
        Text wrapped = Text.empty()
                .append(Text.literal("\uFFFC").formatted(Formatting.DARK_GRAY))
                .append(npcMessage());

        assertTrue(ChatOutputTranslateManager.isSkyblockNpcMessage(wrapped));
    }

    @Test
    void recognizesSkyblockNpcInsideNestedWrapper() {
        Text inner = Text.empty()
                .append(Text.literal("[12:34:56] ").formatted(Formatting.GRAY))
                .append(npcMessage());
        Text outer = Text.empty().append(inner);

        assertTrue(ChatOutputTranslateManager.isSkyblockNpcMessage(outer));
    }

    @Test
    void rejectsOrdinaryChat() {
        assertFalse(ChatOutputTranslateManager.isSkyblockNpcMessage(
                Text.literal("Hello world")
        ));
    }

    private static Text npcMessage() {
        return Text.empty()
                .append(Text.literal("[NPC] ").formatted(Formatting.YELLOW))
                .append(Text.literal("Maddox").formatted(Formatting.GOLD))
                .append(Text.literal(": ").formatted(Formatting.WHITE))
                .append(Text.literal("Bring me the items.").formatted(Formatting.WHITE));
    }
}
