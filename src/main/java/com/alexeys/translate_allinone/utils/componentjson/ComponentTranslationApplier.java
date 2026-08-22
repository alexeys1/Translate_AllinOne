package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.versionapi.MinecraftComponentCodec;
import com.google.gson.JsonElement;
import net.minecraft.text.Text;

public final class ComponentTranslationApplier {
    private final ComponentTranslationAdapter<Text> adapter;

    public ComponentTranslationApplier() {
        this(new ComponentTranslationValidator());
    }

    public ComponentTranslationApplier(ComponentTranslationValidator validator) {
        this.adapter = new ComponentTranslationAdapter<>(MinecraftComponentCodec.INSTANCE, validator);
    }

    public Text apply(
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
