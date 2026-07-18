package com.cedarxuesong.translate_allinone.mixin.mixinInGameGui;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.LookupResult;
import com.cedarxuesong.translate_allinone.utils.cache.ScoreboardTextCache;
import com.cedarxuesong.translate_allinone.utils.cache.TranslationStatus;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import com.cedarxuesong.translate_allinone.utils.translate.TranslationErrorTextSupport;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/InGameHudMixin");

    @Unique
    private static final String MISSING_KEY_HINT = "missing key";

    @Unique
    private static final String KEY_MISMATCH_HINT = "key mismatch";

    @Unique
    private static final String SCOREBOARD_TRANSLATION_ERROR_KEY = "text.translate_allinone.scoreboard.translation_error";

    @Unique
    private static final ThreadLocal<Map<String, Text>> translate_allinone$scoreboardReplacements = new ThreadLocal<>();

    @Unique
    private static final Set<String> translate_allinone$refreshedScoreboardKeysThisHold = new HashSet<>();

    @Unique
    private Text translate_allinone$processTextForTranslation(Text originalText) {
        if (originalText == null || originalText.getString().trim().isEmpty()) {
            return originalText;
        }

        StylePreserver.ExtractionResult styleResult = StylePreserver.extractAndMark(originalText);
        TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
        String unicodeTemplate = templateResult.template();
        String legacyTemplateKey = StylePreserver.toLegacyTemplate(unicodeTemplate, styleResult.styleMap);

        ScoreboardTextCache cache = ScoreboardTextCache.getInstance();
        LookupResult lookupResult = cache.lookupOrQueue(legacyTemplateKey);
        TranslationStatus status = lookupResult.status();
        String translatedTemplate = lookupResult.translation();

        if (status == TranslationStatus.TRANSLATED) {
            String reassembledTranslated = TemplateProcessor.reassemble(translatedTemplate, templateResult.values());
            return StylePreserver.fromLegacyText(reassembledTranslated);
        }

        String reassembledOriginal = TemplateProcessor.reassemble(unicodeTemplate, templateResult.values());
        Text originalTextObject = StylePreserver.reapplyStyles(reassembledOriginal, styleResult.styleMap);

        if (status == TranslationStatus.ERROR) {
            String errorMessage = lookupResult.errorMessage();
            if (translate_allinone$isMissingKeyIssue(errorMessage)) {
                return AnimationManager.getAnimatedStyledText(originalTextObject, legacyTemplateKey, true);
            }
            MutableText errorText = Text.translatable(
                    SCOREBOARD_TRANSLATION_ERROR_KEY,
                    TranslationErrorTextSupport.localizeReason(errorMessage)
            ).formatted(Formatting.RED);
            return errorText;
        }

        return AnimationManager.getAnimatedStyledText(originalTextObject, legacyTemplateKey, false);
    }

    @Unique
    private static String translate_allinone$buildLegacyTemplateKey(Text originalText) {
        if (originalText == null || originalText.getString().trim().isEmpty()) {
            return "";
        }

        StylePreserver.ExtractionResult styleResult = StylePreserver.extractAndMark(originalText);
        TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
        return StylePreserver.toLegacyTemplate(templateResult.template(), styleResult.styleMap);
    }

    @Unique
    private static Set<String> translate_allinone$collectScoreboardTranslationKeys(
            ScoreboardObjective objective,
            ScoreboardConfig config
    ) {
        if (objective == null || config == null || !config.enabled_translate_prefix_and_suffix_name) {
            return Set.of();
        }

        Scoreboard scoreboard = objective.getScoreboard();
        if (scoreboard == null) {
            return Set.of();
        }

        Comparator<ScoreboardEntry> comparator = InGameHudAccessor.getScoreboardEntryComparator();
        Set<String> keys = new LinkedHashSet<>();
        scoreboard.getScoreboardEntries(objective).stream()
                .filter(score -> !score.hidden())
                .sorted(comparator)
                .limit(15L)
                .forEach(scoreboardEntry -> {
                    Team team = scoreboard.getScoreHolderTeam(scoreboardEntry.owner());
                    if (team == null) {
                        return;
                    }

                    String prefixKey = translate_allinone$buildLegacyTemplateKey(team.getPrefix());
                    if (!prefixKey.isBlank()) {
                        keys.add(prefixKey);
                    }

                    String suffixKey = translate_allinone$buildLegacyTemplateKey(team.getSuffix());
                    if (!suffixKey.isBlank()) {
                        keys.add(suffixKey);
                    }
                });
        return keys;
    }

    @Unique
    private static void translate_allinone$maybeForceRefreshCurrentScoreboard(
            ScoreboardObjective objective,
            ScoreboardConfig config
    ) {
        boolean isRefreshPressed = config != null
                && config.keybinding != null
                && KeybindingManager.isPressed(config.keybinding.refreshBinding);

        synchronized (translate_allinone$refreshedScoreboardKeysThisHold) {
            if (!isRefreshPressed) {
                translate_allinone$refreshedScoreboardKeysThisHold.clear();
                return;
            }
        }

        Set<String> currentKeys = translate_allinone$collectScoreboardTranslationKeys(objective, config);
        if (currentKeys.isEmpty()) {
            return;
        }

        Set<String> keysToRefresh = new LinkedHashSet<>();
        synchronized (translate_allinone$refreshedScoreboardKeysThisHold) {
            for (String key : currentKeys) {
                if (translate_allinone$refreshedScoreboardKeysThisHold.add(key)) {
                    keysToRefresh.add(key);
                }
            }
        }

        if (keysToRefresh.isEmpty()) {
            return;
        }

        int refreshedCount = ScoreboardTextCache.getInstance().forceRefresh(keysToRefresh);
        if (refreshedCount > 0) {
            LOGGER.info("Force-refreshed {} current scoreboard translation key(s).", refreshedCount);
        }
    }

    @Unique
    private static boolean translate_allinone$isMissingKeyIssue(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return false;
        }
        String lower = errorMessage.toLowerCase(Locale.ROOT);
        return lower.contains(MISSING_KEY_HINT) || lower.contains(KEY_MISMATCH_HINT);
    }

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void onRenderScoreboardSidebarHead(DrawContext drawContext, ScoreboardObjective objective, CallbackInfo ci) {
        try {
            ScoreboardConfig config = Translate_AllinOne.getConfig().scoreboardTranslate;
            if (config == null || !config.enabled) {
                translate_allinone$scoreboardReplacements.set(null);
                synchronized (translate_allinone$refreshedScoreboardKeysThisHold) {
                    translate_allinone$refreshedScoreboardKeysThisHold.clear();
                }
                return;
            }

            translate_allinone$maybeForceRefreshCurrentScoreboard(objective, config);

            boolean isKeyPressed = config.keybinding != null && KeybindingManager.isPressed(config.keybinding.binding);
            boolean shouldShowOriginal = false;
            ScoreboardConfig.KeybindingMode keybindingMode = config.keybinding == null || config.keybinding.mode == null
                    ? ScoreboardConfig.KeybindingMode.DISABLED
                    : config.keybinding.mode;

            switch (keybindingMode) {
                case HOLD_TO_TRANSLATE:
                    if (!isKeyPressed) shouldShowOriginal = true;
                    break;
                case HOLD_TO_SEE_ORIGINAL:
                    if (isKeyPressed) shouldShowOriginal = true;
                    break;
                case DISABLED:
                    break;
            }

            if (shouldShowOriginal) {
                translate_allinone$scoreboardReplacements.set(null);
                return;
            }

            Scoreboard scoreboard = objective.getScoreboard();
            Comparator<ScoreboardEntry> comparator = InGameHudAccessor.getScoreboardEntryComparator();
            Map<String, Text> replacements = new HashMap<>();

            scoreboard.getScoreboardEntries(objective).stream()
                    .filter(score -> !score.hidden())
                    .sorted(comparator)
                    .limit(15L)
                    .forEach(scoreboardEntry -> {
                        Team team = scoreboard.getScoreHolderTeam(scoreboardEntry.owner());
                        Text plainOwnerName = Text.literal(scoreboardEntry.owner());
                        String originalDecoratedNameKey = Team.decorateName(team, plainOwnerName).getString();

                        MutableText newName = Text.empty();
                        if (team != null) {
                            Text prefix = config.enabled_translate_prefix_and_suffix_name ? translate_allinone$processTextForTranslation(team.getPrefix()) : team.getPrefix();
                            newName.append(prefix);

                            if (config.enabled_translate_player_name) {
                                newName.append(plainOwnerName);
                            }

                            Text suffix = config.enabled_translate_prefix_and_suffix_name ? translate_allinone$processTextForTranslation(team.getSuffix()) : team.getSuffix();
                            newName.append(suffix);
                        } else if (config.enabled_translate_player_name) {
                            newName.append(plainOwnerName);
                        }
                        replacements.put(originalDecoratedNameKey, newName);
                    });

            translate_allinone$scoreboardReplacements.set(replacements);
        } catch (Exception e) {
            LOGGER.error("Failed to prepare scoreboard sidebar replacements", e);
            translate_allinone$scoreboardReplacements.set(null);
        }
    }

    @Redirect(
            method = "method_55439(Lnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/number/NumberFormat;Lnet/minecraft/scoreboard/ScoreboardEntry;)Lnet/minecraft/client/gui/hud/InGameHud$SidebarEntry;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/scoreboard/Team;decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;"
            ),
            require = 0
    )
    private MutableText redirectDecoratedSidebarName(AbstractTeam team, Text name) {
        MutableText decoratedName = Team.decorateName(team, name);
        Map<String, Text> replacements = translate_allinone$scoreboardReplacements.get();
        if (replacements == null) {
            return decoratedName;
        }

        Text replacement = replacements.get(decoratedName.getString());
        return replacement == null ? decoratedName : replacement.copy();
    }

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void onRenderScoreboardSidebarReturn(DrawContext drawContext, ScoreboardObjective objective, CallbackInfo ci) {
        translate_allinone$scoreboardReplacements.remove();
    }
}
