package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ComponentTranslationCacheKey {
    public static final int CANONICAL_FORMAT_VERSION = 1;
    private static final String V2_BINDING_DOMAIN = "translate_allinone:component-cache-v2:binding\n";
    private static final String ENTITY_TEMPLATE_KEY_DOMAIN = "translate_allinone:component-entity-template-v1:key\n";
    private static final String ENTITY_TEMPLATE_BINDING_DOMAIN = "translate_allinone:component-entity-template-v1:binding\n";

    private ComponentTranslationCacheKey() {
    }

    public static String create(ComponentTranslationDocument document, String targetLanguage) {
        return metadata(document, targetLanguage).key();
    }

    public static Metadata metadata(ComponentTranslationDocument document, String targetLanguage) {
        if (document == null || targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("Document and target language are required for a component cache key.");
        }

        JsonElement structure = ComponentTranslationApplier.maskedCopy(document.sourceJson(), document.units());
        JsonArray sources = sourceUnits(document);
        JsonArray tokenMetadata = protectedTokens(document);

        JsonObject material = new JsonObject();
        material.addProperty("canonical_format", CANONICAL_FORMAT_VERSION);
        material.addProperty("protocol", document.protocol());
        material.addProperty("policy_version", document.policyVersion());
        material.addProperty("route", document.route().wireName());
        material.addProperty("target_language", targetLanguage.trim());
        JsonObject semanticSettings = new JsonObject();
        for (Map.Entry<String, String> setting : document.semanticSettings().entrySet()) {
            semanticSettings.addProperty(setting.getKey(), setting.getValue());
        }
        material.add("route_semantic_settings", semanticSettings);
        material.add("structure", structure);
        material.add("source_units", sources);
        material.add("protected_tokens", tokenMetadata);

        String canonicalMaterial = canonicalize(material);
        return new Metadata(
                "sha256:" + sha256(canonicalMaterial),
                "sha256:" + sha256(V2_BINDING_DOMAIN + canonicalMaterial),
                "sha256:" + sha256(canonicalize(structure)),
                "sha256:" + sha256(canonicalize(sources)),
                "sha256:" + sha256(canonicalize(tokenMetadata))
        );
    }

    public static TemplateMetadata entityTemplateMetadata(
            ComponentTranslationDocument document,
            String targetLanguage
    ) {
        if (document == null || targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("Document and target language are required for an entity template key.");
        }
        if (document.route() != ComponentTranslationRoute.ENTITY_NAME) {
            throw new IllegalArgumentException("Entity template keys are only valid for entity-name documents.");
        }

        JsonObject material = new JsonObject();
        material.addProperty("template_format", CANONICAL_FORMAT_VERSION);
        material.addProperty("protocol", document.protocol());
        material.addProperty("policy_version", document.policyVersion());
        material.addProperty("route", document.route().wireName());
        material.addProperty("target_language", targetLanguage.trim());
        JsonObject semanticSettings = new JsonObject();
        for (Map.Entry<String, String> setting : document.semanticSettings().entrySet()) {
            semanticSettings.addProperty(setting.getKey(), setting.getValue());
        }
        material.add("route_semantic_settings", semanticSettings);
        material.add("source_units", sourceUnits(document));
        material.add("protected_tokens", protectedTokens(document));

        String canonicalMaterial = canonicalize(material);
        return new TemplateMetadata(
                "sha256:" + sha256(ENTITY_TEMPLATE_KEY_DOMAIN + canonicalMaterial),
                "sha256:" + sha256(ENTITY_TEMPLATE_BINDING_DOMAIN + canonicalMaterial)
        );
    }

    private static JsonArray sourceUnits(ComponentTranslationDocument document) {
        JsonArray sources = new JsonArray();
        for (ComponentTextUnit unit : document.units()) {
            JsonObject source = new JsonObject();
            source.addProperty("id", unit.id());
            source.addProperty("pointer", unit.jsonPointer());
            source.addProperty("text", unit.sourceText());
            sources.add(source);
        }
        return sources;
    }

    private static JsonArray protectedTokens(ComponentTranslationDocument document) {
        JsonArray tokenMetadata = new JsonArray();
        for (ComponentTextUnit unit : document.units()) {
            JsonObject unitTokens = new JsonObject();
            unitTokens.addProperty("id", unit.id());
            JsonObject tokens = new JsonObject();
            for (Map.Entry<String, Integer> token : unit.protectedTokens().entrySet()) {
                tokens.addProperty(token.getKey(), token.getValue());
            }
            unitTokens.add("tokens", tokens);
            tokenMetadata.add(unitTokens);
        }
        return tokenMetadata;
    }

    static String canonicalize(JsonElement element) {
        if (element == null || element instanceof JsonNull || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonPrimitive()) {
            return element.toString();
        }
        if (element.isJsonArray()) {
            StringBuilder result = new StringBuilder("[");
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                if (index > 0) {
                    result.append(',');
                }
                result.append(canonicalize(array.get(index)));
            }
            return result.append(']').toString();
        }

        JsonObject object = element.getAsJsonObject();
        List<String> keys = new ArrayList<>(object.keySet());
        Collections.sort(keys);
        StringBuilder result = new StringBuilder("{");
        for (int index = 0; index < keys.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            String key = keys.get(index);
            result.append(new com.google.gson.JsonPrimitive(key));
            result.append(':');
            result.append(canonicalize(object.get(key)));
        }
        return result.append('}').toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    public record Metadata(
            String key,
            String binding,
            String structureFingerprint,
            String sourceFingerprint,
            String tokenFingerprint
    ) {
    }

    public record TemplateMetadata(String key, String binding) {
    }
}
