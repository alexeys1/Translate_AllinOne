package com.cedarxuesong.translate_allinone.mixin.mixinInGameGui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Comparator;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.scores.PlayerScoreEntry;

@Mixin(Gui.class)
public interface InGameHudAccessor {
    @Accessor("SCORE_DISPLAY_ORDER")
    static Comparator<PlayerScoreEntry> getScoreboardEntryComparator() {
        throw new AssertionError();
    }
} 