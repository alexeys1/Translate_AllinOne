package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatOutputSpeakerHeaderTest {

    @Test
    void removesSkyblockNpcHeaderFromTranslationPayload() {
        MutableComponent message = Component.empty();
        message.append(Component.literal("[NPC] ").withStyle(ChatFormatting.YELLOW));
        message.append(Component.literal("Maddox").withStyle(ChatFormatting.GOLD));
        message.append(Component.literal(": ").withStyle(ChatFormatting.WHITE));
        message.append(Component.literal("Bring me the items.").withStyle(ChatFormatting.WHITE));

        ChatOutputTranslateManager.PreparedChatTranslation prepared =
                ChatOutputTranslateManager.prepareTranslationPayload(message);

        assertTrue(prepared.header().contains("Maddox"), "header should keep the NPC name");
        assertFalse(prepared.textToTranslate().contains("Maddox"), "NPC name must not reach translation");
        assertFalse(prepared.textToTranslate().contains("[NPC]"), "NPC marker must not reach translation");
        assertTrue(prepared.textToTranslate().contains("Bring me the items."), "dialogue should be translated");
        assertFalse(prepared.textToTranslate().startsWith("</"), "header closing tags must not leak into body");
    }

    @Test
    void reassemblesNpcHeaderAroundTranslatedDialogue() {
        MutableComponent message = Component.empty();
        message.append(Component.literal("[NPC] ").withStyle(ChatFormatting.YELLOW));
        message.append(Component.literal("Maddox").withStyle(ChatFormatting.GOLD));
        message.append(Component.literal(": ").withStyle(ChatFormatting.WHITE));
        message.append(Component.literal("Bring me the items.").withStyle(ChatFormatting.WHITE));

        ChatOutputTranslateManager.PreparedChatTranslation prepared =
                ChatOutputTranslateManager.prepareTranslationPayload(message);
        Component translated = ChatOutputTranslateManager.rebuildTranslatedText(
                "Apporte-moi les objets.",
                prepared
        );

        String result = translated.getString();
        assertTrue(result.startsWith("[NPC] Maddox: "), "original header must be preserved");
        assertTrue(result.contains("Apporte-moi les objets."), "translated dialogue must be present");
    }

    @Test
    void removesSimplePlayerChatHeader() {
        MutableComponent message = Component.empty();
        message.append(Component.literal("Alex").withStyle(ChatFormatting.AQUA));
        message.append(Component.literal(": ").withStyle(ChatFormatting.WHITE));
        message.append(Component.literal("Hello there!").withStyle(ChatFormatting.WHITE));

        ChatOutputTranslateManager.PreparedChatTranslation prepared =
                ChatOutputTranslateManager.prepareTranslationPayload(message);

        assertTrue(prepared.header().contains("Alex"));
        assertFalse(prepared.textToTranslate().contains("Alex"));
        assertTrue(prepared.textToTranslate().contains("Hello there!"));
    }

    @Test
    void removesVanillaAngleBracketHeader() {
        MutableComponent message = Component.empty();
        message.append(Component.literal("<Alex> ").withStyle(ChatFormatting.AQUA));
        message.append(Component.literal("Hello world").withStyle(ChatFormatting.WHITE));

        ChatOutputTranslateManager.PreparedChatTranslation prepared =
                ChatOutputTranslateManager.prepareTranslationPayload(message);

        assertTrue(prepared.header().contains("<Alex>"));
        assertFalse(prepared.textToTranslate().contains("<Alex>"));
        assertTrue(prepared.textToTranslate().contains("Hello world"));
    }

    @Test
    void leavesHeaderlessServerMessageUntouched() {
        MutableComponent message = Component.literal("Welcome to the server!")
                .withStyle(ChatFormatting.YELLOW);

        ChatOutputTranslateManager.PreparedChatTranslation prepared =
                ChatOutputTranslateManager.prepareTranslationPayload(message);

        assertTrue(prepared.header().isEmpty());
        assertTrue(prepared.textToTranslate().contains("Welcome to the server!"));
    }

    @Test
    void bodyColonsAreNotMistakenForHeaderSeparator() {
        MutableComponent message = Component.empty();
        message.append(Component.literal("Alex").withStyle(ChatFormatting.AQUA));
        message.append(Component.literal(": ").withStyle(ChatFormatting.WHITE));
        message.append(Component.literal("Check this: it works.").withStyle(ChatFormatting.WHITE));

        ChatOutputTranslateManager.PreparedChatTranslation prepared =
                ChatOutputTranslateManager.prepareTranslationPayload(message);

        assertTrue(prepared.header().contains("Alex"));
        assertFalse(prepared.textToTranslate().contains("Alex"));
        assertTrue(prepared.textToTranslate().contains("Check this: it works."));
    }

    @Test
    void removesTimestampAndRankHeader() {
        MutableComponent message = Component.empty();
        message.append(Component.literal("[12:34] ").withStyle(ChatFormatting.GRAY));
        message.append(Component.literal("[VIP] ").withStyle(ChatFormatting.GOLD));
        message.append(Component.literal("Steve").withStyle(ChatFormatting.AQUA));
        message.append(Component.literal(": ").withStyle(ChatFormatting.WHITE));
        message.append(Component.literal("Hello everyone!").withStyle(ChatFormatting.WHITE));

        ChatOutputTranslateManager.PreparedChatTranslation prepared =
                ChatOutputTranslateManager.prepareTranslationPayload(message);

        assertTrue(prepared.header().contains("[12:34]"));
        assertTrue(prepared.header().contains("[VIP]"));
        assertTrue(prepared.header().contains("Steve"));
        assertFalse(prepared.textToTranslate().contains("Steve"));
        assertFalse(prepared.textToTranslate().contains("[VIP]"));
        assertTrue(prepared.textToTranslate().contains("Hello everyone!"));
    }
}
