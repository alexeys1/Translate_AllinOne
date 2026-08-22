package com.alexeys.translate_allinone.utils.componentjson;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentTranslationBundleTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void decodesCommonBundleResultAsStyledMinecraftComponent() {
        Component source = Component.literal("Level 42").withStyle(ChatFormatting.GOLD);
        ComponentTranslationBundle bundle = ComponentTranslationBundle.create(
                List.of(source),
                ComponentTranslationRoute.CHAT_OUTPUT,
                "status",
                "2"
        );
        String unitId = bundle.cacheDocument().units().getFirst().id();
        ComponentTranslationResponse response = new ComponentTranslationResponse(
                ComponentTranslationDocument.PROTOCOL,
                Map.of(unitId, "等级 {d1}")
        );

        Component translated = bundle.apply(response).getFirst();

        assertEquals("等级 42", translated.getString());
        assertEquals(source.getStyle().getColor(), translated.getStyle().getColor());
    }
}
