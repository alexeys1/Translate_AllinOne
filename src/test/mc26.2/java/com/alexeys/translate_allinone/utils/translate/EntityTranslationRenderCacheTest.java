package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EntityTranslationRenderCacheTest {
    @Test
    void putReturnsSameResultBeforeExpiry() {
        EntityTranslationRenderCache cache = new EntityTranslationRenderCache(8, 60_000);
        EntityTranslationRenderCache.Key key = key("hello");
        ComponentRenderTranslationSupport.TranslationResult value = result("hello", "你好");

        cache.put(key, value);

        assertSame(value, cache.get(key));
        assertEquals(1, cache.size());
    }

    @Test
    void clearRemovesAllEntries() {
        EntityTranslationRenderCache cache = new EntityTranslationRenderCache(8, 60_000);
        cache.put(key("hello"), result("hello", "你好"));
        cache.put(key("world"), result("world", "世界"));

        cache.clear();

        assertEquals(0, cache.size());
        assertNull(cache.get(key("hello")));
        assertNull(cache.get(key("world")));
    }

    @Test
    void expiredEntryIsRemoved() throws Exception {
        EntityTranslationRenderCache cache = new EntityTranslationRenderCache(8, 1);
        EntityTranslationRenderCache.Key key = key("hello");
        cache.put(key, result("hello", "你好"));

        Thread.sleep(5);

        assertNull(cache.get(key));
        assertEquals(0, cache.size());
    }

    @Test
    void lruEvictsOldestEntry() {
        EntityTranslationRenderCache cache = new EntityTranslationRenderCache(2, 60_000);
        EntityTranslationRenderCache.Key key1 = key("a");
        EntityTranslationRenderCache.Key key2 = key("b");
        EntityTranslationRenderCache.Key key3 = key("c");
        cache.put(key1, result("a", "甲"));
        cache.put(key2, result("b", "乙"));

        assertNotNull(cache.get(key1));
        cache.put(key3, result("c", "丙"));

        assertNotNull(cache.get(key1));
        assertNull(cache.get(key2));
        assertNotNull(cache.get(key3));
    }

    private static EntityTranslationRenderCache.Key key(String text) {
        return new EntityTranslationRenderCache.Key(
                Component.literal(text),
                ComponentTranslationRoute.ENTITY_NAME,
                "entity:name_tag; type=test",
                "Chinese",
                "entity-name-v1"
        );
    }

    private static ComponentRenderTranslationSupport.TranslationResult result(String original, String translated) {
        return new ComponentRenderTranslationSupport.TranslationResult(
                Component.literal(original),
                Component.literal(translated),
                null,
                ComponentTranslationRuntime.State.CACHE_HIT,
                false
        );
    }
}