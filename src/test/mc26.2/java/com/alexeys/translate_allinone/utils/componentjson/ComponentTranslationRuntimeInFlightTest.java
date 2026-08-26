package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTranslationRuntimeInFlightTest {
    @Test
    void rootResolutionCarriesInFlightState() throws Exception {
        ComponentTranslationRuntime.Resolution<String> idle = new ComponentTranslationRuntime.Resolution<>(
                ComponentTranslationRuntime.State.PENDING,
                null,
                "idle-key",
                ""
        );
        assertFalse(idle.inFlight());
        assertFalse(ComponentTranslationRuntime.isWorkInFlight("idle-key"));

        ComponentTranslationRuntimeState<?> state = privateState();
        ComponentTranslationPreparedRequest request = state.preparedRequest(document(), "zh_cn");
        long epoch = state.epoch();

        assertTrue(state.registerQueued(request, "test", epoch));
        ComponentTranslationRuntime.Resolution<String> queued = new ComponentTranslationRuntime.Resolution<>(
                ComponentTranslationRuntime.State.PENDING,
                null,
                request.identity().key(),
                ""
        );
        assertFalse(queued.inFlight());

        assertTrue(state.markWorkInFlight(request.identity().key(), epoch));
        ComponentTranslationRuntime.Resolution<String> inFlight = new ComponentTranslationRuntime.Resolution<>(
                ComponentTranslationRuntime.State.PENDING,
                null,
                request.identity().key(),
                ""
        );
        assertTrue(inFlight.inFlight());
        assertTrue(ComponentTranslationRuntime.isWorkInFlight(request.identity().key()));
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
}
