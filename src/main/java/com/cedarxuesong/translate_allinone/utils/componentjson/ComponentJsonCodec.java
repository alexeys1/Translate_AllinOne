package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.versionapi.MinecraftComponentCodec;
import com.google.gson.JsonElement;
import net.minecraft.text.Text;

public final class ComponentJsonCodec {
    private ComponentJsonCodec() {
    }

    public static JsonElement encode(Text text) {
        return MinecraftComponentCodec.INSTANCE.encode(text);
    }

    public static Text decode(JsonElement json) {
        return MinecraftComponentCodec.INSTANCE.decode(json);
    }
}
