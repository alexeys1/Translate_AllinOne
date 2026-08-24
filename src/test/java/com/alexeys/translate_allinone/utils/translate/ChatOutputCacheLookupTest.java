package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.cache.LookupResult;
import com.alexeys.translate_allinone.utils.cache.TranslationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatOutputCacheLookupTest {
    @Test
    void prefersSkyblockNpcTranslation() {
        LookupResult skyblock = new LookupResult(TranslationStatus.TRANSLATED, "skyblock", null);
        LookupResult chat = new LookupResult(TranslationStatus.TRANSLATED, "chat", null);

        assertEquals("skyblock", ChatOutputTranslateManager.resolveCachedTranslation(skyblock, chat));
    }

    @Test
    void fallsBackToChatTranslation() {
        LookupResult skyblock = new LookupResult(TranslationStatus.IN_PROGRESS, "", null);
        LookupResult chat = new LookupResult(TranslationStatus.TRANSLATED, "chat", null);

        assertEquals("chat", ChatOutputTranslateManager.resolveCachedTranslation(skyblock, chat));
    }

    @Test
    void returnsNullWhenNeitherCacheIsTranslated() {
        LookupResult pending = new LookupResult(TranslationStatus.PENDING, "", null);
        LookupResult missing = new LookupResult(TranslationStatus.NOT_CACHED, "", null);

        assertNull(ChatOutputTranslateManager.resolveCachedTranslation(pending, missing));
    }
}
