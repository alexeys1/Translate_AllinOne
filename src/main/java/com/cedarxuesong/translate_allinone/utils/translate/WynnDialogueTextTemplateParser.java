package com.cedarxuesong.translate_allinone.utils.translate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class WynnDialogueTextTemplateParser {
    private static final String NAMEPLATE_PATH = "hud/dialogue/text/nameplate";
    private static final String BODY_PREFIX = "hud/dialogue/text/wynncraft/body_";
    private static final String CHOICE_ROOT = "hud/dialogue/text/wynncraft/choice";
    private static final String INLINE_MASK_INSERTION = "translate_allinone:wynn_dialogue_inline_mask";

    private WynnDialogueTextTemplateParser() {
    }

    public static WynnDialogueTextTemplate parse(Component text) {
        if (text == null) {
            return null;
        }

        List<WynnDialogueTextTemplate.TemplateToken> tokens = new ArrayList<>();
        text.visit((style, part) -> {
            appendTokens(tokens, style, part);
            return Optional.empty();
        }, Style.EMPTY);

        WynnDialogueTextTemplate.NameplateSlot nameplate = aggregateNameplate(tokens);
        List<WynnDialogueTextTemplate.BodyLineSlot> bodyLines = aggregateBodyLines(tokens);
        List<WynnDialogueTextTemplate.ChoiceSlot> choices = aggregateChoices(tokens);
        if (nameplate == null && bodyLines.isEmpty() && choices.isEmpty()) {
            return null;
        }

        String fingerprint = fingerprint(nameplate, bodyLines, choices);
        return new WynnDialogueTextTemplate(text, tokens, nameplate, bodyLines, choices, fingerprint);
    }

    private static void appendTokens(
            List<WynnDialogueTextTemplate.TemplateToken> tokens,
            Style style,
            String part
    ) {
        if (part == null || part.isEmpty()) {
            return;
        }
        Identifier font = resolveFont(style);
        SlotClassification classification = classify(font);
        if (classification.type() == WynnDialogueTextTemplate.SlotType.DECORATION) {
            tokens.add(new WynnDialogueTextTemplate.TemplateToken(
                    part,
                    style,
                    font,
                    classification.type(),
                    classification.index(),
                    false
            ));
            return;
        }

        StringBuilder run = new StringBuilder();
        Boolean readable = null;
        boolean masked = isMasked(style);
        for (int offset = 0; offset < part.length();) {
            int codePoint = part.codePointAt(offset);
            boolean nextReadable = !masked && !isDecorativeGlyph(codePoint);
            if (readable != null && readable != nextReadable) {
                addRun(tokens, run, style, font, classification, readable);
                run.setLength(0);
            }
            run.appendCodePoint(codePoint);
            readable = nextReadable;
            offset += Character.charCount(codePoint);
        }
        if (!run.isEmpty()) {
            addRun(tokens, run, style, font, classification, Boolean.TRUE.equals(readable));
        }
    }

    private static void addRun(
            List<WynnDialogueTextTemplate.TemplateToken> tokens,
            StringBuilder run,
            Style style,
            Identifier font,
            SlotClassification classification,
            boolean readable
    ) {
        tokens.add(new WynnDialogueTextTemplate.TemplateToken(
                run.toString(),
                style,
                font,
                classification.type(),
                classification.index(),
                readable
        ));
    }

    private static WynnDialogueTextTemplate.NameplateSlot aggregateNameplate(
            List<WynnDialogueTextTemplate.TemplateToken> tokens
    ) {
        List<Integer> indices = tokenIndices(tokens, WynnDialogueTextTemplate.SlotType.NAMEPLATE, 0);
        if (indices.isEmpty()) {
            return null;
        }
        return new WynnDialogueTextTemplate.NameplateSlot(
                indices,
                readableText(tokens, indices),
                firstReadableStyle(tokens, indices)
        );
    }

    private static List<WynnDialogueTextTemplate.BodyLineSlot> aggregateBodyLines(
            List<WynnDialogueTextTemplate.TemplateToken> tokens
    ) {
        TreeMap<Integer, List<Integer>> grouped = groupTokenIndices(tokens, WynnDialogueTextTemplate.SlotType.BODY);
        List<WynnDialogueTextTemplate.BodyLineSlot> slots = new ArrayList<>();
        grouped.forEach((index, indices) -> slots.add(new WynnDialogueTextTemplate.BodyLineSlot(
                index,
                indices,
                readableText(tokens, indices),
                firstReadableStyle(tokens, indices)
        )));
        return List.copyOf(slots);
    }

    private static List<WynnDialogueTextTemplate.ChoiceSlot> aggregateChoices(
            List<WynnDialogueTextTemplate.TemplateToken> tokens
    ) {
        TreeMap<Integer, List<Integer>> grouped = groupTokenIndices(tokens, WynnDialogueTextTemplate.SlotType.CHOICE);
        List<WynnDialogueTextTemplate.ChoiceSlot> slots = new ArrayList<>();
        grouped.forEach((index, indices) -> slots.add(new WynnDialogueTextTemplate.ChoiceSlot(
                index,
                indices,
                readableText(tokens, indices),
                firstReadableStyle(tokens, indices)
        )));
        return List.copyOf(slots);
    }

    private static TreeMap<Integer, List<Integer>> groupTokenIndices(
            List<WynnDialogueTextTemplate.TemplateToken> tokens,
            WynnDialogueTextTemplate.SlotType type
    ) {
        TreeMap<Integer, List<Integer>> grouped = new TreeMap<>();
        for (int index = 0; index < tokens.size(); index++) {
            WynnDialogueTextTemplate.TemplateToken token = tokens.get(index);
            if (token.slotType() == type) {
                grouped.computeIfAbsent(token.slotIndex(), ignored -> new ArrayList<>()).add(index);
            }
        }
        return grouped;
    }

    private static List<Integer> tokenIndices(
            List<WynnDialogueTextTemplate.TemplateToken> tokens,
            WynnDialogueTextTemplate.SlotType type,
            int slotIndex
    ) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            WynnDialogueTextTemplate.TemplateToken token = tokens.get(index);
            if (token.slotType() == type && token.slotIndex() == slotIndex) {
                indices.add(index);
            }
        }
        return List.copyOf(indices);
    }

    private static String readableText(
            List<WynnDialogueTextTemplate.TemplateToken> tokens,
            List<Integer> indices
    ) {
        StringBuilder builder = new StringBuilder();
        for (int index : indices) {
            WynnDialogueTextTemplate.TemplateToken token = tokens.get(index);
            if (token.readable()) {
                builder.append(token.text());
            }
        }
        return WynnDialogueTextTemplate.normalize(builder.toString());
    }

    private static Style firstReadableStyle(
            List<WynnDialogueTextTemplate.TemplateToken> tokens,
            List<Integer> indices
    ) {
        for (int index : indices) {
            WynnDialogueTextTemplate.TemplateToken token = tokens.get(index);
            if (token.readable()) {
                return token.style();
            }
        }
        return indices.isEmpty() ? Style.EMPTY : tokens.get(indices.getFirst()).style();
    }

    private static Identifier resolveFont(Style style) {
        FontDescription font = style == null ? null : style.getFont();
        return font instanceof FontDescription.Resource fontSource ? fontSource.id() : null;
    }

    private static boolean isMasked(Style style) {
        return style != null && INLINE_MASK_INSERTION.equals(style.getInsertion());
    }

    private static SlotClassification classify(Identifier font) {
        if (font == null) {
            return new SlotClassification(WynnDialogueTextTemplate.SlotType.DECORATION, -1);
        }
        String path = font.getPath();
        if (NAMEPLATE_PATH.equals(path)) {
            return new SlotClassification(WynnDialogueTextTemplate.SlotType.NAMEPLATE, 0);
        }
        if (path.startsWith(BODY_PREFIX)) {
            return new SlotClassification(WynnDialogueTextTemplate.SlotType.BODY, parseIndex(path, BODY_PREFIX));
        }
        if (path.startsWith(CHOICE_ROOT)) {
            return new SlotClassification(WynnDialogueTextTemplate.SlotType.CHOICE, parseChoiceIndex(path));
        }
        return new SlotClassification(WynnDialogueTextTemplate.SlotType.DECORATION, -1);
    }

    private static int parseIndex(String path, String prefix) {
        try {
            return Integer.parseInt(path.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private static int parseChoiceIndex(String path) {
        int start = CHOICE_ROOT.length();
        while (start < path.length() && !Character.isDigit(path.charAt(start))) {
            start++;
        }
        if (start >= path.length()) {
            return Integer.MAX_VALUE;
        }
        int end = start + 1;
        while (end < path.length() && Character.isDigit(path.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(path.substring(start, end));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private static boolean isDecorativeGlyph(int codePoint) {
        return codePoint > Character.MAX_VALUE
                || codePoint >= 0xE000 && codePoint <= 0xF8FF;
    }

    private static String fingerprint(
            WynnDialogueTextTemplate.NameplateSlot nameplate,
            List<WynnDialogueTextTemplate.BodyLineSlot> bodyLines,
            List<WynnDialogueTextTemplate.ChoiceSlot> choices
    ) {
        StringBuilder source = new StringBuilder();
        source.append(normalizeFingerprint(nameplate == null ? "" : nameplate.readableText())).append('\n');
        for (WynnDialogueTextTemplate.BodyLineSlot line : bodyLines) {
            source.append("body:").append(line.index()).append(':')
                    .append(normalizeFingerprint(line.readableText())).append('\n');
        }
        for (WynnDialogueTextTemplate.ChoiceSlot choice : choices) {
            source.append("choice:").append(choice.index()).append(':')
                    .append(normalizeFingerprint(choice.readableText())).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String normalizeFingerprint(String value) {
        return WynnDialogueTextTemplate.normalize(value).toLowerCase(Locale.ROOT);
    }

    private record SlotClassification(WynnDialogueTextTemplate.SlotType type, int index) {
    }
}
