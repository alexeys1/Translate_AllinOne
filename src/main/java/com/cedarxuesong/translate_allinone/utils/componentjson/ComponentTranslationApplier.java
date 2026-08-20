package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.versionapi.MinecraftComponentCodec;
import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;

public final class ComponentTranslationApplier {
    private final ComponentTranslationAdapter<Component> adapter;

    public ComponentTranslationApplier() {
        this(new ComponentTranslationValidator());
    }

    public ComponentTranslationApplier(ComponentTranslationValidator validator) {
        this.adapter = new ComponentTranslationAdapter<>(MinecraftComponentCodec.INSTANCE, validator);
    }

    public Component apply(
            ComponentTranslationDocument document,
            ComponentTranslationResponse response
    ) {
        return adapter.apply(document, response);
    }

    public JsonElement applyToJson(
            ComponentTranslationDocument document,
            ComponentTranslationResponse response
    ) {
        return adapter.applyToJson(document, response);
    }
}
