package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatOutputSkyblockNpcTest {
    @Test
    void recognizesFormattedSkyblockNpcDialogue() {
        Text message = Text.literal("[NPC] ").formatted(Formatting.YELLOW)
                .append(Text.literal("Maddox").formatted(Formatting.GOLD))
                .append(Text.literal(": ").formatted(Formatting.WHITE))
                .append(Text.literal("Bring me the items.").formatted(Formatting.WHITE));

        assertTrue(ChatOutputTranslateManager.isSkyblockNpcMessage(message));
    }

    @Test
    void rejectsOrdinaryChatAndEmptyNpcDialogue() {
        Text ordinary = Text.literal("[NPC] Maddox: hello");
        Text emptyBody = Text.literal("[NPC] ").formatted(Formatting.YELLOW)
                .append(Text.literal("Maddox").formatted(Formatting.GOLD))
                .append(Text.literal(": ").formatted(Formatting.WHITE));

        assertFalse(ChatOutputTranslateManager.isSkyblockNpcMessage(ordinary));
        assertFalse(ChatOutputTranslateManager.isSkyblockNpcMessage(emptyBody));
    }
}
