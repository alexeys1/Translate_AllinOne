package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.versionapi.ComponentCodec;
import com.cedarxuesong.translate_allinone.versionapi.MinecraftComponentCodec;
import net.minecraft.network.chat.Component;

import java.util.Set;

public final class ComponentDynamicTemplate {
    private static final ComponentCodec<Component> COMPONENT_CODEC = MinecraftComponentCodec.INSTANCE;

    private final ComponentDynamicJsonTemplate jsonTemplate;

    private ComponentDynamicTemplate(ComponentDynamicJsonTemplate jsonTemplate) {
        this.jsonTemplate = jsonTemplate;
    }

    public static ComponentDynamicTemplate prepare(Component source) {
        return prepare(source, Set.of());
    }

    public static ComponentDynamicTemplate prepare(Component source, Set<String> privateTokens) {
        return new ComponentDynamicTemplate(ComponentDynamicJsonTemplate.prepare(
                COMPONENT_CODEC.encode(source == null ? Component.empty() : source),
                privateTokens
        ));
    }

    public Component templateComponent() {
        return COMPONENT_CODEC.decode(jsonTemplate.templateJson());
    }

    ComponentDynamicJsonTemplate jsonTemplate() {
        return jsonTemplate;
    }

    public Set<String> privatePlaceholders() {
        return jsonTemplate.privatePlaceholders();
    }

    public boolean hasDynamicValues() {
        return jsonTemplate.hasDynamicValues();
    }

    public Component restore(Component translatedTemplate) {
        if (translatedTemplate == null) {
            return Component.empty();
        }
        if (!hasDynamicValues()) {
            return translatedTemplate;
        }
        return COMPONENT_CODEC.decode(jsonTemplate.restore(COMPONENT_CODEC.encode(translatedTemplate)));
    }
}
