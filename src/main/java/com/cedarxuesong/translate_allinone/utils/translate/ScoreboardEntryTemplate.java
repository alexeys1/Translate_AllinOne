package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class ScoreboardEntryTemplate {
    public static final String PLAYER_NAME_TOKEN = "<taio-player-name>";

    public record Prepared(
            String translationTemplateKey,
            List<String> templateValues,
            String playerName
    ) {
        public Component renderOriginal() {
            return render(translationTemplateKey, templateValues, playerName);
        }

        public Component renderTranslated(String translatedTemplate) {
            return render(translatedTemplate, templateValues, playerName);
        }
    }

    private ScoreboardEntryTemplate() {
    }

    public static Prepared prepare(
            Component prefix,
            String playerName,
            boolean includePlayerName,
            Component suffix
    ) {
        String prefixLegacy = toLegacyText(prefix);
        String suffixLegacy = toLegacyText(suffix);
        if (prefixLegacy.isEmpty() && suffixLegacy.isEmpty()) {
            return null;
        }

        StringBuilder source = new StringBuilder(prefixLegacy);
        if (includePlayerName) {
            source.append("\u00a7r").append(PLAYER_NAME_TOKEN);
        }
        if (!suffixLegacy.isEmpty()) {
            source.append("\u00a7r").append(suffixLegacy);
        }

        TemplateProcessor.TemplateExtractionResult extractionResult = TemplateProcessor.extract(source.toString());
        return new Prepared(
                extractionResult.template(),
                List.copyOf(extractionResult.values()),
                includePlayerName ? normalizedPlayerName(playerName) : ""
        );
    }

    public static boolean preservesPlayerNameToken(String sourceTemplate, String translatedTemplate) {
        return countPlayerNameTokens(sourceTemplate) == countPlayerNameTokens(translatedTemplate);
    }

    private static Component render(String template, List<String> templateValues, String playerName) {
        String reassembled = TemplateProcessor.reassemble(
                template == null ? "" : template,
                templateValues == null ? List.of() : templateValues
        );
        return StylePreserver.fromLegacyText(reassembled.replace(PLAYER_NAME_TOKEN, normalizedPlayerName(playerName)));
    }

    private static String toLegacyText(Component text) {
        if (text == null || text.getString().trim().isEmpty()) {
            return "";
        }
        StylePreserver.ExtractionResult styleResult = StylePreserver.extractAndMark(text);
        return StylePreserver.toLegacyTemplate(styleResult.markedText, styleResult.styleMap);
    }

    private static int countPlayerNameTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = 0;
        int start = 0;
        while ((start = text.indexOf(PLAYER_NAME_TOKEN, start)) >= 0) {
            count++;
            start += PLAYER_NAME_TOKEN.length();
        }
        return count;
    }

    private static String normalizedPlayerName(String playerName) {
        return playerName == null ? "" : playerName;
    }
}
