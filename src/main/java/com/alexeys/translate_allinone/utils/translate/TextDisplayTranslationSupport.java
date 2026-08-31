package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
public final class TextDisplayTranslationSupport {
    private static final long STABILITY_WINDOW_MILLIS = 250L;
    private static final int MAX_TEXT_DISPLAY_CHARACTERS = 4_096;
    private static final Map<Integer, StableText> STABLE_TEXT = new ConcurrentHashMap<>();
    private static final Map<TextLayoutKey, Display.TextDisplay.CachedInfo> CACHED_INFO = new ConcurrentHashMap<>();

    private TextDisplayTranslationSupport() {
    }

    public static TextDisplayTranslationSnapshot resolve(
            Display.TextDisplay entity,
            Display.TextDisplay.TextRenderState originalState
    ) {
        if (originalState == null) {
            return new TextDisplayTranslationSnapshot(null, Component.empty());
        }
        Component original = originalState.text();
        OtherTranslationsConfig config = ComponentRenderTranslationSupport.config();
        if (ComponentRenderTranslationSupport.isTranslationBlockedByScreen()
                || !EntityTextTranslationSupport.isFeatureEnabled(config)
                || !config.translate_text_display_entities
                || !EntityTextTranslationSupport.isWithinRadius(entity, config)
                || !ComponentRenderTranslationSupport.isEligible(original, MAX_TEXT_DISPLAY_CHARACTERS)
                || !isStable(entity, original)) {
            return new TextDisplayTranslationSnapshot(originalState, original);
        }
        String type = entity == null || entity.getType() == null ? "unknown" : entity.getType().getDescriptionId();
        String context = "entity:text_display; type=" + type;
        if (!ComponentRenderTranslationSupport.shouldRenderTranslated(config)) {
            ComponentRenderTranslationSupport.forceRefreshAndQueue(
                    original,
                    ComponentTranslationRoute.TEXT_DISPLAY,
                    context,
                    "text-display-v1",
                    config
            );
            return new TextDisplayTranslationSnapshot(originalState, original);
        }
        ComponentRenderTranslationSupport.TranslationResult result = ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.TEXT_DISPLAY,
                context,
                "text-display-v1",
                config,
                ComponentRenderTranslationSupport.isRefreshPressed(config)
        );
        Component translated = ComponentRenderTranslationSupport.displayWithPendingAnimation(
                result,
                "entity:text_display:" + entity.getId()
        );
        return new TextDisplayTranslationSnapshot(originalState, translated);
    }

    public static void resetSession() {
        STABLE_TEXT.clear();
        CACHED_INFO.clear();
    }

    public static Display.TextDisplay.CachedInfo resolveCachedInfo(
            Display.TextDisplay entity,
            Display.TextDisplay.LineSplitter splitter,
            TextDisplayTranslationSnapshot snapshot
    ) {
        if (snapshot == null || !snapshot.isTranslated() || entity == null) {
            return entity == null ? null : entity.cacheDisplay(splitter);
        }
        TextLayoutKey key = new TextLayoutKey(snapshot.displayedText(), snapshot.originalState().lineWidth());
        Display.TextDisplay.CachedInfo cached = CACHED_INFO.get(key);
        if (cached != null) {
            return cached;
        }
        Display.TextDisplay.CachedInfo computed = splitter.split(
                snapshot.displayedText(),
                snapshot.originalState().lineWidth()
        );
        CACHED_INFO.put(key, computed);
        return computed;
    }

    private static boolean isStable(Display.TextDisplay entity, Component text) {
        if (entity == null || text == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        int id = entity.getId();
        StableText observed = STABLE_TEXT.compute(id, (ignored, previous) -> {
            if (previous == null || !previous.text().equals(text)) {
                return new StableText(text.copy(), now);
            }
            return previous;
        });
        return observed != null && now - observed.observedAtMillis() >= STABILITY_WINDOW_MILLIS;
    }

    private record StableText(Component text, long observedAtMillis) {
    }

    private record TextLayoutKey(Component text, int lineWidth) {
    }

}
