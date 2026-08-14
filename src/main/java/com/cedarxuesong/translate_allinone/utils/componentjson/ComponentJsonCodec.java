package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

public final class ComponentJsonCodec {
    private ComponentJsonCodec() {
    }

    public static JsonElement encode(Text text) {
        if (text == null) {
            throw new ComponentJsonException(ComponentJsonException.Kind.CODEC, "Cannot encode a null Text.");
        }
        try {
            return TextCodecs.CODEC
                    .encodeStart(JsonOps.INSTANCE, text)
                    .getOrThrow(false, message -> {
                        throw new ComponentJsonException(
                                ComponentJsonException.Kind.CODEC,
                                "Failed to encode Text: " + message
                        );
                    });
        } catch (ComponentJsonException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ComponentJsonException(ComponentJsonException.Kind.CODEC, "Failed to encode Text.", e);
        }
    }

    public static Text decode(JsonElement json) {
        if (json == null) {
            throw new ComponentJsonException(ComponentJsonException.Kind.CODEC, "Cannot decode null Text JSON.");
        }
        try {
            return TextCodecs.CODEC
                    .parse(JsonOps.INSTANCE, json.deepCopy())
                    .getOrThrow(false, message -> {
                        throw new ComponentJsonException(
                                ComponentJsonException.Kind.CODEC,
                                "Failed to decode Text: " + message
                        );
                    });
        } catch (ComponentJsonException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ComponentJsonException(ComponentJsonException.Kind.CODEC, "Failed to decode Text.", e);
        }
    }
}
