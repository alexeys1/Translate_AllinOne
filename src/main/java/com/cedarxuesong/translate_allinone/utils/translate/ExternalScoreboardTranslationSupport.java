package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationApplier;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationPolicy;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public final class ExternalScoreboardTranslationSupport {
    static final String CONTEXT = "external_scoreboard";
    static final String POLICY_VERSION = "scoreboard-external-v1";
    private static final int RETAINED_TRANSLATION_LIMIT = 512;
    private static final Logger LOGGER = LoggerFactory.getLogger(
            "Translate_AllinOne/ExternalScoreboardTranslationSupport"
    );
    private static final ComponentTranslationApplier APPLIER = new ComponentTranslationApplier();
    private static final AtomicBoolean PREPARE_FAILURE_LOGGED = new AtomicBoolean();
    private static final Map<String, Component> RETAINED_TRANSLATIONS = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
                    return size() > RETAINED_TRANSLATION_LIMIT;
                }
            }
    );

    private ExternalScoreboardTranslationSupport() {
    }

    public static Result translate(Component original, Source source, boolean hidesVanillaScoreboard) {
        Component sourceComponent = original == null ? Component.empty() : original;
        String sourceName = source == null ? "unknown" : source.wireName();
        ScoreboardConfig config = Translate_AllinOne.getConfig() == null
                ? null
                : Translate_AllinOne.getConfig().scoreboardTranslate;
        if (ComponentRenderTranslationSupport.isTranslationBlockedByScreen()
                || !isEnabled(config, hidesVanillaScoreboard)) {
            return new Result(sourceComponent, false);
        }
        boolean showOriginal = ScoreboardTranslationInputSupport.shouldShowOriginal(config);

        try {
            Set<String> privateTokens = collectPrivateTokens(sourceComponent);
            ExternalScoreboardComponentTemplate.Prepared template =
                    ExternalScoreboardComponentTemplate.prepare(sourceComponent, privateTokens);
            ComponentTranslationDocument document = prepareDocument(
                    template.templateComponent(),
                    template.privatePlaceholders()
            );
            if (document.units().isEmpty()) {
                return new Result(sourceComponent, false);
            }

            String targetLanguage = config.target_language;
            String cacheKey = ComponentTranslationRuntime.cacheKey(document, targetLanguage);
            boolean refreshPressed = ScoreboardTranslationInputSupport.isRefreshPressed(config);
            boolean refreshStarted = refreshPressed
                    && ScoreboardTranslationInputSupport.claimRefreshIdentity("v1:" + cacheKey);
            if (refreshStarted) {
                RETAINED_TRANSLATIONS.remove(cacheKey);
                ComponentTranslationRuntime.forceRefresh(document, targetLanguage);
            }
            if (!shouldResolveForDisplayState(showOriginal, refreshPressed)) {
                return new Result(sourceComponent, false);
            }

            ComponentTranslationRuntime.Resolution<Component> resolution = ComponentTranslationRuntime.resolve(
                    document,
                    targetLanguage,
                    "",
                    null,
                    response -> template.restore(APPLIER.apply(document, response)),
                    CONTEXT + "; source=" + sourceName
            );
            Component retained = RETAINED_TRANSLATIONS.get(cacheKey);
            Result result = resultForResolution(sourceComponent, resolution, retained, refreshStarted);
            if (resolution.state() == ComponentTranslationRuntime.State.V1_HIT
                    && result.component() != sourceComponent) {
                RETAINED_TRANSLATIONS.put(cacheKey, result.component().copy());
            }
            return showOriginal ? new Result(sourceComponent, false) : result;
        } catch (RuntimeException e) {
            if (PREPARE_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn(
                        "External scoreboard Component V1 preparation failed; preserving original lines. source={}",
                        sourceName,
                        e
                );
            }
            return new Result(sourceComponent, false);
        }
    }

    public static boolean isEnabled(ScoreboardConfig config, boolean hidesVanillaScoreboard) {
        if (ComponentRenderTranslationSupport.isTranslationBlockedByScreen()
                || config == null
                || !config.enabled
                || config.external_custom_scoreboard_mode == null) {
            return false;
        }
        return switch (config.external_custom_scoreboard_mode) {
            case DISABLED -> false;
            case AUTO -> hidesVanillaScoreboard;
            case FORCE -> true;
        };
    }

    static boolean shouldResolveForDisplayState(boolean showOriginal, boolean refreshPressed) {
        return !showOriginal || refreshPressed;
    }

    static ComponentTranslationDocument prepareDocument(Component component, Set<String> privateTokens) {
        ComponentTranslationPolicy policy = ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.SCOREBOARD)
                .withContext(CONTEXT)
                .withSemanticSetting("route_policy", POLICY_VERSION)
                .withSemanticSetting("layout", "arbitrary-component")
                .withSemanticSetting("private_slot_schema", privateSlotSchema(privateTokens));
        return ComponentTranslationRuntime.prepare(component, policy);
    }

    private static String privateSlotSchema(Set<String> privateSlots) {
        if (privateSlots == null || privateSlots.isEmpty()) {
            return "none";
        }
        return String.join("\u001f", new TreeSet<>(privateSlots));
    }

    static Result resultForResolution(
            Component original,
            ComponentTranslationRuntime.Resolution<Component> resolution,
            Component retainedTranslation
    ) {
        return resultForResolution(original, resolution, retainedTranslation, false);
    }

    static Result resultForResolution(
            Component original,
            ComponentTranslationRuntime.Resolution<Component> resolution,
            Component retainedTranslation,
            boolean refreshStarted
    ) {
        if (resolution != null
                && resolution.state() == ComponentTranslationRuntime.State.V1_HIT
                && resolution.value() != null) {
            return new Result(resolution.value(), false);
        }
        boolean pending = resolution != null
                && resolution.state() == ComponentTranslationRuntime.State.PENDING;
        if (retainedTranslation != null && !refreshStarted) {
            return new Result(retainedTranslation.copy(), pending);
        }
        if (pending) {
            String animationKey = resolution.cacheKey().isBlank()
                    ? CONTEXT
                    : CONTEXT + ":" + resolution.cacheKey();
            return new Result(
                    AnimationManager.getAnimatedStyledText(original, animationKey, false),
                    true
            );
        }
        return new Result(
                original,
                false
        );
    }

    public static void resetSession() {
        RETAINED_TRANSLATIONS.clear();
        PREPARE_FAILURE_LOGGED.set(false);
    }

    static Set<String> collectPrivateTokens(Component component) {
        String plainText = component == null ? "" : component.getString();
        if (plainText.isEmpty()) {
            return Set.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                addIfPresent(tokens, plainText, client.player.getName().getString());
            }
            if (client.getConnection() != null) {
                client.getConnection().getOnlinePlayers().forEach(playerInfo -> {
                    String profileName = profileName(playerInfo.getProfile());
                    addIfPresent(tokens, plainText, profileName);
                });
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Client state can be incomplete during startup or disconnect. The
            // built-in numeric/URL/token policy remains active in that case.
        }
        return Set.copyOf(tokens);
    }

    private static String profileName(Object profile) {
        if (profile == null) {
            return "";
        }
        for (String methodName : new String[]{"name", "getName"}) {
            try {
                Method method = profile.getClass().getMethod(methodName);
                Object value = method.invoke(profile);
                return value instanceof String string ? string : "";
            } catch (ReflectiveOperationException ignored) {
                // Try the other Authlib API shape.
            }
        }
        return "";
    }

    private static void addIfPresent(Set<String> tokens, String plainText, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        String lowerText = plainText.toLowerCase(Locale.ROOT);
        String lowerCandidate = candidate.toLowerCase(Locale.ROOT);
        int searchFrom = 0;
        int offset;
        while ((offset = lowerText.indexOf(lowerCandidate, searchFrom)) >= 0) {
            tokens.add(plainText.substring(offset, offset + candidate.length()));
            searchFrom = offset + candidate.length();
        }
    }

    public enum Source {
        SKYHANNI("skyhanni"),
        CUSTOM_SCOREBOARD("customscoreboard"),
        SCOREBOARD_OVERHAUL("scoreboard_overhaul");

        private final String wireName;

        Source(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    public record Result(Component component, boolean pending) {
        public Result {
            component = component == null ? Component.empty() : component;
        }
    }
}
