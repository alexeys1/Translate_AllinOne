package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.registration.LifecycleEventManager;
import com.alexeys.translate_allinone.utils.cache.component.ComponentTranslationStore;
import com.alexeys.translate_allinone.utils.cache.component.ComponentTranslationStoreRegistry;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.translate.ApiKeyDecryptFailureNotifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ComponentTranslationRuntime {
    private static final int DOCUMENT_CACHE_LIMIT = 512;
    private static final int MIN_UNSTABLE_RUNS = 4;
    private static final int MAX_UNSTABLE_RUN_LENGTH = 2;
    private static final ComponentTranslationClient DEFAULT_CLIENT = new ComponentTranslationClient();
    private static final Supplier<ModConfig> DEFAULT_CONFIG_SUPPLIER = Translate_AllinOne::getConfig;
    private static volatile ComponentTranslationClient client = DEFAULT_CLIENT;
    private static volatile Supplier<ModConfig> configSupplier = DEFAULT_CONFIG_SUPPLIER;
    private static final Map<DocumentMemoKey, MemoizedDocument> DOCUMENTS = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<DocumentMemoKey, MemoizedDocument> eldest) {
                    return size() > DOCUMENT_CACHE_LIMIT;
                }
            }
    );

    private static final Map<String, MemoizedDocument> PLAIN_DOCUMENTS = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, MemoizedDocument> eldest) {
                    return size() > DOCUMENT_CACHE_LIMIT;
                }
            }
    );

    static {
        ComponentTranslationRuntimeCore.configure(new RuntimeAccess());
    }

    private ComponentTranslationRuntime() {
    }

    public static ComponentTranslationDocument prepare(
            Component component,
            ComponentTranslationRoute route,
            String context,
            String policyVersion
    ) {
        return prepare(component, route, context, policyVersion, Set.of());
    }

    public static ComponentTranslationDocument prepare(
            Component component,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            Set<String> privateTokens
    ) {
        if (route == null) {
            throw new IllegalArgumentException("Component translation document input is incomplete.");
        }
        String resolvedContext = context == null || context.isBlank() ? route.wireName() : context.trim();
        String resolvedPolicyVersion = policyVersion == null || policyVersion.isBlank() ? "1" : policyVersion.trim();
        ComponentTranslationPolicy policy = ComponentTranslationPolicy.forRoute(route)
                .withPrivateTokens(privateTokens)
                .withContext(resolvedContext)
                .withSemanticSetting("route_policy", resolvedPolicyVersion);
        return prepare(component, policy);
    }

    private static boolean isColourOnlyFragmentation(Component component) {
        if (component.getSiblings().size() < MIN_UNSTABLE_RUNS) {
            return false;
        }
        int[] runs = {0};
        int[] totalLength = {0};
        Style[] base = {null};
        boolean[] colourOnly = {true};
        try {
            component.visit((style, text) -> {
                if (text != null && !text.isEmpty()) {
                    Style resolved = withoutColor(style == null ? Style.EMPTY : style);
                    if (base[0] == null) {
                        base[0] = resolved;
                    } else if (colourOnly[0] && !base[0].equals(resolved)) {
                        colourOnly[0] = false;
                    }
                    runs[0]++;
                    totalLength[0] += text.length();
                }
                return java.util.Optional.<Void>empty();
            }, Style.EMPTY);
        } catch (RuntimeException error) {
            return false;
        }
        return colourOnly[0]
                && runs[0] >= MIN_UNSTABLE_RUNS
                && totalLength[0] / runs[0] <= MAX_UNSTABLE_RUN_LENGTH;
    }

    private static boolean isColourOnlyRuns(Component component) {
        Style[] base = {null};
        boolean[] colourOnly = {true};
        try {
            component.visit((style, text) -> {
                if (text != null && !text.isEmpty()) {
                    Style resolved = withoutColor(style == null ? Style.EMPTY : style);
                    if (base[0] == null) {
                        base[0] = resolved;
                    } else if (colourOnly[0] && !base[0].equals(resolved)) {
                        colourOnly[0] = false;
                    }
                }
                return java.util.Optional.<Void>empty();
            }, Style.EMPTY);
        } catch (RuntimeException error) {
            return false;
        }
        return colourOnly[0];
    }

    private static Style withoutColor(Style style) {
        return style.withColor((TextColor) null);
    }

    private static int nonEmptyRunCount(Component component) {
        int[] runs = {0};
        try {
            component.visit((style, text) -> {
                if (text != null && !text.isEmpty()) {
                    runs[0]++;
                }
                return java.util.Optional.<Void>empty();
            }, Style.EMPTY);
        } catch (RuntimeException error) {
            return 0;
        }
        return runs[0];
    }

    private static ComponentTranslationDocument plainDocumentFor(String textKey, String plainText) {
        MemoizedDocument plain = PLAIN_DOCUMENTS.get(textKey);
        if (plain == null || !plainText.equals(plain.source().getString())) {
            return null;
        }
        return plain.document();
    }

    public static ComponentTranslationDocument prepare(
            Component component,
            ComponentTranslationPolicy policy
    ) {
        if (component == null || policy == null) {
            throw new IllegalArgumentException("Component translation document input is incomplete.");
        }
        String plainText = component.getString();
        String textKey = policy.route().wireName() + '\u0000' + plainText;
        boolean structureAgnostic = policy.route() == ComponentTranslationRoute.SCREEN_UI
                && isColourOnlyFragmentation(component);
        if (structureAgnostic) {
            ComponentTranslationDocument plain = plainDocumentFor(textKey, plainText);
            if (plain != null) {
                return plain;
            }
        }
        DocumentMemoKey key = new DocumentMemoKey(
                policy.route(),
                policy.version(),
                policy.semanticSettings(),
                structureAgnostic ? 0 : component.hashCode(),
                plainText,
                structureAgnostic
        );
        MemoizedDocument memoized = DOCUMENTS.get(key);
        if (memoized != null && memoized.matches(component, key.structureAgnostic())) {
            return memoized.document();
        }
        if (!structureAgnostic && policy.route() == ComponentTranslationRoute.SCREEN_UI) {
            MemoizedDocument byText = DOCUMENTS.get(new DocumentMemoKey(
                    policy.route(),
                    policy.version(),
                    policy.semanticSettings(),
                    0,
                    plainText,
                    true
            ));
            if (byText != null && byText.matches(component, true) && isColourOnlyRuns(component)) {
                return byText.document();
            }
        }

        long startedAt = System.nanoTime();
        try {
            ComponentTranslationDocument document = new ComponentDocumentBuilder().build(component, policy);
            ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.DOCUMENT_BUILT);
            ComponentTranslationMetrics.recordValue(
                    document,
                    ComponentTranslationMetrics.Measurement.TEXT_UNITS,
                    document.units().size()
            );
            if (document.units().isEmpty()) {
                ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.NO_TEXT);
            }
            MemoizedDocument built = new MemoizedDocument(component.copy(), document);
            DOCUMENTS.put(key, built);
            if (policy.route() == ComponentTranslationRoute.SCREEN_UI
                    && !structureAgnostic
                    && isColourOnlyRuns(component)
                    && nonEmptyRunCount(component) <= 1) {
                PLAIN_DOCUMENTS.put(textKey, built);
            }
            return document;
        } catch (RuntimeException error) {
            ComponentTranslationMetrics.record(policy.route(), ComponentTranslationMetrics.Outcome.DOCUMENT_FAILED);
            if (error instanceof ComponentJsonException componentError
                    && componentError.kind() == ComponentJsonException.Kind.CODEC) {
                ComponentTranslationMetrics.record(
                        policy.route(),
                        ComponentTranslationMetrics.Outcome.CODEC_ENCODE_FAILURE
                );
            }
            throw error;
        } finally {
            ComponentTranslationMetrics.recordNanos(
                    policy.route(),
                    ComponentTranslationMetrics.Timing.DOCUMENT_BUILD,
                    System.nanoTime() - startedAt
            );
        }
    }

    public static <T> Resolution<T> resolve(
            ComponentTranslationDocument document,
            String targetLanguage,
            String legacyKey,
            Supplier<T> legacyLookup,
            Function<ComponentTranslationResponse, T> renderer,
            String requestContext
    ) {
        return adapt(ComponentTranslationRuntimeCore.resolve(
                document,
                targetLanguage,
                legacyKey,
                legacyLookup,
                renderer,
                requestContext
        ));
    }

    public static <T> Resolution<T> resolve(
            ComponentTranslationDocument document,
            String targetLanguage,
            String legacyKey,
            Supplier<T> legacyLookup,
            Function<ComponentTranslationResponse, T> renderer,
            String requestContext,
            boolean queueIfMissing
    ) {
        return adapt(ComponentTranslationRuntimeCore.resolve(
                document,
                targetLanguage,
                legacyKey,
                legacyLookup,
                renderer,
                requestContext,
                queueIfMissing
        ));
    }

    public static boolean forceRefresh(ComponentTranslationDocument document, String targetLanguage) {
        return ComponentTranslationRuntimeCore.forceRefresh(document, targetLanguage);
    }

    public static int clearFailures(ComponentTranslationRoute route) {
        return ComponentTranslationRuntimeCore.clearFailures(route);
    }

    public static long beginSession() {
        long epoch = ComponentTranslationRuntimeCore.beginSession();
        DOCUMENTS.clear();
        PLAIN_DOCUMENTS.clear();
        return epoch;
    }

    public static long endSession() {
        long epoch = ComponentTranslationRuntimeCore.endSession();
        DOCUMENTS.clear();
        PLAIN_DOCUMENTS.clear();
        return epoch;
    }

    public static void resetSession() {
        ComponentTranslationRuntimeCore.resetSession();
        DOCUMENTS.clear();
        PLAIN_DOCUMENTS.clear();
    }

    public static long providerConfigurationChanged() {
        long epoch = ComponentTranslationRuntimeCore.providerConfigurationChanged();
        DOCUMENTS.clear();
        PLAIN_DOCUMENTS.clear();
        return epoch;
    }

    public static long cancelPendingTranslations() {
        long epoch = ComponentTranslationRuntimeCore.cancelPendingTranslations();
        DOCUMENTS.clear();
        PLAIN_DOCUMENTS.clear();
        return epoch;
    }

    public static void beginScreenUiSession(int failureBudget) {
        ComponentTranslationRuntimeCore.beginScreenUiSession(failureBudget);
    }

    public static void endScreenUiSession() {
        ComponentTranslationRuntimeCore.endScreenUiSession();
    }

    public static String cacheKey(ComponentTranslationDocument document, String targetLanguage) {
        return ComponentTranslationRuntimeCore.cacheKey(document, targetLanguage);
    }

    public static boolean isWorkInFlight(String cacheKey) {
        return ComponentTranslationRuntimeCore.isWorkInFlight(cacheKey);
    }

    public static <T> T promoteCompatibleResponse(
            ComponentTranslationDocument currentDocument,
            ComponentTranslationDocument legacyDocument,
            String targetLanguage,
            Function<ComponentTranslationResponse, ComponentTranslationResponse> upgrader,
            Function<ComponentTranslationResponse, T> renderer,
            String requestContext
    ) {
        return ComponentTranslationRuntimeCore.promoteCompatibleResponse(
                currentDocument,
                legacyDocument,
                targetLanguage,
                upgrader,
                renderer,
                requestContext
        );
    }

    public static boolean claimFallbackGeneration(String primaryCacheKey) {
        return ComponentTranslationRuntimeCore.claimFallbackGeneration(primaryCacheKey);
    }

    public static boolean clearFallbackGeneration(String primaryCacheKey) {
        return ComponentTranslationRuntimeCore.clearFallbackGeneration(primaryCacheKey);
    }

    static String candidateHash(ComponentTranslationResponse response) {
        return ComponentTranslationRuntimeCore.candidateHash(response);
    }

    static <T> CandidatePromotion<T> validateAndCommitCandidate(
            ComponentTranslationResponse response,
            Function<ComponentTranslationResponse, T> renderer,
            BooleanSupplier commit
    ) {
        ComponentTranslationRuntimeCore.CandidatePromotion<T> promotion =
                ComponentTranslationRuntimeCore.validateAndCommitCandidate(response, renderer, commit);
        return new CandidatePromotion<>(
                promotion.accepted(),
                promotion.value(),
                promotion.errorMessage(),
                promotion.error()
        );
    }

    static long failureExpiresAtMillis(
            ComponentTranslationRoute route,
            FailureDisposition disposition,
            long nowMillis
    ) {
        return ComponentTranslationRuntimeCore.failureExpiresAtMillis(
                route,
                ComponentTranslationRuntimeCore.FailureDisposition.valueOf(disposition.name()),
                nowMillis
        );
    }

    static void setClientForTests(ComponentTranslationClient testClient) {
        client = testClient == null ? DEFAULT_CLIENT : testClient;
    }

    static void setConfigForTests(ModConfig testConfig) {
        configSupplier = testConfig == null ? DEFAULT_CONFIG_SUPPLIER : () -> testConfig;
    }

    private static <T> Resolution<T> adapt(ComponentTranslationRuntimeCore.Resolution<T> resolution) {
        return new Resolution<>(
                State.valueOf(resolution.state().name()),
                resolution.value(),
                resolution.cacheKey(),
                resolution.errorMessage(),
                FailureDisposition.valueOf(resolution.failureDisposition().name()),
                resolution.inFlight()
        );
    }

    public record Resolution<T>(
            State state,
            T value,
            String cacheKey,
            String errorMessage,
            FailureDisposition failureDisposition,
            boolean inFlight
    ) {
        public Resolution(State state, T value, String cacheKey, String errorMessage) {
            this(state, value, cacheKey, errorMessage, defaultDisposition(state));
        }

        public Resolution(
                State state,
                T value,
                String cacheKey,
                String errorMessage,
                FailureDisposition failureDisposition
        ) {
            this(
                    state,
                    value,
                    cacheKey,
                    errorMessage,
                    failureDisposition,
                    state == State.PENDING && isWorkInFlight(cacheKey)
            );
        }

        public Resolution {
            cacheKey = cacheKey == null ? "" : cacheKey;
            errorMessage = errorMessage == null ? "" : errorMessage;
            failureDisposition = failureDisposition == null ? defaultDisposition(state) : failureDisposition;
            inFlight = state == State.PENDING && isWorkInFlight(cacheKey);
        }

        public boolean allowsTooltipFallback() {
            return failureDisposition == FailureDisposition.TERMINAL_CONTENT_FAILURE;
        }

        private static FailureDisposition defaultDisposition(State state) {
            if (state == State.FAILED) {
                return FailureDisposition.TERMINAL_CONTENT_FAILURE;
            }
            if (state == State.INELIGIBLE) {
                return FailureDisposition.INELIGIBLE;
            }
            return FailureDisposition.NONE;
        }
    }

    public enum FailureDisposition {
        NONE,
        TERMINAL_CONTENT_FAILURE,
        INFRASTRUCTURE_FAILURE,
        INELIGIBLE
    }

    public enum State {
        CACHE_HIT,
        LEGACY_HIT,
        PENDING,
        FAILED,
        MISS,
        NO_TEXT,
        INELIGIBLE
    }

    record CandidatePromotion<T>(
            boolean accepted,
            T value,
            String errorMessage,
            RuntimeException error
    ) {
    }

    private record DocumentMemoKey(
            ComponentTranslationRoute route,
            int policyVersion,
            Map<String, String> semanticSettings,
            int componentHash,
            String plainText,
            boolean structureAgnostic
    ) {
    }

    private record MemoizedDocument(Component source, ComponentTranslationDocument document) {
        private boolean matches(Component candidate, boolean structureAgnostic) {
            return structureAgnostic
                    ? source.getString().equals(candidate.getString())
                    : source.equals(candidate);
        }
    }

    private static final class RuntimeAccess implements ComponentTranslationRuntimeCore.Access {
        @Override
        public ModConfig config() {
            return configSupplier.get();
        }

        @Override
        public boolean readyForTranslation() {
            return LifecycleEventManager.isReadyForTranslation;
        }

        @Override
        public ComponentTranslationStore store(ComponentTranslationRoute route) {
            return StoreHolder.INSTANCE.forRoute(route);
        }

        @Override
        public CompletableFuture<ComponentTranslationResponse> translateResponse(
                ComponentTranslationDocument document,
                String targetLanguage,
                ApiProviderProfile provider,
                String requestContext
        ) {
            return client.translateResponse(document, targetLanguage, provider, requestContext);
        }

        @Override
        public void onNoRoutedModel(ComponentTranslationRuntimeCore.ProviderSurface surface) {
            NoRoutedModelErrorSupport.onNoRoutedModel(switch (surface) {
                case ITEM_TOOLTIP -> NoRoutedModelErrorSupport.Surface.ITEM_TOOLTIP;
                case OTHER_TRANSLATIONS -> NoRoutedModelErrorSupport.Surface.OTHER_TRANSLATIONS;
                case SCOREBOARD -> NoRoutedModelErrorSupport.Surface.SCOREBOARD;
            });
        }

        @Override
        public void onApiKeyDecryptFailure(ComponentTranslationRuntimeCore.ProviderSurface surface) {
            ApiKeyDecryptFailureNotifier.notifyRuntimeIfPresent();
        }

        @Override
        public void flow(ComponentTranslationRoute route, String message, Object... arguments) {
            ComponentTranslationDebugLogger.flow(route, message, arguments);
        }

        @Override
        public void error(ComponentTranslationRoute route, String message, Object... arguments) {
            ComponentTranslationDebugLogger.error(route, message, arguments);
        }

        @Override
        public void entityIdentityMiss(
                ComponentTranslationDocument document,
                String targetLanguage,
                ComponentTranslationCacheIdentity identity,
                String lookupStatus
        ) {
            ComponentTranslationDebugLogger.entityIdentityMiss(
                    document,
                    targetLanguage,
                    identity,
                    lookupStatus
            );
        }

        @Override
        public void entityTemplateReuse(String fullKey, String templateKey) {
            ComponentTranslationDebugLogger.entityTemplateReuse(fullKey, templateKey);
        }
    }

    private static final class StoreHolder {
        private static final ComponentTranslationStoreRegistry INSTANCE = ComponentTranslationStoreRegistry.getInstance();
    }
}
