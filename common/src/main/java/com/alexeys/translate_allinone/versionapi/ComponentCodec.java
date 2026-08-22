package com.alexeys.translate_allinone.versionapi;

import com.google.gson.JsonElement;

public interface ComponentCodec<C> {
    JsonElement encode(C component);

    C decode(JsonElement json);
}
