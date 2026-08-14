package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationBundle;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.cedarxuesong.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import java.util.Arrays;
import java.util.List;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class SignTranslationSupport {
    private static final String FACE_POLICY_VERSION = "sign-face-v1";
    private static final int SIGN_LINE_COUNT = 4;

    private SignTranslationSupport() {
    }

    public static RenderedSignText resolveForRender(
            SignBlockEntity sign,
            TextRenderer textRenderer
    ) {
        if (sign == null) {
            return new RenderedSignText(null, null);
        }
        SignText stateFront = sign.getFrontText();
        SignText stateBack = sign.getBackText();
        boolean filtered = MinecraftClient.getInstance().shouldFilterText();
        int maxTextWidth = sign.getMaxTextWidth();
        if (ComponentRenderTranslationSupport.isTranslationBlockedByScreen()) {
            return new RenderedSignText(stateFront, stateBack);
        }
        OtherTranslationsConfig config = ComponentRenderTranslationSupport.config();
        if (!isFeatureEnabled(config) || !isWithinRadius(sign, config)) {
            return new RenderedSignText(stateFront, stateBack);
        }
        SignText originalFront = sign.getFrontText();
        SignText originalBack = sign.getBackText();
        if (!ComponentRenderTranslationSupport.shouldRenderTranslated(config)) {
            refreshFaceIfNeeded(sign, originalFront, ContinuousSignTranslationCoordinator.Face.FRONT, filtered, config);
            refreshFaceIfNeeded(sign, originalBack, ContinuousSignTranslationCoordinator.Face.BACK, filtered, config);
            return new RenderedSignText(originalFront, originalBack);
        }
        return new RenderedSignText(
                resolveFace(
                        sign,
                        originalFront,
                        ContinuousSignTranslationCoordinator.Face.FRONT,
                        filtered,
                        maxTextWidth,
                        textRenderer,
                        config
                ),
                resolveFace(
                        sign,
                        originalBack,
                        ContinuousSignTranslationCoordinator.Face.BACK,
                        filtered,
                        maxTextWidth,
                        textRenderer,
                        config
                )
        );
    }

    static SignText rebuildSignText(
            SignText original,
            Text[] translated,
            boolean filtered,
            int maxTextWidth,
            TextRenderer textRenderer
    ) {
        if (original == null || translated == null || translated.length != SIGN_LINE_COUNT) {
            return original;
        }
        Text[] source = original.getMessages(filtered);
        if (source == null || source.length != SIGN_LINE_COUNT) {
            return original;
        }
        Text[] guarded = Arrays.copyOf(source, source.length);
        for (int index = 0; index < source.length; index++) {
            Text candidate = translated[index];
            if (candidate != null && fitsLine(candidate, maxTextWidth, textRenderer)) {
                guarded[index] = candidate;
            }
        }
        return new SignText(guarded, guarded, original.getColor(), original.isGlowing());
    }

    private static SignText resolveFace(
            SignBlockEntity sign,
            SignText original,
            ContinuousSignTranslationCoordinator.Face face,
            boolean filtered,
            int maxTextWidth,
            TextRenderer textRenderer,
            OtherTranslationsConfig config
    ) {
        if (original == null) {
            return null;
        }
        ContinuousSignTranslationCoordinator.SignFaceKey key =
                new ContinuousSignTranslationCoordinator.SignFaceKey(sign.getPos(), face);
        Text[] coordinated = ContinuousSignTranslationCoordinator.translatedLines(key);
        if (coordinated != null) {
            return rebuildSignText(original, coordinated, filtered, maxTextWidth, textRenderer);
        }
        String coordinatedAnimationKey = ContinuousSignTranslationCoordinator.pendingAnimationKey(key);
        if (coordinatedAnimationKey != null) {
            return rebuildSignText(
                    original,
                    animateLines(original.getMessages(filtered), coordinatedAnimationKey),
                    filtered,
                    maxTextWidth,
                    textRenderer
            );
        }
        if (!config.continuous_sign_translation) {
            return original;
        }
        if (ContinuousSignTranslationCoordinator.isGroupedFace(key)) {
            return original;
        }

        Text[] source = original.getMessages(filtered);
        if (source == null || source.length != SIGN_LINE_COUNT) {
            return original;
        }
        try {
            ComponentTranslationBundle bundle = ComponentTranslationBundle.create(
                    List.of(source),
                    ComponentTranslationRoute.SIGN_FACE,
                    "sign:face; side=" + face.name().toLowerCase(),
                    FACE_POLICY_VERSION
            );
            if (ComponentRenderTranslationSupport.isRefreshPressed(config)) {
                ComponentRenderTranslationSupport.maybeForceRefresh(bundle.cacheDocument(), config);
            }
            ComponentTranslationRuntime.Resolution<List<Text>> resolution = ComponentTranslationRuntime.resolve(
                    bundle.cacheDocument(),
                    config.target_language,
                    null,
                    () -> null,
                    bundle::apply,
                    "sign:face; pos=" + sign.getPos() + "; side=" + face.name().toLowerCase()
            );
            if (resolution.state() == ComponentTranslationRuntime.State.CACHE_HIT
                    && resolution.value() != null
                    && resolution.value().size() == SIGN_LINE_COUNT) {
                return rebuildSignText(original, resolution.value().toArray(Text[]::new), filtered, maxTextWidth, textRenderer);
            }
            if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
                return rebuildSignText(
                        original,
                        animateLines(source, "sign:face:" + sign.getPos() + ":" + face.name()),
                        filtered,
                        maxTextWidth,
                        textRenderer
                );
            }
        } catch (RuntimeException ignored) {
        }
        return original;
    }

    private static void refreshFaceIfNeeded(
            SignBlockEntity sign,
            SignText original,
            ContinuousSignTranslationCoordinator.Face face,
            boolean filtered,
            OtherTranslationsConfig config
    ) {
        if (original == null || !ComponentRenderTranslationSupport.isRefreshPressed(config)) {
            return;
        }
        if (!config.continuous_sign_translation) {
            return;
        }
        ContinuousSignTranslationCoordinator.SignFaceKey key =
                new ContinuousSignTranslationCoordinator.SignFaceKey(sign.getPos(), face);
        if (ContinuousSignTranslationCoordinator.isGroupedFace(key)) {
            return;
        }
        Text[] source = original.getMessages(filtered);
        if (source == null || source.length != SIGN_LINE_COUNT) {
            return;
        }
        try {
            ComponentTranslationBundle bundle = ComponentTranslationBundle.create(
                    List.of(source),
                    ComponentTranslationRoute.SIGN_FACE,
                    "sign:face; side=" + face.name().toLowerCase(),
                    FACE_POLICY_VERSION
            );
            ComponentRenderTranslationSupport.forceRefreshAndQueue(
                    bundle.cacheDocument(),
                    config,
                    "sign:face; pos=" + sign.getPos() + "; side=" + face.name().toLowerCase()
            );
        } catch (RuntimeException ignored) {
        }
    }

    private static Text[] animateLines(Text[] source, String animationKey) {
        if (source == null || source.length != SIGN_LINE_COUNT) {
            return source;
        }
        Text[] animated = new Text[source.length];
        for (int index = 0; index < source.length; index++) {
            animated[index] = ComponentRenderTranslationSupport.animatePending(
                    source[index],
                    animationKey + ":" + index
            );
        }
        return animated;
    }

    private static boolean fitsLine(Text text, int maxTextWidth, TextRenderer textRenderer) {
        if (text.getString().contains("\n")) {
            return false;
        }
        return textRenderer == null
                || maxTextWidth <= 0
                || textRenderer.wrapLines(text, maxTextWidth).size() <= 1;
    }

    private static boolean isFeatureEnabled(OtherTranslationsConfig config) {
        return ComponentRenderTranslationSupport.isFeatureEnabled(
                config,
                config != null && config.enabled_translate_signs
        );
    }

    private static boolean isWithinRadius(SignBlockEntity sign, OtherTranslationsConfig config) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client == null ? null : client.player;
        if (player == null) {
            return false;
        }
        int radius = MathHelper.clamp(config.sign_translation_radius, 1, 16);
        Vec3d center = Vec3d.ofCenter(sign.getPos());
        return player.squaredDistanceTo(center) <= (double) radius * radius;
    }

    public record RenderedSignText(SignText frontText, SignText backText) {
    }
}
