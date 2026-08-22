package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.mixin.mixinWynnDialogue.WynnDialogueInGameHudAccessor;
import com.alexeys.translate_allinone.mixin.mixinWynnDialogue.WynnDialogueTextRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class WynnDialogueOverlayController {
    private static final WynnDialogueOverlayController INSTANCE = new WynnDialogueOverlayController(
            new MinecraftOverlayAccess(),
            new HudPresentationSink() {
                @Override
                public void present(WynnDialoguePresentation presentation) {
                    WynnDialogueHudPresenter.present(presentation);
                }

                @Override
                public void clear() {
                    WynnDialogueHudPresenter.clear();
                }
            },
            new WynnDialogueInlinePresenter(
                    WynnDialogueOverlayController::measureText,
                    WynnDialogueOverlayController::supportsText
            ),
            original -> {
                WynnDialogueTranslationSupport.traceOverlayEntry(original);
                WynnDialogueTranslationSupport.handleOverlayMessage(original);
                return WynnDialogueTranslationSupport.currentPresentation();
            },
            WynnDialogueTranslationSupport::useHudPresentation,
            WynnDialogueTranslationSupport::shouldRenderTranslatedNow,
            WynnDialogueTranslationSupport::isTranslationFeatureEnabled,
            WynnDialogueTranslationSupport::shouldTranslateNpcNames,
            WynnDialogueTranslationSupport::shouldTranslateNpcOptions,
            WynnDialogueTranslationSupport::hasConfiguredRoute
    );

    private final OverlayAccess overlayAccess;
    private final HudPresentationSink hudSink;
    private final WynnDialogueInlinePresenter inlinePresenter;
    private final OverlayMessageObserver overlayMessageObserver;
    private final BooleanSupplier useHud;
    private final BooleanSupplier showTranslated;
    private final BooleanSupplier enabled;
    private final BooleanSupplier translateNpcName;
    private final BooleanSupplier translateOptions;
    private final BooleanSupplier translationRouteAvailable;

    private ActiveOverlayTemplate activeTemplate;
    private WynnDialoguePresentation currentPresentation;
    private WynnDialoguePresentation lastTranslatedPresentation;
    private Boolean previousHudMode;
    private Boolean previousShowTranslated;
    private Boolean previousEnabled;
    private boolean transforming;

    public static WynnDialogueOverlayController getInstance() {
        return INSTANCE;
    }

    WynnDialogueOverlayController(
            OverlayAccess overlayAccess,
            HudPresentationSink hudSink,
            WynnDialogueInlinePresenter inlinePresenter,
            OverlayMessageObserver overlayMessageObserver,
            BooleanSupplier useHud,
            BooleanSupplier showTranslated,
            BooleanSupplier enabled
    ) {
        this(
                overlayAccess,
                hudSink,
                inlinePresenter,
                overlayMessageObserver,
                useHud,
                showTranslated,
                enabled,
                () -> true,
                () -> true,
                () -> true
        );
    }

    WynnDialogueOverlayController(
            OverlayAccess overlayAccess,
            HudPresentationSink hudSink,
            WynnDialogueInlinePresenter inlinePresenter,
            OverlayMessageObserver overlayMessageObserver,
            BooleanSupplier useHud,
            BooleanSupplier showTranslated,
            BooleanSupplier enabled,
            BooleanSupplier translateNpcName,
            BooleanSupplier translateOptions,
            BooleanSupplier translationRouteAvailable
    ) {
        this.overlayAccess = overlayAccess;
        this.hudSink = hudSink;
        this.inlinePresenter = inlinePresenter;
        this.overlayMessageObserver = overlayMessageObserver;
        this.useHud = useHud;
        this.showTranslated = showTranslated;
        this.enabled = enabled;
        this.translateNpcName = translateNpcName;
        this.translateOptions = translateOptions;
        this.translationRouteAvailable = translationRouteAvailable;
    }

    public synchronized Text transformOverlay(Text original) {
        if (!enabled.getAsBoolean()) {
            disablePresentation();
            return original;
        }
        WynnDialogueTextTemplate template = WynnDialogueTextTemplateParser.parse(original);
        if (template == null) {
            observeNonDialogueOverlay();
            return original;
        }

        beginObservation(template);
        transforming = true;
        try {
            WynnDialoguePresentation presentation = overlayMessageObserver.observe(original);
            if (presentation != null) {
                acceptPresentation(presentation, false);
            }
            return renderForSetter();
        } finally {
            transforming = false;
        }
    }

    public synchronized void onPresentationUpdated(WynnDialoguePresentation presentation) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        acceptPresentation(presentation, true);
    }

    synchronized void onForceRefreshStarted(long observedNonce) {
        if (activeTemplate == null
                || (activeTemplate.observedNonce() != 0L
                && activeTemplate.observedNonce() != observedNonce)) {
            return;
        }
        if (currentPresentation != null && currentPresentation.observedNonce() != observedNonce) {
            return;
        }
        currentPresentation = null;
        lastTranslatedPresentation = null;
    }

    private synchronized void observeNonDialogueOverlay() {
        activeTemplate = null;
        currentPresentation = null;
        lastTranslatedPresentation = null;
    }

    public synchronized void tick() {
        boolean featureEnabled = enabled.getAsBoolean();
        boolean hudMode = useHud.getAsBoolean();
        boolean translated = showTranslated.getAsBoolean();
        if (!featureEnabled) {
            disablePresentation();
            previousEnabled = false;
            previousHudMode = hudMode;
            previousShowTranslated = translated;
            return;
        }
        previousEnabled = true;
        if (activeTemplate != null && overlayAccess.remainingTicks() <= 0) {
            activeTemplate = null;
            currentPresentation = null;
            lastTranslatedPresentation = null;
        }

        if (previousHudMode == null) {
            previousHudMode = hudMode;
            previousShowTranslated = translated;
            return;
        }

        if (previousHudMode != hudMode) {
            if (hudMode) {
                restoreOriginalOverlay();
                if (activeTemplate != null && currentPresentation != null) {
                    hudSink.present(currentPresentation);
                }
            } else {
                hudSink.clear();
                replaceActiveOverlay();
            }
        } else if (!hudMode && !Objects.equals(previousShowTranslated, translated)) {
            replaceActiveOverlay();
        }

        previousHudMode = hudMode;
        previousShowTranslated = translated;
    }

    public synchronized void reset() {
        activeTemplate = null;
        currentPresentation = null;
        lastTranslatedPresentation = null;
        previousHudMode = null;
        previousShowTranslated = null;
        previousEnabled = null;
        transforming = false;
        hudSink.clear();
    }

    synchronized void presentationExpired(long observedNonce) {
        if (currentPresentation != null && currentPresentation.observedNonce() == observedNonce) {
            currentPresentation = null;
        }
        if (lastTranslatedPresentation != null
                && lastTranslatedPresentation.observedNonce() == observedNonce) {
            lastTranslatedPresentation = null;
        }
        if (activeTemplate != null
                && activeTemplate.observedNonce() == observedNonce
                && overlayAccess.remainingTicks() <= 0) {
            activeTemplate = null;
            currentPresentation = null;
            lastTranslatedPresentation = null;
        }
        hudSink.clear();
    }

    private void beginObservation(WynnDialogueTextTemplate template) {
        if (template == null) {
            observeNonDialogueOverlay();
            return;
        }
        if (activeTemplate == null
                || !Objects.equals(activeTemplate.sourceFingerprint(), template.sourceFingerprint())) {
            if (currentPresentation != null
                    && canReuseTranslatedPresentation(template, currentPresentation)
                    && hasMeaningfullyTranslatedContent(currentPresentation)) {
                lastTranslatedPresentation = retainCompletedOptionTranslations(
                        currentPresentation,
                        lastTranslatedPresentation
                );
            }
            if (lastTranslatedPresentation != null
                    && !canReuseTranslatedPresentation(template, lastTranslatedPresentation)) {
                lastTranslatedPresentation = null;
            }
            currentPresentation = null;
        }
        activeTemplate = new ActiveOverlayTemplate(
                0L,
                template.sourceFingerprint(),
                template,
                template.originalText()
        );
        previousHudMode = useHud.getAsBoolean();
        previousShowTranslated = showTranslated.getAsBoolean();
        previousEnabled = true;
    }

    private void acceptPresentation(WynnDialoguePresentation presentation, boolean refreshOverlay) {
        if (presentation == null || activeTemplate == null) {
            return;
        }
        boolean hudMode = useHud.getAsBoolean();
        boolean compatibleWithActive = sourcesCompatible(activeTemplate.template(), presentation);
        if (!compatibleWithActive) {
            if (!hudMode
                    && canReuseTranslatedPresentation(activeTemplate.template(), presentation)
                    && hasMeaningfullyTranslatedContent(presentation)) {
                lastTranslatedPresentation = retainCompletedOptionTranslations(
                        presentation,
                        lastTranslatedPresentation
                );
                if (refreshOverlay) {
                    replaceActiveOverlay();
                }
            }
            return;
        }
        boolean equivalentSource = WynnDialogueInlinePresenter.matches(activeTemplate.template(), presentation)
                || cleanedSourcesMatch(activeTemplate.template(), presentation);
        if (!equivalentSource) {
            if (!hudMode
                    && canReuseTranslatedPresentation(activeTemplate.template(), presentation)
                    && hasMeaningfullyTranslatedContent(presentation)) {
                lastTranslatedPresentation = retainCompletedOptionTranslations(
                        presentation,
                        lastTranslatedPresentation
                );
                if (refreshOverlay) {
                    replaceActiveOverlay();
                }
            }
            return;
        }
        boolean matchesActiveNonce = activeTemplate.observedNonce() == 0L
                || activeTemplate.observedNonce() == presentation.observedNonce();
        if (!matchesActiveNonce) {
            return;
        }
        activeTemplate = new ActiveOverlayTemplate(
                presentation.observedNonce(),
                activeTemplate.sourceFingerprint(),
                activeTemplate.template(),
                activeTemplate.lastInstalledText()
        );
        boolean presentationChanged = !Objects.equals(currentPresentation, presentation);
        currentPresentation = presentation;
        if (hudMode) {
            if (presentationChanged) {
                hudSink.present(presentation);
            }
            return;
        }
        if (refreshOverlay) {
            replaceActiveOverlay();
        }
    }

    private Text renderForSetter() {
        if (activeTemplate == null) {
            return Text.empty();
        }
        Text rendered = activeTemplate.template().originalText();
        if (!useHud.getAsBoolean() && showTranslated.getAsBoolean()) {
            boolean translateName = translateNpcName.getAsBoolean();
            boolean translateChoices = translateOptions.getAsBoolean();
            if (!hasTerminalError(currentPresentation)) {
                WynnDialogueInlineRenderResult currentResult = renderCurrentPresentation(
                        translateName,
                        translateChoices
                );
                if (currentResult.outcome() == WynnDialogueInlineRenderResult.Outcome.TRANSLATED) {
                    rendered = currentResult.text();
                } else {
                    WynnDialogueInlineRenderResult progressiveResult = renderProgressivePresentation(
                            translateName,
                            translateChoices
                    );
                    if (progressiveResult.outcome() == WynnDialogueInlineRenderResult.Outcome.TRANSLATED) {
                        rendered = progressiveResult.text();
                    } else if (!translationRouteAvailable.getAsBoolean()
                            && !hasMeaningfullyTranslatedContent(currentPresentation)) {
                        rendered = activeTemplate.template().originalText();
                    } else if (currentResult.outcome() == WynnDialogueInlineRenderResult.Outcome.ANIMATING) {
                        rendered = currentResult.text();
                    } else {
                        WynnDialogueInlineRenderResult masked = inlinePresenter.renderResult(
                                activeTemplate.template(),
                                null,
                                translateName,
                                translateChoices
                        );
                        rendered = masked.text();
                    }
                }
            }
        }
        activeTemplate = new ActiveOverlayTemplate(
                activeTemplate.observedNonce(),
                activeTemplate.sourceFingerprint(),
                activeTemplate.template(),
                rendered
        );
        return rendered;
    }

    private WynnDialogueInlineRenderResult renderCurrentPresentation(
            boolean translateName,
            boolean translateChoices
    ) {
        if (currentPresentation == null
                || activeTemplate.observedNonce() != currentPresentation.observedNonce()) {
            return rejectedRenderResult();
        }
        WynnDialoguePresentation previousTranslatedPresentation = lastTranslatedPresentation;
        WynnDialoguePresentation alignedPresentation = alignPresentation(
                activeTemplate.template(),
                currentPresentation
        );
        WynnDialoguePresentation renderPresentation = reuseCompletedOptionTranslations(
                alignedPresentation,
                previousTranslatedPresentation
        );
        WynnDialogueInlineRenderResult result = inlinePresenter.renderResult(
                activeTemplate.template(),
                renderPresentation,
                translateName,
                translateChoices
        );
        if (result.outcome() == WynnDialogueInlineRenderResult.Outcome.TRANSLATED) {
            lastTranslatedPresentation = rememberCompletedOptionTranslations(
                    alignedPresentation,
                    previousTranslatedPresentation
            );
        }
        return result;
    }

    private WynnDialogueInlineRenderResult renderProgressivePresentation(
            boolean translateName,
            boolean translateChoices
    ) {
        if (lastTranslatedPresentation == null
                || !canReuseTranslatedPresentation(activeTemplate.template(), lastTranslatedPresentation)) {
            return rejectedRenderResult();
        }
        WynnDialoguePresentation progressivePresentation = applyQueuedOptionStates(
                alignPresentation(activeTemplate.template(), lastTranslatedPresentation),
                currentPresentation
        );
        return inlinePresenter.renderResult(
                activeTemplate.template(),
                progressivePresentation,
                translateName,
                translateChoices
        );
    }

    private static WynnDialogueInlineRenderResult rejectedRenderResult() {
        return new WynnDialogueInlineRenderResult(
                Text.empty(),
                WynnDialogueInlineRenderResult.Outcome.REJECTED
        );
    }

    private void replaceActiveOverlay() {
        if (transforming || activeTemplate == null || overlayAccess.remainingTicks() <= 0) {
            return;
        }
        Text current = overlayAccess.currentText();
        if (!Objects.equals(current, activeTemplate.lastInstalledText())
                && !Objects.equals(current, activeTemplate.template().originalText())) {
            activeTemplate = null;
            currentPresentation = null;
            lastTranslatedPresentation = null;
            return;
        }
        Text replacement = renderForSetter();
        if (!Objects.equals(current, replacement)) {
            overlayAccess.replaceText(replacement);
        }
    }

    private void restoreOriginalOverlay() {
        if (activeTemplate == null || overlayAccess.remainingTicks() <= 0) {
            return;
        }
        Text current = overlayAccess.currentText();
        if (!Objects.equals(current, activeTemplate.lastInstalledText())
                && !Objects.equals(current, activeTemplate.template().originalText())) {
            activeTemplate = null;
            currentPresentation = null;
            lastTranslatedPresentation = null;
            return;
        }
        Text original = activeTemplate.template().originalText();
        if (!Objects.equals(current, original)) {
            overlayAccess.replaceText(original);
        }
        activeTemplate = new ActiveOverlayTemplate(
                activeTemplate.observedNonce(),
                activeTemplate.sourceFingerprint(),
                activeTemplate.template(),
                original
        );
    }

    private void disablePresentation() {
        boolean shouldClearHud = activeTemplate != null
                || currentPresentation != null
                || !Boolean.FALSE.equals(previousEnabled);
        restoreOriginalOverlay();
        activeTemplate = null;
        currentPresentation = null;
        lastTranslatedPresentation = null;
        if (shouldClearHud) {
            hudSink.clear();
        }
    }

    private static int measureText(String value, Style style) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return 0;
        }
        WynnDialogueTextRendererAccessor accessor = (WynnDialogueTextRendererAccessor) client.textRenderer;
        return WynnDialogueFontFallback.measure(
                accessor.translate_allinone$getFontStorageAccessor(),
                value,
                style
        );
    }

    private static boolean supportsText(String value, Style style) {
        if (value == null || value.isBlank()) {
            return true;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return false;
        }
        return WynnDialogueInlinePresenter.supportsText(client.textRenderer, value, style);
    }

    private static boolean sourcesCompatible(
            WynnDialogueTextTemplate template,
            WynnDialoguePresentation presentation
    ) {
        if (template == null || presentation == null) {
            return false;
        }
        if (!normalizedSource(template.npcName()).equals(normalizedSource(presentation.originalNpcName()))) {
            return false;
        }
        String templateDialogue = normalizedSource(template.dialogue());
        String presentationDialogue = normalizedSource(presentation.originalDialogue());
        if (!templateDialogue.equals(presentationDialogue)
                && !removeOneLeadingDuplicateWord(templateDialogue).equals(presentationDialogue)
                && !isProgressiveSourcePair(templateDialogue, presentationDialogue)) {
            return false;
        }
        if (presentation.options().isEmpty()) {
            return true;
        }
        List<String> templateOptions = template.choices().stream()
                .map(WynnDialogueTextTemplate.ChoiceSlot::readableText)
                .filter(value -> !value.isBlank())
                .map(WynnDialogueOverlayController::normalizedSource)
                .toList();
        List<String> presentationOptions = presentation.options().stream()
                .map(WynnDialoguePresentation.OptionPresentation::originalText)
                .map(WynnDialogueOverlayController::normalizedSource)
                .toList();
        return templateOptions.equals(presentationOptions);
    }

    private boolean hasMeaningfullyTranslatedContent(WynnDialoguePresentation presentation) {
        if (presentation == null) {
            return false;
        }
        if (translateNpcName.getAsBoolean()
                && meaningfullyDifferent(presentation.originalNpcName(), presentation.displayedNpcName())) {
            return true;
        }
        if (!presentation.dialoguePending()
                && meaningfullyDifferent(presentation.originalDialogue(), presentation.displayedDialogue())) {
            return true;
        }
        return translateOptions.getAsBoolean() && presentation.options().stream().anyMatch(option ->
                !option.pending() && meaningfullyDifferent(option.originalText(), option.displayedText())
        );
    }

    private static boolean hasTerminalError(WynnDialoguePresentation presentation) {
        return presentation != null
                && !presentation.errorMessage().isBlank()
                && !presentation.dialoguePending()
                && !presentation.optionsPending()
                && presentation.options().stream().noneMatch(WynnDialoguePresentation.OptionPresentation::pending);
    }

    private static boolean canReuseTranslatedPresentation(
            WynnDialogueTextTemplate template,
            WynnDialoguePresentation presentation
    ) {
        if (template == null
                || presentation == null
                || !normalizedSource(template.npcName())
                .equals(normalizedSource(presentation.originalNpcName()))) {
            return false;
        }
        String currentDialogue = normalizedSource(template.dialogue());
        String previousDialogue = normalizedSource(presentation.originalDialogue());
        if (currentDialogue.equals(previousDialogue)
                || removeOneLeadingDuplicateWord(currentDialogue).equals(previousDialogue)) {
            return true;
        }
        return previousDialogue.length() >= 8
                && currentDialogue.length() > previousDialogue.length()
                && currentDialogue.startsWith(previousDialogue);
    }

    private static boolean meaningfullyDifferent(String original, String displayed) {
        return displayed != null
                && !displayed.isBlank()
                && !normalizedSource(original).equals(normalizedSource(displayed));
    }

    private static boolean cleanedSourcesMatch(
            WynnDialogueTextTemplate template,
            WynnDialoguePresentation presentation
    ) {
        return removeOneLeadingDuplicateWord(normalizedSource(template.dialogue()))
                .equals(normalizedSource(presentation.originalDialogue()));
    }

    private static boolean isProgressiveSourcePair(String first, String second) {
        int shorterLength = Math.min(first.length(), second.length());
        return shorterLength >= 8 && (first.startsWith(second) || second.startsWith(first));
    }

    private static String removeOneLeadingDuplicateWord(String value) {
        int firstSpace = value.indexOf(' ');
        if (firstSpace <= 0) {
            return value;
        }
        int secondSpace = value.indexOf(' ', firstSpace + 1);
        if (secondSpace <= firstSpace) {
            return value;
        }
        String firstWord = value.substring(0, firstSpace);
        String secondWord = value.substring(firstSpace + 1, secondSpace);
        return firstWord.equals(secondWord) ? value.substring(firstSpace + 1) : value;
    }

    private static String normalizedSource(String value) {
        return WynnDialogueTextTemplate.normalize(value).toLowerCase(Locale.ROOT);
    }

    private static WynnDialoguePresentation alignPresentation(
            WynnDialogueTextTemplate template,
            WynnDialoguePresentation presentation
    ) {
        boolean optionIndexesMatch = template.choices().size() == presentation.options().size();
        for (int index = 0; optionIndexesMatch && index < template.choices().size(); index++) {
            optionIndexesMatch = template.choices().get(index).index() == presentation.options().get(index).index();
        }
        if (WynnDialogueInlinePresenter.matches(template, presentation) && optionIndexesMatch) {
            return presentation;
        }
        List<WynnDialoguePresentation.OptionPresentation> options = new ArrayList<>();
        List<WynnDialogueTextTemplate.ChoiceSlot> choiceSlots = template.choices().stream()
                .filter(choice -> !choice.readableText().isBlank())
                .toList();
        for (WynnDialogueTextTemplate.ChoiceSlot choice : choiceSlots) {
            WynnDialoguePresentation.OptionPresentation option = findCompatibleOption(choice, presentation.options());
            if (option == null) {
                boolean pending = presentation.optionsPending();
                options.add(new WynnDialoguePresentation.OptionPresentation(
                        choice.index(),
                        choice.readableText(),
                        choice.readableText(),
                        pending,
                        pending ? "option::" + choice.readableText() : ""
                ));
            } else {
                options.add(new WynnDialoguePresentation.OptionPresentation(
                        choice.index(),
                        choice.readableText(),
                        option.displayedText(),
                        option.pending(),
                        option.animationKey()
                ));
            }
        }
        boolean optionsPending = presentation.optionsPending()
                || options.stream().anyMatch(WynnDialoguePresentation.OptionPresentation::pending);
        return new WynnDialoguePresentation(
                presentation.observedNonce(),
                presentation.pageInfo(),
                template.npcName(),
                presentation.displayedNpcName(),
                template.dialogue(),
                presentation.displayedDialogue(),
                options,
                presentation.dialoguePending(),
                optionsPending,
                presentation.dialogueAnimationKey(),
                options.stream().map(WynnDialoguePresentation.OptionPresentation::animationKey).toList(),
                presentation.errorMessage()
        );
    }

    private static WynnDialoguePresentation applyQueuedOptionStates(
            WynnDialoguePresentation progressive,
            WynnDialoguePresentation current
    ) {
        if (progressive == null || current == null || !current.optionsPending()) {
            return progressive;
        }
        List<WynnDialoguePresentation.OptionPresentation> options = new ArrayList<>();
        boolean changed = false;
        for (WynnDialoguePresentation.OptionPresentation option : progressive.options()) {
            if (isCompletedOptionTranslation(option)) {
                options.add(option);
                continue;
            }
            WynnDialoguePresentation.OptionPresentation queued = findCompatiblePendingOption(
                    option,
                    current.options()
            );
            if (queued == null) {
                options.add(option);
                continue;
            }
            options.add(new WynnDialoguePresentation.OptionPresentation(
                    option.index(),
                    option.originalText(),
                    option.originalText(),
                    true,
                    queued.animationKey()
            ));
            changed = true;
        }
        return changed ? withOptions(progressive, options) : progressive;
    }

    private static WynnDialoguePresentation.OptionPresentation findCompatiblePendingOption(
            WynnDialoguePresentation.OptionPresentation target,
            List<WynnDialoguePresentation.OptionPresentation> options
    ) {
        String source = normalizedSource(target.originalText());
        WynnDialoguePresentation.OptionPresentation textMatch = null;
        for (WynnDialoguePresentation.OptionPresentation option : options) {
            if (!option.pending() || !source.equals(normalizedSource(option.originalText()))) {
                continue;
            }
            if (option.index() == target.index()) {
                return option;
            }
            if (textMatch == null) {
                textMatch = option;
            }
        }
        return textMatch;
    }

    private static WynnDialoguePresentation.OptionPresentation findCompatibleOption(
            WynnDialogueTextTemplate.ChoiceSlot choice,
            List<WynnDialoguePresentation.OptionPresentation> options
    ) {
        String source = normalizedSource(choice.readableText());
        WynnDialoguePresentation.OptionPresentation textMatch = null;
        for (WynnDialoguePresentation.OptionPresentation option : options) {
            if (!source.equals(normalizedSource(option.originalText()))) {
                continue;
            }
            if (option.index() == choice.index()) {
                return option;
            }
            if (textMatch == null) {
                textMatch = option;
            }
        }
        return textMatch == null ? findCompatibleScrollingOption(choice, options) : textMatch;
    }

    private static WynnDialoguePresentation.OptionPresentation findCompatibleScrollingOption(
            WynnDialogueTextTemplate.ChoiceSlot choice,
            List<WynnDialoguePresentation.OptionPresentation> options
    ) {
        String source = normalizedSource(choice.readableText());
        WynnDialoguePresentation.OptionPresentation match = null;
        for (WynnDialoguePresentation.OptionPresentation option : options) {
            String candidateSource = normalizedSource(option.originalText());
            if (!isScrollingOptionSourcePair(source, candidateSource)) {
                continue;
            }
            if (option.index() == choice.index()) {
                return option;
            }
            if (match != null
                    && !normalizedSource(match.originalText()).equals(candidateSource)) {
                return null;
            }
            match = option;
        }
        return match;
    }

    private static WynnDialoguePresentation retainCompletedOptionTranslations(
            WynnDialoguePresentation current,
            WynnDialoguePresentation previous
    ) {
        return rememberCompletedOptionTranslations(current, previous);
    }

    private static WynnDialoguePresentation reuseCompletedOptionTranslations(
            WynnDialoguePresentation current,
            WynnDialoguePresentation previous
    ) {
        if (current == null
                || previous == null
                || !canReuseTranslatedPresentationForOptions(current, previous)) {
            return current;
        }
        List<WynnDialoguePresentation.OptionPresentation> options = new ArrayList<>();
        boolean changed = false;
        for (WynnDialoguePresentation.OptionPresentation option : current.options()) {
            if (isCompletedOptionTranslation(option)) {
                options.add(option);
                continue;
            }
            WynnDialoguePresentation.OptionPresentation completed = findCompatibleCompletedOption(
                    option,
                    previous.options()
            );
            if (completed == null) {
                completed = findCompatibleScrollingOption(option, previous.options());
            }
            if (completed == null) {
                options.add(option);
                continue;
            }
            options.add(new WynnDialoguePresentation.OptionPresentation(
                    option.index(),
                    option.originalText(),
                    completed.displayedText(),
                    false,
                    ""
            ));
            changed = true;
        }
        return changed ? withOptions(current, options) : current;
    }

    private static WynnDialoguePresentation rememberCompletedOptionTranslations(
            WynnDialoguePresentation current,
            WynnDialoguePresentation previous
    ) {
        if (current == null
                || previous == null
                || !canReuseTranslatedPresentationForOptions(current, previous)) {
            return current;
        }
        List<WynnDialoguePresentation.OptionPresentation> options = new ArrayList<>(current.options());
        boolean changed = false;
        for (WynnDialoguePresentation.OptionPresentation option : previous.options()) {
            if (!isCompletedOptionTranslation(option)
                    || findCompatibleCompletedOption(option, options) != null) {
                continue;
            }
            options.add(option);
            changed = true;
        }
        return changed ? withOptions(current, options) : current;
    }

    private static boolean canReuseTranslatedPresentationForOptions(
            WynnDialoguePresentation current,
            WynnDialoguePresentation previous
    ) {
        return normalizedSource(current.originalNpcName()).equals(normalizedSource(previous.originalNpcName()))
                && normalizedSource(current.originalDialogue()).equals(normalizedSource(previous.originalDialogue()));
    }

    private static WynnDialoguePresentation.OptionPresentation findCompatibleCompletedOption(
            WynnDialoguePresentation.OptionPresentation target,
            List<WynnDialoguePresentation.OptionPresentation> options
    ) {
        String source = normalizedSource(target.originalText());
        WynnDialoguePresentation.OptionPresentation textMatch = null;
        for (WynnDialoguePresentation.OptionPresentation option : options) {
            if (!isCompletedOptionTranslation(option)
                    || !source.equals(normalizedSource(option.originalText()))) {
                continue;
            }
            if (option.index() == target.index()) {
                return option;
            }
            if (textMatch == null) {
                textMatch = option;
            }
        }
        return textMatch;
    }

    private static WynnDialoguePresentation.OptionPresentation findCompatibleScrollingOption(
            WynnDialoguePresentation.OptionPresentation target,
            List<WynnDialoguePresentation.OptionPresentation> options
    ) {
        String source = normalizedSource(target.originalText());
        WynnDialoguePresentation.OptionPresentation match = null;
        for (WynnDialoguePresentation.OptionPresentation option : options) {
            String candidateSource = normalizedSource(option.originalText());
            if (!isCompletedOptionTranslation(option)
                    || !isScrollingOptionSourcePair(source, candidateSource)) {
                continue;
            }
            if (option.index() == target.index()) {
                return option;
            }
            if (match != null
                    && !normalizedSource(match.originalText()).equals(candidateSource)) {
                return null;
            }
            match = option;
        }
        return match;
    }

    private static boolean isScrollingOptionSourcePair(String first, String second) {
        int shorterLength = Math.min(first.length(), second.length());
        return shorterLength >= 8 && (first.contains(second) || second.contains(first));
    }

    private static boolean isCompletedOptionTranslation(
            WynnDialoguePresentation.OptionPresentation option
    ) {
        return option != null
                && !option.pending()
                && meaningfullyDifferent(option.originalText(), option.displayedText());
    }

    private static WynnDialoguePresentation withOptions(
            WynnDialoguePresentation presentation,
            List<WynnDialoguePresentation.OptionPresentation> options
    ) {
        return new WynnDialoguePresentation(
                presentation.observedNonce(),
                presentation.pageInfo(),
                presentation.originalNpcName(),
                presentation.displayedNpcName(),
                presentation.originalDialogue(),
                presentation.displayedDialogue(),
                options,
                presentation.dialoguePending(),
                options.stream().anyMatch(WynnDialoguePresentation.OptionPresentation::pending),
                presentation.dialogueAnimationKey(),
                options.stream().map(WynnDialoguePresentation.OptionPresentation::animationKey).toList(),
                presentation.errorMessage()
        );
    }

    public interface OverlayAccess {
        Text currentText();

        void replaceText(Text text);

        int remainingTicks();
    }

    public interface HudPresentationSink {
        void present(WynnDialoguePresentation presentation);

        void clear();
    }

    @FunctionalInterface
    public interface OverlayMessageObserver {
        WynnDialoguePresentation observe(Text original);
    }

    private record ActiveOverlayTemplate(
            long observedNonce,
            String sourceFingerprint,
            WynnDialogueTextTemplate template,
            Text lastInstalledText
    ) {
    }

    private static final class MinecraftOverlayAccess implements OverlayAccess {
        @Override
        public Text currentText() {
            WynnDialogueInGameHudAccessor accessor = accessor();
            return accessor == null ? Text.empty() : accessor.translate_allinone$getOverlayMessage();
        }

        @Override
        public void replaceText(Text text) {
            WynnDialogueInGameHudAccessor accessor = accessor();
            if (accessor != null) {
                accessor.translate_allinone$setOverlayMessage(text);
            }
        }

        @Override
        public int remainingTicks() {
            WynnDialogueInGameHudAccessor accessor = accessor();
            return accessor == null ? 0 : accessor.translate_allinone$getOverlayRemaining();
        }

        private WynnDialogueInGameHudAccessor accessor() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.inGameHud == null) {
                return null;
            }
            return (WynnDialogueInGameHudAccessor) client.inGameHud;
        }
    }
}
