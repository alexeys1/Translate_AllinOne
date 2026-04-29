package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.registration.LifecycleEventManager;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.WynnDialogueTextCache;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.ProviderRouteResolver;
import com.cedarxuesong.translate_allinone.utils.config.pojos.WynnCraftConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WynnDialogueTranslationSupport {
    private static final Pattern CHAT_DIALOGUE_PATTERN = Pattern.compile("^\\[(\\d+/\\d+)]\\s*([^:]+):\\s*(.+)$");
    private static final Pattern LEADING_TIMESTAMP_PATTERN = Pattern.compile("^\\[(?:\\d{1,2}:){1,2}\\d{2}]\\s*");
    private static final Pattern OVERLAY_NPC_TAIL_PATTERN = Pattern.compile("((?:[A-Z](?:\\.[A-Z])+\\.?|[A-Z][a-z]+)(?: (?:[A-Z](?:\\.[A-Z])+\\.?|[A-Z][a-z]+))*)$");
    private static final Pattern OVERLAY_NPC_NAME_PATTERN = Pattern.compile("([A-Z][a-z]+(?: [A-Z][a-z]+)*)$");
    private static final Pattern OVERLAY_DUPLICATED_WORD_PREFIX_PATTERN = Pattern.compile("^([A-Z][a-z]+) \\1\\b\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern OVERLAY_SHIP_PREFIX_PATTERN = Pattern.compile("^(?:[A-Z](?:\\.[A-Z])+\\.?\\s+)+");
    private static final Pattern OVERLAY_CHOICE_OPTION_START_PATTERN = Pattern.compile("\\b(?:Can\\s+(?:I|you|we)|Could\\s+(?:I|you|we)|Would\\s+(?:I|you|we)|Will\\s+(?:I|you|we)|Do\\s+(?:I|you|we)|Have\\s+you|Has\\s+it|Are\\s+you|Is\\s+there|How\\s+(?:long|much|many|far|do|does|did|can|are|is|was|were|have|has|will|would)|What(?:'s|\\s+(?:is|are|was|were|do|does|did|about|happened|happens))|Where\\s+(?:is|are|was|were|do|does|did|can|should)|Why\\s+(?:is|are|do|does|did|can|can't)|Who\\s+(?:is|are|was|were)|Which\\s+|hich\\s+|Just\\s+|Tell\\s+me|I\\s+(?:won't|will|would|can|can't|don't|need|want|think|am)|I'll|I'm|I'd|I've|Let's|Maybe|No\\b|Yes\\b)");
    private static final String DIALOGUE_KEY_PREFIX = "dialogue::";
    private static final String NPC_KEY_PREFIX = "npc::";
    private static final String OPTION_KEY_PREFIX = "option::";
    private static final String PLAYER_PLACEHOLDER = "{player}";
    private static final String CONTINUE_MARKER = "to continue";
    private static final String CONTINUE_MARKER_WITH_SPACE = " to continue";
    private static final String CONFIRM_MARKER = "to confirm";
    private static final String CONFIRM_MARKER_WITH_SPACE = " to confirm";
    private static final String CHOOSE_OPTION_MARKER = "to choose option";
    private static final String CHOOSE_OPTION_MARKER_WITH_SPACE = " to choose option";
    private static final int MIN_OVERLAY_RAW_LENGTH = 150;
    private static final int MIN_OVERLAY_DIALOGUE_LENGTH = 15;
    private static final int MIN_PREFIX_MATCH_LENGTH = 5;
    private static final int MIN_OVERLAY_CHOICE_FRAGMENT_LENGTH = 4;
    private static final int MIN_OVERLAY_CHOICE_MERGE_OVERLAP = 5;
    private static final int MIN_OVERLAY_CHOICE_DISPLAY_QUALITY_GAIN = 12;
    private static final int SAME_DIALOGUE_PREFIX_LENGTH = 10;
    private static final long OVERLAY_STABLE_DELAY_MILLIS = 1000L;
    private static final long OVERLAY_OPTIONS_STABLE_DELAY_MILLIS = 2000L;
    private static final long OVERLAY_QUEUE_THROTTLE_MILLIS = 100L;
    private static final long DEBUG_CHAT_LOG_THROTTLE_MILLIS = 1500L;
    private static final long DEBUG_OVERLAY_LOG_THROTTLE_MILLIS = 500L;
    private static final long DEBUG_HUD_LOG_THROTTLE_MILLIS = 1000L;
    private static final long DIALOGUES_LOCAL_HIT_LOG_THROTTLE_MILLIS = 5000L;

    private static final Map<String, Long> DEBUG_LOG_TIMESTAMPS = new ConcurrentHashMap<>();
    private static final WynnSharedDictionaryService SHARED_DICTIONARY_SERVICE = WynnSharedDictionaryService.getInstance();

    private static volatile DialogueCandidate currentCandidate;
    private static volatile String lastOverlayPreparedDialogue = "";
    private static volatile boolean overlayDialogueActive;
    private static volatile boolean overlayDialogueQueued;
    private static volatile long lastOverlayChangedAt;
    private static volatile long lastOverlayObservedAt;
    private static volatile long lastOverlayQueuedAt;
    private static volatile long lastOverlayOptionsChangedAt;
    private static volatile String lastOverlayObservedOptionsSignature = "";
    private static volatile String lastOverlayQueuedOptionsSignature = "";
    private static volatile String lastOverlaySourcePlain = "";
    private static volatile OverlayChoiceOptionsState overlayChoiceOptionsState;
    private static volatile String lastPresentedPayload = "";
    private static volatile PresentedDialogueState lastPresentedState;
    private static final Set<String> refreshedDialogueKeysThisHold = ConcurrentHashMap.newKeySet();

    private WynnDialogueTranslationSupport() {
    }

    public static void init() {
        SHARED_DICTIONARY_SERVICE.loadAll();
        WynnDialogueHudRenderer.init();
    }

    public static boolean isTranslationFeatureEnabled() {
        WynnCraftConfig.NpcDialogueConfig config = getDialogueConfig();
        return config != null && config.enabled;
    }

    public static boolean hasConfiguredRoute() {
        ModConfig config = Translate_AllinOne.getConfig();
        return config != null
                && config.providerManager != null
                && ProviderRouteResolver.resolve(config, ProviderRouteResolver.Route.WYNN_NPC_DIALOGUE) != null;
    }

    public static String getTargetLanguage() {
        ModConfig config = Translate_AllinOne.getConfig();
        if (config == null || config.wynnCraft == null
                || config.wynnCraft.target_language == null
                || config.wynnCraft.target_language.isBlank()) {
            return WynnCraftConfig.DEFAULT_TARGET_LANGUAGE;
        }
        return config.wynnCraft.target_language.trim();
    }

    public static void traceChatEntry(Text message) {
        if (!isDebugEnabled()) {
            return;
        }

        String raw = message == null ? "" : message.getString();
        String plain = sanitizePlainText(raw);
        String normalized = normalizeChatDialogueCandidate(plain);
        if (!looksLikeChatDialogueCandidate(normalized)) {
            return;
        }
        throttledDevLog(
                "chat_entry",
                DEBUG_CHAT_LOG_THROTTLE_MILLIS,
                "chat_entry enabled={} ready={} rawLen={} rawPreview=\"{}\" normalizedPreview=\"{}\"",
                isTranslationFeatureEnabled(),
                LifecycleEventManager.isReadyForTranslation,
                raw.length(),
                describeForLog(raw),
                describeForLog(normalized)
        );
    }

    public static void traceOverlayEntry(Text message) {
        if (!isDebugEnabled()) {
            return;
        }

        String raw = message == null ? "" : message.getString();
        String filtered = filterReadableOverlayText(raw);
        throttledDevLog(
                "overlay_entry",
                DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                "overlay_entry enabled={} ready={} rawLen={} filteredLen={} rawPreview=\"{}\" filteredPreview=\"{}\"",
                isTranslationFeatureEnabled(),
                LifecycleEventManager.isReadyForTranslation,
                raw.length(),
                filtered.length(),
                describeForLog(raw),
                describeForLog(filtered)
        );
    }

    public static void handleChatMessage(Text message) {
        if (message == null) {
            return;
        }

        String raw = message.getString();
        String plain = sanitizePlainText(raw);
        String dialogueCandidate = normalizeChatDialogueCandidate(plain);
        if (!canProcessIncomingText()) {
            if (isDebugEnabled() && looksLikeChatDialogueCandidate(dialogueCandidate)) {
                throttledDevLog(
                        "chat_ignored_state",
                        DEBUG_CHAT_LOG_THROTTLE_MILLIS,
                        "chat_ignored reason={} normalizedPreview=\"{}\"",
                        !isTranslationFeatureEnabled() ? "feature_disabled" : "not_ready",
                        describeForLog(dialogueCandidate)
                );
            }
            return;
        }
        Matcher matcher = CHAT_DIALOGUE_PATTERN.matcher(dialogueCandidate);
        if (!matcher.matches()) {
            if (isDebugEnabled() && looksLikeChatDialogueCandidate(dialogueCandidate)) {
                throttledDevLog(
                        "chat_pattern_miss",
                        DEBUG_CHAT_LOG_THROTTLE_MILLIS,
                        "chat_ignored reason=pattern_miss normalizedPreview=\"{}\"",
                        describeForLog(dialogueCandidate)
                );
            }
            return;
        }

        String pageInfo = "[" + matcher.group(1).trim() + "]";
        String npcName = normalizeDisplayText(matcher.group(2));
        String dialogue = normalizeDisplayText(matcher.group(3));
        if (dialogue.isBlank()) {
            return;
        }

        throttledDevLog(
                "chat_matched",
                DEBUG_CHAT_LOG_THROTTLE_MILLIS,
                "chat_matched rawPreview=\"{}\" normalizedPreview=\"{}\" page={} npc=\"{}\" dialogue=\"{}\"",
                describeForLog(plain),
                describeForLog(dialogueCandidate),
                pageInfo,
                escapeForLog(npcName),
                escapeForLog(dialogue)
        );
        registerCandidate(new DialogueCandidate(System.nanoTime(), pageInfo, npcName, dialogue, false, "", ""));
        queueNpcTranslationIfNeeded(npcName);
        queueDialogueTranslationIfPossible(dialogue, false);
        refreshCurrentDialogueDisplay();
    }

    public static void handleOverlayMessage(Text message) {
        if (message == null) {
            return;
        }

        String raw = message.getString();
        if (raw == null) {
            return;
        }

        if (!canProcessIncomingText()) {
            if (isDebugEnabled()) {
                throttledDevLog(
                        "overlay_ignored_state",
                        DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                        "overlay_ignored reason={} rawLen={} rawPreview=\"{}\"",
                        !isTranslationFeatureEnabled() ? "feature_disabled" : "not_ready",
                        raw.length(),
                        describeForLog(raw)
                );
            }
            return;
        }

        if (raw.length() < MIN_OVERLAY_RAW_LENGTH) {
            throttledDevLog(
                    "overlay_raw_too_short",
                    DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                "overlay_ignored reason=raw_too_short rawLen={} rawPreview=\"{}\"",
                    raw.length(),
                    describeForLog(raw)
            );
            return;
        }

        String sourcePlain = sanitizePlainText(raw);
        String readableText = filterReadableOverlayText(raw);
        List<String> readableSegments = filterReadableOverlayTextSegments(raw);
        boolean shouldTranslateOptions = shouldTranslateNpcOptions();
        if (!shouldTranslateOptions) {
            clearOverlayChoiceOptionsTracking();
        }
        throttledDevLog(
                "overlay_observed",
                DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                "overlay_observed rawLen={} filteredLen={} rawPreview=\"{}\" filteredPreview=\"{}\"",
                raw.length(),
                readableText.length(),
                describeForLog(raw),
                describeForLog(readableText)
        );
        OverlayReadableParse readableParse = tryFontBasedOverlayParse(message, readableText, readableSegments, shouldTranslateOptions);
        if (readableParse == null) {
            readableParse = parseReadableOverlayText(
                    readableText,
                    resolveOverlayParseCandidate(readableText),
                    readableSegments
            );
        }
        if (!readableParse.matched()) {
            throttledDevLog(
                    readableParse.choicePrompt() ? "overlay_choice_rejected" : "overlay_fallback_rejected",
                    DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                    "{} reason={} filteredPreview=\"{}\"",
                    readableParse.choicePrompt() ? "overlay_choice_rejected" : "overlay_fallback_rejected",
                    readableParse.rejectionReason(),
                    describeForLog(readableText)
            );
            if (shouldTranslateOptions && isOverlayChoiceOptionsTrackingActive()) {
                return;
            }
            if (!readableParse.choicePrompt()) {
                clearOverlayTracking();
            }
            return;
        }
        if (shouldTranslateOptions && shouldTreatAsActiveOverlayChoiceFragment(readableParse, readableSegments)) {
            updateActiveOverlayChoiceOptions(readableParse, readableSegments, sourcePlain);
            return;
        }
        if ("tail_fallback".equals(readableParse.mode())) {
            throttledDevLog(
                    "overlay_fallback_matched",
                    DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                    "overlay_fallback_matched npc=\"{}\" dialogue=\"{}\" filteredPreview=\"{}\"",
                    describeForLog(readableParse.npcName()),
                    describeForLog(readableParse.dialogue()),
                    describeForLog(readableText)
            );
        }
        String npcName = readableParse.npcName();
        String dialogue = readableParse.dialogue();
        String preparedDialogue = prepareDialogueValue(dialogue);
        boolean sameDialogue = isLikelySameOverlayDialogue(preparedDialogue);
        if (!sameDialogue) {
            overlayDialogueQueued = false;
            lastOverlayOptionsChangedAt = 0L;
            lastOverlayObservedOptionsSignature = "";
            lastOverlayQueuedOptionsSignature = "";
            clearOverlayChoiceOptionsTracking();
        }
        dialogue = cleanOverlayDialogueText(dialogue, sameDialogue);
        if (dialogue.isBlank()) {
            throttledDevLog(
                    "overlay_empty_dialogue",
                    DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                    "overlay_ignored reason=empty_dialogue npc=\"{}\"",
                    escapeForLog(npcName)
            );
            return;
        }
        preparedDialogue = prepareDialogueValue(dialogue);
        DialogueCandidate previousCandidate = currentCandidate;
        boolean sameCandidate = isSameOverlayDialogueCandidate(previousCandidate, npcName, dialogue);
        boolean sameDialogueCandidate = sameDialogue && sameCandidate;
        if (sameDialogue && !sameCandidate && overlayDialogueQueued) {
            overlayDialogueQueued = false;
        }
        String previousOptionsText = sameDialogueCandidate ? previousCandidate.optionsText() : "";
        String optionsText = shouldTranslateOptions && readableParse.choicePrompt()
                ? chooseOverlayChoiceOptionsDisplayText(
                        previousOptionsText,
                        stabilizeOverlayChoiceOptionsText(npcName, dialogue, readableParse.optionsText())
                )
                : "";
        if (!shouldTranslateOptions || !readableParse.choicePrompt()) {
            clearOverlayChoiceOptionsTracking();
        }
        long observedNonce = sameCandidate ? previousCandidate.observedNonce() : System.nanoTime();
        long now = System.currentTimeMillis();
        lastOverlayPreparedDialogue = preparedDialogue;
        lastOverlayChangedAt = WynnDialogueQueuePolicy.resolveOverlayChangedAt(sameDialogue, lastOverlayChangedAt, now);
        if (shouldTranslateOptions) {
            updateOverlayOptionsStability(optionsText, now);
        } else {
            clearOverlayOptionsQueueTracking();
        }
        lastOverlayObservedAt = now;
        lastOverlaySourcePlain = sourcePlain;
        overlayDialogueActive = true;

        throttledDevLog(
                "overlay_matched",
                DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                "overlay_matched mode={} continueIndex={} npc=\"{}\" dialogue=\"{}\" sameDialogue={}",
                readableParse.mode(),
                readableParse.promptIndex(),
                describeForLog(npcName),
                describeForLog(dialogue),
                sameDialogue
        );
        registerCandidate(new DialogueCandidate(observedNonce, "", npcName, dialogue, true, sourcePlain, optionsText));
        queueNpcTranslationIfNeeded(npcName);
        refreshCurrentDialogueDisplay();
    }

    public static void tick() {
        retireExpiredPresentationIfNeeded(System.currentTimeMillis());
        if (!canProcessIncomingText()) {
            return;
        }

        maybeQueueStableOverlayDialogue();
        refreshCurrentDialogueDisplay();
        maybeCopyCurrentPresentedTextDebug();
    }

    public static void resetSession() {
        currentCandidate = null;
        clearOverlayTracking();
        clearOverlayChoiceOptionsTracking();
        lastPresentedPayload = "";
        lastPresentedState = null;
        refreshedDialogueKeysThisHold.clear();
        WynnDialogueHudRenderer.clear();
    }

    public static void onCacheTranslationsUpdated(Map<String, String> translations) {
        if (translations == null || translations.isEmpty()) {
            return;
        }

        PresentedDialogueState presentedState = lastPresentedState;
        long capturedNonce = presentedState == null ? 0L : presentedState.observedNonce();
        Map<String, String> snapshot = Map.copyOf(translations);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> applyCacheTranslationsToHud(snapshot, capturedNonce));
            return;
        }
        applyCacheTranslationsToHud(snapshot, capturedNonce);
    }

    public static String extractTranslatableValue(String cacheKey) {
        if (cacheKey == null) {
            return "";
        }
        if (cacheKey.startsWith(DIALOGUE_KEY_PREFIX)) {
            return cacheKey.substring(DIALOGUE_KEY_PREFIX.length());
        }
        if (cacheKey.startsWith(NPC_KEY_PREFIX)) {
            return cacheKey.substring(NPC_KEY_PREFIX.length());
        }
        if (cacheKey.startsWith(OPTION_KEY_PREFIX)) {
            return cacheKey.substring(OPTION_KEY_PREFIX.length());
        }
        return cacheKey;
    }

    private static boolean canProcessIncomingText() {
        return isTranslationFeatureEnabled() && LifecycleEventManager.isReadyForTranslation;
    }

    private static WynnCraftConfig.NpcDialogueConfig getDialogueConfig() {
        ModConfig config = Translate_AllinOne.getConfig();
        if (config == null || config.wynnCraft == null) {
            return null;
        }
        return config.wynnCraft.npc_dialogue;
    }

    static boolean isDebugEnabled() {
        WynnCraftConfig.NpcDialogueConfig config = getDialogueConfig();
        return config != null && config.debug != null && config.debug.enabled;
    }

    private static WynnDialogueTextCache cache() {
        return WynnDialogueTextCache.getInstance();
    }

    static boolean shouldShowOriginal(WynnCraftConfig.KeybindingMode mode, boolean isKeyPressed) {
        return WynnDialogueDisplayModeSupport.shouldShowOriginal(mode, isKeyPressed);
    }

    static boolean shouldRenderTranslatedText(WynnCraftConfig.NpcDialogueConfig config, boolean isKeyPressed) {
        return WynnDialogueDisplayModeSupport.shouldRenderTranslatedText(config, resolveSharedKeybindingMode(), isKeyPressed);
    }

    static boolean shouldRequestTranslations(WynnCraftConfig.NpcDialogueConfig config, boolean isKeyPressed) {
        return WynnDialogueDisplayModeSupport.shouldRequestTranslations(config, resolveSharedKeybindingMode(), isKeyPressed);
    }

    private static WynnCraftConfig.KeybindingMode resolveSharedKeybindingMode() {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getSharedHotkeyConfig();
        if (trackerConfig == null || trackerConfig.keybinding == null || trackerConfig.keybinding.mode == null) {
            return WynnCraftConfig.KeybindingMode.DISABLED;
        }
        return trackerConfig.keybinding.mode;
    }

    private static WynnCraftConfig.WynntilsTaskTrackerConfig getSharedHotkeyConfig() {
        ModConfig config = Translate_AllinOne.getConfig();
        if (config == null || config.wynnCraft == null) {
            return null;
        }
        return config.wynnCraft.wynntils_task_tracker;
    }

    private static boolean isSharedTranslationHotkeyPressed() {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getSharedHotkeyConfig();
        return trackerConfig != null
                && trackerConfig.keybinding != null
                && KeybindingManager.isPressed(trackerConfig.keybinding.binding);
    }

    private static boolean isSharedRefreshHotkeyPressed() {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getSharedHotkeyConfig();
        return trackerConfig != null
                && trackerConfig.keybinding != null
                && KeybindingManager.isPressed(trackerConfig.keybinding.refreshBinding);
    }

    private static boolean isDialoguesLocalHitLoggingEnabled() {
        WynnCraftConfig.NpcDialogueConfig config = getDialogueConfig();
        return config != null
                && ((config.debug != null && config.debug.log_dialogues_local_hits)
                || config.log_dialogues_local_hits);
    }

    static void devLog(String message, Object... args) {
        if (!isDebugEnabled()) {
            return;
        }
        Translate_AllinOne.LOGGER.info("[WynnDialogue] " + message, args);
    }

    static void throttledDevLog(String key, long throttleMillis, String message, Object... args) {
        if (!isDebugEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastAt = DEBUG_LOG_TIMESTAMPS.get(key);
        if (lastAt != null && now - lastAt < throttleMillis) {
            return;
        }

        DEBUG_LOG_TIMESTAMPS.put(key, now);
        Translate_AllinOne.LOGGER.info("[WynnDialogue] " + message, args);
    }

    private static void throttledDialoguesLocalHitLog(String match, String input, String output, String context) {
        if (!isDialoguesLocalHitLoggingEnabled()) {
            return;
        }

        String normalizedInput = normalizeDisplayText(input);
        String logKey = "dialogues_local_hit:" + match + ':' + Integer.toHexString(normalizedInput.hashCode());
        long now = System.currentTimeMillis();
        Long lastAt = DEBUG_LOG_TIMESTAMPS.get(logKey);
        if (lastAt != null && now - lastAt < DIALOGUES_LOCAL_HIT_LOG_THROTTLE_MILLIS) {
            return;
        }

        DEBUG_LOG_TIMESTAMPS.put(logKey, now);
        Translate_AllinOne.LOGGER.info(
                "[WynnDialogue] dialogues_local_hit match={} context={} input=\"{}\" output=\"{}\"",
                match,
                context,
                describeForLog(input),
                describeForLog(output)
        );
    }

    private static void throttledDialoguesLocalMissLog(String match, String input, String context, String detail) {
        if (!isDialoguesLocalHitLoggingEnabled()) {
            return;
        }

        String normalizedInput = normalizeDisplayText(input);
        if (normalizedInput.isBlank()) {
            return;
        }

        String logKey = "dialogues_local_miss:" + match + ':' + context + ':' + Integer.toHexString(normalizedInput.hashCode());
        long now = System.currentTimeMillis();
        Long lastAt = DEBUG_LOG_TIMESTAMPS.get(logKey);
        if (lastAt != null && now - lastAt < DIALOGUES_LOCAL_HIT_LOG_THROTTLE_MILLIS) {
            return;
        }

        DEBUG_LOG_TIMESTAMPS.put(logKey, now);
        Translate_AllinOne.LOGGER.info(
                "[WynnDialogue] dialogues_local_miss match={} context={} input=\"{}\" normalized=\"{}\" detail=\"{}\"",
                match,
                context,
                describeForLog(input),
                describeForLog(normalizedInput),
                describeForLog(detail)
        );
    }

    private static void registerCandidate(DialogueCandidate candidate) {
        currentCandidate = candidate;
        if (candidate != null) {
            throttledDevLog(
                "candidate_registered_" + (candidate.overlaySource() ? "overlay" : "chat"),
                candidate.overlaySource() ? DEBUG_OVERLAY_LOG_THROTTLE_MILLIS : DEBUG_CHAT_LOG_THROTTLE_MILLIS,
                "candidate_registered source={} page={} npc=\"{}\" dialogue=\"{}\" options=\"{}\"",
                candidate.overlaySource() ? "overlay" : "chat",
                candidate.pageInfo(),
                describeForLog(candidate.npcName()),
                describeForLog(candidate.dialogue()),
                describeForLog(candidate.optionsText())
            );
        }
    }

    private static DialogueCandidate resolveOverlayParseCandidate(String readableText) {
        DialogueCandidate candidate = currentCandidate;
        if (candidate != null && !isPollutedOverlayChoiceDialogue(candidate.dialogue())) {
            return candidate;
        }

        PresentedDialogueState presentedState = lastPresentedState;
        if (presentedState == null || System.currentTimeMillis() > presentedState.displayUntilEpochMillis()) {
            return candidate;
        }
        if (readableText == null || !readableText.contains(CHOOSE_OPTION_MARKER)) {
            return candidate;
        }
        return new DialogueCandidate(
                presentedState.observedNonce(),
                presentedState.pageInfo(),
                presentedState.originalNpcName(),
                presentedState.originalDialogue(),
                true,
                "",
                presentedState.originalOptionsText()
        );
    }

    private static boolean isPollutedOverlayChoiceDialogue(String dialogue) {
        String normalized = normalizeDisplayText(dialogue);
        return normalized.contains(CHOOSE_OPTION_MARKER) || normalized.contains(CONFIRM_MARKER + " " + CHOOSE_OPTION_MARKER);
    }

    private static boolean isSameOverlayDialogueCandidate(DialogueCandidate candidate, String npcName, String dialogue) {
        return candidate != null
                && candidate.overlaySource()
                && Objects.equals(candidate.npcName(), npcName == null ? "" : npcName)
                && Objects.equals(prepareDialogueValue(candidate.dialogue()), prepareDialogueValue(dialogue));
    }

    private static void refreshCurrentDialogueDisplay() {
        retireExpiredPresentationIfNeeded(System.currentTimeMillis());
        DialogueCandidate candidate = currentCandidate;
        if (candidate == null) {
            return;
        }

        WynnCraftConfig.NpcDialogueConfig dialogueConfig = getDialogueConfig();
        boolean hotkeyPressed = isSharedTranslationHotkeyPressed();
        boolean shouldRenderTranslated = shouldRenderTranslatedText(dialogueConfig, hotkeyPressed);
        boolean shouldRequestTranslations = shouldRequestTranslations(dialogueConfig, hotkeyPressed);
        boolean shouldShowOptions = shouldTranslateNpcOptions();
        maybeForceRefreshCurrentCandidate(candidate);

        boolean allowDisplayQueue = WynnDialogueQueuePolicy.shouldAllowDisplayQueue(
                candidate != null && candidate.overlaySource(),
                shouldRequestTranslations
        );
        DialogueDisplayState dialogueState = shouldRenderTranslated
                ? resolveDialogueDisplayState(candidate.dialogue(), candidate.overlaySource(), allowDisplayQueue)
                : DialogueDisplayState.of(candidate.dialogue(), false, "");
        DialogueDisplayState optionsState = shouldShowOptions
                ? shouldRenderTranslated
                        ? resolveOptionsDisplayState(candidate.optionsText(), allowDisplayQueue)
                        : DialogueDisplayState.of(candidate.optionsText(), false, "")
                : DialogueDisplayState.of("", false, "");
        boolean meaningfullyTranslated = shouldRenderTranslated
                && !dialogueState.pending()
                && isMeaningfullyTranslated(candidate.dialogue(), dialogueState.displayText());
        if (shouldDelayShortOverlayFallback(candidate, meaningfullyTranslated)) {
            return;
        }

        String displayDialogue = dialogueState.displayText();
        String displayOptionsText = optionsState.displayText();
        String originalOptionsText = shouldShowOptions ? candidate.optionsText() : "";

        String translatedNpcName = shouldRenderTranslated
                ? resolveNpcName(candidate.npcName(), shouldRequestTranslations)
                : candidate.npcName();
        String presentedPayload = candidate.observedNonce()
                + "\n" + candidate.pageInfo()
                + "\n" + translatedNpcName
                + "\n" + candidate.dialogue()
                + "\n" + displayDialogue
                + "\n" + dialogueState.pending()
                + "\n" + dialogueState.animationKey()
                + "\n" + originalOptionsText
                + "\n" + displayOptionsText
                + "\n" + optionsState.pending()
                + "\n" + optionsState.animationKey();
        if (presentedPayload.equals(lastPresentedPayload)) {
            return;
        }

        boolean optionsPending = optionsState.pending() && shouldRenderTranslated;
        WynnDialogueHudRenderer.showDialogue(
                candidate.pageInfo(),
                translatedNpcName,
                candidate.dialogue(),
                displayDialogue,
                dialogueState.pending() && shouldRenderTranslated,
                dialogueState.animationKey(),
                displayOptionsText,
                optionsPending,
                optionsState.animationKey()
        );
        lastPresentedState = new PresentedDialogueState(
                candidate.observedNonce(),
                candidate.pageInfo(),
                candidate.npcName(),
                translatedNpcName,
                candidate.dialogue(),
                displayDialogue,
                originalOptionsText,
                displayOptionsText,
                dialogueState.pending() && shouldRenderTranslated,
                dialogueState.animationKey(),
                System.currentTimeMillis() + WynnDialogueHudRenderer.getDisplayDurationMillis()
        );
        throttledDevLog(
                "hud_prepare",
                DEBUG_HUD_LOG_THROTTLE_MILLIS,
                "hud_prepare page={} npc=\"{}\" original=\"{}\" display=\"{}\" pending={} animationKey=\"{}\"",
                candidate.pageInfo(),
                describeForLog(translatedNpcName),
                describeForLog(candidate.dialogue()),
                describeForLog(displayDialogue),
                dialogueState.pending() && shouldRenderTranslated,
                dialogueState.animationKey()
        );
        lastPresentedPayload = presentedPayload;
    }

    private static void maybeCopyCurrentPresentedTextDebug() {
        PresentedDialogueState presentedState = lastPresentedState;
        if (presentedState == null || System.currentTimeMillis() > presentedState.displayUntilEpochMillis()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return;
        }

        List<TooltipTextDebugCopySupport.TextDebugEntry> entries = new ArrayList<>();
        Set<String> keys = ConcurrentHashMap.newKeySet();
        addTextDebugEntry(entries, keys, prepareDialogueValue(presentedState.originalDialogue()));
        for (String optionLine : splitOptionDisplayLines(presentedState.originalOptionsText())) {
            addTextDebugEntry(entries, keys, prepareOptionValue(optionLine));
        }
        TooltipTextDebugCopySupport.maybeCopyTextEntries(entries, "text.translate_allinone.dialogue_debug.copied");
    }

    private static void addTextDebugEntry(
            List<TooltipTextDebugCopySupport.TextDebugEntry> entries,
            Set<String> keys,
            String text
    ) {
        if (entries == null || keys == null || text == null || text.isBlank() || !keys.add(text)) {
            return;
        }

        entries.add(new TooltipTextDebugCopySupport.TextDebugEntry(text, text));
    }

    private static void applyCacheTranslationsToHud(Map<String, String> translations, long capturedNonce) {
        PresentedDialogueState stateBeforeRefresh = lastPresentedState;
        if (capturedNonce != 0L
                && stateBeforeRefresh != null
                && capturedNonce != stateBeforeRefresh.observedNonce()) {
            throttledDevLog(
                    "stale_translation_callback",
                    DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                    "Dropping stale translation callback — nonce mismatch. captured={} current={}",
                    capturedNonce,
                    stateBeforeRefresh.observedNonce()
            );
            return;
        }

        refreshCurrentDialogueDisplay();

        PresentedDialogueState presentedState = lastPresentedState;
        if (presentedState == null || System.currentTimeMillis() > presentedState.displayUntilEpochMillis()) {
            return;
        }

        String updatedNpcName = presentedState.displayedNpcName();
        String updatedDialogue = presentedState.displayedDialogue();
        boolean shouldShowOptions = shouldTranslateNpcOptions();
        String updatedOptionsText = shouldShowOptions ? presentedState.displayedOptionsText() : "";
        List<String> presentedOptionKeys = shouldShowOptions
                ? buildOptionCacheKeys(presentedState.originalOptionsText())
                : List.of();
        boolean optionTranslationTouched = false;
        boolean changed = false;
        if (!shouldShowOptions && !presentedState.displayedOptionsText().isBlank()) {
            changed = true;
        }

        for (Map.Entry<String, String> entry : translations.entrySet()) {
            String key = entry.getKey();
            String translation = entry.getValue();
            if (key == null || key.isBlank() || translation == null || translation.isBlank()) {
                continue;
            }

            if (key.startsWith(DIALOGUE_KEY_PREFIX)) {
                String preparedDialogue = key.substring(DIALOGUE_KEY_PREFIX.length());
                if (!prepareDialogueValue(presentedState.originalDialogue()).equals(preparedDialogue)) {
                    continue;
                }

                String restoredTranslation = restorePlayerPlaceholders(translation);
                if (!isMeaningfullyTranslated(presentedState.originalDialogue(), restoredTranslation)) {
                    continue;
                }
                if (!Objects.equals(updatedDialogue, restoredTranslation)) {
                    updatedDialogue = restoredTranslation;
                    changed = true;
                }
                continue;
            }

            if (key.startsWith(OPTION_KEY_PREFIX)) {
                if (shouldShowOptions && presentedOptionKeys.contains(key)) {
                    optionTranslationTouched = true;
                }
                continue;
            }

            if (key.startsWith(NPC_KEY_PREFIX) && shouldTranslateNpcNames()) {
                String preparedNpc = key.substring(NPC_KEY_PREFIX.length());
                if (!prepareNpcValue(presentedState.originalNpcName()).equals(preparedNpc)) {
                    continue;
                }

                String restoredTranslation = restorePlayerPlaceholders(translation);
                if (!isMeaningfullyTranslated(presentedState.originalNpcName(), restoredTranslation)) {
                    continue;
                }
                if (!Objects.equals(updatedNpcName, restoredTranslation)) {
                    updatedNpcName = restoredTranslation;
                    changed = true;
                }
            }
        }

        boolean optionsPending = false;
        String optionsAnimationKey = "";
        if (shouldShowOptions && optionTranslationTouched) {
            DialogueDisplayState optionsState = resolveOptionsDisplayState(presentedState.originalOptionsText(), false);
            if (!Objects.equals(updatedOptionsText, optionsState.displayText())) {
                updatedOptionsText = optionsState.displayText();
                changed = true;
            }
            optionsPending = optionsState.pending();
            optionsAnimationKey = optionsState.animationKey();
        }

        if (!changed) {
            return;
        }

        WynnDialogueHudRenderer.showDialogue(
                presentedState.pageInfo(),
                updatedNpcName,
                presentedState.originalDialogue(),
                updatedDialogue,
                false,
                "",
                updatedOptionsText,
                optionsPending,
                optionsAnimationKey
        );
        PresentedDialogueState refreshedState = new PresentedDialogueState(
                presentedState.observedNonce(),
                presentedState.pageInfo(),
                presentedState.originalNpcName(),
                updatedNpcName,
                presentedState.originalDialogue(),
                updatedDialogue,
                presentedState.originalOptionsText(),
                updatedOptionsText,
                false,
                "",
                System.currentTimeMillis() + WynnDialogueHudRenderer.getDisplayDurationMillis()
        );
        lastPresentedState = refreshedState;

        DialogueCandidate candidate = currentCandidate;
        long payloadNonce = candidate != null
                && Objects.equals(candidate.pageInfo(), presentedState.pageInfo())
                && Objects.equals(candidate.npcName(), presentedState.originalNpcName())
                && Objects.equals(candidate.dialogue(), presentedState.originalDialogue())
                ? candidate.observedNonce()
                : refreshedState.observedNonce();
        lastPresentedPayload = payloadNonce
                + "\n" + presentedState.pageInfo()
                + "\n" + updatedNpcName
                + "\n" + presentedState.originalDialogue()
                + "\n" + updatedDialogue
                + "\nfalse\n"
                + "\n" + presentedState.originalOptionsText()
                + "\n" + updatedOptionsText
                + "\nfalse\n" + optionsAnimationKey;
        throttledDevLog(
                "hud_async_refresh",
                DEBUG_HUD_LOG_THROTTLE_MILLIS,
                "hud_async_refresh page={} npc=\"{}\" original=\"{}\" display=\"{}\"",
                presentedState.pageInfo(),
                describeForLog(updatedNpcName),
                describeForLog(presentedState.originalDialogue()),
                describeForLog(updatedDialogue)
        );
    }

    static void retireExpiredPresentationIfNeeded(long nowMillis) {
        PresentedDialogueState presentedState = lastPresentedState;
        if (presentedState == null || nowMillis <= presentedState.displayUntilEpochMillis()) {
            return;
        }

        DialogueCandidate candidate = currentCandidate;
        if (candidate != null && candidate.observedNonce() == presentedState.observedNonce()) {
            currentCandidate = null;
        }

        lastPresentedState = null;
        lastPresentedPayload = "";
        WynnDialogueHudRenderer.clear();
    }

    static void installPresentationStateForTest(long displayUntilEpochMillis, boolean candidateMatchesPresentedState) {
        DialogueCandidate candidate = new DialogueCandidate(42L, "[1/1]", "Test NPC", "Test dialogue", false, "", "");
        currentCandidate = candidate;
        lastPresentedState = new PresentedDialogueState(
                candidateMatchesPresentedState ? candidate.observedNonce() : candidate.observedNonce() + 1L,
                candidate.pageInfo(),
                candidate.npcName(),
                candidate.npcName(),
                candidate.dialogue(),
                "Translated dialogue",
                "",
                "",
                false,
                "",
                displayUntilEpochMillis
        );
        lastPresentedPayload = "test";
    }

    static boolean hasCurrentCandidateForTest() {
        return currentCandidate != null;
    }

    static boolean hasPresentedDialogueStateForTest() {
        return lastPresentedState != null;
    }

    private static boolean shouldDelayShortOverlayFallback(DialogueCandidate candidate, boolean meaningfullyTranslated) {
        return candidate.overlaySource()
                && !meaningfullyTranslated
                && candidate.dialogue() != null
                && candidate.dialogue().length() < MIN_OVERLAY_DIALOGUE_LENGTH;
    }

    private static boolean looksLikeCompleteOverlayDialogue(String dialogue) {
        if (dialogue == null) {
            return false;
        }

        String trimmed = dialogue.trim();
        if (trimmed.length() < MIN_OVERLAY_DIALOGUE_LENGTH) {
            return false;
        }
        if (trimmed.endsWith("...") || trimmed.endsWith("…")) {
            return true;
        }

        char lastChar = trimmed.charAt(trimmed.length() - 1);
        return switch (lastChar) {
            case '.', '!', '?', '"', '\'', '”', '’', '。', '！', '？' -> true;
            default -> false;
        };
    }

    private static void maybeForceRefreshCurrentCandidate(DialogueCandidate candidate) {
        if (candidate == null || !isSharedRefreshHotkeyPressed()) {
            refreshedDialogueKeysThisHold.clear();
            return;
        }

        Set<String> refreshKeys = buildForceRefreshKeys(candidate, lastPresentedState, System.currentTimeMillis());

        if (refreshKeys.isEmpty()) {
            return;
        }

        if (!refreshedDialogueKeysThisHold.addAll(refreshKeys)) {
            return;
        }

        int refreshedCount = cache().forceRefresh(refreshKeys);
        if (refreshedCount > 0) {
            throttledDevLog(
                    "dialogue_force_refresh",
                    DEBUG_HUD_LOG_THROTTLE_MILLIS,
                    "dialogue_force_refresh count={} npc=\"{}\" dialogue=\"{}\"",
                    refreshedCount,
                    describeForLog(candidate.npcName()),
                    describeForLog(candidate.dialogue())
            );
        }
    }

    private static void maybeQueueStableOverlayDialogue() {
        DialogueCandidate candidate = currentCandidate;
        if (candidate == null || !candidate.overlaySource()) {
            return;
        }

        if (!overlayDialogueActive || candidate.dialogue().length() < MIN_OVERLAY_DIALOGUE_LENGTH || !hasConfiguredRoute()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!WynnDialogueQueuePolicy.hasMetOverlayStableDelay(lastOverlayChangedAt, now, OVERLAY_STABLE_DELAY_MILLIS)) {
            return;
        }
        if (now - lastOverlayQueuedAt < OVERLAY_QUEUE_THROTTLE_MILLIS) {
            return;
        }
        if (!looksLikeCompleteOverlayDialogue(candidate.dialogue())) {
            throttledDevLog(
                    "overlay_queue_incomplete_sentence",
                    DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                    "overlay_queue_skipped reason=incomplete_sentence dialogue=\"{}\"",
                    describeForLog(candidate.dialogue())
            );
            return;
        }

        boolean queuedAny = false;
        if (!overlayDialogueQueued) {
            if (queueDialogueTranslationIfPossible(candidate.dialogue(), true)) {
                overlayDialogueQueued = true;
                queuedAny = true;
                throttledDevLog(
                        "overlay_queue_ready",
                        DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                        "overlay_queue_ready npc=\"{}\" dialogue=\"{}\" stableMs={}",
                        describeForLog(candidate.npcName()),
                        describeForLog(candidate.dialogue()),
                        now - lastOverlayChangedAt
                );
            }
        }

        String optionsSignature = buildOptionsSignature(candidate.optionsText());
        if (!shouldTranslateNpcOptions()) {
            clearOverlayOptionsQueueTracking();
        } else if (!optionsSignature.isBlank()
                && !Objects.equals(lastOverlayObservedOptionsSignature, optionsSignature)) {
            updateOverlayOptionsStability(candidate.optionsText(), now);
        }
        if (shouldTranslateNpcOptions()
                && !optionsSignature.isBlank()
                && hasStableUnqueuedOverlayOptions(
                optionsSignature,
                lastOverlayObservedOptionsSignature,
                lastOverlayQueuedOptionsSignature,
                lastOverlayOptionsChangedAt,
                now
        )) {
            if (queueOptionsTranslationIfPossible(candidate.optionsText())) {
                lastOverlayQueuedOptionsSignature = optionsSignature;
                queuedAny = true;
                throttledDevLog(
                        "overlay_options_queue_ready",
                        DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                        "overlay_options_queue_ready npc=\"{}\" optionCount={} stableMs={}",
                        describeForLog(candidate.npcName()),
                        splitOptionDisplayLines(candidate.optionsText()).size(),
                        now - lastOverlayOptionsChangedAt
                );
            }
        }

        if (queuedAny) {
            lastOverlayQueuedAt = now;
        }
    }

    private static Set<String> buildForceRefreshKeys(
            DialogueCandidate candidate,
            PresentedDialogueState presentedState,
            long nowMillis
    ) {
        Set<String> refreshKeys = new LinkedHashSet<>();
        addCandidateForceRefreshKeys(refreshKeys, candidate);
        if (shouldUsePresentedStateForForceRefresh(candidate, presentedState, nowMillis)) {
            addPresentedStateForceRefreshKeys(refreshKeys, presentedState);
        }
        return refreshKeys;
    }

    private static boolean shouldUsePresentedStateForForceRefresh(
            DialogueCandidate candidate,
            PresentedDialogueState presentedState,
            long nowMillis
    ) {
        if (presentedState == null || nowMillis > presentedState.displayUntilEpochMillis()) {
            return false;
        }
        if (candidate == null) {
            return true;
        }
        return candidate.observedNonce() == presentedState.observedNonce();
    }

    private static void addCandidateForceRefreshKeys(Set<String> refreshKeys, DialogueCandidate candidate) {
        if (candidate == null) {
            return;
        }
        addDialogueForceRefreshKey(refreshKeys, candidate.dialogue());
        addNpcForceRefreshKey(refreshKeys, candidate.npcName());
        addOptionForceRefreshKeys(refreshKeys, candidate.optionsText());
    }

    private static void addPresentedStateForceRefreshKeys(
            Set<String> refreshKeys,
            PresentedDialogueState presentedState
    ) {
        if (presentedState == null) {
            return;
        }
        addDialogueForceRefreshKey(refreshKeys, presentedState.originalDialogue());
        addNpcForceRefreshKey(refreshKeys, presentedState.originalNpcName());
        addOptionForceRefreshKeys(refreshKeys, presentedState.originalOptionsText());
    }

    private static void addDialogueForceRefreshKey(Set<String> refreshKeys, String dialogue) {
        String preparedDialogue = prepareDialogueValue(dialogue);
        if (!preparedDialogue.isBlank()
                && !SHARED_DICTIONARY_SERVICE.hasPreparedDialogueTranslation(preparedDialogue)) {
            refreshKeys.add(DIALOGUE_KEY_PREFIX + preparedDialogue);
        }
    }

    private static void addNpcForceRefreshKey(Set<String> refreshKeys, String npcName) {
        if (shouldTranslateNpcNames()
                && npcName != null
                && !npcName.isBlank()
                && !SHARED_DICTIONARY_SERVICE.hasPreparedNpcTranslation(prepareNpcValue(npcName))) {
            refreshKeys.add(buildNpcCacheKey(npcName));
        }
    }

    private static void addOptionForceRefreshKeys(Set<String> refreshKeys, String optionsText) {
        if (!shouldTranslateNpcOptions()) {
            return;
        }
        refreshKeys.addAll(buildOptionCacheKeys(optionsText));
    }

    private static boolean queueDialogueTranslationIfPossible(String dialogue, boolean allowLocalPrefixShortCircuit) {
        WynnCraftConfig.NpcDialogueConfig dialogueConfig = getDialogueConfig();
        boolean hotkeyPressed = isSharedTranslationHotkeyPressed();
        if (!shouldRequestTranslations(dialogueConfig, hotkeyPressed)) {
            throttledDevLog(
                    "queue_dialogue_hotkey_blocked",
                    DEBUG_HUD_LOG_THROTTLE_MILLIS,
                    "queue_skipped type=dialogue reason=hotkey_not_pressed mode={} dialogue=\"{}\"",
                    resolveSharedKeybindingMode(),
                    describeForLog(dialogue)
            );
            return false;
        }

        String preparedDialogue = prepareDialogueValue(dialogue);
        WynnSharedDictionaryService.LookupResult exactLookup =
                SHARED_DICTIONARY_SERVICE.lookupPreparedDialogue(preparedDialogue);
        if (exactLookup.hit()) {
            throttledDialoguesLocalHitLog(
                    "exact",
                    preparedDialogue,
                    restorePlayerPlaceholders(exactLookup.translation()),
                    "queue_skip"
            );
            return true;
        }
        throttledDialoguesLocalMissLog("exact", preparedDialogue, "queue", "prepared dialogue was not found in dialogues dictionary");
        if (allowLocalPrefixShortCircuit) {
            WynnSharedDictionaryService.LookupResult prefixLookup =
                    SHARED_DICTIONARY_SERVICE.lookupPreparedDialogueByPrefix(preparedDialogue);
            if (prefixLookup.hit()) {
                throttledDialoguesLocalHitLog(
                        "prefix",
                        preparedDialogue,
                        restorePlayerPlaceholders(prefixLookup.translation()),
                        "queue_skip"
                );
                return true;
            }
            throttledDialoguesLocalMissLog("prefix", preparedDialogue, "queue", "no prepared dialogue prefix entry matched");
        }

        if (!hasConfiguredRoute()) {
            throttledDevLog(
                    "queue_dialogue_no_route",
                    DEBUG_HUD_LOG_THROTTLE_MILLIS,
                    "queue_skipped type=dialogue reason=no_route dialogue=\"{}\"",
                    describeForLog(dialogue)
            );
            return false;
        }
        cache().lookupOrQueue(buildDialogueCacheKey(dialogue));
        throttledDevLog(
                "queue_dialogue",
                DEBUG_HUD_LOG_THROTTLE_MILLIS,
                "queue_dialogue key=\"{}\"",
                describeForLog(buildDialogueCacheKey(dialogue))
        );
        return true;
    }

    private static boolean queueOptionsTranslationIfPossible(String optionsText) {
        if (!shouldTranslateNpcOptions()) {
            return false;
        }
        List<String> optionKeys = buildOptionCacheKeys(optionsText);
        if (optionKeys.isEmpty()) {
            return false;
        }

        WynnCraftConfig.NpcDialogueConfig dialogueConfig = getDialogueConfig();
        boolean hotkeyPressed = isSharedTranslationHotkeyPressed();
        if (!shouldRequestTranslations(dialogueConfig, hotkeyPressed)) {
            throttledDevLog(
                    "queue_options_hotkey_blocked",
                    DEBUG_HUD_LOG_THROTTLE_MILLIS,
                    "queue_skipped type=option reason=hotkey_not_pressed mode={} optionCount={}",
                    resolveSharedKeybindingMode(),
                    optionKeys.size()
            );
            return false;
        }

        if (!hasConfiguredRoute()) {
            throttledDevLog(
                    "queue_options_no_route",
                    DEBUG_HUD_LOG_THROTTLE_MILLIS,
                    "queue_skipped type=option reason=no_route optionCount={}",
                    optionKeys.size()
            );
            return false;
        }

        for (String optionKey : optionKeys) {
            cache().lookupOrQueue(optionKey);
        }
        throttledDevLog(
                "queue_options",
                DEBUG_HUD_LOG_THROTTLE_MILLIS,
                "queue_options count={} keys=\"{}\"",
                optionKeys.size(),
                describeForLog(String.join(", ", optionKeys))
        );
        return true;
    }

    private static void updateOverlayOptionsStability(String optionsText, long now) {
        String optionsSignature = buildOptionsSignature(optionsText);
        if (optionsSignature.isBlank()) {
            lastOverlayOptionsChangedAt = 0L;
            lastOverlayObservedOptionsSignature = "";
            return;
        }
        if (Objects.equals(lastOverlayObservedOptionsSignature, optionsSignature)) {
            return;
        }
        lastOverlayObservedOptionsSignature = optionsSignature;
        lastOverlayOptionsChangedAt = now;
    }

    private static boolean hasStableUnqueuedOverlayOptions(
            String optionsSignature,
            String observedOptionsSignature,
            String queuedOptionsSignature,
            long optionsChangedAt,
            long now
    ) {
        return optionsSignature != null
                && !optionsSignature.isBlank()
                && Objects.equals(observedOptionsSignature, optionsSignature)
                && !Objects.equals(queuedOptionsSignature, optionsSignature)
                && WynnDialogueQueuePolicy.hasMetOverlayStableDelay(
                optionsChangedAt,
                now,
                OVERLAY_OPTIONS_STABLE_DELAY_MILLIS
        );
    }

    private static void queueNpcTranslationIfNeeded(String npcName) {
        WynnCraftConfig.NpcDialogueConfig dialogueConfig = getDialogueConfig();
        boolean hotkeyPressed = isSharedTranslationHotkeyPressed();
        if (!shouldTranslateNpcNames() || npcName == null || npcName.isBlank() || !hasConfiguredRoute()) {
            return;
        }
        if (!shouldRequestTranslations(dialogueConfig, hotkeyPressed)) {
            throttledDevLog(
                    "queue_npc_hotkey_blocked",
                    DEBUG_HUD_LOG_THROTTLE_MILLIS,
                    "queue_skipped type=npc reason=hotkey_not_pressed mode={} npc=\"{}\"",
                    resolveSharedKeybindingMode(),
                    describeForLog(npcName)
            );
            return;
        }
        WynnSharedDictionaryService.LookupResult npcLookup =
                SHARED_DICTIONARY_SERVICE.lookupPreparedNpc(prepareNpcValue(npcName));
        if (npcLookup.hit()) {
            throttledDialoguesLocalHitLog(
                    "exact",
                    npcName,
                    npcLookup.translation(),
                    "queue_skip_npc"
            );
            return;
        }
        throttledDialoguesLocalMissLog("npc", npcName, "queue_npc", "prepared NPC name was not found in dialogues dictionary");
        cache().lookupOrQueue(buildNpcCacheKey(npcName));
        throttledDevLog(
                "queue_npc",
                DEBUG_HUD_LOG_THROTTLE_MILLIS,
                "queue_npc key=\"{}\"",
                describeForLog(buildNpcCacheKey(npcName))
        );
    }

    private static String resolveNpcName(String npcName, boolean allowQueue) {
        if (!shouldTranslateNpcNames() || npcName == null || npcName.isBlank()) {
            return npcName == null ? "" : npcName;
        }

        WynnSharedDictionaryService.LookupResult npcLookup =
                SHARED_DICTIONARY_SERVICE.lookupPreparedNpc(prepareNpcValue(npcName));
        if (npcLookup.hit()) {
            throttledDialoguesLocalHitLog("exact", npcName, npcLookup.translation(), "resolve_npc");
            return npcLookup.translation();
        }
        throttledDialoguesLocalMissLog("npc", npcName, "resolve_npc", "prepared NPC name was not found in dialogues dictionary");

        WynnDialogueTextCache.LookupResult lookup = allowQueue && hasConfiguredRoute()
                ? cache().lookupOrQueue(buildNpcCacheKey(npcName))
                : cache().peek(buildNpcCacheKey(npcName));
        if (lookup.status() == WynnDialogueTextCache.TranslationStatus.TRANSLATED
                && lookup.translation() != null
                && !lookup.translation().isBlank()) {
            return restorePlayerPlaceholders(lookup.translation());
        }
        return npcName;
    }

    private static DialogueDisplayState resolveDialogueTranslation(String dialogue, boolean allowPrefixFallback) {
        return resolveDialogueDisplayState(dialogue, allowPrefixFallback, false);
    }

    private static DialogueDisplayState resolveDialogueDisplayState(String dialogue, boolean allowPrefixFallback, boolean allowQueue) {
        if (dialogue == null || dialogue.isBlank()) {
            return DialogueDisplayState.of(dialogue == null ? "" : dialogue, false, "");
        }

        String preparedDialogue = prepareDialogueValue(dialogue);
        WynnSharedDictionaryService.LookupResult exactLookup =
                SHARED_DICTIONARY_SERVICE.lookupPreparedDialogue(preparedDialogue);
        if (exactLookup.hit()) {
            String restoredTranslation = restorePlayerPlaceholders(exactLookup.translation());
            throttledDialoguesLocalHitLog("exact", preparedDialogue, restoredTranslation, "resolve");
            return DialogueDisplayState.of(restoredTranslation, false, "");
        }
        throttledDialoguesLocalMissLog("exact", preparedDialogue, "resolve", "prepared dialogue was not found in dialogues dictionary");

        if (allowPrefixFallback) {
            String prefixTranslation = findDialogueByPrefix(preparedDialogue);
            if (prefixTranslation != null && !prefixTranslation.isBlank()) {
                return DialogueDisplayState.of(prefixTranslation, false, "");
            }

            // Overlay 打字机展开阶段的短半句宁可先显示原文，也不要吃到旧的半句缓存。
            if (dialogue.length() < MIN_OVERLAY_DIALOGUE_LENGTH) {
                return DialogueDisplayState.of(dialogue, false, "");
            }
        }

        WynnDialogueTextCache.LookupResult lookup = allowQueue && hasConfiguredRoute()
                ? cache().lookupOrQueue(buildDialogueCacheKey(dialogue))
                : cache().peek(buildDialogueCacheKey(dialogue));
        if (lookup.status() == WynnDialogueTextCache.TranslationStatus.TRANSLATED
                && lookup.translation() != null
                && !lookup.translation().isBlank()) {
            return DialogueDisplayState.of(restorePlayerPlaceholders(lookup.translation()), false, "");
        }

        if (lookup.status() == WynnDialogueTextCache.TranslationStatus.PENDING
                || lookup.status() == WynnDialogueTextCache.TranslationStatus.IN_PROGRESS) {
            return DialogueDisplayState.of(dialogue, true, buildDialogueCacheKey(dialogue));
        }

        return DialogueDisplayState.of(dialogue, false, "");
    }

    private static DialogueDisplayState resolveOptionsDisplayState(String optionsText, boolean allowQueue) {
        if (!shouldTranslateNpcOptions()) {
            return DialogueDisplayState.of("", false, "");
        }
        List<String> optionLines = splitOptionDisplayLines(optionsText);
        if (optionLines.isEmpty()) {
            return DialogueDisplayState.of("", false, "");
        }

        List<String> displayLines = new ArrayList<>();
        boolean pending = false;
        String animationKey = "";
        for (String optionLine : optionLines) {
            String optionKey = buildOptionCacheKey(optionLine);
            WynnDialogueTextCache.LookupResult lookup = allowQueue && hasConfiguredRoute()
                    ? cache().lookupOrQueue(optionKey)
                    : cache().peek(optionKey);
            if (lookup.status() == WynnDialogueTextCache.TranslationStatus.TRANSLATED
                    && lookup.translation() != null
                    && !lookup.translation().isBlank()) {
                String restoredTranslation = normalizeDisplayText(restorePlayerPlaceholders(lookup.translation()));
                appendOptionDisplayLines(displayLines, optionLine, restoredTranslation);
                continue;
            }

            if (lookup.status() == WynnDialogueTextCache.TranslationStatus.PENDING
                    || lookup.status() == WynnDialogueTextCache.TranslationStatus.IN_PROGRESS) {
                pending = true;
                if (animationKey.isBlank()) {
                    animationKey = optionKey;
                }
            }
            displayLines.add(optionLine);
        }

        return DialogueDisplayState.of(String.join("\n", displayLines), pending, animationKey);
    }

    private static void appendOptionDisplayLines(List<String> displayLines, String optionLine, String translatedLine) {
        if (displayLines == null) {
            return;
        }
        String original = normalizeDisplayText(optionLine);
        if (original.isBlank()) {
            return;
        }

        String translated = normalizeDisplayText(translatedLine);
        if (!translated.isBlank() && isMeaningfullyTranslated(original, translated)) {
            displayLines.add(translated);
        } else {
            displayLines.add(original);
        }
    }

    private static String findDialogueByPrefix(String preparedDialogue) {
        if (preparedDialogue == null || preparedDialogue.length() < MIN_PREFIX_MATCH_LENGTH) {
            return null;
        }

        WynnSharedDictionaryService.LookupResult prefixLookup =
                SHARED_DICTIONARY_SERVICE.lookupPreparedDialogueByPrefix(preparedDialogue);
        if (prefixLookup.hit()) {
            String restoredTranslation = restorePlayerPlaceholders(prefixLookup.translation());
            throttledDialoguesLocalHitLog("prefix", preparedDialogue, restoredTranslation, "resolve");
            return restoredTranslation;
        }
        throttledDialoguesLocalMissLog("prefix", preparedDialogue, "resolve", "no prepared dialogue prefix entry matched");

        Map<String, String> snapshot = cache().snapshotTranslations();
        String bestPreparedMatch = null;
        String bestTranslation = null;
        int bestExtraLength = Integer.MAX_VALUE;

        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(DIALOGUE_KEY_PREFIX)) {
                continue;
            }

            String preparedCandidate = key.substring(DIALOGUE_KEY_PREFIX.length());
            if (preparedCandidate.isBlank()) {
                continue;
            }

            if (preparedCandidate.equals(preparedDialogue)) {
                return restorePlayerPlaceholders(entry.getValue());
            }

            if (!preparedCandidate.startsWith(preparedDialogue)) {
                continue;
            }

            int extraLength = preparedCandidate.length() - preparedDialogue.length();
            if (extraLength < bestExtraLength) {
                bestPreparedMatch = preparedCandidate;
                bestTranslation = entry.getValue();
                bestExtraLength = extraLength;
            }
        }

        if (bestPreparedMatch == null || bestTranslation == null || bestTranslation.isBlank()) {
            return null;
        }

        return cropTranslatedPrefix(bestPreparedMatch, preparedDialogue, restorePlayerPlaceholders(bestTranslation));
    }

    private static String cropTranslatedPrefix(String preparedFull, String preparedPartial, String translatedFull) {
        if (preparedFull == null || preparedPartial == null || translatedFull == null || translatedFull.isBlank()) {
            return translatedFull;
        }
        if (preparedPartial.length() >= preparedFull.length()) {
            return translatedFull;
        }

        double ratio = Math.max(0.05D, Math.min(1.0D, (double) preparedPartial.length() / (double) preparedFull.length()));
        int endIndex = Math.max(1, Math.min(translatedFull.length(), (int) Math.ceil(translatedFull.length() * ratio)));

        if (translatedFull.indexOf(' ') >= 0) {
            while (endIndex < translatedFull.length()
                    && !Character.isWhitespace(translatedFull.charAt(endIndex))
                    && !isBoundaryPunctuation(translatedFull.charAt(endIndex))) {
                endIndex++;
            }
            while (endIndex > 0 && Character.isWhitespace(translatedFull.charAt(endIndex - 1))) {
                endIndex--;
            }
        }

        return translatedFull.substring(0, endIndex).trim();
    }

    private static String cleanOverlayDialogueText(String dialogue, boolean sameDialogue) {
        if (dialogue == null || dialogue.isBlank()) {
            return dialogue == null ? "" : dialogue;
        }

        String cleaned = dialogue.trim();
        if (sameDialogue) {
            cleaned = OVERLAY_DUPLICATED_WORD_PREFIX_PATTERN.matcher(cleaned).replaceFirst("$1 ");
        }

        if (sameDialogue && looksLikeEarlyOverlayFragment(cleaned)) {
            cleaned = OVERLAY_SHIP_PREFIX_PATTERN.matcher(cleaned).replaceFirst("").trim();
        }

        cleaned = cleaned.replaceAll("([A-Za-z])([A-Z][a-z])", "$1 $2");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private static boolean looksLikeEarlyOverlayFragment(String dialogue) {
        if (dialogue == null || dialogue.isBlank()) {
            return false;
        }
        if (dialogue.length() > 32) {
            return false;
        }
        return dialogue.endsWith("C.S.S")
                || dialogue.startsWith("C.S.S ")
                || dialogue.matches("^[A-Za-z ]{1,16}C\\.S\\.S$");
    }

    private static boolean isBoundaryPunctuation(char value) {
        return switch (value) {
            case '.', ',', '!', '?', ';', ':', '"', '\'', '，', '。', '！', '？', '；', '：', '、' -> true;
            default -> false;
        };
    }

    private static boolean shouldTranslateNpcNames() {
        WynnCraftConfig.NpcDialogueConfig config = getDialogueConfig();
        return config != null && config.translate_npc_name;
    }

    private static boolean shouldTranslateNpcOptions() {
        WynnCraftConfig.NpcDialogueConfig config = getDialogueConfig();
        return config != null && config.translate_options;
    }

    private static String buildDialogueCacheKey(String dialogue) {
        return DIALOGUE_KEY_PREFIX + prepareDialogueValue(dialogue);
    }

    private static String buildNpcCacheKey(String npcName) {
        return NPC_KEY_PREFIX + prepareNpcValue(npcName);
    }

    private static List<String> buildOptionCacheKeys(String optionsText) {
        List<String> optionLines = splitOptionDisplayLines(optionsText);
        if (optionLines.isEmpty()) {
            return List.of();
        }

        List<String> optionKeys = new ArrayList<>();
        for (String optionLine : optionLines) {
            String optionKey = buildOptionCacheKey(optionLine);
            if (!optionKey.equals(OPTION_KEY_PREFIX) && !optionKeys.contains(optionKey)) {
                optionKeys.add(optionKey);
            }
        }
        return optionKeys;
    }

    private static String buildOptionsSignature(String optionsText) {
        List<String> optionKeys = buildOptionCacheKeys(optionsText);
        if (optionKeys.isEmpty()) {
            return "";
        }
        return String.join("\n", optionKeys);
    }

    private static String buildOptionCacheKey(String optionText) {
        return OPTION_KEY_PREFIX + prepareOptionValue(optionText);
    }

    private static String prepareDialogueValue(String dialogue) {
        return replacePlayerNameWithPlaceholder(normalizeDisplayText(dialogue));
    }

    private static String prepareNpcValue(String npcName) {
        return normalizeDisplayText(npcName);
    }

    private static String prepareOptionValue(String optionText) {
        return replacePlayerNameWithPlaceholder(normalizeDisplayText(optionText));
    }

    private static List<String> splitOptionDisplayLines(String optionsText) {
        if (optionsText == null || optionsText.isBlank()) {
            return List.of();
        }

        List<String> optionLines = new ArrayList<>();
        String normalized = optionsText.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\\n", -1)) {
            String optionLine = normalizeDisplayText(line);
            if (!optionLine.isBlank()) {
                optionLines.add(optionLine);
            }
        }
        return optionLines;
    }

    private static String replacePlayerNameWithPlaceholder(String value) {
        String playerName = getPlayerName();
        if (value == null || value.isBlank() || playerName.isBlank()) {
            return value == null ? "" : value;
        }
        return value.replace(playerName, PLAYER_PLACEHOLDER);
    }

    private static String restorePlayerPlaceholders(String value) {
        String playerName = getPlayerName();
        if (value == null || value.isBlank() || playerName.isBlank()) {
            return value == null ? "" : value;
        }
        return value.replace(PLAYER_PLACEHOLDER, playerName);
    }

    private static String getPlayerName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.getName() == null) {
            return "";
        }
        return client.player.getName().getString();
    }

    private static String sanitizePlainText(String value) {
        if (value == null) {
            return "";
        }
        return AnimationManager.stripFormatting(value).replace('\u00A0', ' ').trim();
    }

    private static String normalizeDisplayText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = sanitizePlainText(value)
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"');
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private static String filterReadableOverlayText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(value.length());
        boolean pendingSeparator = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if ((current >= 32 && current <= 126) || (current >= 160 && current <= 255)) {
                if (pendingSeparator && shouldInsertOverlayBoundarySpace(builder, current)) {
                    builder.append(' ');
                }
                builder.append(current);
                pendingSeparator = false;
            } else if (builder.length() > 0) {
                pendingSeparator = true;
            }
        }
        return normalizeDisplayText(builder.toString());
    }

    private static List<String> filterReadableOverlayTextSegments(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<String> segments = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if ((current >= 32 && current <= 126) || (current >= 160 && current <= 255)) {
                builder.append(current);
                continue;
            }

            appendReadableOverlaySegment(segments, builder);
        }
        appendReadableOverlaySegment(segments, builder);
        return segments;
    }

    private static void appendReadableOverlaySegment(List<String> segments, StringBuilder builder) {
        if (builder == null || builder.length() == 0) {
            return;
        }

        String segment = normalizeDisplayText(builder.toString());
        builder.setLength(0);
        if (!segment.isBlank()) {
            segments.add(segment);
        }
    }

    private static boolean shouldInsertOverlayBoundarySpace(StringBuilder builder, char nextReadableChar) {
        if (builder == null || builder.length() == 0) {
            return false;
        }

        char previousReadableChar = builder.charAt(builder.length() - 1);
        if (Character.isWhitespace(previousReadableChar) || Character.isWhitespace(nextReadableChar)) {
            return false;
        }
        if (Character.isLetterOrDigit(previousReadableChar) && Character.isLetterOrDigit(nextReadableChar)) {
            return true;
        }
        return isBoundaryPunctuation(previousReadableChar) && Character.isLetterOrDigit(nextReadableChar);
    }

    private static String normalizeChatDialogueCandidate(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }

        String candidate = plain;
        while (true) {
            Matcher matcher = LEADING_TIMESTAMP_PATTERN.matcher(candidate);
            if (!matcher.find()) {
                break;
            }
            candidate = candidate.substring(matcher.end()).trim();
        }
        return candidate;
    }

    private static boolean looksLikeChatDialogueCandidate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        return candidate.startsWith("[") && candidate.contains("/") && candidate.contains(":");
    }

    static OverlayReadableParse parseReadableOverlayTextForTest(
            String readableText,
            String currentNpcName,
            String currentDialogue
    ) {
        return parseReadableOverlayTextForTest(readableText, currentNpcName, currentDialogue, List.of());
    }

    static OverlayReadableParse parseReadableOverlayTextForTest(
            String readableText,
            String currentNpcName,
            String currentDialogue,
            List<String> readableSegments
    ) {
        DialogueCandidate existingCandidate = null;
        if (currentDialogue != null && !currentDialogue.isBlank()) {
            existingCandidate = new DialogueCandidate(
                    0L,
                    "",
                    currentNpcName == null ? "" : currentNpcName,
                    currentDialogue,
                    true,
                    "",
                    ""
            );
        }
        return parseReadableOverlayText(readableText, existingCandidate, readableSegments);
    }

    static List<String> buildOptionCacheKeysForTest(String optionsText) {
        return buildOptionCacheKeys(optionsText);
    }

    static String resolveOptionsDisplayTextForTest(String optionsText, Map<String, String> translations) {
        return resolveOptionsDisplayText(optionsText, translations);
    }

    static boolean hasStableUnqueuedOverlayOptionsForTest(
            String optionsText,
            String observedOptionsText,
            String queuedOptionsText,
            long optionsChangedAt,
            long now
    ) {
        return hasStableUnqueuedOverlayOptions(
                buildOptionsSignature(optionsText),
                buildOptionsSignature(observedOptionsText),
                buildOptionsSignature(queuedOptionsText),
                optionsChangedAt,
                now
        );
    }

    private static String resolveOptionsDisplayText(String optionsText, Map<String, String> translations) {
        List<String> optionLines = splitOptionDisplayLines(optionsText);
        if (optionLines.isEmpty()) {
            return "";
        }

        Map<String, String> safeTranslations = translations == null ? Map.of() : translations;
        List<String> displayLines = new ArrayList<>();
        for (String optionLine : optionLines) {
            String translated = safeTranslations.get(buildOptionCacheKey(optionLine));
            String restoredTranslation = translated == null || translated.isBlank()
                    ? ""
                    : normalizeDisplayText(restorePlayerPlaceholders(translated));
            appendOptionDisplayLines(displayLines, optionLine, restoredTranslation);
        }
        return String.join("\n", displayLines);
    }

    private static OverlayReadableParse tryFontBasedOverlayParse(
            Text message,
            String readableText,
            List<String> readableSegments,
            boolean shouldTranslateOptions
    ) {
        WynnDialogueFontExtractor.FontExtractionResult fontResult = WynnDialogueFontExtractor.extract(message);
        if (!fontResult.matched()) {
            return null;
        }

        String body = fontResult.dialogue();
        if (body.isBlank()) {
            return null;
        }

        String npcName = normalizeDisplayText(fontResult.npcName());
        String dialogue = normalizeDisplayText(body);

        if (dialogue.length() < MIN_OVERLAY_DIALOGUE_LENGTH) {
            return null;
        }

        String optionsText = "";
        if (shouldTranslateOptions) {
            optionsText = normalizeOverlayChoiceOptionsText(fontResult.optionsText());
        }

        String mode = resolveFontOverlayMode(readableText);
        int promptIndex = findOverlayPromptIndex(readableText);

        throttledDevLog(
                "overlay_font_matched",
                DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                "overlay_font_matched npc=\"{}\" dialogue=\"{}\" options=\"{}\" mode={}",
                describeForLog(npcName),
                describeForLog(dialogue),
                describeForLog(optionsText),
                mode
        );

        return OverlayReadableParse.matched(
                mode,
                promptIndex,
                !optionsText.isBlank(),
                npcName,
                dialogue,
                optionsText
        );
    }

    private static String resolveFontOverlayMode(String readableText) {
        if (readableText == null) {
            return "font";
        }
        if (readableText.contains(CHOOSE_OPTION_MARKER)) {
            return "font::" + CHOOSE_OPTION_MARKER;
        }
        if (readableText.contains(CONFIRM_MARKER)) {
            return "font::" + CONFIRM_MARKER;
        }
        if (readableText.contains(CONTINUE_MARKER)) {
            return "font::" + CONTINUE_MARKER;
        }
        return "font";
    }

    private static int findOverlayPromptIndex(String readableText) {
        if (readableText == null) {
            return -1;
        }
        OverlayPromptMarker marker = findOverlayPromptMarker(readableText);
        return marker != null ? marker.index() : -1;
    }

    private static OverlayReadableParse parseReadableOverlayText(String readableText, DialogueCandidate existingCandidate) {
        return parseReadableOverlayText(readableText, existingCandidate, List.of());
    }

    private static OverlayReadableParse parseReadableOverlayText(
            String readableText,
            DialogueCandidate existingCandidate,
            List<String> readableSegments
    ) {
        if (readableText == null || readableText.isBlank()) {
            return OverlayReadableParse.rejected(false, "filtered_blank");
        }

        OverlayPromptMarker promptMarker = findOverlayPromptMarker(readableText);
        if (promptMarker == null) {
            OverlayDialogueFallbackParse fallbackParse = tryExtractFallbackOverlayDialogue(readableText);
            if (!fallbackParse.matched()) {
                return OverlayReadableParse.rejected(false, fallbackParse.rejectionReason());
            }
            return OverlayReadableParse.matched(
                    "tail_fallback",
                    -1,
                    false,
                    fallbackParse.npcName(),
                    fallbackParse.dialogue(),
                    ""
            );
        }

        String promptPayload = readableText.substring(promptMarker.endIndex()).trim();
        if (promptPayload.isBlank()) {
            return OverlayReadableParse.rejected(
                    promptMarker.kind() == OverlayPromptKind.CHOOSE_OPTION,
                    "prompt_payload_blank"
            );
        }
        if (promptMarker.kind() == OverlayPromptKind.CHOOSE_OPTION) {
            return parseChoiceOptionOverlayPayload(promptMarker, promptPayload, existingCandidate, readableSegments);
        }
        return parseOverlayDialoguePayload(
                promptMarker.mode(),
                promptMarker.index(),
                promptPayload,
                false,
                existingCandidate == null ? "" : existingCandidate.npcName()
        );
    }

    private static OverlayReadableParse parseChoiceOptionOverlayPayload(
            OverlayPromptMarker promptMarker,
            String promptPayload,
            DialogueCandidate existingCandidate,
            List<String> readableSegments
    ) {
        String strippedPayload = stripLeadingOverlayPromptMarkers(promptPayload);
        if (strippedPayload.isBlank()) {
            return OverlayReadableParse.rejected(true, "choice_payload_blank");
        }

        OverlayReadableParse payloadParse =
                parseOverlayDialoguePayload(
                        promptMarker.mode(),
                        promptMarker.index(),
                        strippedPayload,
                        true,
                        existingCandidate == null ? "" : existingCandidate.npcName()
                );
        if (!payloadParse.matched()) {
            return payloadParse;
        }

        OverlayReadableParse inferredPayloadParse = inferChoiceOptionOverlayPayload(
                promptMarker,
                payloadParse,
                readableSegments
        );
        if (shouldKeepExistingDialogueForInferredChoicePayload(inferredPayloadParse, existingCandidate)) {
            String npcName = payloadParse.npcName().isBlank()
                    ? existingCandidate.npcName()
                    : payloadParse.npcName();
            return OverlayReadableParse.matched(
                    promptMarker.mode(),
                    promptMarker.index(),
                    true,
                    npcName,
                    existingCandidate.dialogue(),
                    inferredPayloadParse.optionsText()
            );
        }

        if (existingCandidate != null
                && existingCandidate.dialogue() != null
                && !existingCandidate.dialogue().isBlank()
                && overlayChoicePayloadStartsWithDialogue(payloadParse.dialogue(), existingCandidate.dialogue())) {
            String npcName = payloadParse.npcName().isBlank()
                    ? existingCandidate.npcName()
                    : payloadParse.npcName();
            String dialogue = restoreChoiceDialogueCompletionPunctuation(existingCandidate.dialogue(), payloadParse.dialogue());
            String flatOptionsText = extractOverlayChoiceOptionsText(payloadParse.dialogue(), dialogue);
            String segmentedOptionsText = extractOverlayChoiceOptionsTextFromSegments(
                    readableSegments,
                    dialogue,
                    npcName
            );
            String optionsText = chooseBetterOverlayChoiceOptionsText(segmentedOptionsText, flatOptionsText);
            OverlayReadableParse existingPayloadParse = OverlayReadableParse.matched(
                    promptMarker.mode(),
                    promptMarker.index(),
                    true,
                    npcName,
                    dialogue,
                    optionsText
            );
            if (shouldPreferInferredChoicePayload(existingPayloadParse, inferredPayloadParse, existingCandidate)) {
                return inferredPayloadParse;
            }
            return existingPayloadParse;
        }

        if (inferredPayloadParse.matched()) {
            return inferredPayloadParse;
        }
        return OverlayReadableParse.rejected(true, "choice_dialogue_prefix_missing");
    }

    private static boolean shouldKeepExistingDialogueForInferredChoicePayload(
            OverlayReadableParse inferredPayloadParse,
            DialogueCandidate existingCandidate
    ) {
        if (inferredPayloadParse == null
                || !inferredPayloadParse.matched()
                || existingCandidate == null
                || existingCandidate.dialogue() == null
                || existingCandidate.dialogue().isBlank()
                || inferredPayloadParse.optionsText().isBlank()) {
            return false;
        }

        String candidateDialogue = prepareDialogueValue(existingCandidate.dialogue());
        String inferredDialogue = prepareDialogueValue(inferredPayloadParse.dialogue());
        if (candidateDialogue.isBlank()
                || inferredDialogue.isBlank()
                || inferredDialogue.length() <= candidateDialogue.length()
                || !inferredDialogue.startsWith(candidateDialogue)) {
            return false;
        }
        if (endsWithDialogueCompletionPunctuation(existingCandidate.dialogue())) {
            if (candidateDialogue.endsWith("...") && inferredDialogue.length() > candidateDialogue.length()) {
                String continuation = inferredDialogue.substring(candidateDialogue.length()).stripLeading();
                if (!continuation.isBlank()
                        && Character.isLetter(continuation.charAt(0))
                        && !OVERLAY_CHOICE_OPTION_START_PATTERN.matcher(continuation).lookingAt()) {
                    return false;
                }
            }
            return true;
        }
        return hasCompletedSentenceBeforeEnd(inferredDialogue.substring(candidateDialogue.length()));
    }

    private static boolean shouldPreferInferredChoicePayload(
            OverlayReadableParse existingPayloadParse,
            OverlayReadableParse inferredPayloadParse,
            DialogueCandidate existingCandidate
    ) {
        if (existingPayloadParse == null
                || inferredPayloadParse == null
                || !inferredPayloadParse.matched()
                || existingCandidate == null) {
            return false;
        }

        String candidateDialogue = prepareDialogueValue(existingCandidate.dialogue());
        String existingDialogue = prepareDialogueValue(existingPayloadParse.dialogue());
        String inferredDialogue = prepareDialogueValue(inferredPayloadParse.dialogue());
        if (candidateDialogue.isBlank()
                || existingDialogue.isBlank()
                || inferredDialogue.isBlank()
                || inferredDialogue.length() <= existingDialogue.length()) {
            return false;
        }
        if (!inferredDialogue.startsWith(candidateDialogue)) {
            return false;
        }
        if (endsWithDialogueCompletionPunctuation(existingCandidate.dialogue())) {
            if (!(candidateDialogue.endsWith("...") && inferredDialogue.length() > candidateDialogue.length())) {
                return false;
            }
            String continuation = inferredDialogue.substring(candidateDialogue.length()).stripLeading();
            if (continuation.isBlank()
                    || !Character.isLetter(continuation.charAt(0))
                    || OVERLAY_CHOICE_OPTION_START_PATTERN.matcher(continuation).lookingAt()) {
                return false;
            }
        }
        if (hasCompletedSentenceBeforeEnd(inferredDialogue.substring(candidateDialogue.length()))) {
            return false;
        }
        return !inferredPayloadParse.optionsText().isBlank();
    }

    private static boolean hasCompletedSentenceBeforeEnd(String value) {
        String normalized = normalizeDisplayText(value);
        if (normalized.isBlank()) {
            return false;
        }

        for (int i = 0; i < normalized.length(); i++) {
            if (!isDialogueCompletionPunctuation(normalized.charAt(i))) {
                continue;
            }
            for (int j = i + 1; j < normalized.length(); j++) {
                if (!Character.isWhitespace(normalized.charAt(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static OverlayReadableParse inferChoiceOptionOverlayPayload(
            OverlayPromptMarker promptMarker,
            OverlayReadableParse payloadParse,
            List<String> readableSegments
    ) {
        if (promptMarker == null || payloadParse == null || !payloadParse.matched()) {
            return OverlayReadableParse.rejected(true, "choice_payload_inference_unavailable");
        }

        OverlayChoicePayloadSplit flatSplit = inferChoicePayloadSplitFromFlatText(payloadParse.dialogue());
        OverlayChoicePayloadSplit segmentSplit = inferChoicePayloadSplitFromSegments(
                readableSegments,
                payloadParse.npcName()
        );
        OverlayChoicePayloadSplit bestSplit = chooseBetterInferredChoicePayloadSplit(segmentSplit, flatSplit);
        if (!bestSplit.matched()) {
            return OverlayReadableParse.rejected(true, bestSplit.rejectionReason());
        }

        String segmentedOptionsText = extractOverlayChoiceOptionsTextFromSegments(
                readableSegments,
                bestSplit.dialogue(),
                payloadParse.npcName()
        );
        String optionsText = chooseBetterOverlayChoiceOptionsText(segmentedOptionsText, bestSplit.optionsText());
        if (optionsText.isBlank()) {
            return OverlayReadableParse.rejected(true, "choice_options_missing");
        }

        return OverlayReadableParse.matched(
                promptMarker.mode(),
                promptMarker.index(),
                true,
                payloadParse.npcName(),
                bestSplit.dialogue(),
                optionsText
        );
    }

    private static OverlayChoicePayloadSplit chooseBetterInferredChoicePayloadSplit(
            OverlayChoicePayloadSplit segmentSplit,
            OverlayChoicePayloadSplit flatSplit
    ) {
        if (segmentSplit != null && segmentSplit.matched()) {
            if (flatSplit != null
                    && flatSplit.matched()
                    && Objects.equals(prepareDialogueValue(segmentSplit.dialogue()), prepareDialogueValue(flatSplit.dialogue()))) {
                return new OverlayChoicePayloadSplit(
                        true,
                        segmentSplit.dialogue(),
                        chooseBetterOverlayChoiceOptionsText(segmentSplit.optionsText(), flatSplit.optionsText()),
                        ""
                );
            }
            return segmentSplit;
        }
        if (flatSplit != null && flatSplit.matched()) {
            return flatSplit;
        }
        return OverlayChoicePayloadSplit.rejected("choice_dialogue_prefix_missing");
    }

    private static OverlayChoicePayloadSplit inferChoicePayloadSplitFromSegments(
            List<String> readableSegments,
            String npcName
    ) {
        if (readableSegments == null || readableSegments.isEmpty()) {
            return OverlayChoicePayloadSplit.rejected("choice_segments_missing");
        }

        List<String> payloadSegments = extractChoicePayloadSegments(readableSegments, npcName);
        OverlayChoicePayloadSplit trustedStartSplit = inferChoicePayloadSplitFromTrustedOptionStart(payloadSegments);
        if (trustedStartSplit.matched()) {
            return trustedStartSplit;
        }
        OverlayChoicePayloadSplit completedDialogueSplit = inferChoicePayloadSplitAfterCompletedDialogue(payloadSegments);
        if (completedDialogueSplit.matched()) {
            return completedDialogueSplit;
        }
        return OverlayChoicePayloadSplit.rejected("choice_segment_options_missing");
    }

    private static List<String> extractChoicePayloadSegments(List<String> readableSegments, String npcName) {
        boolean afterChoiceMarker = false;
        List<String> payloadSegments = new ArrayList<>();
        for (String readableSegment : readableSegments) {
            String segment = normalizeDisplayText(readableSegment);
            if (segment.isBlank()) {
                continue;
            }

            if (!afterChoiceMarker) {
                int markerIndex = segment.indexOf(CHOOSE_OPTION_MARKER);
                if (markerIndex < 0) {
                    continue;
                }
                segment = segment.substring(markerIndex + CHOOSE_OPTION_MARKER.length()).trim();
                afterChoiceMarker = true;
            }

            segment = stripOverlayChoiceNpcTail(stripLeadingOverlayPromptMarkers(segment), npcName);
            if (segment.isBlank() || isOverlayPromptControlText(segment)) {
                continue;
            }

            payloadSegments.add(segment);
        }
        return payloadSegments;
    }

    private static OverlayChoicePayloadSplit inferChoicePayloadSplitFromTrustedOptionStart(List<String> payloadSegments) {
        if (payloadSegments == null || payloadSegments.isEmpty()) {
            return OverlayChoicePayloadSplit.rejected("choice_segments_missing");
        }

        List<String> dialogueParts = new ArrayList<>();
        List<String> optionFragments = new ArrayList<>();
        boolean collectingOptions = false;
        for (String segment : payloadSegments) {
            List<String> fragments = splitOverlayChoiceOptionFragments(segment);
            boolean startsWithTrustedOption = !fragments.isEmpty() && isTrustedOverlayChoiceOptionFragment(fragments.get(0));
            if (!collectingOptions && !startsWithTrustedOption) {
                dialogueParts.add(segment);
                continue;
            }

            collectingOptions = true;
            optionFragments.addAll(fragments);
        }

        return buildChoicePayloadSplit(dialogueParts, optionFragments, "choice_segment_dialogue_missing", "choice_segment_options_missing");
    }

    private static OverlayChoicePayloadSplit inferChoicePayloadSplitAfterCompletedDialogue(List<String> payloadSegments) {
        if (payloadSegments == null || payloadSegments.size() < 2) {
            return OverlayChoicePayloadSplit.rejected("choice_segments_missing");
        }

        for (int splitIndex = 1; splitIndex < payloadSegments.size(); splitIndex++) {
            List<String> dialogueParts = new ArrayList<>(payloadSegments.subList(0, splitIndex));
            String dialogue = normalizeDisplayText(String.join(" ", dialogueParts));
            if (dialogue.length() < MIN_OVERLAY_DIALOGUE_LENGTH || !endsWithDialogueCompletionPunctuation(dialogue)) {
                continue;
            }

            List<String> optionFragments = new ArrayList<>();
            for (String segment : payloadSegments.subList(splitIndex, payloadSegments.size())) {
                optionFragments.addAll(splitOverlayChoiceOptionFragments(segment));
            }
            return buildChoicePayloadSplit(
                    dialogueParts,
                    optionFragments,
                    "choice_segment_dialogue_missing",
                    "choice_segment_options_missing"
            );
        }
        return OverlayChoicePayloadSplit.rejected("choice_segment_dialogue_completion_missing");
    }

    private static OverlayChoicePayloadSplit buildChoicePayloadSplit(
            List<String> dialogueParts,
            List<String> optionFragments,
            String dialogueRejectionReason,
            String optionsRejectionReason
    ) {
        String dialogue = normalizeDisplayText(String.join(" ", dialogueParts == null ? List.of() : dialogueParts));
        String optionsText = normalizeOverlayChoiceOptionsText(String.join("\n", optionFragments == null ? List.of() : optionFragments));
        if (dialogue.isBlank()) {
            return OverlayChoicePayloadSplit.rejected(dialogueRejectionReason);
        }
        if (optionsText.isBlank()) {
            return OverlayChoicePayloadSplit.rejected(optionsRejectionReason);
        }
        return OverlayChoicePayloadSplit.matched(dialogue, optionsText);
    }

    private static OverlayChoicePayloadSplit inferChoicePayloadSplitFromFlatText(String payloadDialogueText) {
        String payload = normalizeDisplayText(payloadDialogueText);
        if (payload.isBlank()) {
            return OverlayChoicePayloadSplit.rejected("choice_payload_blank");
        }

        Matcher matcher = OVERLAY_CHOICE_OPTION_START_PATTERN.matcher(payload);
        while (matcher.find()) {
            int splitIndex = matcher.start();
            if (splitIndex <= 0 || !hasDialogueCompletionBefore(payload, splitIndex)) {
                continue;
            }

            String dialogue = normalizeDisplayText(payload.substring(0, splitIndex));
            String optionsText = normalizeOverlayChoiceOptionsText(payload.substring(splitIndex));
            if (dialogue.length() < MIN_OVERLAY_DIALOGUE_LENGTH || optionsText.isBlank()) {
                continue;
            }
            if (splitOverlayChoiceOptionFragments(optionsText).size() < 2) {
                continue;
            }
            return OverlayChoicePayloadSplit.matched(dialogue, optionsText);
        }
        return OverlayChoicePayloadSplit.rejected("choice_dialogue_prefix_missing");
    }

    private static boolean hasDialogueCompletionBefore(String value, int splitIndex) {
        if (value == null || splitIndex <= 0 || splitIndex > value.length()) {
            return false;
        }

        for (int i = splitIndex - 1; i >= 0; i--) {
            char current = value.charAt(i);
            if (Character.isWhitespace(current)) {
                continue;
            }
            return isDialogueCompletionPunctuation(current);
        }
        return false;
    }

    private static String restoreChoiceDialogueCompletionPunctuation(String dialogue, String payload) {
        String normalizedDialogue = normalizeDisplayText(dialogue);
        String normalizedPayload = normalizeDisplayText(payload);
        if (normalizedDialogue.isBlank() || normalizedPayload.isBlank()) {
            return normalizedDialogue;
        }
        if (!normalizedPayload.startsWith(normalizedDialogue)
                || normalizedPayload.length() <= normalizedDialogue.length()) {
            return normalizedDialogue;
        }

        String remainder = normalizedPayload.substring(normalizedDialogue.length());
        Matcher optionStartMatcher = OVERLAY_CHOICE_OPTION_START_PATTERN.matcher(remainder);
        if (optionStartMatcher.find() && optionStartMatcher.start() > 0
                && hasDialogueCompletionBefore(normalizedPayload, normalizedDialogue.length() + optionStartMatcher.start())) {
            String extraDialogue = remainder.substring(0, optionStartMatcher.start()).stripTrailing();
            if (!extraDialogue.isBlank()) {
                return (normalizedDialogue + extraDialogue).trim();
            }
        }

        if (endsWithDialogueCompletionPunctuation(normalizedDialogue)) {
            return normalizedDialogue;
        }
        char nextChar = normalizedPayload.charAt(normalizedDialogue.length());
        if (isDialogueCompletionPunctuation(nextChar)) {
            return normalizedDialogue + nextChar;
        }
        return normalizedDialogue;
    }

    private static OverlayReadableParse parseOverlayDialoguePayload(
            String mode,
            int promptIndex,
            String dialogueWithNpc,
            boolean choicePrompt
    ) {
        return parseOverlayDialoguePayload(mode, promptIndex, dialogueWithNpc, choicePrompt, "");
    }

    private static OverlayReadableParse parseOverlayDialoguePayload(
            String mode,
            int promptIndex,
            String dialogueWithNpc,
            boolean choicePrompt,
            String preferredNpcName
    ) {
        if (dialogueWithNpc == null || dialogueWithNpc.isBlank()) {
            return OverlayReadableParse.rejected(choicePrompt, "dialogue_payload_blank");
        }

        OverlayNpcTailParse overlayNpc = extractOverlayNpcFromTail(dialogueWithNpc, preferredNpcName);
        String npcName = overlayNpc.canonicalName();
        String dialogue = overlayNpc.fullTail().isBlank()
                ? dialogueWithNpc
                : dialogueWithNpc.substring(0, dialogueWithNpc.length() - overlayNpc.fullTail().length()).trim();
        dialogue = normalizeDisplayText(dialogue);
        return OverlayReadableParse.matched(mode, promptIndex, choicePrompt, npcName, dialogue, "");
    }

    private static OverlayPromptMarker findOverlayPromptMarker(String readableText) {
        if (readableText == null || readableText.isBlank()) {
            return null;
        }

        OverlayPromptMarker chooseOptionMarker = findOverlayPromptMarker(
                readableText,
                OverlayPromptKind.CHOOSE_OPTION,
                CHOOSE_OPTION_MARKER,
                CHOOSE_OPTION_MARKER_WITH_SPACE
        );
        if (chooseOptionMarker != null) {
            return chooseOptionMarker;
        }

        OverlayPromptMarker best = null;
        best = chooseEarlierPromptMarker(
                best,
                findOverlayPromptMarker(readableText, OverlayPromptKind.CONTINUE, CONTINUE_MARKER, CONTINUE_MARKER_WITH_SPACE)
        );
        return chooseEarlierPromptMarker(
                best,
                findOverlayPromptMarker(readableText, OverlayPromptKind.CONFIRM, CONFIRM_MARKER, CONFIRM_MARKER_WITH_SPACE)
        );
    }

    private static OverlayPromptMarker findOverlayPromptMarker(
            String readableText,
            OverlayPromptKind kind,
            String marker,
            String markerWithSpace
    ) {
        int withSpaceIndex = readableText.indexOf(markerWithSpace);
        if (withSpaceIndex >= 0) {
            return new OverlayPromptMarker(kind, withSpaceIndex, markerWithSpace);
        }

        int markerIndex = readableText.indexOf(marker);
        if (markerIndex >= 0) {
            return new OverlayPromptMarker(kind, markerIndex, marker);
        }
        return null;
    }

    private static OverlayPromptMarker chooseEarlierPromptMarker(
            OverlayPromptMarker current,
            OverlayPromptMarker candidate
    ) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.index() < current.index()) {
            return candidate;
        }
        return current;
    }

    private static String stripLeadingOverlayPromptMarkers(String value) {
        String stripped = normalizeDisplayText(value);
        while (true) {
            String next = stripLeadingOverlayPromptMarker(stripped, CHOOSE_OPTION_MARKER);
            next = stripLeadingOverlayPromptMarker(next, CONTINUE_MARKER);
            next = stripLeadingOverlayPromptMarker(next, CONFIRM_MARKER);
            if (next.equals(stripped)) {
                return stripped;
            }
            stripped = next;
        }
    }

    private static String stripLeadingOverlayPromptMarker(String value, String marker) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = normalizeDisplayText(value);
        if (normalized.equals(marker)) {
            return "";
        }
        if (normalized.startsWith(marker + " ")) {
            return normalized.substring(marker.length()).trim();
        }
        return normalized;
    }

    private static boolean overlayChoicePayloadStartsWithDialogue(String payload, String dialogue) {
        String preparedPayload = prepareDialogueValue(payload);
        String preparedDialogue = prepareDialogueValue(dialogue);
        if (preparedPayload.isBlank() || preparedDialogue.isBlank()) {
            return false;
        }
        if (!preparedPayload.startsWith(preparedDialogue)) {
            return false;
        }
        if (preparedPayload.length() == preparedDialogue.length()) {
            return true;
        }
        return !Character.isLetterOrDigit(preparedPayload.charAt(preparedDialogue.length()));
    }

    private static String extractOverlayChoiceOptionsText(String payload, String dialogue) {
        String normalizedPayload = normalizeDisplayText(payload);
        String normalizedDialogue = normalizeDisplayText(dialogue);
        if (normalizedPayload.isBlank() || normalizedDialogue.isBlank()) {
            return "";
        }
        if (!normalizedPayload.startsWith(normalizedDialogue)) {
            return "";
        }

        String optionsText = normalizedPayload.substring(normalizedDialogue.length()).trim();
        if (optionsText.isBlank()) {
            return "";
        }
        optionsText = stripLeadingDialogueCompletionPunctuation(optionsText);
        return normalizeOverlayChoiceOptionsText(optionsText);
    }

    private static String extractOverlayChoiceOptionsTextFromSegments(
            List<String> readableSegments,
            String dialogue,
            String npcName
    ) {
        if (readableSegments == null || readableSegments.isEmpty() || dialogue == null || dialogue.isBlank()) {
            return "";
        }

        boolean afterChoiceMarker = false;
        boolean consumedDialogue = false;
        List<String> optionFragments = new ArrayList<>();
        List<String> standaloneOptionFragments = new ArrayList<>();
        for (String readableSegment : readableSegments) {
            String segment = normalizeDisplayText(readableSegment);
            if (segment.isBlank()) {
                continue;
            }

            if (!afterChoiceMarker) {
                int markerIndex = segment.indexOf(CHOOSE_OPTION_MARKER);
                if (markerIndex < 0) {
                    addStandaloneOverlayChoiceOptionFragments(standaloneOptionFragments, segment, dialogue, npcName);
                    continue;
                }
                segment = segment.substring(markerIndex + CHOOSE_OPTION_MARKER.length()).trim();
                afterChoiceMarker = true;
            }

            segment = stripLeadingOverlayPromptMarkers(segment);
            if (segment.isBlank() || isOverlayPromptControlText(segment)) {
                continue;
            }

            if (!consumedDialogue) {
                ChoiceDialoguePrefixRemoval removal = removeChoiceDialoguePrefix(segment, dialogue);
                if (!removal.matched()) {
                    if (isOverlayChoiceDialogueSegment(segment, dialogue)) {
                        continue;
                    }
                    addStandaloneOverlayChoiceOptionFragments(standaloneOptionFragments, segment, dialogue, npcName);
                    continue;
                }
                consumedDialogue = true;
                segment = removal.remainingText();
            }

            segment = stripOverlayChoiceNpcTail(segment, npcName);
            if (!segment.isBlank()) {
                optionFragments.add(segment);
            }
        }

        if (!consumedDialogue || optionFragments.isEmpty()) {
            return normalizeOverlayChoiceOptionsText(String.join("\n", standaloneOptionFragments));
        }
        return normalizeOverlayChoiceOptionsText(String.join("\n", optionFragments));
    }

    private static void addStandaloneOverlayChoiceOptionFragments(
            List<String> optionFragments,
            String segment,
            String dialogue,
            String npcName
    ) {
        if (optionFragments == null || segment == null || segment.isBlank()) {
            return;
        }

        String cleanSegment = stripOverlayChoiceNpcTail(stripLeadingOverlayPromptMarkers(segment), npcName);
        if (cleanSegment.isBlank()
                || isOverlayPromptControlText(cleanSegment)
                || isOverlayChoiceDialogueSegment(cleanSegment, dialogue)) {
            return;
        }

        List<String> fragments = splitOverlayChoiceOptionFragments(cleanSegment);
        for (String fragment : fragments) {
            if (isStandaloneOverlayChoiceOptionFragment(fragment)) {
                optionFragments.add(fragment);
            }
        }
    }

    private static boolean isStandaloneOverlayChoiceOptionFragment(String value) {
        String fragment = cleanOverlayChoiceOptionFragment(value);
        if (fragment.isBlank()) {
            return false;
        }
        if (isOverlayPromptControlText(fragment)) {
            return false;
        }
        return !looksLikeCompositeOverlayChoiceFragment(fragment);
    }

    private static boolean startsStandaloneOverlayChoiceOptionLine(String value) {
        String fragment = cleanOverlayChoiceOptionFragment(value);
        if (fragment.isBlank()) {
            return false;
        }
        if (isTrustedOverlayChoiceOptionFragment(fragment)) {
            return true;
        }
        char first = fragment.charAt(0);
        return Character.isUpperCase(first) || Character.isDigit(first) || first == '"' || first == '\'';
    }

    private static String chooseBetterOverlayChoiceOptionsText(String segmentedOptionsText, String flatOptionsText) {
        String segmented = normalizeOverlayChoiceOptionsText(segmentedOptionsText);
        String flat = normalizeOverlayChoiceOptionsText(flatOptionsText);
        if (segmented.isBlank()) {
            return flat;
        }
        if (flat.isBlank()) {
            return segmented;
        }

        int segmentedLines = splitOverlayChoiceOptionFragments(segmented).size();
        int flatLines = splitOverlayChoiceOptionFragments(flat).size();
        if (segmentedLines >= 2 && segmentedLines >= flatLines) {
            return segmented;
        }
        if (flatLines >= 2 && flatLines > segmentedLines) {
            return flat;
        }
        return segmented.length() >= flat.length() ? segmented : flat;
    }

    private static String normalizeOverlayChoiceOptionsText(String optionsText) {
        List<String> fragments = splitOverlayChoiceOptionFragments(optionsText);
        if (fragments.isEmpty()) {
            return "";
        }
        return String.join("\n", fragments);
    }

    private static List<String> splitOverlayChoiceOptionFragments(String optionsText) {
        if (optionsText == null || optionsText.isBlank()) {
            return List.of();
        }

        List<String> fragments = new ArrayList<>();
        String normalized = optionsText.replace('\r', '\n');
        String[] lines = normalized.split("\\n", -1);
        for (String line : lines) {
            String cleanLine = normalizeDisplayText(line);
            if (cleanLine.isBlank()) {
                continue;
            }

            cleanLine = stripLeadingDialogueCompletionPunctuation(cleanLine);
            String[] punctuationChunks = cleanLine.split("(?<=[.!?])\\s+");
            for (String punctuationChunk : punctuationChunks) {
                addOverlayChoiceOptionStartFragments(fragments, punctuationChunk);
            }
        }
        return fragments;
    }

    private static void addOverlayChoiceOptionStartFragments(List<String> fragments, String value) {
        String normalized = normalizeDisplayText(value);
        if (normalized.isBlank()) {
            return;
        }

        Matcher matcher = OVERLAY_CHOICE_OPTION_START_PATTERN.matcher(normalized);
        int start = 0;
        while (matcher.find()) {
            if (matcher.start() <= start) {
                continue;
            }
            String before = normalized.substring(start, matcher.start()).trim();
            if (before.length() >= MIN_OVERLAY_CHOICE_FRAGMENT_LENGTH) {
                appendOverlayChoiceOptionFragment(fragments, before);
                start = matcher.start();
            }
        }
        appendOverlayChoiceOptionFragment(fragments, normalized.substring(start));
    }

    private static void appendOverlayChoiceOptionFragment(List<String> fragments, String value) {
        String fragment = cleanOverlayChoiceOptionFragment(value);
        if (!fragment.isBlank()) {
            fragments.add(fragment);
        }
    }

    private static String cleanOverlayChoiceOptionFragment(String value) {
        String fragment = normalizeDisplayText(value);
        fragment = stripLeadingDialogueCompletionPunctuation(fragment);
        fragment = restoreMissingLeadingOverlayChoiceStart(fragment);
        if (fragment.length() < MIN_OVERLAY_CHOICE_FRAGMENT_LENGTH || !containsHumanReadableLetters(fragment)) {
            return "";
        }
        return fragment;
    }

    private static String restoreMissingLeadingOverlayChoiceStart(String value) {
        String normalized = normalizeDisplayText(value);
        if (normalized.startsWith("hich ")) {
            return "Which " + normalized.substring("hich ".length());
        }
        return normalized;
    }

    private static String stripLeadingDialogueCompletionPunctuation(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String stripped = normalizeDisplayText(value);
        while (!stripped.isBlank() && isDialogueCompletionPunctuation(stripped.charAt(0))) {
            stripped = stripped.substring(1).trim();
        }
        return stripped;
    }

    private static boolean isDialogueCompletionPunctuation(char value) {
        return value == '.' || value == '!' || value == '?';
    }

    private static boolean isOverlayPromptControlText(String value) {
        String normalized = normalizeDisplayText(value);
        return normalized.equals(CHOOSE_OPTION_MARKER)
                || normalized.equals(CONFIRM_MARKER)
                || normalized.equals(CONTINUE_MARKER);
    }

    private static ChoiceDialoguePrefixRemoval removeChoiceDialoguePrefix(String value, String dialogue) {
        String normalizedValue = normalizeDisplayText(value);
        String normalizedDialogue = normalizeDisplayText(dialogue);
        if (normalizedValue.isBlank() || normalizedDialogue.isBlank()) {
            return new ChoiceDialoguePrefixRemoval(false, "");
        }
        if (!normalizedValue.startsWith(normalizedDialogue)) {
            return new ChoiceDialoguePrefixRemoval(false, "");
        }

        String remaining = normalizedValue.substring(normalizedDialogue.length()).trim();
        remaining = stripLeadingDialogueCompletionPunctuation(remaining);
        return new ChoiceDialoguePrefixRemoval(true, remaining);
    }

    private static boolean isOverlayChoiceDialogueSegment(String value, String dialogue) {
        String preparedValue = prepareDialogueValue(value);
        String preparedDialogue = prepareDialogueValue(dialogue);
        return !preparedValue.isBlank()
                && !preparedDialogue.isBlank()
                && (preparedDialogue.contains(preparedValue) || preparedValue.contains(preparedDialogue));
    }

    private static String stripOverlayChoiceNpcTail(String value, String npcName) {
        String stripped = normalizeDisplayText(value);
        String normalizedNpcName = normalizeDisplayText(npcName);
        if (stripped.isBlank() || normalizedNpcName.isBlank()) {
            return stripped;
        }
        if (stripped.equals(normalizedNpcName)) {
            return "";
        }
        if (stripped.endsWith(" " + normalizedNpcName)) {
            return stripped.substring(0, stripped.length() - normalizedNpcName.length()).trim();
        }
        return stripped;
    }

    private static synchronized String stabilizeOverlayChoiceOptionsText(String npcName, String dialogue, String optionsText) {
        String preparedDialogue = prepareDialogueValue(dialogue);
        if (preparedDialogue.isBlank()) {
            return "";
        }

        List<String> fragments = splitOverlayChoiceOptionFragments(optionsText);
        if (overlayChoiceOptionsState == null || !overlayChoiceOptionsState.matches(preparedDialogue, npcName)) {
            overlayChoiceOptionsState = new OverlayChoiceOptionsState(preparedDialogue, normalizeDisplayText(npcName));
        }
        overlayChoiceOptionsState.merge(fragments);
        return overlayChoiceOptionsState.displayText();
    }

    private static synchronized void clearOverlayChoiceOptionsTracking() {
        overlayChoiceOptionsState = null;
    }

    private static void clearOverlayOptionsQueueTracking() {
        lastOverlayOptionsChangedAt = 0L;
        lastOverlayObservedOptionsSignature = "";
        lastOverlayQueuedOptionsSignature = "";
    }

    private static synchronized boolean isOverlayChoiceOptionsTrackingActive() {
        return overlayChoiceOptionsState != null;
    }

    private static boolean shouldTreatAsActiveOverlayChoiceFragment(
            OverlayReadableParse readableParse,
            List<String> readableSegments
    ) {
        if (readableParse == null || readableParse.choicePrompt() || !isOverlayChoiceOptionsTrackingActive()) {
            return false;
        }

        DialogueCandidate candidate = currentCandidate;
        if (candidate == null || !candidate.overlaySource()) {
            return false;
        }
        if (!overlayChoiceOptionsState.matchesCandidate(candidate)) {
            return false;
        }

        String dialogue = normalizeDisplayText(readableParse.dialogue());
        if (dialogue.contains(CHOOSE_OPTION_MARKER) || dialogue.contains(CONFIRM_MARKER)) {
            return true;
        }
        if (overlayChoicePayloadStartsWithDialogue(stripLeadingOverlayPromptMarkers(dialogue), candidate.dialogue())) {
            return true;
        }
        String segmentedOptionsText = extractOverlayChoiceOptionsTextFromSegments(
                readableSegments,
                candidate.dialogue(),
                candidate.npcName()
        );
        if (!segmentedOptionsText.isBlank()) {
            return true;
        }
        return looksLikeOverlayChoiceOptionsFragment(dialogue);
    }

    private static void updateActiveOverlayChoiceOptions(
            OverlayReadableParse readableParse,
            List<String> readableSegments,
            String sourcePlain
    ) {
        if (!shouldTranslateNpcOptions()) {
            clearOverlayChoiceOptionsTracking();
            return;
        }
        DialogueCandidate candidate = currentCandidate;
        if (candidate == null || !candidate.overlaySource() || !overlayChoiceOptionsState.matchesCandidate(candidate)) {
            return;
        }

        String payload = stripLeadingOverlayPromptMarkers(readableParse.dialogue());
        String flatOptionsText = extractOverlayChoiceOptionsText(payload, candidate.dialogue());
        if (flatOptionsText.isBlank() && looksLikeOverlayChoiceOptionsFragment(payload)) {
            flatOptionsText = payload;
        }
        String segmentedOptionsText = extractOverlayChoiceOptionsTextFromSegments(
                readableSegments,
                candidate.dialogue(),
                candidate.npcName()
        );
        String optionsText = chooseBetterOverlayChoiceOptionsText(segmentedOptionsText, flatOptionsText);
        String stabilizedOptionsText = stabilizeOverlayChoiceOptionsText(
                candidate.npcName(),
                candidate.dialogue(),
                optionsText
        );
        String displayOptionsText = chooseOverlayChoiceOptionsDisplayText(candidate.optionsText(), stabilizedOptionsText);
        if (displayOptionsText.isBlank() || Objects.equals(displayOptionsText, candidate.optionsText())) {
            return;
        }

        currentCandidate = new DialogueCandidate(
                candidate.observedNonce(),
                candidate.pageInfo(),
                candidate.npcName(),
                candidate.dialogue(),
                true,
                sourcePlain == null ? candidate.overlaySourcePlain() : sourcePlain,
                displayOptionsText
        );
        updateOverlayOptionsStability(displayOptionsText, System.currentTimeMillis());
        refreshCurrentDialogueDisplay();
    }

    private static boolean looksLikeOverlayChoiceOptionsFragment(String value) {
        String normalized = normalizeDisplayText(value);
        if (normalized.isBlank()) {
            return false;
        }
        if (normalized.startsWith(CHOOSE_OPTION_MARKER) || normalized.startsWith(CONFIRM_MARKER)) {
            return true;
        }
        if (splitOverlayChoiceOptionFragments(normalized).size() >= 2) {
            return true;
        }
        return OVERLAY_CHOICE_OPTION_START_PATTERN.matcher(normalized).find()
                && (normalized.endsWith("?") || normalized.endsWith("."));
    }

    static synchronized String stabilizeOverlayChoiceOptionsTextForTest(
            String npcName,
            String dialogue,
            String optionsText
    ) {
        return stabilizeOverlayChoiceOptionsText(npcName, dialogue, optionsText);
    }

    static String chooseOverlayChoiceOptionsDisplayTextForTest(String previousText, String nextText) {
        return chooseOverlayChoiceOptionsDisplayText(previousText, nextText);
    }

    private static String chooseOverlayChoiceOptionsDisplayText(String previousText, String nextText) {
        String previous = normalizeOverlayChoiceOptionsText(previousText);
        String next = normalizeOverlayChoiceOptionsText(nextText);
        if (previous.isBlank()) {
            return next;
        }
        if (next.isBlank() || previous.equals(next)) {
            return previous;
        }
        int previousQuality = overlayChoiceOptionsDisplayQuality(previous);
        int nextQuality = overlayChoiceOptionsDisplayQuality(next);
        if (splitOverlayChoiceOptionFragments(next).size() > splitOverlayChoiceOptionFragments(previous).size()) {
            return next;
        }
        return nextQuality >= previousQuality + MIN_OVERLAY_CHOICE_DISPLAY_QUALITY_GAIN ? next : previous;
    }

    private static int overlayChoiceOptionsDisplayQuality(String optionsText) {
        int score = 0;
        List<String> fragments = splitOverlayChoiceOptionFragments(optionsText);
        for (String fragment : fragments) {
            score += 100 + overlayChoiceOptionLineQuality(fragment);
        }
        return score;
    }

    private static int overlayChoiceOptionMergeScore(String existing, String fragment) {
        String normalizedExisting = normalizeDisplayText(existing).toLowerCase(Locale.ROOT);
        String normalizedFragment = normalizeDisplayText(fragment).toLowerCase(Locale.ROOT);
        if (normalizedExisting.isBlank() || normalizedFragment.isBlank()) {
            return 0;
        }
        if (normalizedExisting.equals(normalizedFragment)) {
            return 1000 + normalizedExisting.length();
        }
        if (Math.min(normalizedExisting.length(), normalizedFragment.length()) >= MIN_OVERLAY_CHOICE_MERGE_OVERLAP
                && (normalizedExisting.contains(normalizedFragment) || normalizedFragment.contains(normalizedExisting))) {
            return 500 + Math.min(normalizedExisting.length(), normalizedFragment.length());
        }
        int commonPrefixLength = overlayChoiceOptionCommonPrefixLength(normalizedExisting, normalizedFragment);
        if (commonPrefixLength >= MIN_OVERLAY_CHOICE_MERGE_OVERLAP) {
            return 400 + commonPrefixLength;
        }
        return Math.max(
                overlayChoiceOptionOverlap(normalizedExisting, normalizedFragment),
                overlayChoiceOptionOverlap(normalizedFragment, normalizedExisting)
        );
    }

    private static String mergeOverlayChoiceOptionText(String existing, String fragment) {
        String normalizedExisting = normalizeDisplayText(existing);
        String normalizedFragment = normalizeDisplayText(fragment);
        String existingLower = normalizedExisting.toLowerCase(Locale.ROOT);
        String fragmentLower = normalizedFragment.toLowerCase(Locale.ROOT);
        if (existingLower.equals(fragmentLower)) {
            return normalizedExisting;
        }
        if (existingLower.contains(fragmentLower)) {
            return normalizedExisting;
        }
        if (fragmentLower.contains(existingLower)) {
            return chooseBetterOverlayChoiceOptionLine(normalizedExisting, normalizedFragment);
        }

        int commonPrefixLength = overlayChoiceOptionCommonPrefixLength(existingLower, fragmentLower);
        if (commonPrefixLength >= MIN_OVERLAY_CHOICE_MERGE_OVERLAP) {
            return chooseBetterOverlayChoiceOptionLine(normalizedExisting, normalizedFragment);
        }

        int forwardOverlap = overlayChoiceOptionOverlap(existingLower, fragmentLower);
        int backwardOverlap = overlayChoiceOptionOverlap(fragmentLower, existingLower);
        if (forwardOverlap >= backwardOverlap && forwardOverlap >= MIN_OVERLAY_CHOICE_MERGE_OVERLAP) {
            return chooseBetterOverlayChoiceOptionLine(
                    normalizedExisting + normalizedFragment.substring(forwardOverlap),
                    normalizedExisting,
                    normalizedFragment
            );
        }
        if (backwardOverlap >= MIN_OVERLAY_CHOICE_MERGE_OVERLAP) {
            return chooseBetterOverlayChoiceOptionLine(
                    normalizedFragment + normalizedExisting.substring(backwardOverlap),
                    normalizedExisting,
                    normalizedFragment
            );
        }
        return chooseBetterOverlayChoiceOptionLine(normalizedExisting, normalizedFragment);
    }

    private static int overlayChoiceOptionOverlap(String left, String right) {
        int max = Math.min(left.length(), right.length());
        for (int overlap = max; overlap >= MIN_OVERLAY_CHOICE_MERGE_OVERLAP; overlap--) {
            if (left.regionMatches(left.length() - overlap, right, 0, overlap)) {
                return overlap;
            }
        }
        return 0;
    }

    private static int overlayChoiceOptionCommonPrefixLength(String left, String right) {
        int max = Math.min(left.length(), right.length());
        int length = 0;
        while (length < max && left.charAt(length) == right.charAt(length)) {
            length++;
        }
        return length;
    }

    private static String chooseBetterOverlayChoiceOptionLine(String first, String second) {
        return overlayChoiceOptionLineQuality(second) > overlayChoiceOptionLineQuality(first) ? second : first;
    }

    private static String chooseBetterOverlayChoiceOptionLine(String first, String second, String third) {
        return chooseBetterOverlayChoiceOptionLine(chooseBetterOverlayChoiceOptionLine(first, second), third);
    }

    private static int overlayChoiceOptionLineQuality(String value) {
        String normalized = normalizeDisplayText(value);
        if (normalized.isBlank()) {
            return 0;
        }

        int score = Math.min(normalized.length(), 120);
        if (isTrustedOverlayChoiceOptionFragment(normalized)) {
            score += 50;
        }
        if (endsWithDialogueCompletionPunctuation(normalized)) {
            score += 80;
        }
        if (looksLikeCompositeOverlayChoiceFragment(normalized)) {
            score -= 120;
        }
        return score;
    }

    private static boolean isTrustedOverlayChoiceOptionFragment(String value) {
        Matcher matcher = OVERLAY_CHOICE_OPTION_START_PATTERN.matcher(normalizeDisplayText(value));
        return matcher.find() && matcher.start() == 0;
    }

    private static boolean endsWithDialogueCompletionPunctuation(String value) {
        String normalized = normalizeDisplayText(value);
        return !normalized.isBlank() && isDialogueCompletionPunctuation(normalized.charAt(normalized.length() - 1));
    }

    private static boolean looksLikeCompositeOverlayChoiceFragment(String value) {
        String normalized = normalizeDisplayText(value);
        if (normalized.isBlank()) {
            return false;
        }

        Matcher matcher = OVERLAY_CHOICE_OPTION_START_PATTERN.matcher(normalized);
        int matches = 0;
        while (matcher.find()) {
            matches++;
            if (matches > 1) {
                return true;
            }
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("can i ")) {
            return lower.contains(" bother you") || lower.contains(" other you");
        }
        if (lower.startsWith("can you ")) {
            return lower.contains(" been enjoying")
                    || lower.contains(" one of those chick")
                    || lower.contains(" of those chick")
                    || lower.contains(" won't bother");
        }
        if (lower.startsWith("have you ")) {
            return lower.contains(" tell me something")
                    || lower.contains(" one of those chick")
                    || lower.contains(" of those chick")
                    || lower.contains(" won't bother")
                    || lower.contains(" bother you");
        }
        if (lower.startsWith("i won't ")) {
            return lower.contains(" tell me something") || lower.contains(" been enjoying") || lower.contains(" one of those chick");
        }
        return false;
    }

    private static boolean looksLikeCompositeOverlayChoiceFragment(String value, List<String> existingLines) {
        String normalized = normalizeDisplayText(value);
        if (normalized.isBlank() || existingLines == null || existingLines.size() < 2) {
            return false;
        }

        String normalizedLower = normalized.toLowerCase(Locale.ROOT);
        int matchedLines = 0;
        for (String existingLine : existingLines) {
            if (overlayChoiceFragmentTouchesExistingLine(normalizedLower, existingLine)) {
                matchedLines++;
                if (matchedLines > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean overlayChoiceFragmentTouchesExistingLine(String normalizedFragmentLower, String existingLine) {
        String existingLower = normalizeDisplayText(existingLine).toLowerCase(Locale.ROOT);
        if (normalizedFragmentLower.isBlank() || existingLower.isBlank()) {
            return false;
        }
        if (Math.min(normalizedFragmentLower.length(), existingLower.length()) >= MIN_OVERLAY_CHOICE_MERGE_OVERLAP
                && (normalizedFragmentLower.contains(existingLower) || existingLower.contains(normalizedFragmentLower))) {
            return true;
        }
        if (overlayChoiceOptionCommonPrefixLength(existingLower, normalizedFragmentLower) >= MIN_OVERLAY_CHOICE_MERGE_OVERLAP) {
            return true;
        }
        if (Math.max(
                overlayChoiceOptionOverlap(existingLower, normalizedFragmentLower),
                overlayChoiceOptionOverlap(normalizedFragmentLower, existingLower)
        ) >= MIN_OVERLAY_CHOICE_MERGE_OVERLAP) {
            return true;
        }
        return containsOverlayChoiceLineAnchor(normalizedFragmentLower, existingLower);
    }

    private static boolean containsOverlayChoiceLineAnchor(String normalizedFragmentLower, String existingLower) {
        String[] words = existingLower.split("[^a-z0-9']+");
        for (int i = 0; i < words.length - 1; i++) {
            String first = words[i];
            String second = words[i + 1];
            if (!isStrongOverlayChoiceAnchorWord(first) && !isStrongOverlayChoiceAnchorWord(second)) {
                continue;
            }
            String phrase = first + " " + second;
            if (normalizedFragmentLower.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStrongOverlayChoiceAnchorWord(String word) {
        return word != null && (word.length() >= 4 || word.indexOf('\'') >= 0);
    }

    private static OverlayDialogueFallbackParse tryExtractFallbackOverlayDialogue(String readableText) {
        if (readableText == null || readableText.isBlank()) {
            return OverlayDialogueFallbackParse.rejected("filtered_blank");
        }

        OverlayNpcTailParse overlayNpc = extractOverlayNpcFromTail(readableText);
        if (overlayNpc.fullTail().isBlank() || overlayNpc.canonicalName().isBlank()) {
            return OverlayDialogueFallbackParse.rejected("npc_tail_missing");
        }

        String dialogue = readableText.substring(0, readableText.length() - overlayNpc.fullTail().length()).trim();
        dialogue = normalizeDisplayText(dialogue);
        if (dialogue.isBlank()) {
            return OverlayDialogueFallbackParse.rejected("dialogue_empty");
        }
        if (dialogue.length() < MIN_PREFIX_MATCH_LENGTH) {
            return OverlayDialogueFallbackParse.rejected("dialogue_too_short");
        }
        if (!containsHumanReadableLetters(dialogue)) {
            return OverlayDialogueFallbackParse.rejected("dialogue_not_readable");
        }
        return OverlayDialogueFallbackParse.matched(overlayNpc.canonicalName(), dialogue);
    }

    private static boolean containsHumanReadableLetters(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String extractNpcNameFromTail(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        Matcher matcher = OVERLAY_NPC_NAME_PATTERN.matcher(value.trim());
        if (!matcher.find()) {
            return "";
        }

        String candidate = matcher.group(1).trim();
        if (candidate.length() < 3 || candidate.length() > 30) {
            return "";
        }
        if (candidate.split(" ").length > 4) {
            return "";
        }
        return candidate;
    }

    private static OverlayNpcTailParse extractOverlayNpcFromTail(String value) {
        return extractOverlayNpcFromTail(value, "");
    }

    private static OverlayNpcTailParse extractOverlayNpcFromTail(String value, String preferredNpcName) {
        if (value == null || value.isBlank()) {
            return new OverlayNpcTailParse("", "");
        }

        String normalizedValue = normalizeDisplayText(value);
        String normalizedPreferredNpcName = normalizeDisplayText(preferredNpcName);
        if (endsWithPreferredOverlayNpcName(normalizedValue, normalizedPreferredNpcName)) {
            return new OverlayNpcTailParse(normalizedPreferredNpcName, normalizedPreferredNpcName);
        }

        Matcher matcher = OVERLAY_NPC_TAIL_PATTERN.matcher(normalizedValue);
        if (!matcher.find()) {
            return new OverlayNpcTailParse("", "");
        }

        String fullTail = matcher.group(1).trim();
        if (fullTail.length() < 3 || fullTail.length() > 40) {
            return new OverlayNpcTailParse("", "");
        }
        if (fullTail.split(" ").length > 5) {
            return new OverlayNpcTailParse("", "");
        }

        String canonicalName = extractNpcNameFromTail(fullTail);
        if (canonicalName.isBlank()) {
            canonicalName = fullTail;
        }

        return new OverlayNpcTailParse(fullTail, canonicalName);
    }

    private static boolean endsWithPreferredOverlayNpcName(String value, String preferredNpcName) {
        if (value == null || value.isBlank() || preferredNpcName == null || preferredNpcName.isBlank()) {
            return false;
        }
        if (!value.endsWith(preferredNpcName)) {
            return false;
        }
        int start = value.length() - preferredNpcName.length();
        if (start < 0) {
            return false;
        }
        if (start == 0) {
            return true;
        }
        char previous = value.charAt(start - 1);
        return !Character.isLetterOrDigit(previous);
    }

    private static boolean isLikelySameOverlayDialogue(String preparedDialogue) {
        if (preparedDialogue == null || preparedDialogue.isBlank() || lastOverlayPreparedDialogue.isBlank()) {
            return false;
        }
        if (preparedDialogue.length() < lastOverlayPreparedDialogue.length()) {
            return false;
        }

        String prefix = lastOverlayPreparedDialogue.length() >= SAME_DIALOGUE_PREFIX_LENGTH
                ? lastOverlayPreparedDialogue.substring(0, SAME_DIALOGUE_PREFIX_LENGTH)
                : lastOverlayPreparedDialogue;
        return preparedDialogue.startsWith(prefix);
    }

    private static void clearOverlayTracking() {
        if (overlayDialogueActive) {
            devLog("overlay_state_cleared");
        }
        lastOverlayPreparedDialogue = "";
        overlayDialogueActive = false;
        overlayDialogueQueued = false;
        lastOverlayChangedAt = 0L;
        lastOverlayObservedAt = 0L;
        lastOverlayQueuedAt = 0L;
        clearOverlayOptionsQueueTracking();
        lastOverlaySourcePlain = "";
        clearOverlayChoiceOptionsTracking();
    }

    private static boolean isMeaningfullyTranslated(String original, String translated) {
        if (translated == null || translated.isBlank()) {
            return false;
        }
        return !normalizeDisplayText(original).equalsIgnoreCase(normalizeDisplayText(translated));
    }

    private static String escapeForLog(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static String describeForLog(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(value.length(), 180);
        for (int i = 0; i < limit; i++) {
            char current = value.charAt(i);
            if (current >= 32 && current <= 126) {
                builder.append(current);
            } else if (current == '\n') {
                builder.append("\\n");
            } else if (current == '\r') {
                builder.append("\\r");
            } else if (current == '\t') {
                builder.append("\\t");
            } else {
                builder.append(String.format("\\u%04X", (int) current));
            }
        }
        if (value.length() > limit) {
            builder.append("...(len=").append(value.length()).append(')');
        }
        return escapeForLog(builder.toString());
    }

    private record ChoiceDialoguePrefixRemoval(boolean matched, String remainingText) {
    }

    private record OverlayChoicePayloadSplit(
            boolean matched,
            String dialogue,
            String optionsText,
            String rejectionReason
    ) {
        private static OverlayChoicePayloadSplit matched(String dialogue, String optionsText) {
            return new OverlayChoicePayloadSplit(
                    true,
                    dialogue == null ? "" : dialogue,
                    optionsText == null ? "" : optionsText,
                    ""
            );
        }

        private static OverlayChoicePayloadSplit rejected(String rejectionReason) {
            return new OverlayChoicePayloadSplit(
                    false,
                    "",
                    "",
                    rejectionReason == null ? "unknown" : rejectionReason
            );
        }
    }

    private static final class OverlayChoiceOptionsState {
        private final String dialogueKey;
        private final String npcName;
        private final List<String> lines = new ArrayList<>();

        private OverlayChoiceOptionsState(String dialogueKey, String npcName) {
            this.dialogueKey = dialogueKey == null ? "" : dialogueKey;
            this.npcName = npcName == null ? "" : npcName;
        }

        private boolean matches(String dialogueKey, String npcName) {
            return Objects.equals(this.dialogueKey, dialogueKey == null ? "" : dialogueKey)
                    && Objects.equals(this.npcName, normalizeDisplayText(npcName));
        }

        private boolean matchesCandidate(DialogueCandidate candidate) {
            return candidate != null
                    && matches(prepareDialogueValue(candidate.dialogue()), candidate.npcName());
        }

        private void merge(List<String> fragments) {
            if (fragments == null || fragments.isEmpty()) {
                return;
            }

            List<String> cleanFragments = new ArrayList<>();
            for (String fragment : fragments) {
                String cleanFragment = cleanOverlayChoiceOptionFragment(fragment);
                if (!cleanFragment.isBlank()) {
                    cleanFragments.add(cleanFragment);
                }
            }
            if (cleanFragments.isEmpty()) {
                return;
            }
            if (mergeOrderedFrame(cleanFragments)) {
                return;
            }

            for (String cleanFragment : cleanFragments) {
                merge(cleanFragment);
            }
        }

        private void merge(String fragment) {
            String cleanFragment = cleanOverlayChoiceOptionFragment(fragment);
            if (cleanFragment.isBlank()) {
                return;
            }
            if (looksLikeCompositeOverlayChoiceFragment(cleanFragment)
                    || looksLikeCompositeOverlayChoiceFragment(cleanFragment, lines)) {
                return;
            }

            int bestIndex = -1;
            int bestScore = 0;
            for (int i = 0; i < lines.size(); i++) {
                int score = overlayChoiceOptionMergeScore(lines.get(i), cleanFragment);
                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = i;
                }
            }

            if (bestIndex >= 0 && bestScore >= MIN_OVERLAY_CHOICE_MERGE_OVERLAP) {
                lines.set(bestIndex, mergeOverlayChoiceOptionText(lines.get(bestIndex), cleanFragment));
                return;
            }

            if (!isTrustedOverlayChoiceOptionFragment(cleanFragment)
                    && !startsStandaloneOverlayChoiceOptionLine(cleanFragment)) {
                return;
            }

            lines.add(cleanFragment);
        }

        private boolean mergeOrderedFrame(List<String> fragments) {
            if (fragments == null || fragments.size() < 2 || lines.size() != fragments.size()) {
                return false;
            }

            List<String> mergedLines = new ArrayList<>(lines);
            boolean[] used = new boolean[lines.size()];
            for (String fragment : fragments) {
                int bestIndex = -1;
                int bestScore = 0;
                for (int i = 0; i < lines.size(); i++) {
                    if (used[i]) {
                        continue;
                    }
                    int score = overlayChoiceOptionMergeScore(lines.get(i), fragment);
                    if (score > bestScore) {
                        bestScore = score;
                        bestIndex = i;
                    }
                }

                if (bestIndex < 0 || bestScore < MIN_OVERLAY_CHOICE_MERGE_OVERLAP) {
                    return false;
                }

                used[bestIndex] = true;
                mergedLines.set(bestIndex, mergeOverlayChoiceOptionText(lines.get(bestIndex), fragment));
            }

            lines.clear();
            lines.addAll(mergedLines);
            return true;
        }

        private String displayText() {
            List<String> displayLines = new ArrayList<>();
            for (String line : lines) {
                String cleanLine = cleanOverlayChoiceOptionFragment(line);
                if (!cleanLine.isBlank()) {
                    displayLines.add(cleanLine);
                }
            }
            return String.join("\n", displayLines);
        }
    }

    private enum OverlayPromptKind {
        CONTINUE,
        CONFIRM,
        CHOOSE_OPTION
    }

    private record OverlayPromptMarker(OverlayPromptKind kind, int index, String markerText) {
        private int endIndex() {
            return index + markerText.length();
        }

        private String mode() {
            return switch (kind) {
                case CONTINUE -> "continue_marker";
                case CONFIRM -> "confirm_marker";
                case CHOOSE_OPTION -> "choice_options";
            };
        }
    }

    record OverlayReadableParse(
            boolean matched,
            String mode,
            int promptIndex,
            boolean choicePrompt,
            String npcName,
            String dialogue,
            String optionsText,
            String rejectionReason
    ) {
        private static OverlayReadableParse matched(
                String mode,
                int promptIndex,
                boolean choicePrompt,
                String npcName,
                String dialogue,
                String optionsText
        ) {
            return new OverlayReadableParse(
                    true,
                    mode == null ? "" : mode,
                    promptIndex,
                    choicePrompt,
                    npcName == null ? "" : npcName,
                    dialogue == null ? "" : dialogue,
                    optionsText == null ? "" : optionsText,
                    ""
            );
        }

        private static OverlayReadableParse rejected(boolean choicePrompt, String rejectionReason) {
            return new OverlayReadableParse(
                    false,
                    "",
                    -1,
                    choicePrompt,
                    "",
                    "",
                    "",
                    rejectionReason == null ? "unknown" : rejectionReason
            );
        }
    }

    private record DialogueCandidate(
            long observedNonce,
            String pageInfo,
            String npcName,
            String dialogue,
            boolean overlaySource,
            String overlaySourcePlain,
            String optionsText
    ) {
        private DialogueCandidate {
            optionsText = optionsText == null ? "" : optionsText;
        }
    }

    private record OverlayNpcTailParse(String fullTail, String canonicalName) {
    }

    private record OverlayDialogueFallbackParse(
            boolean matched,
            String npcName,
            String dialogue,
            String rejectionReason
    ) {
        private static OverlayDialogueFallbackParse matched(String npcName, String dialogue) {
            return new OverlayDialogueFallbackParse(
                    true,
                    npcName == null ? "" : npcName,
                    dialogue == null ? "" : dialogue,
                    ""
            );
        }

        private static OverlayDialogueFallbackParse rejected(String rejectionReason) {
            return new OverlayDialogueFallbackParse(false, "", "", rejectionReason == null ? "unknown" : rejectionReason);
        }
    }

    private record PresentedDialogueState(
            long observedNonce,
            String pageInfo,
            String originalNpcName,
            String displayedNpcName,
            String originalDialogue,
            String displayedDialogue,
            String originalOptionsText,
            String displayedOptionsText,
            boolean pending,
            String animationKey,
            long displayUntilEpochMillis
    ) {
        private PresentedDialogueState {
            pageInfo = pageInfo == null ? "" : pageInfo;
            originalNpcName = originalNpcName == null ? "" : originalNpcName;
            displayedNpcName = displayedNpcName == null ? "" : displayedNpcName;
            originalDialogue = originalDialogue == null ? "" : originalDialogue;
            displayedDialogue = displayedDialogue == null ? "" : displayedDialogue;
            originalOptionsText = originalOptionsText == null ? "" : originalOptionsText;
            displayedOptionsText = displayedOptionsText == null ? "" : displayedOptionsText;
            animationKey = animationKey == null ? "" : animationKey;
        }

        private PresentedDialogueState(
                String pageInfo,
                String originalNpcName,
                String displayedNpcName,
                String originalDialogue,
                String displayedDialogue,
                String originalOptionsText,
                String displayedOptionsText,
                boolean pending,
                String animationKey,
                long displayUntilEpochMillis
        ) {
            this(
                    System.nanoTime(),
                    pageInfo == null ? "" : pageInfo,
                    originalNpcName == null ? "" : originalNpcName,
                    displayedNpcName == null ? "" : displayedNpcName,
                    originalDialogue == null ? "" : originalDialogue,
                    displayedDialogue == null ? "" : displayedDialogue,
                    originalOptionsText == null ? "" : originalOptionsText,
                    displayedOptionsText == null ? "" : displayedOptionsText,
                    pending,
                    animationKey == null ? "" : animationKey,
                    displayUntilEpochMillis
            );
        }
    }

    private record DialogueDisplayState(
            String displayText,
            boolean pending,
            String animationKey
    ) {
        private static DialogueDisplayState of(String displayText, boolean pending, String animationKey) {
            return new DialogueDisplayState(
                    displayText == null ? "" : displayText,
                    pending,
                    animationKey == null ? "" : animationKey
            );
        }
    }
}
