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
    private static final String DIALOGUE_KEY_PREFIX = "dialogue::";
    private static final String NPC_KEY_PREFIX = "npc::";
    private static final String PLAYER_PLACEHOLDER = "{player}";
    private static final String CONTINUE_MARKER = "to continue";
    private static final String CONTINUE_MARKER_WITH_SPACE = " to continue";
    private static final int MIN_OVERLAY_RAW_LENGTH = 150;
    private static final int MIN_OVERLAY_DIALOGUE_LENGTH = 15;
    private static final int MIN_PREFIX_MATCH_LENGTH = 5;
    private static final int SAME_DIALOGUE_PREFIX_LENGTH = 10;
    private static final long OVERLAY_STABLE_DELAY_MILLIS = 1000L;
    private static final long OVERLAY_QUEUE_THROTTLE_MILLIS = 100L;
    private static final long DEBUG_CHAT_LOG_THROTTLE_MILLIS = 1500L;
    private static final long DEBUG_OVERLAY_LOG_THROTTLE_MILLIS = 500L;
    private static final long DEBUG_HUD_LOG_THROTTLE_MILLIS = 1000L;
    private static final long DIALOGUES_LOCAL_HIT_LOG_THROTTLE_MILLIS = 5000L;

    private static final Map<String, Long> DEBUG_LOG_TIMESTAMPS = new ConcurrentHashMap<>();

    private static volatile DialogueCandidate currentCandidate;
    private static volatile String lastOverlayPreparedDialogue = "";
    private static volatile boolean overlayDialogueActive;
    private static volatile boolean overlayDialogueQueued;
    private static volatile long lastOverlayChangedAt;
    private static volatile long lastOverlayObservedAt;
    private static volatile long lastOverlayQueuedAt;
    private static volatile String lastOverlaySourcePlain = "";
    private static volatile String lastPresentedPayload = "";
    private static volatile PresentedDialogueState lastPresentedState;
    private static final Set<String> refreshedDialogueKeysThisHold = ConcurrentHashMap.newKeySet();

    private WynnDialogueTranslationSupport() {
    }

    public static void init() {
        localDictionary().load();
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
        registerCandidate(new DialogueCandidate(System.nanoTime(), pageInfo, npcName, dialogue, false, ""));
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
        throttledDevLog(
                "overlay_observed",
                DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                "overlay_observed rawLen={} filteredLen={} rawPreview=\"{}\" filteredPreview=\"{}\"",
                raw.length(),
                readableText.length(),
                describeForLog(raw),
                describeForLog(readableText)
        );
        int continueIndex = indexOfContinueMarker(readableText);
        if (continueIndex < 0) {
            throttledDevLog(
                    "overlay_no_continue_marker",
                    DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                    "overlay_ignored reason=no_continue_marker filteredPreview=\"{}\"",
                    describeForLog(readableText)
            );
            clearOverlayTracking();
            return;
        }

        int markerLength = readableText.startsWith(CONTINUE_MARKER_WITH_SPACE, continueIndex)
                ? CONTINUE_MARKER_WITH_SPACE.length()
                : CONTINUE_MARKER.length();
        String dialogueWithNpc = readableText.substring(continueIndex + markerLength).trim();
        if (dialogueWithNpc.isBlank()) {
            return;
        }

        OverlayNpcTailParse overlayNpc = extractOverlayNpcFromTail(dialogueWithNpc);
        String npcName = overlayNpc.canonicalName();
        String dialogue = overlayNpc.fullTail().isBlank()
                ? dialogueWithNpc
                : dialogueWithNpc.substring(0, dialogueWithNpc.length() - overlayNpc.fullTail().length()).trim();
        dialogue = normalizeDisplayText(dialogue);
        String preparedDialogue = prepareDialogueValue(dialogue);
        boolean sameDialogue = isLikelySameOverlayDialogue(preparedDialogue);
        if (!sameDialogue) {
            overlayDialogueQueued = false;
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
        long now = System.currentTimeMillis();
        lastOverlayPreparedDialogue = preparedDialogue;
        lastOverlayChangedAt = WynnDialogueQueuePolicy.resolveOverlayChangedAt(sameDialogue, lastOverlayChangedAt, now);
        lastOverlayObservedAt = now;
        lastOverlaySourcePlain = sourcePlain;
        overlayDialogueActive = true;

        throttledDevLog(
                "overlay_matched",
                DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                "overlay_matched continueIndex={} npc=\"{}\" dialogue=\"{}\" sameDialogue={}",
                continueIndex,
                describeForLog(npcName),
                describeForLog(dialogue),
                sameDialogue
        );
        registerCandidate(new DialogueCandidate(System.nanoTime(), "", npcName, dialogue, true, sourcePlain));
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
    }

    public static void resetSession() {
        currentCandidate = null;
        clearOverlayTracking();
        lastPresentedPayload = "";
        lastPresentedState = null;
        refreshedDialogueKeysThisHold.clear();
        WynnDialogueHudRenderer.clear();
    }

    public static void onCacheTranslationsUpdated(Map<String, String> translations) {
        if (translations == null || translations.isEmpty()) {
            return;
        }

        Map<String, String> snapshot = Map.copyOf(translations);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> applyCacheTranslationsToHud(snapshot));
            return;
        }
        applyCacheTranslationsToHud(snapshot);
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

    private static WynnDialogueLocalDictionary localDictionary() {
        return WynnDialogueLocalDictionary.getInstance();
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
        return config != null && config.log_dialogues_local_hits;
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

    private static void registerCandidate(DialogueCandidate candidate) {
        currentCandidate = candidate;
        if (candidate != null) {
            throttledDevLog(
                "candidate_registered_" + (candidate.overlaySource() ? "overlay" : "chat"),
                candidate.overlaySource() ? DEBUG_OVERLAY_LOG_THROTTLE_MILLIS : DEBUG_CHAT_LOG_THROTTLE_MILLIS,
                "candidate_registered source={} page={} npc=\"{}\" dialogue=\"{}\"",
                candidate.overlaySource() ? "overlay" : "chat",
                candidate.pageInfo(),
                describeForLog(candidate.npcName()),
                describeForLog(candidate.dialogue())
            );
        }
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
        maybeForceRefreshCurrentCandidate(candidate, shouldRequestTranslations);

        boolean allowDisplayQueue = WynnDialogueQueuePolicy.shouldAllowDisplayQueue(
                candidate != null && candidate.overlaySource(),
                shouldRequestTranslations
        );
        DialogueDisplayState dialogueState = shouldRenderTranslated
                ? resolveDialogueDisplayState(candidate.dialogue(), candidate.overlaySource(), allowDisplayQueue)
                : DialogueDisplayState.of(candidate.dialogue(), false, "");
        boolean meaningfullyTranslated = shouldRenderTranslated
                && !dialogueState.pending()
                && isMeaningfullyTranslated(candidate.dialogue(), dialogueState.displayText());
        if (shouldDelayShortOverlayFallback(candidate, meaningfullyTranslated)) {
            return;
        }

        String displayDialogue = dialogueState.displayText();

        String translatedNpcName = shouldRenderTranslated
                ? resolveNpcName(candidate.npcName(), shouldRequestTranslations)
                : candidate.npcName();
        String presentedPayload = candidate.observedNonce()
                + "\n" + candidate.pageInfo()
                + "\n" + translatedNpcName
                + "\n" + candidate.dialogue()
                + "\n" + displayDialogue
                + "\n" + dialogueState.pending()
                + "\n" + dialogueState.animationKey();
        if (presentedPayload.equals(lastPresentedPayload)) {
            return;
        }

        WynnDialogueHudRenderer.showDialogue(
                candidate.pageInfo(),
                translatedNpcName,
                candidate.dialogue(),
                displayDialogue,
                dialogueState.pending() && shouldRenderTranslated,
                dialogueState.animationKey()
        );
        lastPresentedState = new PresentedDialogueState(
                candidate.observedNonce(),
                candidate.pageInfo(),
                candidate.npcName(),
                translatedNpcName,
                candidate.dialogue(),
                displayDialogue,
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

    private static void applyCacheTranslationsToHud(Map<String, String> translations) {
        refreshCurrentDialogueDisplay();

        PresentedDialogueState presentedState = lastPresentedState;
        if (presentedState == null || System.currentTimeMillis() > presentedState.displayUntilEpochMillis()) {
            return;
        }

        String updatedNpcName = presentedState.displayedNpcName();
        String updatedDialogue = presentedState.displayedDialogue();
        boolean changed = false;

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

        if (!changed) {
            return;
        }

        WynnDialogueHudRenderer.showDialogue(
                presentedState.pageInfo(),
                updatedNpcName,
                presentedState.originalDialogue(),
                updatedDialogue,
                false,
                ""
        );
        PresentedDialogueState refreshedState = new PresentedDialogueState(
                presentedState.observedNonce(),
                presentedState.pageInfo(),
                presentedState.originalNpcName(),
                updatedNpcName,
                presentedState.originalDialogue(),
                updatedDialogue,
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
                + "\nfalse\n";
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
        DialogueCandidate candidate = new DialogueCandidate(42L, "[1/1]", "Test NPC", "Test dialogue", false, "");
        currentCandidate = candidate;
        lastPresentedState = new PresentedDialogueState(
                candidateMatchesPresentedState ? candidate.observedNonce() : candidate.observedNonce() + 1L,
                candidate.pageInfo(),
                candidate.npcName(),
                candidate.npcName(),
                candidate.dialogue(),
                "Translated dialogue",
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

    private static void maybeForceRefreshCurrentCandidate(DialogueCandidate candidate, boolean allowRequests) {
        if (!allowRequests || candidate == null || !isSharedRefreshHotkeyPressed()) {
            refreshedDialogueKeysThisHold.clear();
            return;
        }

        ConcurrentHashMap<String, Boolean> refreshKeys = new ConcurrentHashMap<>();
        String dialogueKey = buildDialogueCacheKey(candidate.dialogue());
        if (!dialogueKey.isBlank() && !localDictionary().hasDialogueTranslation(prepareDialogueValue(candidate.dialogue()))) {
            refreshKeys.put(dialogueKey, Boolean.TRUE);
        }

        if (shouldTranslateNpcNames()
                && candidate.npcName() != null
                && !candidate.npcName().isBlank()
                && !localDictionary().hasNpcTranslation(prepareNpcValue(candidate.npcName()))) {
            refreshKeys.put(buildNpcCacheKey(candidate.npcName()), Boolean.TRUE);
        }

        if (refreshKeys.isEmpty()) {
            return;
        }

        if (!refreshedDialogueKeysThisHold.addAll(refreshKeys.keySet())) {
            return;
        }

        int refreshedCount = cache().forceRefresh(refreshKeys.keySet());
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
        if (candidate == null || !candidate.overlaySource() || overlayDialogueQueued) {
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

        overlayDialogueQueued = true;
        lastOverlayQueuedAt = now;
        throttledDevLog(
                "overlay_queue_ready",
                DEBUG_OVERLAY_LOG_THROTTLE_MILLIS,
                "overlay_queue_ready npc=\"{}\" dialogue=\"{}\" stableMs={}",
                describeForLog(candidate.npcName()),
                describeForLog(candidate.dialogue()),
                now - lastOverlayChangedAt
        );
        queueDialogueTranslationIfPossible(candidate.dialogue(), true);
    }

    private static void queueDialogueTranslationIfPossible(String dialogue, boolean allowLocalPrefixShortCircuit) {
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
            return;
        }

        String preparedDialogue = prepareDialogueValue(dialogue);
        if (localDictionary().hasDialogueTranslation(preparedDialogue)) {
            String localTranslation = localDictionary().findDialogueTranslation(preparedDialogue);
            throttledDialoguesLocalHitLog(
                    "exact",
                    preparedDialogue,
                    localTranslation == null ? "" : restorePlayerPlaceholders(localTranslation),
                    "queue_skip"
            );
            return;
        }
        if (allowLocalPrefixShortCircuit && localDictionary().findDialogueByPrefix(preparedDialogue) != null) {
            String localTranslation = localDictionary().findDialogueByPrefix(preparedDialogue);
            throttledDialoguesLocalHitLog(
                    "prefix",
                    preparedDialogue,
                    localTranslation == null ? "" : restorePlayerPlaceholders(localTranslation),
                    "queue_skip"
            );
            return;
        }

        if (!hasConfiguredRoute()) {
            throttledDevLog(
                    "queue_dialogue_no_route",
                    DEBUG_HUD_LOG_THROTTLE_MILLIS,
                    "queue_skipped type=dialogue reason=no_route dialogue=\"{}\"",
                    describeForLog(dialogue)
            );
            return;
        }
        cache().lookupOrQueue(buildDialogueCacheKey(dialogue));
        throttledDevLog(
                "queue_dialogue",
                DEBUG_HUD_LOG_THROTTLE_MILLIS,
                "queue_dialogue key=\"{}\"",
                describeForLog(buildDialogueCacheKey(dialogue))
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
        if (localDictionary().hasNpcTranslation(prepareNpcValue(npcName))) {
            String localTranslation = localDictionary().findNpcTranslation(prepareNpcValue(npcName));
            throttledDialoguesLocalHitLog(
                    "exact",
                    npcName,
                    localTranslation == null ? "" : localTranslation,
                    "queue_skip_npc"
            );
            return;
        }
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

        String localTranslation = localDictionary().findNpcTranslation(prepareNpcValue(npcName));
        if (localTranslation != null && !localTranslation.isBlank()) {
            throttledDialoguesLocalHitLog("exact", npcName, localTranslation, "resolve_npc");
            return localTranslation;
        }

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
        String localTranslation = localDictionary().findDialogueTranslation(preparedDialogue);
        if (localTranslation != null && !localTranslation.isBlank()) {
            String restoredTranslation = restorePlayerPlaceholders(localTranslation);
            throttledDialoguesLocalHitLog("exact", preparedDialogue, restoredTranslation, "resolve");
            return DialogueDisplayState.of(restoredTranslation, false, "");
        }

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

    private static String findDialogueByPrefix(String preparedDialogue) {
        if (preparedDialogue == null || preparedDialogue.length() < MIN_PREFIX_MATCH_LENGTH) {
            return null;
        }

        String localPrefixTranslation = localDictionary().findDialogueByPrefix(preparedDialogue);
        if (localPrefixTranslation != null && !localPrefixTranslation.isBlank()) {
            String restoredTranslation = restorePlayerPlaceholders(localPrefixTranslation);
            throttledDialoguesLocalHitLog("prefix", preparedDialogue, restoredTranslation, "resolve");
            return restoredTranslation;
        }

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

    private static String buildDialogueCacheKey(String dialogue) {
        return DIALOGUE_KEY_PREFIX + prepareDialogueValue(dialogue);
    }

    private static String buildNpcCacheKey(String npcName) {
        return NPC_KEY_PREFIX + prepareNpcValue(npcName);
    }

    private static String prepareDialogueValue(String dialogue) {
        return replacePlayerNameWithPlaceholder(normalizeDisplayText(dialogue));
    }

    private static String prepareNpcValue(String npcName) {
        return normalizeDisplayText(npcName);
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
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if ((current >= 32 && current <= 126) || (current >= 160 && current <= 255)) {
                builder.append(current);
            }
        }
        return normalizeDisplayText(builder.toString());
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

    private static int indexOfContinueMarker(String readableText) {
        if (readableText == null || readableText.isBlank()) {
            return -1;
        }

        int withSpaceIndex = readableText.indexOf(CONTINUE_MARKER_WITH_SPACE);
        if (withSpaceIndex >= 0) {
            return withSpaceIndex;
        }
        return readableText.indexOf(CONTINUE_MARKER);
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
        if (value == null || value.isBlank()) {
            return new OverlayNpcTailParse("", "");
        }

        Matcher matcher = OVERLAY_NPC_TAIL_PATTERN.matcher(value.trim());
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
        lastOverlaySourcePlain = "";
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

    private record DialogueCandidate(
            long observedNonce,
            String pageInfo,
            String npcName,
            String dialogue,
            boolean overlaySource,
            String overlaySourcePlain
    ) {
    }

    private record OverlayNpcTailParse(String fullTail, String canonicalName) {
    }

    private record PresentedDialogueState(
            long observedNonce,
            String pageInfo,
            String originalNpcName,
            String displayedNpcName,
            String originalDialogue,
            String displayedDialogue,
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
            animationKey = animationKey == null ? "" : animationKey;
        }

        private PresentedDialogueState(
                String pageInfo,
                String originalNpcName,
                String displayedNpcName,
                String originalDialogue,
                String displayedDialogue,
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
