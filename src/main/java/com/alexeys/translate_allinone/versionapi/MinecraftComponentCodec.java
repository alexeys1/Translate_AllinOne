package com.alexeys.translate_allinone.versionapi;

import com.alexeys.translate_allinone.utils.componentjson.ComponentJsonException;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

public final class MinecraftComponentCodec implements ComponentCodec<Component> {
    public static final MinecraftComponentCodec INSTANCE = new MinecraftComponentCodec();

    private MinecraftComponentCodec() {
    }

    @Override
    public JsonElement encode(Component component) {
        if (component == null) {
            throw new ComponentJsonException(ComponentJsonException.Kind.CODEC, "Cannot encode a null Component.");
        }
        try {
            return ComponentSerialization.CODEC
                    .encodeStart(JsonOps.INSTANCE, component)
                    .getOrThrow(message -> new ComponentJsonException(
                            ComponentJsonException.Kind.CODEC,
                            "Failed to encode Component: " + message
                    ));
        } catch (ComponentJsonException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ComponentJsonException(ComponentJsonException.Kind.CODEC, "Failed to encode Component.", e);
        }
    }

    @Override
    public Component decode(JsonElement json) {
        if (json == null) {
            throw new ComponentJsonException(ComponentJsonException.Kind.CODEC, "Cannot decode null Component JSON.");
        }
        try {
            return ComponentSerialization.CODEC
                    .parse(JsonOps.INSTANCE, json.deepCopy())
                    .getOrThrow(message -> new ComponentJsonException(
                            ComponentJsonException.Kind.CODEC,
                            "Failed to decode Component JSON: " + message
                    ));
        } catch (ComponentJsonException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ComponentJsonException(ComponentJsonException.Kind.CODEC, "Failed to decode Component JSON.", e);
        }
    }
}
