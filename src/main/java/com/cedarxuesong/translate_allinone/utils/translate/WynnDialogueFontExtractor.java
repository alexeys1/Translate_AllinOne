package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

final class WynnDialogueFontExtractor {

    private static final String WYNN_FONT_NAMEPLATE = "hud/dialogue/text/nameplate";
    private static final String WYNN_FONT_BODY_PREFIX = "hud/dialogue/text/wynncraft/body_";
    private static final String WYNN_FONT_CHOICE_PREFIX = "hud/dialogue/text/wynncraft/choice_";

    private WynnDialogueFontExtractor() {
    }

    static FontExtractionResult extract(Text text) {
        if (text == null) {
            return FontExtractionResult.empty();
        }

        StringBuilder npcNameBuilder = new StringBuilder();
        Map<Integer, StringBuilder> bodyBuilders = new LinkedHashMap<>();
        TreeMap<Integer, StringBuilder> choiceBuilders = new TreeMap<>();
        boolean[] hasDialogue = {false};

        text.visit((style, part) -> {
            Identifier font = resolveFont(style);
            if (font == null) {
                return Optional.empty();
            }

            String path = font.getPath();
            if (WYNN_FONT_NAMEPLATE.equals(path)) {
                hasDialogue[0] = true;
                String cleaned = cleanPua(part);
                if (!cleaned.isBlank()) {
                    npcNameBuilder.append(cleaned);
                }
            } else if (path.startsWith(WYNN_FONT_BODY_PREFIX)) {
                hasDialogue[0] = true;
                int index = extractFontIndex(path, WYNN_FONT_BODY_PREFIX);
                String cleaned = cleanPua(part);
                if (!cleaned.isBlank()) {
                    bodyBuilders.computeIfAbsent(index, k -> new StringBuilder()).append(cleaned).append(' ');
                }
            } else if (path.startsWith(WYNN_FONT_CHOICE_PREFIX)) {
                hasDialogue[0] = true;
                int index = extractFontIndex(path, WYNN_FONT_CHOICE_PREFIX);
                String cleaned = cleanPua(part);
                if (!cleaned.isBlank()) {
                    choiceBuilders.computeIfAbsent(index, k -> new StringBuilder()).append(cleaned);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);

        if (!hasDialogue[0]) {
            return FontExtractionResult.empty();
        }

        String npcName = npcNameBuilder.toString().trim();

        StringBuilder bodyBuilder = new StringBuilder();
        bodyBuilders.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String segment = entry.getValue().toString().trim().replaceAll("\\s+", " ");
                    if (!segment.isBlank()) {
                        if (bodyBuilder.length() > 0) {
                            bodyBuilder.append(' ');
                        }
                        bodyBuilder.append(segment);
                    }
                });
        String body = bodyBuilder.toString().trim();

        List<String> choices = new ArrayList<>();
        for (StringBuilder choiceBuilder : choiceBuilders.values()) {
            String choice = choiceBuilder.toString().trim();
            if (!choice.isBlank()) {
                choices.add(choice);
            }
        }

        String optionsText = choices.isEmpty() ? "" : String.join("\n", choices);

        return new FontExtractionResult(true, npcName, body, optionsText);
    }

    private static Identifier resolveFont(Style style) {
        Object fontObj = style.getFont();
        if (fontObj instanceof Identifier id) {
            return id;
        }
        if (fontObj != null) {
            String s = fontObj.toString();
            if (s.contains("id=")) {
                String extracted = null;
                try {
                    extracted = s.substring(s.indexOf("id=") + 3);
                    if (extracted.contains("]")) {
                        extracted = extracted.substring(0, extracted.indexOf("]"));
                    }
                    if (extracted.contains(",")) {
                        extracted = extracted.split(",")[0].trim();
                    }
                    return Identifier.of(extracted);
                } catch (Exception ignored) {
                    Translate_AllinOne.LOGGER.debug("Font identifier resolution failed for extracted value: {}", extracted);
                }
            }
        }
        return null;
    }

    private static int extractFontIndex(String path, String prefix) {
        try {
            String indexStr = path.substring(prefix.length());
            return Integer.parseInt(indexStr);
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private static String cleanPua(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '' && c <= '') {
                continue;
            }
            if (c >= '\uD800' && c <= '\uDFFF') {
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    record FontExtractionResult(
            boolean matched,
            String npcName,
            String dialogue,
            String optionsText
    ) {
        private static FontExtractionResult empty() {
            return new FontExtractionResult(false, "", "", "");
        }
    }
}
