package com.alexeys.translate_allinone.mixin.mixinInGameGui;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.alexeys.translate_allinone.utils.translate.ExternalScoreboardTranslationSupport;
import com.alexeys.translate_allinone.utils.translate.ScoreboardOverhaulReflectionSupport;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "me.jfenn.scoreboardoverhaul.impl.ScoreboardAccessor",
        remap = false
)
public abstract class ScoreboardOverhaulAccessorMixin {
    @Inject(
            method = "getScoreList",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void translate_allinone$translateOverhaulScores(
            @Coerce Object objectiveInfo,
            CallbackInfoReturnable<List<?>> cir
    ) {
        if (!FabricLoader.getInstance().isModLoaded("scoreboard-overhaul")) {
            return;
        }

        ScoreboardConfig config = Translate_AllinOne.getConfig() == null
                ? null
                : Translate_AllinOne.getConfig().scoreboardTranslate;
        if (!ExternalScoreboardTranslationSupport.isEnabled(config, true)) {
            return;
        }

        List<?> original = cir.getReturnValue();
        List<?> translated = ScoreboardOverhaulReflectionSupport.translateScores(original, true);
        if (translated != original) {
            cir.setReturnValue(translated);
        }
    }
}
