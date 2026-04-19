package com.cedarxuesong.translate_allinone.registration;

import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.WynnCraftConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class ConfigMigrationSupport {
    private ConfigMigrationSupport() {
    }

    static boolean migrateLegacyItemWynnCompatibilityConfig(JsonElement rawConfig, ModConfig loadedConfig) {
        if (loadedConfig == null) {
            return false;
        }

        JsonObject itemTranslateObject = getItemTranslateObject(rawConfig);
        if (itemTranslateObject == null || !itemTranslateObject.has("wynn_item_compatibility")) {
            return false;
        }

        if (loadedConfig.wynnCraft == null) {
            loadedConfig.wynnCraft = new WynnCraftConfig();
        }

        if (!hasExplicitWynnCraftCompatibility(rawConfig)) {
            JsonElement legacyWynnCompatibility = itemTranslateObject.get("wynn_item_compatibility");
            if (legacyWynnCompatibility != null && legacyWynnCompatibility.isJsonPrimitive()) {
                try {
                    loadedConfig.wynnCraft.wynn_item_compatibility = legacyWynnCompatibility.getAsBoolean();
                } catch (RuntimeException ignored) {
                }
            }
        }

        return true;
    }

    static void syncDerivedConfigState(ModConfig config) {
        if (config == null || config.itemTranslate == null || config.wynnCraft == null) {
            return;
        }
        config.itemTranslate.wynn_item_compatibility = config.wynnCraft.wynn_item_compatibility;
    }

    private static boolean hasExplicitWynnCraftCompatibility(JsonElement rawConfig) {
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
