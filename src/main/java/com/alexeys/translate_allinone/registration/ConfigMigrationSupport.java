package com.alexeys.translate_allinone.registration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class ConfigMigrationSupport {
    private ConfigMigrationSupport() {
    }

    static boolean hasDeprecatedWynnItemCompatibilityConfig(JsonElement rawConfig) {
        JsonObject itemTranslateObject = getItemTranslateObject(rawConfig);
        if (itemTranslateObject != null && itemTranslateObject.has("wynn_item_compatibility")) {
            return true;
        }
        JsonObject wynnCraftObject = getWynnCraftObject(rawConfig);
        return wynnCraftObject != null && wynnCraftObject.has("wynn_item_compatibility");
    }

    private static JsonObject getWynnCraftObject(JsonElement rawConfig) {
        if (rawConfig == null || !rawConfig.isJsonObject()) {
            return null;
        }
        JsonElement wynnCraftElement = rawConfig.getAsJsonObject().get("wynnCraft");
        if (wynnCraftElement == null || !wynnCraftElement.isJsonObject()) {
            return null;
        }
        return wynnCraftElement.getAsJsonObject();
    }

    static JsonObject getItemTranslateObject(JsonElement rawConfig) {
        if (rawConfig == null || !rawConfig.isJsonObject()) {
            return null;
        }
        JsonObject root = rawConfig.getAsJsonObject();
        JsonElement itemTranslateElement = root.get("itemTranslate");
        if (itemTranslateElement == null || !itemTranslateElement.isJsonObject()) {
            itemTranslateElement = root.get("itemTranslateConfig");
        }
        if (itemTranslateElement == null || !itemTranslateElement.isJsonObject()) {
            itemTranslateElement = root.get("ItemTranslateConfig");
        }
        if (itemTranslateElement == null || !itemTranslateElement.isJsonObject()) {
            return null;
        }
        return itemTranslateElement.getAsJsonObject();
    }
}
