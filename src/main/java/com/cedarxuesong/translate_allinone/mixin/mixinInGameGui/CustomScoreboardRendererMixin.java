package com.cedarxuesong.translate_allinone.mixin.mixinInGameGui;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.cedarxuesong.translate_allinone.utils.translate.CustomScoreboardReflectionSupport;
import com.cedarxuesong.translate_allinone.utils.translate.ExternalScoreboardTranslationSupport;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "me.owdding.customscoreboard.feature.customscoreboard.CustomScoreboardRenderer",
        remap = false
)
public abstract class CustomScoreboardRendererMixin {
    @Unique
    private static final Logger translate_allinone$logger = LoggerFactory.getLogger(
            "Translate_AllinOne/CustomScoreboardRendererMixin"
    );
    @Unique
    private static final AtomicBoolean translate_allinone$hideProbeFailureLogged = new AtomicBoolean();
    @Unique
    private static volatile Method translate_allinone$hideVanillaProbe;

    @Inject(
            method = "createDisplay",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void translate_allinone$translateExternalLines(CallbackInfoReturnable<List<?>> cir) {
        if (!FabricLoader.getInstance().isModLoaded("customscoreboard")) {
            return;
        }

        ScoreboardConfig config = Translate_AllinOne.getConfig() == null
                ? null
                : Translate_AllinOne.getConfig().scoreboardTranslate;
        boolean hidesVanilla = config != null
                && config.external_custom_scoreboard_mode == ScoreboardConfig.ExternalCustomScoreboardMode.AUTO
                && translate_allinone$hideHypixelScoreboard();
        if (!ExternalScoreboardTranslationSupport.isEnabled(config, hidesVanilla)) {
            return;
        }

        List<?> original = cir.getReturnValue();
        List<?> translated = CustomScoreboardReflectionSupport.translateLines(original, hidesVanilla);
        if (translated != original) {
            cir.setReturnValue(translated);
        }
    }

    private boolean translate_allinone$hideHypixelScoreboard() {
        try {
            Method method = translate_allinone$hideVanillaProbe;
            if (method == null) {
                method = this.getClass().getDeclaredMethod("hideHypixelScoreboard");
                if (!method.trySetAccessible()) {
                    throw new IllegalAccessException("hideHypixelScoreboard is inaccessible");
                }
                translate_allinone$hideVanillaProbe = method;
            }
            Object result = method.invoke(this);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (translate_allinone$hideProbeFailureLogged.compareAndSet(false, true)) {
                translate_allinone$logger.warn(
                        "Unable to query CustomScoreboard's hide-vanilla setting; AUTO compatibility remains inactive.",
                        e
                );
            }
            return false;
        }
    }
}
