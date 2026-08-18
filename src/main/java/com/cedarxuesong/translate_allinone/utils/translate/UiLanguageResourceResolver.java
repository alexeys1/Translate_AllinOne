package com.cedarxuesong.translate_allinone.utils.translate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class UiLanguageResourceResolver {
    private static final Map<ResourceKey, CompletableFuture<Map<String, String>>> RESOURCES = new ConcurrentHashMap<>();

    private UiLanguageResourceResolver() {
    }

    public static Lookup lookup(String modId, String targetLanguage, String translationKey) {
        if (modId == null || modId.isBlank() || targetLanguage == null || targetLanguage.isBlank()
                || translationKey == null || translationKey.isBlank()) {
            return Lookup.miss();
        }

        boolean pending = false;
        for (String locale : locales(targetLanguage)) {
            ResourceKey resourceKey = new ResourceKey(modId, locale);
            CompletableFuture<Map<String, String>> resource = RESOURCES.computeIfAbsent(
                    resourceKey,
                    UiLanguageResourceResolver::loadAsync
            );
            if (!resource.isDone()) {
                pending = true;
                continue;
            }
            try {
                String value = resource.join().get(translationKey);
                if (value != null && !value.isBlank()) {
                    return Lookup.hit(value);
                }
            } catch (RuntimeException ignored) {
                continue;
            }
        }
        return pending ? Lookup.pending() : Lookup.miss();
    }

    public static void clear() {
        RESOURCES.clear();
    }

    private static CompletableFuture<Map<String, String>> loadAsync(ResourceKey key) {
        return CompletableFuture.supplyAsync(() -> load(key));
    }

    private static Map<String, String> load(ResourceKey key) {
        try {
            Object container = FabricLoader.getInstance().getModContainer(key.modId()).orElse(null);
            if (container == null) {
                return Map.of();
            }
            List<Path> roots = rootPaths(container);
            Map<String, String> values = new java.util.LinkedHashMap<>();
            for (Path root : roots) {
                Path path = root.resolve("assets")
                        .resolve(key.modId())
                        .resolve("lang")
                        .resolve(key.locale() + ".json");
                read(path, values);
            }
            return Map.copyOf(values);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static void read(Path path, Map<String, String> values) {
        try {
            if (!Files.isRegularFile(path)) {
                return;
            }
            JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject object = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                    values.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static List<Path> rootPaths(Object container) {
        List<Path> roots = new ArrayList<>();
        try {
            Method method = container.getClass().getMethod("getRootPaths");
            Object result = method.invoke(container);
            if (result instanceof Iterable<?> iterable) {
                for (Object value : iterable) {
                    if (value instanceof Path path) {
                        roots.add(path);
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return roots;
    }

    private static List<String> locales(String targetLanguage) {
        String normalized = targetLanguage.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("chinese") || normalized.equals("zh")) {
            return List.of("zh_cn", "zh");
        }
        if (normalized.equals("english") || normalized.equals("en")) {
            return List.of("en_us", "en");
        }
        return List.of(normalized);
    }

    public record Lookup(State state, String value) {
        private static Lookup hit(String value) {
            return new Lookup(State.HIT, value);
        }

        private static Lookup pending() {
            return new Lookup(State.PENDING, "");
        }

        private static Lookup miss() {
            return new Lookup(State.MISS, "");
        }
    }

    public enum State {
        HIT,
        PENDING,
        MISS
    }

    private record ResourceKey(String modId, String locale) {
    }
}

