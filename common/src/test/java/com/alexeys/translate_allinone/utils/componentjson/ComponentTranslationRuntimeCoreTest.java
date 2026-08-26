package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.utils.cache.component.ComponentTranslationStore;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.translate.TranslationFeatureGate;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTranslationRuntimeCoreTest {
    private final AtomicInteger errors = new AtomicInteger();

    @BeforeEach
    void configureRuntime() {
        TranslationFeatureGate.update(true);
        ComponentTranslationRuntimeCore.configure(new TestAccess());
        ComponentTranslationRuntimeCore.resetSession();
    }

    @Test
    void resolvesIncompleteAndEmptyDocumentsWithoutVersionServices() {
        ComponentTranslationRuntimeCore.Resolution<String> incomplete = ComponentTranslationRuntimeCore.resolve(
                null,
                "zh_cn",
                "",
                null,
                response -> "translated",
                "test"
        );
        ComponentTranslationRuntimeCore.Resolution<String> empty = ComponentTranslationRuntimeCore.resolve(
                emptyDocument(),
                "zh_cn",
                "",
                null,
                response -> "translated",
                "test"
        );

        assertEquals(ComponentTranslationRuntimeCore.State.INELIGIBLE, incomplete.state());
        assertEquals(ComponentTranslationRuntimeCore.State.NO_TEXT, empty.state());
        assertEquals(1, errors.get());
    }

    @Test
    void validatesCandidatesBeforeCommitting() {
        AtomicInteger commits = new AtomicInteger();
        ComponentTranslationRuntimeCore.CandidatePromotion<String> accepted =
                ComponentTranslationRuntimeCore.validateAndCommitCandidate(
                        response(),
                        value -> value.translations().get("u0"),
                        () -> commits.incrementAndGet() == 1
                );
        ComponentTranslationRuntimeCore.CandidatePromotion<String> rejected =
                ComponentTranslationRuntimeCore.validateAndCommitCandidate(
                        response(),
                        value -> value.translations().get("u0"),
                        () -> false
                );

        assertTrue(accepted.accepted());
        assertEquals("你好", accepted.value());
        assertFalse(rejected.accepted());
        assertEquals(1, commits.get());
    }

    @Test
    void keepsEveryFailureUntilAnExplicitReset() {
        assertEquals(
                Long.MAX_VALUE,
                ComponentTranslationRuntimeCore.failureExpiresAtMillis(
                        ComponentTranslationRoute.SCOREBOARD,
                        ComponentTranslationRuntimeCore.FailureDisposition.TERMINAL_CONTENT_FAILURE,
                        100L
                )
        );
        assertEquals(
                Long.MAX_VALUE,
                ComponentTranslationRuntimeCore.failureExpiresAtMillis(
                        ComponentTranslationRoute.SIGN_FACE,
                        ComponentTranslationRuntimeCore.FailureDisposition.INFRASTRUCTURE_FAILURE,
                        100L
                )
        );
    }

    @Test
    void resolutionCarriesInFlightOnlyForPendingInFlightWork() throws Exception {
        ComponentTranslationRuntimeCore.Resolution<String> idle = new ComponentTranslationRuntimeCore.Resolution<>(
                ComponentTranslationRuntimeCore.State.PENDING,
                null,
                "idle-key",
                ""
        );
        assertFalse(idle.inFlight());
        assertFalse(ComponentTranslationRuntimeCore.isWorkInFlight("idle-key"));

        ComponentTranslationRuntimeState<?> state = privateState();
        ComponentTranslationPreparedRequest request = state.preparedRequest(document(), "zh_cn");
        long epoch = state.epoch();

        assertTrue(state.registerQueued(request, "test", epoch));
        ComponentTranslationRuntimeCore.Resolution<String> queued = new ComponentTranslationRuntimeCore.Resolution<>(
                ComponentTranslationRuntimeCore.State.PENDING,
                null,
                request.identity().key(),
                ""
        );
        assertFalse(queued.inFlight());

        assertTrue(state.markWorkInFlight(request.identity().key(), epoch));
        ComponentTranslationRuntimeCore.Resolution<String> inFlight = new ComponentTranslationRuntimeCore.Resolution<>(
                ComponentTranslationRuntimeCore.State.PENDING,
                null,
                request.identity().key(),
                ""
        );
        assertTrue(inFlight.inFlight());
        assertTrue(ComponentTranslationRuntimeCore.isWorkInFlight(request.identity().key()));

        ComponentTranslationRuntimeCore.Resolution<String> cacheHit = new ComponentTranslationRuntimeCore.Resolution<>(
                ComponentTranslationRuntimeCore.State.CACHE_HIT,
                "value",
                request.identity().key(),
                ""
        );
        assertFalse(cacheHit.inFlight());
    }

    private static ComponentTranslationRuntimeState<?> privateState() throws Exception {
        Field field = ComponentTranslationRuntimeCore.class.getDeclaredField("STATE");
        field.setAccessible(true);
        return (ComponentTranslationRuntimeState<?>) field.get(null);
    }

    private static ComponentTranslationDocument document() {
        return new ComponentJsonDocumentBuilder().build(
                JsonParser.parseString("{\"text\":\"hello\"}"),
                ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.CHAT_OUTPUT)
        );
    }

    private static ComponentTranslationDocument emptyDocument() {
        return new ComponentTranslationDocument(
                ComponentTranslationDocument.PROTOCOL,
                ComponentTranslationPolicy.CURRENT_VERSION,
                ComponentTranslationRoute.CHAT_OUTPUT,
                JsonParser.parseString("{\"text\":\"\"}"),
                List.of(),
                Map.of()
        );
    }

    private static ComponentTranslationResponse response() {
        return new ComponentTranslationResponse(
                ComponentTranslationDocument.PROTOCOL,
                Map.of("u0", "你好")
        );
    }

    private final class TestAccess implements ComponentTranslationRuntimeCore.Access {
        @Override
        public ModConfig config() {
            return new ModConfig();
        }

        @Override
        public boolean readyForTranslation() {
            return true;
        }

        @Override
        public ComponentTranslationStore store(ComponentTranslationRoute route) {
            throw new AssertionError("Store access was not expected");
        }

        @Override
        public CompletableFuture<ComponentTranslationResponse> translateResponse(
                ComponentTranslationDocument document,
                String targetLanguage,
                ApiProviderProfile provider,
                String requestContext
        ) {
            throw new AssertionError("Provider access was not expected");
        }

        @Override
        public void onNoRoutedModel(ComponentTranslationRuntimeCore.ProviderSurface surface) {
        }

        @Override
        public void flow(ComponentTranslationRoute route, String message, Object... arguments) {
        }

        @Override
        public void error(ComponentTranslationRoute route, String message, Object... arguments) {
            errors.incrementAndGet();
        }

        @Override
        public void textContent(ComponentTranslationDocument document, String cacheKey) {
        }

        @Override
        public void entityIdentityMiss(
                ComponentTranslationDocument document,
                String targetLanguage,
                ComponentTranslationCacheIdentity identity,
                String lookupStatus
        ) {
        }

        @Override
        public void entityTemplateReuse(String fullKey, String templateKey) {
        }
    }
}
