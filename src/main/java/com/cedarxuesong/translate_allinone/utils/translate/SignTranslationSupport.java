package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationBundle;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.cedarxuesong.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.Vec3;
public final class SignTranslationSupport {
    private static final String FACE_POLICY_VERSION = "sign-face-v1";
    private static final Map<SignText, FormattedCharSequence[]> REGISTERED_RENDER_LINES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SignTranslationSupport() {
    }

    public static RenderedSignText resolveForRender(SignBlockEntity sign, SignRenderState state, Font font) {
        if (sign == null || state == null) {
            return new RenderedSignText(null, null);
        }
        SignText stateFront = state.frontText;
        SignText stateBack = state.backText;
        if (ComponentRenderTranslationSupport.isTranslationBlockedByScreen()) {
            return new RenderedSignText(stateFront, stateBack);
        }
        OtherTranslationsConfig config = ComponentRenderTranslationSupport.config();
        if (!isFeatureEnabled(config)
                || !isWithinRadius(sign, config)) {
            return new RenderedSignText(stateFront, stateBack);
        }
        SignText originalFront = sign.getFrontText();
        SignText originalBack = sign.getBackText();
        boolean filtered = state.isTextFilteringEnabled;
        if (!ComponentRenderTranslationSupport.shouldRenderTranslated(config)) {
            refreshFaceIfNeeded(sign, originalFront, ContinuousSignTranslationCoordinator.Face.FRONT, filtered, config);
            refreshFaceIfNeeded(sign, originalBack, ContinuousSignTranslationCoordinator.Face.BACK, filtered, config);
            return new RenderedSignText(originalFront, originalBack);
        }
        return new RenderedSignText(
                resolveFace(sign, originalFront, ContinuousSignTranslationCoordinator.Face.FRONT, filtered, state, font, config),
                resolveFace(sign, originalBack, ContinuousSignTranslationCoordinator.Face.BACK, filtered, state, font, config)
        );
    }

    public static void registerForSubmit(SignRenderState state, SignText signText, Font font) {
        if (state == null || state.blockPos == null || signText == null) {
            return;
        }
        if (ComponentRenderTranslationSupport.isTranslationBlockedByScreen()) {
            clearRegisteredRenderLines(signText);
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null
                || !(client.level.getBlockEntity(state.blockPos) instanceof SignBlockEntity sign)) {
            clearRegisteredRenderLines(signText);
            return;
        }

        ContinuousSignTranslationCoordinator.Face face = submittedFace(state, signText);
        RenderedSignText resolved = resolveForRender(sign, state, font);
        SignText renderedText = face == ContinuousSignTranslationCoordinator.Face.FRONT
                ? resolved.frontText()
                : resolved.backText();
        if (renderedText == null || renderedText == signText) {
            clearRegisteredRenderLines(signText);
            return;
        }

        Component[] lines = renderedText.getMessages(state.isTextFilteringEnabled);
        if (lines == null || lines.length != SignText.LINES) {
            clearRegisteredRenderLines(signText);
            return;
        }
        FormattedCharSequence[] visualLines = new FormattedCharSequence[SignText.LINES];
        for (int index = 0; index < SignText.LINES; index++) {
            Component line = lines[index];
            visualLines[index] = line == null ? FormattedCharSequence.EMPTY : line.getVisualOrderText();
        }
        REGISTERED_RENDER_LINES.put(signText, visualLines);
    }

    static ContinuousSignTranslationCoordinator.Face submittedFace(SignRenderState state, SignText signText) {
        return state != null && signText == state.backText && signText != state.frontText
                ? ContinuousSignTranslationCoordinator.Face.BACK
                : ContinuousSignTranslationCoordinator.Face.FRONT;
    }

    public static FormattedCharSequence[] getRegisteredRenderLines(SignText signText) {
        if (signText == null) {
            return null;
        }
        FormattedCharSequence[] lines = REGISTERED_RENDER_LINES.get(signText);
        return lines == null ? null : Arrays.copyOf(lines, lines.length);
    }

    private static void clearRegisteredRenderLines(SignText signText) {
        if (signText != null) {
            REGISTERED_RENDER_LINES.remove(signText);
        }
    }

    static SignText rebuildSignText(SignText original, Component[] translated, SignRenderState state, Font font) {
        if (original == null || translated == null || translated.length != SignText.LINES) {
            return original;
        }
        Component[] source = original.getMessages(state.isTextFilteringEnabled);
        Component[] guarded = Arrays.copyOf(source, source.length);
        for (int index = 0; index < source.length; index++) {
            Component candidate = translated[index];
            if (candidate != null && fitsLine(candidate, state, font)) {
                guarded[index] = candidate;
            }
        }
        return new SignText(guarded, guarded, original.getColor(), original.hasGlowingText());
    }

    private static SignText resolveFace(
            SignBlockEntity sign,
            SignText original,
            ContinuousSignTranslationCoordinator.Face face,
            boolean filtered,
            SignRenderState state,
            Font font,
            OtherTranslationsConfig config
    ) {
        if (original == null) {
            return null;
        }
        ContinuousSignTranslationCoordinator.SignFaceKey key =
                new ContinuousSignTranslationCoordinator.SignFaceKey(sign.getBlockPos(), face);
        Component[] coordinated = ContinuousSignTranslationCoordinator.translatedLines(key);
        if (coordinated != null) {
            return rebuildSignText(original, coordinated, state, font);
        }
        String coordinatedAnimationKey = ContinuousSignTranslationCoordinator.pendingAnimationKey(key);
        if (coordinatedAnimationKey != null) {
            return rebuildSignText(
                    original,
                    animateLines(original.getMessages(filtered), coordinatedAnimationKey),
                    state,
                    font
            );
        }
        if (!config.continuous_sign_translation) {
            return original;
        }
        if (config.continuous_sign_translation && ContinuousSignTranslationCoordinator.isGroupedFace(key)) {
            return original;
        }

        Component[] source = original.getMessages(filtered);
        if (source == null || source.length != SignText.LINES) {
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
            ComponentTranslationRuntime.Resolution<List<Component>> resolution = ComponentTranslationRuntime.resolve(
                    bundle.cacheDocument(),
                    config.target_language,
                    null,
                    () -> null,
                    bundle::apply,
                    "sign:face; pos=" + sign.getBlockPos() + "; side=" + face.name().toLowerCase()
            );
            if (resolution.state() == ComponentTranslationRuntime.State.CACHE_HIT
                    && resolution.value() != null
                    && resolution.value().size() == SignText.LINES) {
                return rebuildSignText(original, resolution.value().toArray(Component[]::new), state, font);
            }
            if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
                return rebuildSignText(
                        original,
                        animateLines(source, "sign:face:" + sign.getBlockPos() + ":" + face.name()),
                        state,
                        font
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
                new ContinuousSignTranslationCoordinator.SignFaceKey(sign.getBlockPos(), face);
        if (config.continuous_sign_translation && ContinuousSignTranslationCoordinator.isGroupedFace(key)) {
            return;
        }
        Component[] source = original.getMessages(filtered);
        if (source == null || source.length != SignText.LINES) {
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
                    "sign:face; pos=" + sign.getBlockPos() + "; side=" + face.name().toLowerCase()
            );
        } catch (RuntimeException ignored) {
        }
    }

    private static Component[] animateLines(Component[] source, String animationKey) {
        if (source == null || source.length != SignText.LINES) {
            return source;
        }
        Component[] animated = new Component[source.length];
        for (int index = 0; index < source.length; index++) {
            animated[index] = ComponentRenderTranslationSupport.animatePending(
                    source[index],
                    animationKey + ":" + index
            );
        }
        return animated;
    }

    private static boolean fitsLine(Component component, SignRenderState state, Font font) {
        if (component.getString().contains("\n")) {
            return false;
        }
        return font == null
                || state.maxTextLineWidth <= 0
                || font.split(component, state.maxTextLineWidth).size() <= 1;
    }

    private static boolean isFeatureEnabled(OtherTranslationsConfig config) {
        return ComponentRenderTranslationSupport.isFeatureEnabled(
                config,
                config != null && config.enabled_translate_signs
        );
    }

    private static boolean isWithinRadius(SignBlockEntity sign, OtherTranslationsConfig config) {
        Minecraft client = Minecraft.getInstance();
        Player player = client == null ? null : client.player;
        if (player == null) {
            return false;
        }
        int radius = Math.clamp(config.sign_translation_radius, 1, 16);
        Vec3 center = Vec3.atCenterOf(sign.getBlockPos());
        return player.position().distanceToSqr(center) <= (double) radius * radius;
    }

    public record RenderedSignText(SignText frontText, SignText backText) {
    }
}
