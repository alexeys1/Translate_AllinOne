package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;

public final class ComponentTranslationApplier {
    private final ComponentTranslationJsonApplier jsonApplier;

    public ComponentTranslationApplier() {
        this(new ComponentTranslationValidator());
    }

    public ComponentTranslationApplier(ComponentTranslationValidator validator) {
        this.jsonApplier = new ComponentTranslationJsonApplier(validator);
    }

    public Component apply(
            ComponentTranslationDocument document,
            ComponentTranslationResponse response
    ) {
        return ComponentJsonCodec.decode(applyToJson(document, response));
    }

    public JsonElement applyToJson(
            ComponentTranslationDocument document,
            ComponentTranslationResponse response
    ) {
        return jsonApplier.apply(document, response);
    }
}
