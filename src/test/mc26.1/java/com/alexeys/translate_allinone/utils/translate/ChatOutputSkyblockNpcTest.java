package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
        Component wrapped = Component.empty()
                .append(Component.literal("[12:34:56] ").withStyle(ChatFormatting.GRAY))
                .append(npcMessage());

        assertTrue(ChatOutputTranslateManager.isSkyblockNpcMessage(wrapped));
    }

    @Test
    void recognizesSkyblockNpcInsideAvatarWrapper() {
        Component wrapped = Component.empty()
                .append(Component.literal("\uFFFC").withStyle(ChatFormatting.DARK_GRAY))
                .append(npcMessage());

        assertTrue(ChatOutputTranslateManager.isSkyblockNpcMessage(wrapped));
    }

    @Test
    void recognizesSkyblockNpcInsideNestedWrapper() {
        Component inner = Component.empty()
                .append(Component.literal("[12:34:56] ").withStyle(ChatFormatting.GRAY))
                .append(npcMessage());
        Component outer = Component.empty().append(inner);

        assertTrue(ChatOutputTranslateManager.isSkyblockNpcMessage(outer));
    }

    @Test
    void rejectsOrdinaryChat() {
        assertFalse(ChatOutputTranslateManager.isSkyblockNpcMessage(
                Component.literal("Hello world")
        ));
    }

    private static MutableComponent npcMessage() {
        return Component.empty()
                .append(Component.literal("[NPC] ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("Maddox").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("Bring me the items.").withStyle(ChatFormatting.WHITE));
    }
}
