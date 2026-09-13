package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentRenderTranslationSupportFastPathTest {
    @AfterEach
    void tearDown() {
        ComponentRenderTranslationSupport.setTranslationPipelineForTesting(null);
        ComponentRenderTranslationSupport.resetRenderCache();
    }

    @Test
    void entityCacheHitIsReusedWithoutCallingPipelineAgain() {
        AtomicInteger calls = new AtomicInteger();
        Component original = Component.literal("hello");
        OtherTranslationsConfig config = new OtherTranslationsConfig();
        ComponentRenderTranslationSupport.setTranslationPipelineForTesting((component, route, context, policy, cfg, refresh, tokens) -> {
            calls.incrementAndGet();
            return new ComponentRenderTranslationSupport.TranslationResult(
                    component,
                    Component.literal("translated"),
                    null,
                    ComponentTranslationRuntime.State.CACHE_HIT,
                    false
            );
        });

        ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.ENTITY_NAME,
                "entity:name_tag; type=test",
                "entity-name-v1",
                config,
                false,
                Set.of()
        );
        ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.ENTITY_NAME,
                "entity:name_tag; type=test",
                "entity-name-v1",
                config,
                false,
                Set.of()
        );

        assertEquals(1, calls.get());
    }

    @Test
    void refreshModeBypassesCache() {
        AtomicInteger calls = new AtomicInteger();
        Component original = Component.literal("hello");
        OtherTranslationsConfig config = new OtherTranslationsConfig();
        ComponentRenderTranslationSupport.setTranslationPipelineForTesting((component, route, context, policy, cfg, refresh, tokens) -> {
            calls.incrementAndGet();
            return new ComponentRenderTranslationSupport.TranslationResult(
                    component,
                    Component.literal("translated"),
                    null,
                    ComponentTranslationRuntime.State.CACHE_HIT,
                    false
            );
        });

        ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.ENTITY_NAME,
                "entity:name_tag; type=test",
                "entity-name-v1",
                config,
                true,
                Set.of()
        );
        ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.ENTITY_NAME,
                "entity:name_tag; type=test",
                "entity-name-v1",
                config,
                true,
                Set.of()
        );

        assertEquals(2, calls.get());
    }

    @Test
    void refreshRemovesStaleCacheEntry() {
        AtomicInteger calls = new AtomicInteger();
        Component original = Component.literal("hello");
        OtherTranslationsConfig config = new OtherTranslationsConfig();
        ComponentRenderTranslationSupport.setTranslationPipelineForTesting((component, route, context, policy, cfg, refresh, tokens) -> {
            calls.incrementAndGet();
            return new ComponentRenderTranslationSupport.TranslationResult(
                    component,
                    Component.literal("translated"),
                    null,
                    ComponentTranslationRuntime.State.CACHE_HIT,
                    false
            );
        });

        ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.ENTITY_NAME,
                "entity:name_tag; type=test",
                "entity-name-v1",
                config,
                false,
                Set.of()
        );
        ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.ENTITY_NAME,
                "entity:name_tag; type=test",
                "entity-name-v1",
                config,
                true,
                Set.of()
        );
        ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.ENTITY_NAME,
                "entity:name_tag; type=test",
                "entity-name-v1",
                config,
                false,
                Set.of()
        );

        assertEquals(3, calls.get());
    }

    @Test
    void nonEntityRouteDoesNotUseCache() {
        AtomicInteger calls = new AtomicInteger();
        Component original = Component.literal("hello");
        OtherTranslationsConfig config = new OtherTranslationsConfig();
        ComponentRenderTranslationSupport.setTranslationPipelineForTesting((component, route, context, policy, cfg, refresh, tokens) -> {
            calls.incrementAndGet();
            return new ComponentRenderTranslationSupport.TranslationResult(
                    component,
                    Component.literal("translated"),
                    null,
                    ComponentTranslationRuntime.State.CACHE_HIT,
                    false
            );
        });

        ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.BOOK_PAGE,
                "book:page",
                "book-page-v1",
                config,
                false,
                Set.of()
        );
        ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.BOOK_PAGE,
                "book:page",
                "book-page-v1",
                config,
                false,
                Set.of()
        );

        assertEquals(2, calls.get());
    }

    @Test
    void privateTokensDisableEntityFastPath() {
        AtomicInteger calls = new AtomicInteger();
        Component original = Component.literal("hello");
        OtherTranslationsConfig config = new OtherTranslationsConfig();
        ComponentRenderTranslationSupport.setTranslationPipelineForTesting((component, route, context, policy, cfg, refresh, tokens) -> {
            calls.incrementAndGet();
            return new ComponentRenderTranslationSupport.TranslationResult(
                    component,
                    Component.literal("translated"),
                    null,
                    ComponentTranslationRuntime.State.CACHE_HIT,
                    false
            );
        });

        ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.ENTITY_NAME,
                "entity:name_tag; type=test",
                "entity-name-v1",
                config,
                false,
                Set.of("secret")
        );
        ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.ENTITY_NAME,
                "entity:name_tag; type=test",
                "entity-name-v1",
                config,
                false,
                Set.of("secret")
        );

        assertEquals(2, calls.get());
    }
}