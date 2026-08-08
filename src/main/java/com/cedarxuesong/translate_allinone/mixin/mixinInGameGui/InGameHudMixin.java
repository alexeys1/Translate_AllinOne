package com.cedarxuesong.translate_allinone.mixin.mixinInGameGui;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.cedarxuesong.translate_allinone.utils.translate.ScoreboardComponentTranslationSupport;
import com.cedarxuesong.translate_allinone.utils.translate.ScoreboardEntryTemplate;
import com.cedarxuesong.translate_allinone.utils.translate.ScoreboardTranslationInputSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TranslationFeatureGate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
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
    private static final ThreadLocal<Map<AbstractTeam, Map<String, Text>>> translate_allinone$scoreboardReplacements = new ThreadLocal<>();

    @Unique
    private Text translate_allinone$processEntryForComponent(
            ScoreboardObjective objective,
            Team team,
            String playerName,
            ScoreboardConfig config
    ) {
        Text owner = Text.literal(playerName).formatted(team.getColor());
        try {
            ScoreboardEntryTemplate.Prepared prepared = ScoreboardComponentTranslationSupport.prepare(
                    objective,
                    team.getPrefix(),
                    owner,
                    config.enabled_translate_player_name,
                    translate_allinone$isProtectedPlayerName(playerName),
                    team.getSuffix()
            );
            return ScoreboardComponentTranslationSupport.resolve(prepared, config);
        } catch (RuntimeException error) {
            LOGGER.warn(
                    "Failed to prepare scoreboard Text entry; preserving original entry. ownerLength={}",
                    playerName == null ? 0 : playerName.length(),
                    error
            );
            return Text.empty()
                    .append(team.getPrefix())
                    .append(owner)
                    .append(team.getSuffix());
        }
    }

    @Unique
    private static List<ScoreboardEntryTemplate.Prepared> translate_allinone$collectScoreboardPreparedEntries(
            ScoreboardObjective objective,
            ScoreboardConfig config
    ) {
        if (objective == null || config == null || !config.enabled_translate_prefix_and_suffix_name) {
            return List.of();
        }

        Scoreboard scoreboard = objective.getScoreboard();
        if (scoreboard == null) {
            return List.of();
        }

        Comparator<ScoreboardEntry> comparator = InGameHudAccessor.getScoreboardEntryComparator();
        List<ScoreboardEntryTemplate.Prepared> entries = new ArrayList<>();
        scoreboard.getScoreboardEntries(objective).stream()
                .filter(score -> !score.hidden())
                .sorted(comparator)
                .limit(15L)
                .forEach(scoreboardEntry -> {
                    Team team = scoreboard.getScoreHolderTeam(scoreboardEntry.owner());
                    if (team == null) {
                        return;
                    }

                    Text owner = Text.literal(scoreboardEntry.owner()).formatted(team.getColor());
                    ScoreboardEntryTemplate.Prepared prepared = ScoreboardComponentTranslationSupport.prepare(
                            objective,
                            team.getPrefix(),
                            owner,
                            config.enabled_translate_player_name,
                            translate_allinone$isProtectedPlayerName(scoreboardEntry.owner()),
                            team.getSuffix()
                    );
                    if (prepared != null) {
                        entries.add(prepared);
                    }
                });
        return List.copyOf(entries);
    }

    @Unique
    private static void translate_allinone$maybeForceRefreshCurrentScoreboard(
            ScoreboardObjective objective,
            ScoreboardConfig config
    ) {
        if (!ScoreboardTranslationInputSupport.isRefreshPressed(config)) {
            return;
        }

        List<ScoreboardEntryTemplate.Prepared> currentEntries;
        try {
            currentEntries = translate_allinone$collectScoreboardPreparedEntries(objective, config);
        } catch (RuntimeException error) {
            LOGGER.warn("Failed to prepare current scoreboard entries for refresh.", error);
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
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void onRenderScoreboardSidebarHead(DrawContext drawContext, ScoreboardObjective objective, CallbackInfo ci) {
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

            ScoreboardComponentTranslationSupport.beginObjective(objective);
            translate_allinone$maybeForceRefreshCurrentScoreboard(objective, config);
            if (ScoreboardTranslationInputSupport.shouldShowOriginal(config)) {
                translate_allinone$scoreboardReplacements.set(null);
                return;
            }

            Scoreboard scoreboard = objective.getScoreboard();
            Comparator<ScoreboardEntry> comparator = InGameHudAccessor.getScoreboardEntryComparator();
            Map<AbstractTeam, Map<String, Text>> replacements = new IdentityHashMap<>();

            scoreboard.getScoreboardEntries(objective).stream()
                    .filter(score -> !score.hidden())
                    .sorted(comparator)
                    .limit(15L)
                    .forEach(scoreboardEntry -> {
                        Team team = scoreboard.getScoreHolderTeam(scoreboardEntry.owner());
                        Text plainOwnerName = Text.literal(scoreboardEntry.owner());

                        Text newName;
                        if (team != null) {
                            if (config.enabled_translate_prefix_and_suffix_name) {
                                newName = translate_allinone$processEntryForComponent(
                                        objective,
                                        team,
                                        scoreboardEntry.owner(),
                                        config
                                );
                            } else {
                                MutableText originalName = Text.empty().append(team.getPrefix());
                                if (config.enabled_translate_player_name) {
                                    originalName.append(plainOwnerName);
                                }
                                newName = originalName.append(team.getSuffix());
                            }
                        } else {
                            newName = config.enabled_translate_player_name ? plainOwnerName : Text.empty();
                        }
                        replacements
                                .computeIfAbsent(team, ignored -> new HashMap<>())
                                .put(scoreboardEntry.owner(), newName);
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
        Map<AbstractTeam, Map<String, Text>> replacements = translate_allinone$scoreboardReplacements.get();
        if (replacements == null) {
            return decoratedName;
        }

        Map<String, Text> teamReplacements = replacements.get(team);
        Text replacement = teamReplacements == null ? null : teamReplacements.get(name.getString());
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
