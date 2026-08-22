package com.alexeys.translate_allinone.versionapi;

import com.alexeys.translate_allinone.utils.componentjson.ComponentJsonException;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

public final class MinecraftComponentCodec implements ComponentCodec<Text> {
    public static final MinecraftComponentCodec INSTANCE = new MinecraftComponentCodec();

    private MinecraftComponentCodec() {
    }

    @Override
    public JsonElement encode(Text text) {
        if (text == null) {
            throw new ComponentJsonException(ComponentJsonException.Kind.CODEC, "Cannot encode a null Text.");
        }
        try {
            return TextCodecs.CODEC
                    .encodeStart(JsonOps.INSTANCE, text)
                    .getOrThrow(message -> new ComponentJsonException(
                            ComponentJsonException.Kind.CODEC,
                            "Failed to encode Text: " + message
                    ));
        } catch (ComponentJsonException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ComponentJsonException(ComponentJsonException.Kind.CODEC, "Failed to encode Text.", e);
        }
    }

    @Override
    public Text decode(JsonElement json) {
        if (json == null) {
            throw new ComponentJsonException(ComponentJsonException.Kind.CODEC, "Cannot decode null Text JSON.");
        }
        try {
            return TextCodecs.CODEC
                    .parse(JsonOps.INSTANCE, json.deepCopy())
                    .getOrThrow(message -> new ComponentJsonException(
                            ComponentJsonException.Kind.CODEC,
                            "Failed to decode Text: " + message
                    ));
        } catch (ComponentJsonException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ComponentJsonException(ComponentJsonException.Kind.CODEC, "Failed to decode Text.", e);
        }
    }
}
