package com.alexeys.translate_allinone.mixin.mixinInGameGui;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationMetrics;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationPolicy;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.alexeys.translate_allinone.utils.translate.ScoreboardComponentTranslationSupport;
import com.alexeys.translate_allinone.utils.translate.ComponentRenderTranslationSupport;
import com.alexeys.translate_allinone.utils.translate.ScoreboardEntryTemplate;
import com.alexeys.translate_allinone.utils.translate.ScoreboardTranslationInputSupport;
import com.alexeys.translate_allinone.utils.translate.TranslationFeatureGate;
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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static final ThreadLocal<Map<Team, Map<String, Component>>> translate_allinone$scoreboardReplacements = new ThreadLocal<>();

    @Unique
    private Component translate_allinone$processEntryForComponent(
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
                    "Failed to prepare scoreboard Component entry; preserving original entry. ownerLength={}",
                    playerName == null ? 0 : playerName.length(),
                    e
            );
            return Component.empty()
                    .append(team.getPlayerPrefix())
                    .append(owner)
                    .append(team.getPlayerSuffix());
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

                    Component owner = Component.literal(scoreboardEntry.owner()).withStyle(team.getColor());
                    ScoreboardEntryTemplate.Prepared prepared = ScoreboardComponentTranslationSupport.prepare(
                            objective,
                            team.getPlayerPrefix(),
                            owner,
                            config.enabled_translate_player_name,
                            translate_allinone$isProtectedPlayerName(scoreboardEntry.owner()),
                            team.getPlayerSuffix()
                    );
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
        if (!ScoreboardTranslationInputSupport.isRefreshPressed(config)) {
            return;
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
        for (ScoreboardEntryTemplate.Prepared prepared : currentEntries) {
            Set<String> identities = ScoreboardComponentTranslationSupport.refreshIdentities(
                    prepared,
                    config.target_language
            );
            boolean unseen = false;
            for (String identity : identities) {
                unseen |= ScoreboardTranslationInputSupport.claimRefreshIdentity(identity);
            }
            if (unseen) {
                entriesToRefresh.add(prepared);
            }
        }

        if (entriesToRefresh.isEmpty()) {
            return;
        }

        int refreshedCount = ScoreboardComponentTranslationSupport.forceRefresh(
                entriesToRefresh,
                config.target_language
        );
        if (refreshedCount > 0) {
            LOGGER.info("Force-refreshed {} current scoreboard translation key(s).", refreshedCount);
        }
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
            if (!TranslationFeatureGate.isEnabled()) {
                translate_allinone$scoreboardReplacements.set(null);
                ScoreboardComponentTranslationSupport.reset();
                ScoreboardTranslationInputSupport.reset();
                return;
            }
            ScoreboardConfig config = Translate_AllinOne.getConfig().scoreboardTranslate;
            if (config == null || !config.enabled) {
                translate_allinone$scoreboardReplacements.set(null);
                ScoreboardComponentTranslationSupport.reset();
                ScoreboardTranslationInputSupport.reset();
                return;
            }
            if (ComponentRenderTranslationSupport.isTranslationBlockedByScreen()) {
                translate_allinone$scoreboardReplacements.set(null);
                return;
            }

            ScoreboardComponentTranslationSupport.beginObjective(objective);
            translate_allinone$maybeForceRefreshCurrentScoreboard(objective, config);
            if (ScoreboardTranslationInputSupport.shouldShowOriginal(config)) {
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
                                newName = translate_allinone$processEntryForComponent(
                                        objective,
                                        team,
                                        scoreboardEntry.owner(),
                                        config
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
