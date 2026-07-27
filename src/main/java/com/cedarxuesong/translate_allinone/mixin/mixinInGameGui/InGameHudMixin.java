package com.cedarxuesong.translate_allinone.mixin.mixinInGameGui;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.LookupResult;
import com.cedarxuesong.translate_allinone.utils.cache.ScoreboardTextCache;
import com.cedarxuesong.translate_allinone.utils.cache.TranslationStatus;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationMetrics;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationPolicy;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.translate.ScoreboardComponentTranslationSupport;
import com.cedarxuesong.translate_allinone.utils.translate.ScoreboardEntryTemplate;
import com.cedarxuesong.translate_allinone.utils.translate.TranslationErrorTextSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
    /**
     * Replacements are keyed by the exact team instance and raw scoreboard owner.
     * Using the flattened decorated component as the key would let differently
     * styled prefix/owner/suffix Components overwrite one another.
     */
    private static final ThreadLocal<Map<Team, Map<String, Component>>> translate_allinone$scoreboardReplacements = new ThreadLocal<>();

    @Unique
    private static final Set<String> translate_allinone$refreshedScoreboardKeysThisHold = new HashSet<>();

    @Unique
    private Component translate_allinone$processEntryForTranslation(
            Component prefix,
            String playerName,
            boolean includePlayerName,
            Component suffix
    ) {
        ScoreboardEntryTemplate.Prepared prepared = ScoreboardEntryTemplate.prepare(
                prefix,
                playerName,
                includePlayerName,
                suffix
        );
        if (prepared == null) {
            MutableComponent original = Component.empty().append(prefix == null ? Component.empty() : prefix);
            if (includePlayerName) {
                original.append(Component.literal(playerName));
            }
            return original.append(suffix == null ? Component.empty() : suffix);
        }

        ScoreboardTextCache cache = ScoreboardTextCache.getInstance();
        LookupResult lookupResult = cache.lookupOrQueue(prepared.translationTemplateKey());
        TranslationStatus status = lookupResult.status();
        String translatedTemplate = lookupResult.translation();

        if (status == TranslationStatus.TRANSLATED) {
            return prepared.renderTranslated(translatedTemplate);
        }

        Component originalTextObject = prepared.renderOriginal();

        if (status == TranslationStatus.ERROR) {
            String errorMessage = lookupResult.errorMessage();
            if (translate_allinone$isMissingKeyIssue(errorMessage)) {
                return AnimationManager.getAnimatedStyledText(originalTextObject, prepared.translationTemplateKey(), true);
            }
            MutableComponent errorText = Component.translatable(
                    SCOREBOARD_TRANSLATION_ERROR_KEY,
                    TranslationErrorTextSupport.localizeReason(errorMessage)
            ).withStyle(ChatFormatting.RED);
            return errorText;
        }

        return AnimationManager.getAnimatedStyledText(originalTextObject, prepared.translationTemplateKey(), false);
    }

    @Unique
    private Component translate_allinone$processEntryForComponentV1(
            Objective objective,
            PlayerTeam team,
            String playerName,
            ScoreboardConfig config
    ) {
        Component owner = Component.literal(playerName).withStyle(team.getColor());
        try {
            ScoreboardEntryTemplate.Prepared prepared = ScoreboardComponentTranslationSupport.prepare(
                    objective,
                    team.getPlayerPrefix(),
                    owner,
                    config.enabled_translate_player_name,
                    translate_allinone$isProtectedPlayerName(playerName),
                    team.getPlayerSuffix()
            );
            return ScoreboardComponentTranslationSupport.resolve(prepared, config);
        } catch (RuntimeException e) {
            LOGGER.warn(
                    "Failed to prepare scoreboard Component V1 entry; using the legacy route. ownerLength={}",
                    playerName == null ? 0 : playerName.length(),
                    e
            );
            return translate_allinone$processEntryForTranslation(
                    team.getPlayerPrefix(),
                    playerName,
                    config.enabled_translate_player_name,
                    team.getPlayerSuffix()
            );
        }
    }

    @Unique
    private static List<ScoreboardEntryTemplate.Prepared> translate_allinone$collectScoreboardPreparedEntries(
            Objective objective,
            ScoreboardConfig config
    ) {
        if (objective == null || config == null || !config.enabled_translate_prefix_and_suffix_name) {
            return List.of();
        }

        Scoreboard scoreboard = objective.getScoreboard();
        if (scoreboard == null) {
            return List.of();
        }

        Comparator<PlayerScoreEntry> comparator = InGameHudAccessor.getScoreboardEntryComparator();
        List<ScoreboardEntryTemplate.Prepared> entries = new ArrayList<>();
        scoreboard.listPlayerScores(objective).stream()
                .filter(score -> !score.isHidden())
                .sorted(comparator)
                .limit(15L)
                .forEach(scoreboardEntry -> {
                    PlayerTeam team = scoreboard.getPlayersTeam(scoreboardEntry.owner());
                    if (team == null) {
                        return;
                    }

                    ScoreboardEntryTemplate.Prepared prepared;
                    if (config.component_json_v1_scoreboard) {
                        Component owner = Component.literal(scoreboardEntry.owner()).withStyle(team.getColor());
                        prepared = ScoreboardComponentTranslationSupport.prepare(
                                objective,
                                team.getPlayerPrefix(),
                                owner,
                                config.enabled_translate_player_name,
                                translate_allinone$isProtectedPlayerName(scoreboardEntry.owner()),
                                team.getPlayerSuffix()
                        );
                    } else {
                        prepared = ScoreboardEntryTemplate.prepare(
                                team.getPlayerPrefix(),
                                scoreboardEntry.owner(),
                                config.enabled_translate_player_name,
                                team.getPlayerSuffix()
                        );
                    }
                    if (prepared != null) {
                        entries.add(prepared);
                    }
                });
        return List.copyOf(entries);
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

        List<ScoreboardEntryTemplate.Prepared> currentEntries;
        try {
            currentEntries = translate_allinone$collectScoreboardPreparedEntries(objective, config);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to prepare current scoreboard entries for refresh.", e);
            return;
        }
        if (currentEntries.isEmpty()) {
            return;
        }

        List<ScoreboardEntryTemplate.Prepared> entriesToRefresh = new ArrayList<>();
        synchronized (translate_allinone$refreshedScoreboardKeysThisHold) {
            for (ScoreboardEntryTemplate.Prepared prepared : currentEntries) {
                Set<String> identities = config.component_json_v1_scoreboard
                        ? ScoreboardComponentTranslationSupport.refreshIdentities(
                                prepared,
                                config.target_language
                        )
                        : prepared.translationTemplateKey().isBlank()
                                ? Set.of()
                                : Set.of("legacy:" + prepared.translationTemplateKey());
                boolean unseen = false;
                for (String identity : identities) {
                    unseen |= translate_allinone$refreshedScoreboardKeysThisHold.add(identity);
                }
                if (unseen) {
                    entriesToRefresh.add(prepared);
                }
            }
        }

        if (entriesToRefresh.isEmpty()) {
            return;
        }

        int refreshedCount;
        if (config.component_json_v1_scoreboard) {
            refreshedCount = ScoreboardComponentTranslationSupport.forceRefresh(
                    entriesToRefresh,
                    config.target_language
            );
        } else {
            Set<String> legacyKeys = new LinkedHashSet<>();
            for (ScoreboardEntryTemplate.Prepared prepared : entriesToRefresh) {
                if (!prepared.translationTemplateKey().isBlank()) {
                    legacyKeys.add(prepared.translationTemplateKey());
                }
            }
            refreshedCount = ScoreboardTextCache.getInstance().forceRefresh(legacyKeys);
        }
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

    @Unique
    private static boolean translate_allinone$isProtectedPlayerName(String owner) {
        if (owner == null || owner.isEmpty() || owner.length() > 16) {
            return false;
        }
        for (int index = 0; index < owner.length(); index++) {
            char character = owner.charAt(index);
            boolean allowed = character == '_'
                    || (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9');
            if (!allowed) {
                return false;
            }
        }
        return true;
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
                ScoreboardComponentTranslationSupport.reset();
                synchronized (translate_allinone$refreshedScoreboardKeysThisHold) {
                    translate_allinone$refreshedScoreboardKeysThisHold.clear();
                }
                return;
            }

            ScoreboardComponentTranslationSupport.beginObjective(objective);
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
            Map<Team, Map<String, Component>> replacements = new IdentityHashMap<>();
            long framePreparationStartedAt = System.nanoTime();

            scoreboard.listPlayerScores(objective).stream()
                    .filter(score -> !score.isHidden())
                    .sorted(comparator)
                    .limit(15L)
                    .forEach(scoreboardEntry -> {
                        PlayerTeam team = scoreboard.getPlayersTeam(scoreboardEntry.owner());
                        Component plainOwnerName = Component.literal(scoreboardEntry.owner());
                        Component newName;
                        if (team != null) {
                            if (config.enabled_translate_prefix_and_suffix_name) {
                                newName = config.component_json_v1_scoreboard
                                        ? translate_allinone$processEntryForComponentV1(
                                                objective,
                                                team,
                                                scoreboardEntry.owner(),
                                                config
                                        )
                                        : translate_allinone$processEntryForTranslation(
                                                team.getPlayerPrefix(),
                                                scoreboardEntry.owner(),
                                                config.enabled_translate_player_name,
                                                team.getPlayerSuffix()
                                        );
                            } else {
                                MutableComponent originalName = Component.empty().append(team.getPlayerPrefix());
                                if (config.enabled_translate_player_name) {
                                    originalName.append(plainOwnerName);
                                }
                                newName = originalName.append(team.getPlayerSuffix());
                            }
                        } else {
                            newName = config.enabled_translate_player_name ? plainOwnerName : Component.empty();
                        }
                        replacements
                                .computeIfAbsent(team, ignored -> new HashMap<>())
                                .put(scoreboardEntry.owner(), newName);
                    });

            if (config.component_json_v1_scoreboard) {
                ComponentTranslationMetrics.recordValue(
                        ComponentTranslationRoute.SCOREBOARD,
                        ComponentTranslationPolicy.CURRENT_VERSION,
                        ComponentTranslationMetrics.Measurement.SCOREBOARD_FRAME_ENTRIES,
                        replacements.values().stream().mapToInt(Map::size).sum()
                );
                ComponentTranslationMetrics.recordNanos(
                        ComponentTranslationRoute.SCOREBOARD,
                        ComponentTranslationPolicy.CURRENT_VERSION,
                        ComponentTranslationMetrics.Timing.SCOREBOARD_FRAME_PREPARE,
                        System.nanoTime() - framePreparationStartedAt
                );
            }
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
        Map<Team, Map<String, Component>> replacements = translate_allinone$scoreboardReplacements.get();
        if (replacements == null) {
            return decoratedName;
        }

        Map<String, Component> teamReplacements = replacements.get(team);
        Component replacement = teamReplacements == null ? null : teamReplacements.get(name.getString());
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
