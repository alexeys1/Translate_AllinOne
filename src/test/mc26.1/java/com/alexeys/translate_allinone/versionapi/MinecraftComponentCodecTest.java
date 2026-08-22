package com.alexeys.translate_allinone.versionapi;

import com.alexeys.translate_allinone.utils.componentjson.ComponentDocumentBuilder;
import com.alexeys.translate_allinone.utils.componentjson.ComponentDynamicTemplate;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.google.gson.JsonElement;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftComponentCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void roundTripPreservesTextAndStyle() {
        Component source = Component.literal("Hello")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" world"));

        JsonElement encoded = MinecraftComponentCodec.INSTANCE.encode(source);
        Component decoded = MinecraftComponentCodec.INSTANCE.decode(encoded);

        assertEquals(source.getString(), decoded.getString());
        assertEquals(source.getStyle().getColor(), decoded.getStyle().getColor());
    }

    @Test
    void documentBuilderDelegatesThroughMinecraftCodec() {
        ComponentTranslationDocument document = new ComponentDocumentBuilder().build(
                Component.literal("Level 42"),
                ComponentTranslationRoute.CHAT_OUTPUT
        );

        assertEquals(1, document.units().size());
        assertEquals("Level 42", document.units().get(0).sourceText());
    }

    @Test
    void dynamicTemplateRestoresMinecraftComponent() {
        ComponentDynamicTemplate template = ComponentDynamicTemplate.prepare(Component.literal("Level 42"));

        assertEquals("Level {d1}", template.templateComponent().getString());
        assertEquals("等级 42", template.restore(Component.literal("等级 {d1}")).getString());
    }
}
