package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import com.alexeys.translate_allinone.utils.text.LegacyComponentTextCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class UiTranslationRuntime {
    private static final String POLICY_VERSION = "screen-ui-v1";
    private static final ThreadLocal<Set<FormattedCharSequence>> HANDLED_FORMATTED_SEQUENCES =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final int SCREEN_TRANSLATION_NOTIFY_LIMIT = 8192;
    private static final Set<String> NOTIFIED_SCREEN_TRANSLATIONS = Collections.newSetFromMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > SCREEN_TRANSLATION_NOTIFY_LIMIT;
                }
            }
    );
    private static volatile int SCREEN_TRANSLATION_VERSION = 0;
    private static int FRAME_ID = 0;

    private UiTranslationRuntime() {
    }

    public static int currentFrameId() {
        return FRAME_ID;
    }

    public static int screenTranslationVersion() {
        return SCREEN_TRANSLATION_VERSION;
    }

    static void notifyScreenTranslationAvailable(String source, UiTextRole role, String targetLanguage) {
        if (source == null || role == null || role != UiTextRole.OPTION) {
            return;
        }
        String key = role.wireName() + '\u0000'
                + (targetLanguage == null ? "" : targetLanguage) + '\u0000'
                + source;
        synchronized (NOTIFIED_SCREEN_TRANSLATIONS) {
            if (NOTIFIED_SCREEN_TRANSLATIONS.add(key)) {
                SCREEN_TRANSLATION_VERSION++;
            }
        }
    }

    public static UiTranslationResult resolve(Component source, UiTextRole requestedRole) {
        UiScreenAdapter adapter = UiTranslationScope.adapter();
        UiTextRole role = resolveRole(requestedRole);
        OtherTranslationsConfig config = currentConfig();
        String targetLanguage = config == null || config.target_language == null
                ? OtherTranslationsConfig.DEFAULT_TARGET_LANGUAGE
                : config.target_language;
        String modId = adapter == null ? "" : adapter.modId();
        String screenId = adapter == null ? "" : adapter.screenId();
        Component safeSource = source == null ? Component.empty() : source;

        if (!UiTranslationScope.isActive()
                || UiTranslationScope.isInternal()
                || adapter == null
                || !adapter.supports(role)
                || config == null
                || !config.enabled
                || !config.enabled_screen_translation
                || !ComponentRenderTranslationSupport.shouldRenderTranslated(config)) {
            return UiTranslationResult.original(
                    modId,
                    screenId,
                    role,
                    safeSource,
                    targetLanguage,
                    UiTranslationStatus.ORIGINAL
            );
        }

        String sourceText = safeSource.getString();
        UiTranslationResult cached = UiTranslationScope.lookup(sourceText, role, targetLanguage);
        if (cached != null) {
            UiTranslationDiagnostics.recordText(adapter, role, sourceText, null, cached.status());
            return cached;
        }

        boolean userInput = UiTranslationScope.isUserInput() && role != UiTextRole.DESCRIPTION;
        UiTextFilter.Decision decision = UiScreenTextPolicy.evaluate(
                sourceText,
                role,
                userInput
        );
        if (!decision.eligible()) {
            UiTranslationResult result = result(
                    adapter,
                    role,
                    safeSource,
                    safeSource,
                    UiTranslationStatus.INELIGIBLE,
                    targetLanguage,
                    false
            );
            UiTranslationDiagnostics.recordText(adapter, role, sourceText, decision, result.status());
            UiTranslationScope.remember(sourceText, role, targetLanguage, result);
            return result;
        }

        try {
            Component translationSource = aiSource(safeSource);
            Set<String> decorativeGlyphs = UiScreenTextPolicy.decorativeGlyphs(sourceText);
            ComponentRenderTranslationSupport.TranslationResult translated =
                    ComponentRenderTranslationSupport.translate(
                            translationSource,
                            ComponentTranslationRoute.SCREEN_UI,
                            adapter.modId() + "/" + adapter.screenId() + "/" + role.wireName(),
                            POLICY_VERSION + ":" + role.wireName(),
                            config,
                            decorativeGlyphs
                    );
            UiTranslationStatus status = status(translated.state());
            String animationKey = "screen-ui:" + adapter.modId() + "/" + adapter.screenId() + "/" + role.wireName() + ":" + sourceText;
            Component visible;
            if (translated.state()
                    == com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime.State.CACHE_HIT
                    && translated.displayed() != null) {
                visible = translated.displayed();
            } else if (translated.state()
                    == com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime.State.PENDING) {
                visible = ComponentRenderTranslationSupport.animatePending(safeSource, animationKey);
            } else {
                visible = safeSource;
            }
            UiTranslationResult result = result(
                    adapter,
                    role,
                    safeSource,
                    visible,
                    status,
                    targetLanguage,
                    false
            );
            UiTranslationDiagnostics.recordText(adapter, role, sourceText, decision, result.status());
            UiTranslationScope.remember(sourceText, role, targetLanguage, result);
            return result;
        } catch (RuntimeException error) {
            UiTranslationResult result = result(
                    adapter,
                    role,
                    safeSource,
                    safeSource,
                    UiTranslationStatus.FAILED,
                    targetLanguage,
                    false
            );
            UiTranslationDiagnostics.recordText(adapter, role, sourceText, decision, result.status());
            UiTranslationScope.remember(sourceText, role, targetLanguage, result);
            return result;
        }
    }

    public static String translateString(String source, UiTextRole role) {
        if (source == null) {
            return null;
        }
        if (source.indexOf('§') >= 0) {
            Component decoded = LegacyComponentTextCodec.decode(source);
            return LegacyComponentTextCodec.encode(resolve(decoded, role).visibleComponent());
        }
        return resolve(Component.literal(source), role).visibleText();
    }

    public static String translateStringInCurrentScreen(String source, UiTextRole role) {
        if (source == null) {
            return null;
        }
        return withCurrentScreen(() -> translateString(source, role), source);
    }

    public static String translateStringAnimated(String source, UiTextRole role) {
        if (source == null) {
            return null;
        }
        Component decoded = source.indexOf('§') >= 0
                ? LegacyComponentTextCodec.decode(source)
                : Component.literal(source);
        return LegacyComponentTextCodec.encode(resolve(decoded, role).visibleComponent());
    }

    public static String translateStringAnimatedInCurrentScreen(String source, UiTextRole role) {
        if (source == null) {
            return null;
        }
        return withCurrentScreen(() -> translateStringAnimated(source, role), source);
    }

    public static Component translateComponentInCurrentScreen(Component source, UiTextRole role) {
        Component fallback = source == null ? Component.empty() : source;
        return withCurrentScreen(() -> translateComponent(source, role), fallback);
    }

    public static FormattedCharSequence translateFormattedCharSequenceInCurrentScreen(
            FormattedCharSequence source,
            UiTextRole role
    ) {
        return withCurrentScreen(() -> translateFormattedCharSequence(source, role), source);
    }

    public static FormattedText translateFormattedTextInCurrentScreen(FormattedText source, UiTextRole role) {
        return withCurrentScreen(() -> translateFormattedText(source, role), source);
    }

    public static void markFormattedSequenceHandled(FormattedCharSequence sequence) {
        if (sequence != null) {
            HANDLED_FORMATTED_SEQUENCES.get().add(sequence);
        }
    }

    public static FormattedCharSequence translateFormattedCharSequence(
            FormattedCharSequence source,
            UiTextRole role
    ) {
        if (source == null || !UiTranslationScope.isActive() || UiTranslationScope.isInternal()) {
            return source;
        }
        Set<FormattedCharSequence> handled = HANDLED_FORMATTED_SEQUENCES.get();
        if (handled.contains(source)) {
            return source;
        }
        Component component = componentFromFormattedSequence(source);
        if (component.getString().isEmpty()) {
            return source;
        }
        FormattedCharSequence visible = translateComponent(component, role).getVisualOrderText();
        if (visible != source) {
            markFormattedSequenceHandled(visible);
        }
        return visible;
    }

    public static Component translateComponent(Component source, UiTextRole role) {
        return resolve(source, role).visibleComponent();
    }

    public static FormattedText translateFormattedText(FormattedText source, UiTextRole role) {
        if (source == null || !UiTranslationScope.isActive() || UiTranslationScope.isInternal()) {
            return source;
        }
        if (source instanceof Component component) {
            return resolve(component, role).visibleComponent();
        }
        Component component = componentFromFormattedText(source);
        if (component.getString().isEmpty()) {
            return source;
        }
        return translateComponent(component, role);
    }

    public static List<Component> translateComponents(List<Component> source, UiTextRole role) {
        if (source == null || source.isEmpty() || !UiTranslationScope.isActive()) {
            return source;
        }
        List<Component> translated = new ArrayList<>(source.size());
        for (Component component : source) {
            translated.add(component == null ? null : translateComponent(component, role));
        }
        return java.util.Collections.unmodifiableList(translated);
    }

    public static void beginFrame() {
        HANDLED_FORMATTED_SEQUENCES.remove();
        FRAME_ID++;
    }

    public static void reset() {
        UiLanguageResourceResolver.clear();
        UiTranslationDiagnostics.reset();
        HANDLED_FORMATTED_SEQUENCES.remove();
    }

    public static <T> T withoutNestedTranslation(Supplier<T> action) {
        return UiTranslationScope.withInternal(action);
    }

    private static <T> T withCurrentScreen(Supplier<T> action, T fallback) {
        if (UiTranslationScope.isInternal()) {
            return fallback;
        }
        if (UiTranslationScope.isActive()) {
            return action.get();
        }
        Screen screen = currentScreen();
        if (screen == null) {
            return fallback;
        }
        try (UiTranslationScope.Scope scope = UiTranslationScope.enter(screen)) {
            return action.get();
        } catch (RuntimeException error) {
            return fallback;
        }
    }

    private static Screen currentScreen() {
        try {
            Minecraft client = Minecraft.getInstance();
            return client == null ? null : client.screen;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static OtherTranslationsConfig currentConfig() {
        try {
            ModConfig config = Translate_AllinOne.getConfig();
            return config == null ? null : config.otherTranslations;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static UiTextRole resolveRole(UiTextRole requestedRole) {
        if (requestedRole != null) {
            return requestedRole;
        }
        return UiTranslationScope.role();
    }

    private static UiTranslationResult result(
            UiScreenAdapter adapter,
            UiTextRole role,
            Component source,
            Component visible,
            UiTranslationStatus status,
            String targetLanguage,
            boolean nativeResource
    ) {
        return new UiTranslationResult(
                adapter.modId(),
                adapter.screenId(),
                role,
                source,
                visible,
                source.getString(),
                visible.getString(),
                status,
                targetLanguage,
                nativeResource
        );
    }

    private static UiTranslationStatus status(
            com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime.State state
    ) {
        return switch (state) {
            case CACHE_HIT, LEGACY_HIT -> UiTranslationStatus.TRANSLATED;
            case PENDING, MISS -> UiTranslationStatus.PENDING;
            case FAILED -> UiTranslationStatus.FAILED;
            case NO_TEXT, INELIGIBLE -> UiTranslationStatus.INELIGIBLE;
        };
    }

    private static UiLanguageResourceResolver.Lookup lookupNativeResource(
            Component source,
            String modId,
            String targetLanguage
    ) {
        if (!(source.getContents() instanceof TranslatableContents contents)) {
            return new UiLanguageResourceResolver.Lookup(
                    UiLanguageResourceResolver.State.MISS,
                    ""
            );
        }
        boolean pending = false;
        for (String resourceModId : nativeResourceModIds(modId)) {
            UiLanguageResourceResolver.Lookup lookup = UiLanguageResourceResolver.lookup(
                    resourceModId,
                    targetLanguage,
                    contents.getKey()
            );
            if (lookup.state() == UiLanguageResourceResolver.State.HIT) {
                return lookup;
            }
            pending |= lookup.state() == UiLanguageResourceResolver.State.PENDING;
        }
        return new UiLanguageResourceResolver.Lookup(
                pending ? UiLanguageResourceResolver.State.PENDING : UiLanguageResourceResolver.State.MISS,
                ""
        );
    }

    private static List<String> nativeResourceModIds(String fallbackModId) {
        if (fallbackModId == null || fallbackModId.isBlank()) {
            return List.of();
        }
        return List.of(fallbackModId);
    }

    private static boolean hasTranslationKey(Component source) {
        return source.getContents() instanceof TranslatableContents;
    }

    private static Component componentFromFormattedSequence(FormattedCharSequence source) {
        List<FormattedSequencePart> parts = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Style[] currentStyle = {Style.EMPTY};
        FormattedCharSequence sequence = tryCureCaxtonSequence(source);
        try {
            sequence.accept((index, style, codePoint) -> {
                Style resolvedStyle = style == null ? Style.EMPTY : style;
                if (!text.isEmpty() && !currentStyle[0].equals(resolvedStyle)) {
                    parts.add(new FormattedSequencePart(text.toString(), currentStyle[0]));
                    text.setLength(0);
                }
                currentStyle[0] = resolvedStyle;
                text.appendCodePoint(codePoint);
                return true;
            });
        } catch (RuntimeException error) {
            return Component.empty();
        }
        if (!text.isEmpty()) {
            parts.add(new FormattedSequencePart(text.toString(), currentStyle[0]));
        }
        if (parts.isEmpty()) {
            return Component.empty();
        }
        if (parts.size() == 1) {
            FormattedSequencePart part = parts.getFirst();
            return Component.literal(part.text()).setStyle(part.style());
        }
        MutableComponent result = Component.empty();
        for (FormattedSequencePart part : parts) {
            result.append(Component.literal(part.text()).setStyle(part.style()));
        }
        return result;
    }

    private static FormattedCharSequence tryCureCaxtonSequence(FormattedCharSequence source) {
        if (source == null || !source.getClass().getName().startsWith("xyz.flirora.caxton.")) {
            return source;
        }
        try {
            Method cure = findCureMethod(source.getClass());
            if (cure != null) {
                cure.setAccessible(true);
                Object result = cure.invoke(source);
                if (result instanceof FormattedCharSequence cured) {
                    return cured;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return source;
    }

    private static Method findCureMethod(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod("cure");
                if (method.getParameterCount() == 0) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        try {
            Method method = type.getMethod("cure");
            if (method.getParameterCount() == 0) {
                return method;
            }
        } catch (NoSuchMethodException ignored) {
        }
        return null;
    }

    private static Component componentFromFormattedText(FormattedText source) {
        MutableComponent result = Component.empty();
        try {
            source.visit((style, text) -> {
                if (text != null && !text.isEmpty()) {
                    result.append(Component.literal(text).setStyle(
                            style == null ? Style.EMPTY : style
                    ));
                }
                return java.util.Optional.<Void>empty();
            }, Style.EMPTY);
        } catch (RuntimeException error) {
            return Component.empty();
        }
        return result;
    }

    private static Component nativeComponent(Component source, String template) {
        String value = format(template, ((TranslatableContents) source.getContents()).getArgs());
        Component translated = Component.literal(value).setStyle(source.getStyle());
        for (Component sibling : source.getSiblings()) {
            translated = translated.copy().append(sibling.copy());
        }
        return translated;
    }

    private static Component aiSource(Component source) {
        MutableComponent materialized = Component.empty();
        source.visit((style, text) -> {
            if (text != null && !text.isEmpty()) {
                materialized.append(Component.literal(text).setStyle(
                        style == null ? net.minecraft.network.chat.Style.EMPTY : style
                ));
            }
            return java.util.Optional.<java.lang.Void>empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return materialized;
    }

    private static String format(String template, Object[] args) {
        if (template == null || args == null || args.length == 0 || !template.contains("%")) {
            return template == null ? "" : template;
        }
        Object[] values = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            Object value = args[index];
            values[index] = value instanceof Component component ? component.getString() : value;
        }
        try {
            return String.format(Locale.ROOT, template, values);
        } catch (RuntimeException error) {
            return template;
        }
    }

    private record FormattedSequencePart(String text, Style style) {
    }
}
