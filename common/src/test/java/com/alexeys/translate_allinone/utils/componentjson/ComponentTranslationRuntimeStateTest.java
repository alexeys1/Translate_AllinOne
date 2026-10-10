package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTranslationRuntimeStateTest {
    @Test
    void memoizesPreparedRequestsByDocumentIdentityAndLanguage() {
        ComponentTranslationRuntimeState<String> state = new ComponentTranslationRuntimeState<>();
        ComponentTranslationDocument document = document();

        ComponentTranslationPreparedRequest first = state.preparedRequest(document, " zh_cn ");
        ComponentTranslationPreparedRequest second = state.preparedRequest(document, "zh_cn");

        assertSame(first, second);
        assertEquals("zh_cn", first.targetLanguage());
    }

    @Test
    void completesCurrentWorkAndRejectsStaleWork() {
        ComponentTranslationRuntimeState<String> state = new ComponentTranslationRuntimeState<>();
        ComponentTranslationPreparedRequest request = state.preparedRequest(document(), "zh_cn");
        long firstEpoch = state.advanceSession();

        assertTrue(state.registerQueued(request, "test", firstEpoch));
        assertFalse(state.registerQueued(request, "test", firstEpoch));
        assertTrue(state.markWorkInFlight(request.identity().key(), firstEpoch));

        ComponentTranslationRuntimeState.WorkCompletion stored = state.complete(
                request,
                response(),
                firstEpoch,
                true,
                false,
                "hash",
                "test",
                () -> true
        );

        assertTrue(stored.current());
        assertTrue(stored.stored());
        assertFalse(state.hasActiveWork(request.identity().key()));

        assertTrue(state.registerQueued(request, "test", firstEpoch));
        assertTrue(state.markWorkInFlight(request.identity().key(), firstEpoch));
        state.advanceSession();

        ComponentTranslationRuntimeState.WorkCompletion stale = state.complete(
                request,
                response(),
                firstEpoch,
                true,
                false,
                "hash",
                "test",
                () -> true
        );

        assertFalse(stale.current());
        state.clear();
        assertFalse(state.hasActiveWork(request.identity().key()));
    }

    @Test
    void exposesQueuedAndInFlightWorkState() {
        ComponentTranslationRuntimeState<String> state = new ComponentTranslationRuntimeState<>();
        ComponentTranslationPreparedRequest request = state.preparedRequest(document(), "zh_cn");
        long epoch = state.advanceSession();

        assertTrue(state.registerQueued(request, "test", epoch));
        assertFalse(state.isWorkInFlight(request.identity().key()));

        assertTrue(state.markWorkInFlight(request.identity().key(), epoch));
        assertTrue(state.isWorkInFlight(request.identity().key()));

        ComponentTranslationRuntimeState.WorkCompletion stored = state.complete(
                request,
                response(),
                epoch,
                true,
                false,
                "hash",
                "test",
                () -> true
        );
        assertTrue(stored.stored());
        assertFalse(state.isWorkInFlight(request.identity().key()));

        assertTrue(state.registerQueued(request, "test", epoch));
        assertTrue(state.markWorkInFlight(request.identity().key(), epoch));
        state.advanceSession();
        assertFalse(state.isWorkInFlight(request.identity().key()));
    }

    @Test
    void expiresFailuresAndScopesFallbackClaimsToSession() {
        ComponentTranslationRuntimeState<String> state = new ComponentTranslationRuntimeState<>();
        state.putFailure("key", new ComponentTranslationRuntimeState.FailureState<>("failed", 20L, "terminal"));

        assertEquals("failed", state.activeFailure("key", 20L).message());
        assertEquals(null, state.activeFailure("key", 21L));

        state.advanceSession();
        assertTrue(state.claimFallbackGeneration("key"));
        assertFalse(state.claimFallbackGeneration("key"));
        state.advanceSession();
        assertTrue(state.claimFallbackGeneration("key"));
    }

    private static ComponentTranslationDocument document() {
        return new ComponentJsonDocumentBuilder().build(
                JsonParser.parseString("{\"text\":\"hello\"}"),
                ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.CHAT_OUTPUT)
        );
    }

    private static ComponentTranslationResponse response() {
        return new ComponentTranslationResponse(
                ComponentTranslationDocument.PROTOCOL,
                java.util.Map.of("u0", "你好")
        );
    }
}
