package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;

public final class TextDisplayTranslationSupport {
    private static final long STABILITY_WINDOW_MILLIS = 250L;
    private static final int MAX_TEXT_DISPLAY_CHARACTERS = 4_096;
    private static final Map<Integer, StableText> stableText = new ConcurrentHashMap<>();
    private static final Map<TextLayoutKey, DisplayEntity.TextDisplayEntity.TextLines> CACHED_TEXT_LINES = new ConcurrentHashMap<>();

    private TextDisplayTranslationSupport() {
    }

    public static TextDisplayTranslationSnapshot resolve(
            DisplayEntity.TextDisplayEntity entity,
            DisplayEntity.TextDisplayEntity.Data originalState
    ) {
        if (originalState == null) {
            return new TextDisplayTranslationSnapshot(null, Text.empty());
        }
        Text original = originalState.text();
        OtherTranslationsConfig config = ComponentRenderTranslationSupport.config();
        if (ComponentRenderTranslationSupport.isTranslationBlockedByScreen()
                || !EntityTextTranslationSupport.isFeatureEnabled(config)
                || !config.translate_text_display_entities
                || !EntityTextTranslationSupport.isWithinRadius(entity, config)
                || !ComponentRenderTranslationSupport.isEligible(original, MAX_TEXT_DISPLAY_CHARACTERS)
                || !isStable(entity, original)) {
            return new TextDisplayTranslationSnapshot(originalState, original);
        }
        String type = entity == null || entity.getType() == null ? "unknown" : entity.getType().getTranslationKey();
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
        Text translated = ComponentRenderTranslationSupport.displayWithPendingAnimation(
                result,
                "entity:text_display:" + entity.getId()
        );
        return new TextDisplayTranslationSnapshot(originalState, translated);
    }

    public static void resetSession() {
        stableText.clear();
        CACHED_TEXT_LINES.clear();
    }

    public static DisplayEntity.TextDisplayEntity.TextLines resolveCachedTextLines(
            DisplayEntity.TextDisplayEntity entity,
            DisplayEntity.TextDisplayEntity.LineSplitter splitter,
            TextDisplayTranslationSnapshot snapshot
    ) {
        if (snapshot == null || !snapshot.isTranslated() || entity == null) {
            return entity == null ? null : entity.splitLines(splitter);
        }
        TextLayoutKey key = new TextLayoutKey(snapshot.displayedText(), snapshot.originalState().lineWidth());
        DisplayEntity.TextDisplayEntity.TextLines cached = CACHED_TEXT_LINES.get(key);
        if (cached != null) {
            return cached;
        }
        DisplayEntity.TextDisplayEntity.TextLines computed = splitter.split(
                snapshot.displayedText(),
                snapshot.originalState().lineWidth()
        );
        CACHED_TEXT_LINES.put(key, computed);
        return computed;
    }

    private static boolean isStable(DisplayEntity.TextDisplayEntity entity, Text text) {
        if (entity == null || text == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        int id = entity.getId();
        StableText observed = stableText.compute(id, (ignored, previous) -> {
            if (previous == null || !previous.text().equals(text)) {
                return new StableText(text.copy(), now);
            }
            return previous;
        });
        return observed != null && now - observed.observedAtMillis() >= STABILITY_WINDOW_MILLIS;
    }

    private record StableText(Text text, long observedAtMillis) {
    }

    private record TextLayoutKey(Text text, int lineWidth) {
    }
}
