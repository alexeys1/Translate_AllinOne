package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.mixin.mixinWynnDialogue.WynnDialogueTextRendererAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WynnDialogueInlinePresenter {
    private static final int POSITIONING_GLYPH_BASE = 0xD0000;
    private static final int MIN_POSITIONING_GLYPH = 0xC0000;
    private static final int MAX_POSITIONING_GLYPH = 0xDFFFF;
    private final WidthMeasurer widthMeasurer;
    private final CharacterSupport characterSupport;

    public WynnDialogueInlinePresenter(Font textRenderer) {
        this((value, style) -> textRenderer == null
                ? 0
                : textRenderer.width(Component.literal(value).setStyle(style)),
                (value, style) -> supportsText(textRenderer, value, style));
    }

    WynnDialogueInlinePresenter(WidthMeasurer widthMeasurer) {
        this(widthMeasurer, (value, style) -> true);
    }

    WynnDialogueInlinePresenter(WidthMeasurer widthMeasurer, CharacterSupport characterSupport) {
        this.widthMeasurer = widthMeasurer;
        this.characterSupport = characterSupport;
    }

    public Component render(WynnDialogueTextTemplate template, WynnDialoguePresentation presentation) {
        boolean translateOptions = presentation != null && !presentation.options().isEmpty();
        return renderResult(template, presentation, true, translateOptions).text();
    }

    WynnDialogueInlineRenderResult renderResult(
            WynnDialogueTextTemplate template,
            WynnDialoguePresentation presentation,
            boolean translateNpcName,
            boolean translateOptions
    ) {
        if (template == null) {
            return new WynnDialogueInlineRenderResult(
                    Component.empty(),
                    WynnDialogueInlineRenderResult.Outcome.REJECTED
            );
        }
        if (presentation != null && !matches(template, presentation)) {
            return new WynnDialogueInlineRenderResult(
                    Component.empty(),
                    WynnDialogueInlineRenderResult.Outcome.REJECTED
            );
        }

        Map<SlotKey, SlotAction> actions = new HashMap<>();
        planNameplate(template, presentation, translateNpcName, actions);
        planBody(template, presentation, actions);
        planChoices(template, presentation, translateOptions, actions);
        return rebuild(template, actions);
    }

    private void planNameplate(
            WynnDialogueTextTemplate template,
            WynnDialoguePresentation presentation,
            boolean translateNpcName,
            Map<SlotKey, SlotAction> actions
    ) {
        if (template.nameplate() == null) {
            return;
        }
        SlotKey key = new SlotKey(WynnDialogueTextTemplate.SlotType.NAMEPLATE, 0);
        if (!translateNpcName) {
            actions.put(key, SlotAction.keep());
            return;
        }
        if (presentation != null
                && meaningfullyDifferent(template.npcName(), presentation.displayedNpcName())) {
            String name = truncate(
                    presentation.displayedNpcName(),
                    template.nameplate().style(),
                    measuredWidth(template.nameplate().readableText(), template.nameplate().style())
            );
            if (!name.isBlank() && supports(name, template.nameplate().style())) {
                actions.put(key, SlotAction.replace(name));
                return;
            }
        }
        actions.put(key, SlotAction.mask());
    }

    private void planBody(
            WynnDialogueTextTemplate template,
            WynnDialoguePresentation presentation,
            Map<SlotKey, SlotAction> actions
    ) {
        boolean pending = presentation != null && presentation.dialoguePending();
        List<WynnDialogueTextTemplate.BodyLineSlot> writableLines = template.bodyLines().stream()
                .filter(line -> !line.readableText().isBlank())
                .toList();
        boolean completedTranslation = presentation != null
                && !presentation.dialoguePending()
                && meaningfullyDifferent(template.dialogue(), presentation.displayedDialogue());
        if (completedTranslation) {
            List<String> wrapped = wrapBody(presentation.displayedDialogue(), writableLines);
            boolean supported = wrapped.stream().anyMatch(value -> !value.isBlank());
            for (int index = 0; supported && index < wrapped.size(); index++) {
                supported = supports(wrapped.get(index), writableLines.get(index).style());
            }
            if (supported) {
                for (int index = 0; index < writableLines.size(); index++) {
                    WynnDialogueTextTemplate.BodyLineSlot line = writableLines.get(index);
                    actions.put(
                            new SlotKey(WynnDialogueTextTemplate.SlotType.BODY, line.index()),
                            SlotAction.replace(index < wrapped.size() ? wrapped.get(index) : "")
                    );
                }
                return;
            }
        }
        for (WynnDialogueTextTemplate.BodyLineSlot line : template.bodyLines()) {
            actions.put(
                    new SlotKey(WynnDialogueTextTemplate.SlotType.BODY, line.index()),
                    pending
                            ? SlotAction.animate()
                            : completedTranslation
                            ? SlotAction.mask()
                            : SlotAction.keep()
            );
        }
    }

    private void planChoices(
            WynnDialogueTextTemplate template,
            WynnDialoguePresentation presentation,
            boolean translateOptions,
            Map<SlotKey, SlotAction> actions
    ) {
        Map<Integer, WynnDialoguePresentation.OptionPresentation> optionsByIndex = new HashMap<>();
        if (presentation != null) {
            for (WynnDialoguePresentation.OptionPresentation option : presentation.options()) {
                optionsByIndex.put(option.index(), option);
            }
        }
        boolean animateMissingOptions = presentation != null && presentation.optionsPending();
        for (WynnDialogueTextTemplate.ChoiceSlot choice : template.choices()) {
            SlotKey key = new SlotKey(WynnDialogueTextTemplate.SlotType.CHOICE, choice.index());
            if (!translateOptions) {
                actions.put(key, SlotAction.keep());
                continue;
            }
            WynnDialoguePresentation.OptionPresentation option = optionsByIndex.get(choice.index());
            if (option != null
                    && !option.pending()
                    && meaningfullyDifferent(choice.readableText(), option.displayedText())) {
                String replacement = truncate(
                        option.displayedText(),
                        choice.style(),
                        measuredWidth(choice.readableText(), choice.style())
                );
                if (!replacement.isBlank() && supports(replacement, choice.style())) {
                    actions.put(key, SlotAction.replace(replacement));
                    continue;
                }
            }
            if ((option != null && option.pending()) || (option == null && animateMissingOptions)) {
                actions.put(key, SlotAction.animate());
            } else {
                actions.put(key, SlotAction.keep());
            }
        }
    }

    private WynnDialogueInlineRenderResult rebuild(
            WynnDialogueTextTemplate template,
            Map<SlotKey, SlotAction> actions
    ) {
        MutableComponent rebuilt = Component.empty();
        Set<SlotKey> inserted = new HashSet<>();
        Set<SlotKey> rejected = new HashSet<>();
        Map<SlotKey, Integer> originalWidths = originalWidths(template);
        boolean translated = false;
        boolean animated = false;
        boolean masked = false;
        for (WynnDialogueTextTemplate.TemplateToken token : template.tokens()) {
            SlotKey key = new SlotKey(token.slotType(), token.slotIndex());
            SlotAction action = actions.getOrDefault(key, SlotAction.keep());
            if (!token.readable()) {
                rebuilt.append(Component.literal(token.text()).setStyle(token.style()));
                continue;
            }
            if (action.type() == SlotActionType.KEEP) {
                rebuilt.append(Component.literal(token.text()).setStyle(token.style()));
                continue;
            }
            if (action.type() == SlotActionType.ANIMATE) {
                rebuilt.append(Component.literal(token.text()).setStyle(WynnDialogueInlinePendingAnimation.mark(token.style())));
                animated = true;
                continue;
            }
            if (action.type() == SlotActionType.REPLACE) {
                if (rejected.contains(key)) {
                    rebuilt.append(Component.literal(token.text()).setStyle(token.style()));
                    continue;
                }
                if (inserted.add(key)) {
                    AdvanceCompensation compensation = advanceCompensation(
                            originalWidths.getOrDefault(key, 0) - measuredWidth(action.text(), token.style()),
                            token.style()
                    );
                    if (compensation == null) {
                        rejected.add(key);
                        rebuilt.append(Component.literal(token.text()).setStyle(token.style()));
                        continue;
                    }
                    if (!action.text().isEmpty()) {
                        rebuilt.append(Component.literal(action.text()).setStyle(token.style()));
                        translated = true;
                    }
                    if (!compensation.text().isEmpty()) {
                        rebuilt.append(Component.literal(compensation.text()).setStyle(compensation.style()));
                    }
                }
                continue;
            }
            MaskedToken maskedToken = maskToken(token);
            if (!maskedToken.text().isEmpty()) {
                rebuilt.append(Component.literal(maskedToken.text()).setStyle(maskedToken.style()));
            }
            masked = true;
        }
        WynnDialogueInlineRenderResult.Outcome outcome = translated
                ? WynnDialogueInlineRenderResult.Outcome.TRANSLATED
                : animated
                ? WynnDialogueInlineRenderResult.Outcome.ANIMATING
                : masked
                ? WynnDialogueInlineRenderResult.Outcome.MASKED
                : WynnDialogueInlineRenderResult.Outcome.ORIGINAL;
        return new WynnDialogueInlineRenderResult(rebuilt, outcome);
    }

    private Map<SlotKey, Integer> originalWidths(WynnDialogueTextTemplate template) {
        Map<SlotKey, Integer> widths = new HashMap<>();
        for (WynnDialogueTextTemplate.TemplateToken token : template.tokens()) {
            if (!token.readable()) {
                continue;
            }
            SlotKey key = new SlotKey(token.slotType(), token.slotIndex());
            widths.merge(key, measuredWidth(token.text(), token.style()), Integer::sum);
        }
        return widths;
    }

    private AdvanceCompensation advanceCompensation(int advance, Style sourceStyle) {
        if (advance == 0) {
            return new AdvanceCompensation("", Style.EMPTY);
        }
        int codePoint = POSITIONING_GLYPH_BASE + advance;
        if (codePoint < MIN_POSITIONING_GLYPH || codePoint > MAX_POSITIONING_GLYPH) {
            return null;
        }
        Style resolvedSourceStyle = sourceStyle == null ? Style.EMPTY : sourceStyle;
        Style compensationStyle = resolvedSourceStyle.getFont() == null
                ? Style.EMPTY
                : Style.EMPTY.withFont(resolvedSourceStyle.getFont());
        String text = new String(Character.toChars(codePoint));
        if (!supports(text, compensationStyle) || measuredWidth(text, compensationStyle) != advance) {
            return null;
        }
        return new AdvanceCompensation(text, compensationStyle);
    }

    private MaskedToken maskToken(WynnDialogueTextTemplate.TemplateToken token) {
        return new MaskedToken(
                token.text(),
                WynnDialogueInlineMaskStyle.mask(token.style())
        );
    }

    private List<String> wrapBody(
            String value,
            List<WynnDialogueTextTemplate.BodyLineSlot> lines
    ) {
        if (value == null || value.isBlank() || lines.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        String remaining = value.trim();
        for (int index = 0; index < lines.size() && !remaining.isBlank(); index++) {
            WynnDialogueTextTemplate.BodyLineSlot line = lines.get(index);
            int lineWidth = measuredWidth(line.readableText(), line.style());
            if (lineWidth <= 0) {
                return List.of();
            }
            if (index == lines.size() - 1) {
                result.add(truncate(remaining, line.style(), lineWidth));
                break;
            }
            LineSplit split = splitFirstLine(remaining, line.style(), lineWidth);
            result.add(split.line());
            remaining = split.remaining();
        }
        return List.copyOf(result);
    }

    private LineSplit splitFirstLine(String value, Style style, int maxWidth) {
        if (measuredWidth(value, style) <= maxWidth) {
            return new LineSplit(value, "");
        }
        int acceptedEnd = 0;
        int lastWhitespaceEnd = -1;
        for (int offset = 0; offset < value.length();) {
            int next = offset + Character.charCount(value.codePointAt(offset));
            String candidate = value.substring(0, next);
            if (measuredWidth(candidate, style) > maxWidth) {
                break;
            }
            acceptedEnd = next;
            if (Character.isWhitespace(value.codePointAt(offset))) {
                lastWhitespaceEnd = offset;
            }
            offset = next;
        }
        if (acceptedEnd <= 0) {
            int firstEnd = Character.charCount(value.codePointAt(0));
            return new LineSplit(value.substring(0, firstEnd), value.substring(firstEnd).stripLeading());
        }
        int lineEnd = lastWhitespaceEnd > 0 ? lastWhitespaceEnd : acceptedEnd;
        return new LineSplit(
                value.substring(0, lineEnd).stripTrailing(),
                value.substring(lastWhitespaceEnd > 0 ? lastWhitespaceEnd : acceptedEnd).stripLeading()
        );
    }

    private String truncate(String value, Style style, int maxWidth) {
        if (value == null || value.isBlank() || maxWidth <= 0) {
            return "";
        }
        String normalized = value.trim();
        if (measuredWidth(normalized, style) <= maxWidth) {
            return normalized;
        }
        String ellipsis = supports("\u2026", style) ? "\u2026" : supports("...", style) ? "..." : "";
        if (ellipsis.isEmpty()) {
            return "";
        }
        if (measuredWidth(ellipsis, style) > maxWidth) {
            return "";
        }
        int acceptedEnd = 0;
        for (int offset = 0; offset < normalized.length();) {
            int next = offset + Character.charCount(normalized.codePointAt(offset));
            if (measuredWidth(normalized.substring(0, next) + ellipsis, style) > maxWidth) {
                break;
            }
            acceptedEnd = next;
            offset = next;
        }
        return normalized.substring(0, acceptedEnd).stripTrailing() + ellipsis;
    }

    private int measuredWidth(String value, Style style) {
        return widthMeasurer.width(value == null ? "" : value, style == null ? Style.EMPTY : style);
    }

    private boolean supports(String value, Style style) {
        return characterSupport.supports(value == null ? "" : value, style == null ? Style.EMPTY : style);
    }

    static boolean supportsText(Font textRenderer, String value, Style style) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (textRenderer == null) {
            return false;
        }
        WynnDialogueTextRendererAccessor accessor = (WynnDialogueTextRendererAccessor) textRenderer;
        return WynnDialogueFontFallback.supports(
                accessor.translate_allinone$getGlyphsProvider(),
                value,
                style
        );
    }

    static boolean matches(WynnDialogueTextTemplate template, WynnDialoguePresentation presentation) {
        if (!normalizedEquals(template.npcName(), presentation.originalNpcName())) {
            return false;
        }
        if (!normalizedEquals(template.dialogue(), presentation.originalDialogue())) {
            return false;
        }
        List<String> templateOptions = template.choices().stream()
                .map(WynnDialogueTextTemplate.ChoiceSlot::readableText)
                .filter(value -> !value.isBlank())
                .toList();
        List<String> presentationOptions = presentation.options().stream()
                .map(WynnDialoguePresentation.OptionPresentation::originalText)
                .toList();
        return presentationOptions.isEmpty() || templateOptions.equals(presentationOptions);
    }

    private static boolean meaningfullyDifferent(String original, String displayed) {
        return displayed != null
                && !displayed.isBlank()
                && !WynnDialogueTextTemplate.normalize(original)
                .equals(WynnDialogueTextTemplate.normalize(displayed));
    }

    private static boolean normalizedEquals(String first, String second) {
        return WynnDialogueTextTemplate.normalize(first).equals(WynnDialogueTextTemplate.normalize(second));
    }

    @FunctionalInterface
    interface WidthMeasurer {
        int width(String value, Style style);
    }

    @FunctionalInterface
    interface CharacterSupport {
        boolean supports(String value, Style style);
    }

    private enum SlotActionType {
        KEEP,
        ANIMATE,
        REPLACE,
        MASK
    }

    private record SlotKey(WynnDialogueTextTemplate.SlotType type, int index) {
    }

    private record SlotAction(SlotActionType type, String text) {
        private static SlotAction keep() {
            return new SlotAction(SlotActionType.KEEP, "");
        }

        private static SlotAction replace(String text) {
            return new SlotAction(SlotActionType.REPLACE, text == null ? "" : text);
        }

        private static SlotAction animate() {
            return new SlotAction(SlotActionType.ANIMATE, "");
        }

        private static SlotAction mask() {
            return new SlotAction(SlotActionType.MASK, "");
        }
    }

    private record MaskedToken(String text, Style style) {
    }

    private record AdvanceCompensation(String text, Style style) {
    }

    private record LineSplit(String line, String remaining) {
    }
}
