package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignTranslationSupportTest {
    @Test
    void distinguishesUnchangedGroupResultFromTranslatedFace() {
        Component[] source = lines("<--------------", "The Forge", "", "");
        Component[] unchanged = lines("<--------------", "The Forge", "", "");
        Component[] translated = lines("<--------------", "锻造坊", "", "");

        assertFalse(SignTranslationSupport.hasVisibleTranslation(source, unchanged));
        assertTrue(SignTranslationSupport.hasVisibleTranslation(source, translated));
    }

    private static Component[] lines(String first, String second, String third, String fourth) {
        return new Component[]{
                Component.literal(first),
                Component.literal(second),
                Component.literal(third),
                Component.literal(fourth)
        };
    }
}
