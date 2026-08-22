package com.alexeys.translate_allinone.mixin.mixinInGameGui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Comparator;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.scores.PlayerScoreEntry;

@Mixin(Hud.class)
public interface InGameHudAccessor {
    @Accessor("SCORE_DISPLAY_ORDER")
    static Comparator<PlayerScoreEntry> getScoreboardEntryComparator() {
        throw new AssertionError();
    }
}
