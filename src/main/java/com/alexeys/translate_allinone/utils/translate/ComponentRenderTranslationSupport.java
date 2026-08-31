package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.AnimationManager;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationApplier;
import com.alexeys.translate_allinone.utils.componentjson.ComponentDynamicTemplate;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import com.alexeys.translate_allinone.utils.input.KeybindingManager;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
public final class ComponentRenderTranslationSupport {
    private static final Set<String> REFRESHED_KEYS = new HashSet<>();
    private static boolean refreshHeld;
    private static final EntityTranslationRenderCache ENTITY_RENDER_CACHE = new EntityTranslationRenderCache();

    @FunctionalInterface
    interface TranslationPipeline {
        TranslationResult translate(
                Component original,
                ComponentTranslationRoute route,
                String context,
                String policyVersion,
                OtherTranslationsConfig config,
                boolean allowForceRefresh,
                Set<String> privateTokens
        );
    }

    private static volatile TranslationPipeline translationPipeline = ComponentRenderTranslationSupport::translateHeavy;

    private ComponentRenderTranslationSupport() {
    }

    static OtherTranslationsConfig config() {
        ModConfig config = Translate_AllinOne.getConfig();
        return config == null ? null : config.otherTranslations;
    }

    static boolean isFeatureEnabled(OtherTranslationsConfig config, boolean featureEnabled) {
        return TranslationFeatureGate.isEnabled() && config != null && config.enabled && featureEnabled;
    }

    static boolean shouldRenderTranslated(OtherTranslationsConfig config) {
        boolean pressed = config != null
                && config.keybinding != null
                && KeybindingManager.isPressed(config.keybinding.binding);
        return shouldRenderTranslated(config, pressed);
    }

    static boolean shouldRenderTranslated(OtherTranslationsConfig config, boolean pressed) {
        if (!TranslationFeatureGate.isEnabled() || config == null) {
            return false;
        }
        OtherTranslationsConfig.KeybindingMode mode = config.keybinding == null || config.keybinding.mode == null
                ? OtherTranslationsConfig.KeybindingMode.DISABLED
                : config.keybinding.mode;
        return switch (mode) {
            case HOLD_TO_TRANSLATE -> pressed;
            case HOLD_TO_SEE_ORIGINAL -> !pressed;
            case DISABLED -> true;
        };
    }

    public static boolean isTranslationBlockedByScreen() {
        Minecraft client = Minecraft.getInstance();
        return client != null
                && (client.screen instanceof InventoryScreen
                || client.screen instanceof BookEditScreen
                || client.screen instanceof BookViewScreen);
    }

    static TranslationResult translate(
            Component original,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            OtherTranslationsConfig config
    ) {
        return translate(original, route, context, policyVersion, config, false, Set.of());
    }

    static TranslationResult translate(
            Component original,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            OtherTranslationsConfig config,
            Set<String> privateTokens
    ) {
        return translate(original, route, context, policyVersion, config, false, privateTokens);
    }

    static TranslationResult translate(
            Component original,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            OtherTranslationsConfig config,
            boolean allowForceRefresh
    ) {
        return translate(original, route, context, policyVersion, config, allowForceRefresh, Set.of());
    }

    static TranslationResult translate(
            Component original,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            OtherTranslationsConfig config,
            boolean allowForceRefresh,
            Set<String> privateTokens
    ) {
        if (!TranslationFeatureGate.isEnabled() || original == null || config == null) {
            return TranslationResult.original(original, null);
        }
        EntityTranslationRenderCache.Key cacheKey = null;
        boolean entityCandidate = isEntityRenderRoute(route)
                && (privateTokens == null || privateTokens.isEmpty());
        if (entityCandidate) {
            cacheKey = new EntityTranslationRenderCache.Key(
                    original,
                    route,
                    context,
                    config.target_language,
                    policyVersion
            );
            if (!allowForceRefresh) {
                TranslationResult cached = ENTITY_RENDER_CACHE.get(cacheKey);
                if (cached != null) {
                    return cached;
                }
            } else {
                ENTITY_RENDER_CACHE.remove(cacheKey);
            }
        }
        TranslationResult result = translationPipeline.translate(
                original,
                route,
                context,
                policyVersion,
                config,
                allowForceRefresh,
                privateTokens
        );
        if (cacheKey != null
                && !allowForceRefresh
                && result.state() == ComponentTranslationRuntime.State.CACHE_HIT
                && result.displayed() != null) {
            ENTITY_RENDER_CACHE.put(cacheKey, result);
        }
        return result;
    }

    private static boolean isEntityRenderRoute(ComponentTranslationRoute route) {
        return route == ComponentTranslationRoute.ENTITY_NAME || route == ComponentTranslationRoute.TEXT_DISPLAY;
    }

    private static TranslationResult translateHeavy(
            Component original,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            OtherTranslationsConfig config,
            boolean allowForceRefresh,
            Set<String> privateTokens
    ) {
        try {
            ComponentDynamicTemplate template = ComponentDynamicTemplate.prepare(original, privateTokens);
            ComponentTranslationDocument document = ComponentTranslationRuntime.prepare(
                    template.templateComponent(),
                    route,
                    context,
                    policyVersion,
                    template.privatePlaceholders()
            );
            if (document.units().isEmpty()) {
                return TranslationResult.original(original, document);
            }
            if (allowForceRefresh) {
                maybeForceRefresh(document, config);
            }
            ComponentTranslationRuntime.Resolution<Component> resolution = ComponentTranslationRuntime.resolve(
                    document,
                    config.target_language,
                    null,
                    () -> null,
                    response -> template.restore(new ComponentTranslationApplier().apply(document, response)),
                    context
            );
            if (resolution.state() == ComponentTranslationRuntime.State.CACHE_HIT && resolution.value() != null) {
                return new TranslationResult(
                        original,
                        resolution.value(),
                        document,
                        resolution.state(),
                        resolution.inFlight()
                );
            }
            return new TranslationResult(
                    original,
                    original,
                    document,
                    resolution.state(),
                    resolution.inFlight()
            );
        } catch (RuntimeException error) {
            return TranslationResult.original(original, null);
        }
    }

    static void forceRefreshAndQueue(
            Component original,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            OtherTranslationsConfig config
    ) {
        if (!TranslationFeatureGate.isEnabled() || original == null || config == null || !isRefreshPressed(config)) {
            return;
        }
        try {
            ComponentDynamicTemplate template = ComponentDynamicTemplate.prepare(original);
            forceRefreshAndQueue(
                    ComponentTranslationRuntime.prepare(template.templateComponent(), route, context, policyVersion),
                    config,
                    context
            );
        } catch (RuntimeException ignored) {
        }
    }

    static void forceRefreshAndQueue(
            ComponentTranslationDocument document,
            OtherTranslationsConfig config,
            String requestContext
    ) {
        if (!TranslationFeatureGate.isEnabled()
                || document == null
                || document.units().isEmpty()
                || config == null
                || !isRefreshPressed(config)) {
            return;
        }
        try {
            maybeForceRefresh(document, config);
            ComponentTranslationRuntime.resolve(
                    document,
                    config.target_language,
                    null,
                    () -> null,
                    response -> null,
                    requestContext
            );
        } catch (RuntimeException ignored) {
        }
    }

    static boolean isEligible(Component component, int maximumCharacters) {
        if (component == null) {
            return false;
        }
        String plainText = component.getString();
        if (plainText == null || plainText.isBlank() || plainText.length() > maximumCharacters) {
            return false;
        }
        return plainText.codePoints().noneMatch(ComponentRenderTranslationSupport::isPrivateUseCodePoint);
    }

    static Component displayWithPendingAnimation(TranslationResult result, String animationKey) {
        if (result == null) {
            return Component.empty();
        }
        if (!TranslationFeatureGate.isEnabled()) {
            return result.original();
        }
        return result.state() == ComponentTranslationRuntime.State.PENDING
                ? animatePending(result.original(), animationKey)
                : result.displayed();
    }

    static Component animatePending(Component original, String animationKey) {
        if (original == null) {
            return Component.empty();
        }
        if (!TranslationFeatureGate.isEnabled()) {
            return original;
        }
        String resolvedKey = animationKey == null || animationKey.isBlank()
                ? "component-render-pending"
                : animationKey;
        return AnimationManager.getAnimatedStyledText(original, resolvedKey, false);
    }

    public static void resetRenderCache() {
        ENTITY_RENDER_CACHE.clear();
    }

    static void setTranslationPipelineForTesting(TranslationPipeline pipeline) {
        translationPipeline = pipeline == null ? ComponentRenderTranslationSupport::translateHeavy : pipeline;
    }

    static void resetRefreshState() {
        synchronized (REFRESHED_KEYS) {
            REFRESHED_KEYS.clear();
            refreshHeld = false;
        }
    }

    public static void tickRefreshState(OtherTranslationsConfig config) {
        if (!TranslationFeatureGate.isEnabled()) {
            resetRefreshState();
            return;
        }
        boolean pressed = isRefreshPressed(config);
        if (!pressed) {
            synchronized (REFRESHED_KEYS) {
                REFRESHED_KEYS.clear();
                refreshHeld = false;
            }
        }
    }

    static void maybeForceRefresh(ComponentTranslationDocument document, OtherTranslationsConfig config) {
        if (!TranslationFeatureGate.isEnabled() || document == null || config == null || config.keybinding == null) {
            return;
        }
        boolean pressed = isRefreshPressed(config);
        synchronized (REFRESHED_KEYS) {
            if (!pressed) {
                REFRESHED_KEYS.clear();
                refreshHeld = false;
                return;
            }
            if (!refreshHeld) {
                REFRESHED_KEYS.clear();
                refreshHeld = true;
                ENTITY_RENDER_CACHE.clear();
            }
            String key = ComponentTranslationRuntime.cacheKey(document, config.target_language);
            if (REFRESHED_KEYS.add(key)) {
                ComponentTranslationRuntime.forceRefresh(document, config.target_language);
            }
        }
    }

    static boolean isRefreshPressed(OtherTranslationsConfig config) {
        return config != null
                && config.keybinding != null
                && config.keybinding.refreshBinding != null
                && KeybindingManager.isPressed(config.keybinding.refreshBinding);
    }

    private static boolean isPrivateUseCodePoint(int codePoint) {
        return Character.getType(codePoint) == Character.PRIVATE_USE
                || (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
    }

    record TranslationResult(
            Component original,
            Component displayed,
            ComponentTranslationDocument document,
            ComponentTranslationRuntime.State state,
            boolean inFlight
    ) {
        static TranslationResult original(Component component, ComponentTranslationDocument document) {
            return new TranslationResult(
                    component,
                    component,
                    document,
                    ComponentTranslationRuntime.State.INELIGIBLE,
                    false
            );
        }

        boolean isTranslated() {
            return state == ComponentTranslationRuntime.State.CACHE_HIT && displayed != null && !displayed.equals(original);
        }
    }
}
