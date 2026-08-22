package com.alexeys.translate_allinone.utils.componentjson;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTranslationPolicyTest {
    @Test
    void protectsFormattingValuesLinksAndPrivateTokens() {
        ComponentTranslationPolicy policy = ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.TOOLTIP_LINE)
                .withPrivateTokens(Set.of("{taio.private.a}"))
                .withContext("tooltip:item");

        Map<String, Integer> tokens = policy.protectedTokenMultiset(
                "§aDamage {taio.private.a} 12.5% https://example.test <s0>Text</s0>"
        );

        assertEquals(1, tokens.get("§a"));
        assertEquals(2, tokens.get("{taio.private.a}"));
        assertEquals(1, tokens.get("12.5%"));
        assertEquals(1, tokens.get("https://example.test"));
        assertEquals(1, tokens.get("<s0>"));
        assertEquals(1, tokens.get("</s0>"));
        assertEquals("tooltip:item", policy.semanticSettings().get("context"));
        assertFalse(policy.allowsLiteral("\uE000"));
        assertTrue(policy.allowsLiteral("ordinary text"));
    }
}
