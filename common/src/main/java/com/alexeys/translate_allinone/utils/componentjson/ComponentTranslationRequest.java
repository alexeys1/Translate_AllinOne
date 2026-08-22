package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public record ComponentTranslationRequest(
        String protocol,
        String targetLanguage,
        List<Item> items
) {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public ComponentTranslationRequest {
        if (!ComponentTranslationDocument.PROTOCOL.equals(protocol)) {
            throw new IllegalArgumentException("Unsupported component translation protocol: " + protocol);
        }
        if (targetLanguage == null || targetLanguage.isBlank() || items == null) {
            throw new IllegalArgumentException("Component translation request is incomplete.");
        }
        targetLanguage = targetLanguage.trim();
        items = List.copyOf(items);
    }

    public static ComponentTranslationRequest fromDocument(
            ComponentTranslationDocument document,
            String targetLanguage
    ) {
        List<Item> items = document.units().stream()
                .map(unit -> new Item(unit.id(), unit.sourceText(), unit.context()))
                .toList();
        return new ComponentTranslationRequest(document.protocol(), targetLanguage, items);
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("protocol", protocol);
        root.addProperty("target_language", targetLanguage);
        JsonArray itemArray = new JsonArray();
        for (Item item : items) {
            JsonObject itemObject = new JsonObject();
            itemObject.addProperty("id", item.id());
            itemObject.addProperty("text", item.text());
            itemObject.addProperty("context", item.context());
            itemArray.add(itemObject);
        }
        root.add("items", itemArray);
        return GSON.toJson(root);
    }

    public record Item(String id, String text, String context) {
        public Item {
            if (id == null || id.isBlank() || text == null || context == null || context.isBlank()) {
                throw new IllegalArgumentException("Component translation request item is incomplete.");
            }
        }
    }
}
