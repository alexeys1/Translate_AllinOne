package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentDynamicTemplate;
import java.util.Set;
import net.minecraft.network.chat.Component;

final class ExternalScoreboardComponentTemplate {
    private ExternalScoreboardComponentTemplate() {
    }

    static Prepared prepare(Component source, Set<String> privateTokens) {
        return new Prepared(ComponentDynamicTemplate.prepare(source, privateTokens));
    }

    record Prepared(ComponentDynamicTemplate dynamicTemplate) {
        Prepared {
            dynamicTemplate = dynamicTemplate == null
                    ? ComponentDynamicTemplate.prepare(Component.empty())
                    : dynamicTemplate;
        }

        Component templateComponent() {
            return dynamicTemplate.templateComponent();
        }

        Set<String> privatePlaceholders() {
            return dynamicTemplate.privatePlaceholders();
        }

        Component restore(Component translatedTemplate) {
            return dynamicTemplate.restore(translatedTemplate);
        }
    }
}
