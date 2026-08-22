package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationBundle;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import java.util.Arrays;
import java.util.List;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.block.entity.state.SignBlockEntityRenderState;
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
            SignBlockEntityRenderState state,
            TextRenderer textRenderer
    ) {
        if (sign == null || state == null) {
            return new RenderedSignText(null, null);
        }
        SignText stateFront = state.frontText;
        SignText stateBack = state.backText;
        if (ComponentRenderTranslationSupport.isTranslationBlockedByScreen()) {
            return new RenderedSignText(stateFront, stateBack);
        }
        OtherTranslationsConfig config = ComponentRenderTranslationSupport.config();
        if (!isFeatureEnabled(config) || !isWithinRadius(sign, config)) {
            return new RenderedSignText(stateFront, stateBack);
        }
        SignText originalFront = sign.getFrontText();
        SignText originalBack = sign.getBackText();
        boolean filtered = state.filterText;
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
                        state,
                        textRenderer,
                        config
                ),
                resolveFace(
                        sign,
                        originalBack,
                        ContinuousSignTranslationCoordinator.Face.BACK,
                        filtered,
                        state,
                        textRenderer,
                        config
                )
        );
    }

    static SignText rebuildSignText(
            SignText original,
            Text[] translated,
            SignBlockEntityRenderState state,
            TextRenderer textRenderer
    ) {
        if (original == null || translated == null || translated.length != SIGN_LINE_COUNT) {
            return original;
        }
        Text[] source = original.getMessages(state.filterText);
        if (source == null || source.length != SIGN_LINE_COUNT) {
            return original;
        }
        Text[] guarded = Arrays.copyOf(source, source.length);
        for (int index = 0; index < source.length; index++) {
            Text candidate = translated[index];
            if (candidate != null && fitsLine(candidate, state, textRenderer)) {
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
            SignBlockEntityRenderState state,
            TextRenderer textRenderer,
            OtherTranslationsConfig config
    ) {
        if (original == null) {
            return null;
        }
        ContinuousSignTranslationCoordinator.SignFaceKey key =
                new ContinuousSignTranslationCoordinator.SignFaceKey(sign.getPos(), face);
        Text[] source = original.getMessages(filtered);
        if (source == null || source.length != SIGN_LINE_COUNT) {
            return original;
        }
        Text[] coordinated = ContinuousSignTranslationCoordinator.translatedLines(key);
        if (coordinated != null && hasVisibleTranslation(source, coordinated)) {
            return rebuildSignText(original, coordinated, state, textRenderer);
        }
        String coordinatedAnimationKey = ContinuousSignTranslationCoordinator.pendingAnimationKey(key);
        if (coordinatedAnimationKey != null) {
            return rebuildSignText(
                    original,
                    animateLines(original.getMessages(filtered), coordinatedAnimationKey),
                    state,
                    textRenderer
            );
        }
        if (!config.continuous_sign_translation) {
            return coordinated == null
                    ? original
                    : rebuildSignText(original, coordinated, state, textRenderer);
        }
        if (coordinated == null && ContinuousSignTranslationCoordinator.isGroupedFace(key)) {
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
                return rebuildSignText(original, resolution.value().toArray(Text[]::new), state, textRenderer);
            }
            if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
                return rebuildSignText(
                        original,
                        animateLines(source, "sign:face:" + sign.getPos() + ":" + face.name()),
                        state,
                        textRenderer
                );
            }
        } catch (RuntimeException ignored) {
        }
        return coordinated == null
                ? original
                : rebuildSignText(original, coordinated, state, textRenderer);
    }

    static boolean hasVisibleTranslation(Text[] source, Text[] translated) {
        if (source == null
                || translated == null
                || source.length != SIGN_LINE_COUNT
                || translated.length != SIGN_LINE_COUNT) {
            return false;
        }
        for (int index = 0; index < SIGN_LINE_COUNT; index++) {
            Text sourceLine = source[index];
            Text translatedLine = translated[index];
            if (sourceLine != null
                    && translatedLine != null
                    && !sourceLine.getString().equals(translatedLine.getString())) {
                return true;
            }
        }
        return false;
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

    private static boolean fitsLine(Text text, SignBlockEntityRenderState state, TextRenderer textRenderer) {
        if (text.getString().contains("\n")) {
            return false;
        }
        return textRenderer == null
                || state.maxTextWidth <= 0
                || textRenderer.wrapLines(text, state.maxTextWidth).size() <= 1;
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
