package com.alexeys.translate_allinone.utils.componentjson;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ScreenUiDecorativeGlyphTemplateTest {
    @Test
    void protectsAndRestoresDecorativeGlyph() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        String glyph = "\uE000";
        ComponentDynamicTemplate template = ComponentDynamicTemplate.prepare(
                Component.literal(glyph + " Settings"),
                Set.of(glyph)
        );

        ComponentTranslationDocument document = ComponentTranslationRuntime.prepare(
                template.templateComponent(),
                ComponentTranslationRoute.SCREEN_UI,
                "example/screen/option",
                "screen-ui-v1:option",
                template.privatePlaceholders()
        );

        assertEquals("{taio.private.a} Settings", template.templateComponent().getString());
        assertEquals(Set.of("{taio.private.a}"), template.privatePlaceholders());
        assertFalse(document.units().isEmpty());
        assertEquals(glyph + " 设置", template.restore(Component.literal("{taio.private.a} 设置")).getString());
    }
}
