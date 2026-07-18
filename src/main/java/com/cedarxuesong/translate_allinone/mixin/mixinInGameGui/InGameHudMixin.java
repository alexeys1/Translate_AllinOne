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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

@Mixin(Gui.class)
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
    private static final ThreadLocal<Map<String, Component>> translate_allinone$scoreboardReplacements = new ThreadLocal<>();

    @Unique
    private static final Set<String> translate_allinone$refreshedScoreboardKeysThisHold = new HashSet<>();

    @Unique
    private Component translate_allinone$processTextForTranslation(Component originalText) {
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
        Component originalTextObject = StylePreserver.reapplyStyles(reassembledOriginal, styleResult.styleMap);

        if (status == TranslationStatus.ERROR) {
            String errorMessage = lookupResult.errorMessage();
            if (translate_allinone$isMissingKeyIssue(errorMessage)) {
                return AnimationManager.getAnimatedStyledText(originalTextObject, legacyTemplateKey, true);
            }
            MutableComponent errorText = Component.translatable(
                    SCOREBOARD_TRANSLATION_ERROR_KEY,
                    TranslationErrorTextSupport.localizeReason(errorMessage)
            ).withStyle(ChatFormatting.RED);
            return errorText;
        }

        return AnimationManager.getAnimatedStyledText(originalTextObject, legacyTemplateKey, false);
    }

    @Unique
    private static String translate_allinone$buildLegacyTemplateKey(Component originalText) {
        if (originalText == null || originalText.getString().trim().isEmpty()) {
            return "";
        }

        StylePreserver.ExtractionResult styleResult = StylePreserver.extractAndMark(originalText);
        TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
        return StylePreserver.toLegacyTemplate(templateResult.template(), styleResult.styleMap);
    }

    @Unique
    private static Set<String> translate_allinone$collectScoreboardTranslationKeys(
            Objective objective,
            ScoreboardConfig config
    ) {
        if (objective == null || config == null || !config.enabled_translate_prefix_and_suffix_name) {
            return Set.of();
        }

        Scoreboard scoreboard = objective.getScoreboard();
        if (scoreboard == null) {
            return Set.of();
        }

        Comparator<PlayerScoreEntry> comparator = InGameHudAccessor.getScoreboardEntryComparator();
        Set<String> keys = new LinkedHashSet<>();
        scoreboard.listPlayerScores(objective).stream()
                .filter(score -> !score.isHidden())
                .sorted(comparator)
                .limit(15L)
                .forEach(scoreboardEntry -> {
                    PlayerTeam team = scoreboard.getPlayersTeam(scoreboardEntry.owner());
                    if (team == null) {
                        return;
                    }

                    String prefixKey = translate_allinone$buildLegacyTemplateKey(team.getPlayerPrefix());
                    if (!prefixKey.isBlank()) {
                        keys.add(prefixKey);
                    }

                    String suffixKey = translate_allinone$buildLegacyTemplateKey(team.getPlayerSuffix());
                    if (!suffixKey.isBlank()) {
                        keys.add(suffixKey);
                    }
                });
        return keys;
    }

    @Unique
    private static void translate_allinone$maybeForceRefreshCurrentScoreboard(
            Objective objective,
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
            method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void onRenderScoreboardSidebarHead(GuiGraphicsExtractor drawContext, Objective objective, CallbackInfo ci) {
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
            Comparator<PlayerScoreEntry> comparator = InGameHudAccessor.getScoreboardEntryComparator();
            Map<String, Component> replacements = new HashMap<>();

            scoreboard.listPlayerScores(objective).stream()
                    .filter(score -> !score.isHidden())
                    .sorted(comparator)
                    .limit(15L)
                    .forEach(scoreboardEntry -> {
                        PlayerTeam team = scoreboard.getPlayersTeam(scoreboardEntry.owner());
                        Component plainOwnerName = Component.literal(scoreboardEntry.owner());
                        String originalDecoratedNameKey = PlayerTeam.formatNameForTeam(team, plainOwnerName).getString();

                        MutableComponent newName = Component.empty();
                        if (team != null) {
                            Component prefix = config.enabled_translate_prefix_and_suffix_name ? translate_allinone$processTextForTranslation(team.getPlayerPrefix()) : team.getPlayerPrefix();
                            newName.append(prefix);

                            if (config.enabled_translate_player_name) {
                                newName.append(plainOwnerName);
                            }
                            
                            Component suffix = config.enabled_translate_prefix_and_suffix_name ? translate_allinone$processTextForTranslation(team.getPlayerSuffix()) : team.getPlayerSuffix();
                            newName.append(suffix);

                        } else {
                            if (config.enabled_translate_player_name) {
                                newName.append(plainOwnerName);
                            }
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
            method = "lambda$displayScoreboardSidebar$1(Lnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/network/chat/numbers/NumberFormat;Lnet/minecraft/world/scores/PlayerScoreEntry;)Lnet/minecraft/client/gui/Gui$1DisplayEntry;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"
            ),
            require = 0
    )
    private MutableComponent redirectDecoratedSidebarName(Team team, Component name) {
        MutableComponent decoratedName = PlayerTeam.formatNameForTeam(team, name);
        Map<String, Component> replacements = translate_allinone$scoreboardReplacements.get();
        if (replacements == null) {
            return decoratedName;
        }

        Component replacement = replacements.get(decoratedName.getString());
        return replacement == null ? decoratedName : replacement.copy();
    }

    @Inject(
            method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void onRenderScoreboardSidebarReturn(GuiGraphicsExtractor drawContext, Objective objective, CallbackInfo ci) {
        translate_allinone$scoreboardReplacements.remove();
    }
}
