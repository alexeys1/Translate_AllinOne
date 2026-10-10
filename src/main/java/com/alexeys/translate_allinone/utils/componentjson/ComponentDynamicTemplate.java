package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.versionapi.ComponentCodec;
import com.alexeys.translate_allinone.versionapi.MinecraftComponentCodec;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ComponentDynamicTemplate {
    private static final ComponentCodec<Text> COMPONENT_CODEC = MinecraftComponentCodec.INSTANCE;
    private static final int TEMPLATE_CACHE_LIMIT = 256;
    private static final Map<Key, Prepared> TEMPLATES = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, Prepared> eldest) {
                    return size() > TEMPLATE_CACHE_LIMIT;
                }
            }
    );

    private final ComponentDynamicJsonTemplate jsonTemplate;
    private final Text templateComponent;

    private ComponentDynamicTemplate(ComponentDynamicJsonTemplate jsonTemplate, Text templateComponent) {
        this.jsonTemplate = jsonTemplate;
        this.templateComponent = templateComponent;
    }

    public static ComponentDynamicTemplate prepare(Text source) {
        return prepare(source, Set.of());
    }

    public static ComponentDynamicTemplate prepare(Text source, Set<String> privateTokens) {
        Key key = new Key(
                source == null ? Text.empty() : source,
                privateTokens == null ? Set.of() : privateTokens
        );
        Prepared prepared = TEMPLATES.get(key);
        if (prepared == null) {
            ComponentDynamicJsonTemplate jsonTemplate = ComponentDynamicJsonTemplate.prepare(
                    COMPONENT_CODEC.encode(key.source()),
                    key.privateTokens()
            );
            prepared = new Prepared(jsonTemplate, COMPONENT_CODEC.decode(jsonTemplate.templateJson()));
            TEMPLATES.put(key, prepared);
        }
        return new ComponentDynamicTemplate(prepared.jsonTemplate(), prepared.templateComponent());
    }

    public Text templateComponent() {
        return templateComponent.copy();
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

    private record Key(Text source, Set<String> privateTokens) {
    }

    private record Prepared(ComponentDynamicJsonTemplate jsonTemplate, Text templateComponent) {
    }
}
