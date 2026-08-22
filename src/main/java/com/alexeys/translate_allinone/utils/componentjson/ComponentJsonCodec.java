package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.versionapi.MinecraftComponentCodec;
import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;

public final class ComponentJsonCodec {
    private ComponentJsonCodec() {
    }

    public static JsonElement encode(Component component) {
        return MinecraftComponentCodec.INSTANCE.encode(component);
    }

    public static Component decode(JsonElement json) {
        return MinecraftComponentCodec.INSTANCE.decode(json);
    }
}
