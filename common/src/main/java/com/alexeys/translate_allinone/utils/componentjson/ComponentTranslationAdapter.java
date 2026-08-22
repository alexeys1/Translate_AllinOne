package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.versionapi.ComponentCodec;
import com.google.gson.JsonElement;

import java.util.Objects;

public final class ComponentTranslationAdapter<C> {
    private final ComponentCodec<C> componentCodec;
    private final ComponentTranslationJsonApplier jsonApplier;

    public ComponentTranslationAdapter(ComponentCodec<C> componentCodec) {
        this(componentCodec, new ComponentTranslationValidator());
    }

    public ComponentTranslationAdapter(
            ComponentCodec<C> componentCodec,
            ComponentTranslationValidator validator
    ) {
        this.componentCodec = Objects.requireNonNull(componentCodec, "componentCodec");
        this.jsonApplier = new ComponentTranslationJsonApplier(validator);
    }

    public C apply(
            ComponentTranslationDocument document,
            ComponentTranslationResponse response
    ) {
        return componentCodec.decode(applyToJson(document, response));
    }

    public JsonElement applyToJson(
            ComponentTranslationDocument document,
            ComponentTranslationResponse response
    ) {
        return jsonApplier.apply(document, response);
    }
}
