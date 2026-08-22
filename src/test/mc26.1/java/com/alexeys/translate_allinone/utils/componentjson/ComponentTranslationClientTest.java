package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.llmapi.LlmCompletion;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentTranslationClientTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void convertsCommonResponseToMinecraftComponent() {
        ComponentTranslationClient client = new ComponentTranslationClient(
                new ComponentResponseParser(),
                new ComponentTranslationValidator(),
                new ComponentTranslationApplier(),
                settings -> (messages, context, observer, schema) -> CompletableFuture.completedFuture(
                        new LlmCompletion(
                                "{\"protocol\":\"taio-component-v1\",\"translations\":{\"u0\":\"你好\"}}",
                                "stop"
                        )
                ),
                0L
        );
        ComponentTranslationDocument document = new ComponentDocumentBuilder().build(
                Component.literal("Hello"),
                ComponentTranslationRoute.CHAT_OUTPUT
        );
        ApiProviderProfile profile = new ApiProviderProfile();
        profile.model_id = "test-model";
        profile.system_prompt_suffix = "";

        Component translated = client.translate(document, "Chinese", profile, "chat-output").join();

        assertEquals("你好", translated.getString());
    }
}
