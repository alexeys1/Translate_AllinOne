package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.versionapi.ComponentCodec;
import com.alexeys.translate_allinone.versionapi.MinecraftComponentCodec;
import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;

public final class ComponentDocumentBuilder {
    private static final ComponentCodec<Component> COMPONENT_CODEC = MinecraftComponentCodec.INSTANCE;

    private final ComponentJsonDocumentBuilder jsonBuilder;

    public ComponentDocumentBuilder() {
        this(ComponentJsonLimits.DEFAULT);
    }

    public ComponentDocumentBuilder(ComponentJsonLimits limits) {
        this.jsonBuilder = new ComponentJsonDocumentBuilder(limits);
    }

    public ComponentTranslationDocument build(Component component, ComponentTranslationRoute route) {
        return build(COMPONENT_CODEC.encode(component), ComponentTranslationPolicy.forRoute(route));
    }

    public ComponentTranslationDocument build(Component component, ComponentTranslationPolicy policy) {
        return build(COMPONENT_CODEC.encode(component), policy);
    }

    public ComponentTranslationDocument build(JsonElement sourceJson, ComponentTranslationPolicy policy) {
        return jsonBuilder.build(sourceJson, policy);
    }
}
