package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignTranslationSupportTest {
    @Test
    void distinguishesUnchangedGroupResultFromTranslatedFace() {
        Text[] source = lines("<--------------", "The Forge", "", "");
        Text[] unchanged = lines("<--------------", "The Forge", "", "");
        Text[] translated = lines("<--------------", "锻造坊", "", "");

        assertFalse(SignTranslationSupport.hasVisibleTranslation(source, unchanged));
        assertTrue(SignTranslationSupport.hasVisibleTranslation(source, translated));
    }

    private static Text[] lines(String first, String second, String third, String fourth) {
        return new Text[]{
                Text.literal(first),
                Text.literal(second),
                Text.literal(third),
                Text.literal(fourth)
        };
    }
}
