package com.cedarxuesong.translate_allinone.utils.translate;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public record WynnDialogueTextTemplate(
        Text originalText,
        List<TemplateToken> tokens,
        NameplateSlot nameplate,
        List<BodyLineSlot> bodyLines,
        List<ChoiceSlot> choices,
        String sourceFingerprint
) {
    public WynnDialogueTextTemplate {
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
        bodyLines = bodyLines == null ? List.of() : List.copyOf(bodyLines);
        choices = choices == null ? List.of() : List.copyOf(choices);
        sourceFingerprint = sourceFingerprint == null ? "" : sourceFingerprint;
    }

    public boolean matched() {
        return nameplate != null || !bodyLines.isEmpty() || !choices.isEmpty();
    }

    public String npcName() {
        return nameplate == null ? "" : nameplate.readableText();
    }

    public String dialogue() {
        StringBuilder dialogue = new StringBuilder();
        for (BodyLineSlot bodyLine : bodyLines) {
            String line = bodyLine.readableText();
            if (line.isBlank()) {
                continue;
            }
            appendLine(dialogue, line);
        }
        return dialogue.toString();
    }

    public String optionsText() {
        return String.join("\n", choices.stream()
                .map(ChoiceSlot::readableText)
                .filter(value -> !value.isBlank())
                .toList());
    }

    public Text rebuildOriginal() {
        MutableText rebuilt = Text.empty();
        for (TemplateToken token : tokens) {
            rebuilt.append(Text.literal(token.text()).setStyle(token.style()));
        }
        return rebuilt;
    }

    private static void appendLine(StringBuilder builder, String line) {
        String normalized = normalize(line);
        if (normalized.isBlank()) {
            return;
        }
        if (!builder.isEmpty() && shouldInsertSpace(builder, normalized)) {
            builder.append(' ');
        }
        builder.append(normalized);
    }

    private static boolean shouldInsertSpace(StringBuilder builder, String next) {
        int previousCodePoint = builder.codePointBefore(builder.length());
        int nextCodePoint = next.codePointAt(0);
        return !isCjk(previousCodePoint)
                && !isCjk(nextCodePoint)
                && !Character.isWhitespace(previousCodePoint)
                && !Character.isWhitespace(nextCodePoint);
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public enum SlotType {
        DECORATION,
        NAMEPLATE,
        BODY,
        CHOICE
    }

    public record TemplateToken(
            String text,
            Style style,
            Identifier font,
            SlotType slotType,
            int slotIndex,
            boolean readable
    ) {
        public TemplateToken {
            text = text == null ? "" : text;
            style = style == null ? Style.EMPTY : style;
            slotType = slotType == null ? SlotType.DECORATION : slotType;
        }
    }

    public record NameplateSlot(List<Integer> tokenIndices, String readableText, Style style) {
        public NameplateSlot {
            tokenIndices = tokenIndices == null ? List.of() : List.copyOf(tokenIndices);
            readableText = normalize(readableText);
            style = style == null ? Style.EMPTY : style;
        }
    }

    public record BodyLineSlot(int index, List<Integer> tokenIndices, String readableText, Style style) {
        public BodyLineSlot {
            tokenIndices = tokenIndices == null ? List.of() : List.copyOf(tokenIndices);
            readableText = normalize(readableText);
            style = style == null ? Style.EMPTY : style;
        }
    }

    public record ChoiceSlot(int index, List<Integer> tokenIndices, String readableText, Style style) {
        public ChoiceSlot {
            tokenIndices = tokenIndices == null ? List.of() : List.copyOf(tokenIndices);
            readableText = normalize(readableText);
            style = style == null ? Style.EMPTY : style;
        }
    }
}
