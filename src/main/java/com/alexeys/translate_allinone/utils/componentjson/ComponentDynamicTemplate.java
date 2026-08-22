package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.versionapi.ComponentCodec;
import com.alexeys.translate_allinone.versionapi.MinecraftComponentCodec;
import net.minecraft.text.Text;

import java.util.Set;

public final class ComponentDynamicTemplate {
    private static final ComponentCodec<Text> COMPONENT_CODEC = MinecraftComponentCodec.INSTANCE;

    private final ComponentDynamicJsonTemplate jsonTemplate;

    private ComponentDynamicTemplate(ComponentDynamicJsonTemplate jsonTemplate) {
        this.jsonTemplate = jsonTemplate;
    }

    public static ComponentDynamicTemplate prepare(Text source) {
        return prepare(source, Set.of());
    }

    public static ComponentDynamicTemplate prepare(Text source, Set<String> privateTokens) {
        return new ComponentDynamicTemplate(ComponentDynamicJsonTemplate.prepare(
                COMPONENT_CODEC.encode(source == null ? Text.empty() : source),
                privateTokens
        ));
    }

    public Text templateComponent() {
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

    public Text restore(Text translatedTemplate) {
        if (translatedTemplate == null) {
            return Text.empty();
        }
        if (!hasDynamicValues()) {
            return translatedTemplate;
        }
        return COMPONENT_CODEC.decode(jsonTemplate.restore(COMPONENT_CODEC.encode(translatedTemplate)));
    }
}
