package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.versionapi.ComponentCodec;
import com.alexeys.translate_allinone.versionapi.MinecraftComponentCodec;
import com.google.gson.JsonElement;
import net.minecraft.text.Text;

public final class ComponentDocumentBuilder {
    private static final ComponentCodec<Text> COMPONENT_CODEC = MinecraftComponentCodec.INSTANCE;

    private final ComponentJsonDocumentBuilder jsonBuilder;

    public ComponentDocumentBuilder() {
        this(ComponentJsonLimits.DEFAULT);
    }

    public ComponentDocumentBuilder(ComponentJsonLimits limits) {
        this.jsonBuilder = new ComponentJsonDocumentBuilder(limits);
    }

    public ComponentTranslationDocument build(Text text, ComponentTranslationRoute route) {
        return build(COMPONENT_CODEC.encode(text), ComponentTranslationPolicy.forRoute(route));
    }

    public ComponentTranslationDocument build(Text text, ComponentTranslationPolicy policy) {
        return build(COMPONENT_CODEC.encode(text), policy);
    }

    public ComponentTranslationDocument build(JsonElement sourceJson, ComponentTranslationPolicy policy) {
        return jsonBuilder.build(sourceJson, policy);
    }
}
