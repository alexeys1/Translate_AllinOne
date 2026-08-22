package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationBundle;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
public final class ContinuousSignTranslationCoordinator {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int MAX_FACES_PER_GROUP = 16;
    private static final String STANDALONE_SIGN_POLICY_VERSION = "sign-standalone-v2";
    private static volatile Map<SignFaceKey, Component[]> TRANSLATED = Map.of();
    private static volatile Map<SignFaceKey, String> PENDING_ANIMATION_KEYS = Map.of();
    private static volatile Set<SignFaceKey> GROUPED_FACES = Set.of();
    private static int ticksUntilScan;
    private static ClientLevel lastLevel;
    private static int lastRadius = -1;
    private static boolean lastContinuousTranslation;
    private static boolean refreshWasPressed;

    private ContinuousSignTranslationCoordinator() {
    }

    public static void tick() {
        if (ComponentRenderTranslationSupport.isTranslationBlockedByScreen()) {
            reset();
            return;
        }
        OtherTranslationsConfig config = ComponentRenderTranslationSupport.config();
        if (!isActive(config)) {
            reset();
            return;
        }
        boolean renderTranslated = ComponentRenderTranslationSupport.shouldRenderTranslated(config);
        boolean refreshPressed = ComponentRenderTranslationSupport.isRefreshPressed(config);
        if (!renderTranslated && !refreshPressed) {
            reset();
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client == null ? null : client.level;
        Player player = client == null ? null : client.player;
        if (level == null || player == null) {
            reset();
            return;
        }
        int radius = Math.clamp(config.sign_translation_radius, 1, 16);
        boolean refreshStarted = refreshPressed && !refreshWasPressed;
        refreshWasPressed = refreshPressed;
        if (level == lastLevel
                && radius == lastRadius
                && config.continuous_sign_translation == lastContinuousTranslation
                && !refreshStarted
                && ticksUntilScan-- > 0) {
            return;
        }
        ticksUntilScan = SCAN_INTERVAL_TICKS;
        lastLevel = level;
        lastRadius = radius;
        lastContinuousTranslation = config.continuous_sign_translation;
        rebuild(level, player, radius, client.isTextFilteringEnabled(), config, renderTranslated, refreshPressed);
    }

    public static Component[] translatedLines(SignFaceKey key) {
        Component[] lines = TRANSLATED.get(key);
        return lines == null ? null : Arrays.copyOf(lines, lines.length);
    }

    public static boolean isGroupedFace(SignFaceKey key) {
        return GROUPED_FACES.contains(key);
    }

    public static String pendingAnimationKey(SignFaceKey key) {
        return PENDING_ANIMATION_KEYS.get(key);
    }

    public static void reset() {
        TRANSLATED = Map.of();
        PENDING_ANIMATION_KEYS = Map.of();
        GROUPED_FACES = Set.of();
        ticksUntilScan = 0;
        lastLevel = null;
        lastRadius = -1;
        lastContinuousTranslation = false;
        refreshWasPressed = false;
    }

    private static void rebuild(
            ClientLevel level,
            Player player,
            int radius,
            boolean filteringEnabled,
            OtherTranslationsConfig config,
            boolean renderTranslated,
            boolean refreshPressed
    ) {
        List<FaceCandidate> candidates = scanLoadedSigns(level, player, radius, filteringEnabled);
        if (!config.continuous_sign_translation) {
            Map<SignFaceKey, Component[]> translated = new HashMap<>();
            Map<SignFaceKey, String> pendingAnimationKeys = new HashMap<>();
            for (List<FaceCandidate> sign : standaloneSigns(candidates)) {
                translateStandaloneSign(sign, config, translated, pendingAnimationKeys, refreshPressed);
            }
            TRANSLATED = Map.copyOf(translated);
            PENDING_ANIMATION_KEYS = Map.copyOf(pendingAnimationKeys);
            GROUPED_FACES = Set.of();
            return;
        }
        List<List<FaceCandidate>> groups = adjacentGroups(candidates);
        Set<SignFaceKey> grouped = new HashSet<>();
        Map<SignFaceKey, Component[]> translated = new HashMap<>();
        Map<SignFaceKey, String> pendingAnimationKeys = new HashMap<>();
        for (List<FaceCandidate> group : groups) {
            if (group.size() < 2) {
                for (FaceCandidate candidate : group) {
                    translateFace(
                            candidate,
                            config,
                            translated,
                            pendingAnimationKeys,
                            refreshPressed
                    );
                }
                continue;
            }
            grouped.addAll(group.stream().map(FaceCandidate::key).toList());
            for (int start = 0; start < group.size(); start += MAX_FACES_PER_GROUP) {
                List<FaceCandidate> partition = group.subList(start, Math.min(start + MAX_FACES_PER_GROUP, group.size()));
                if (!renderTranslated && !refreshPressed) {
                    continue;
                }
                translateGroup(
                        partition,
                        config,
                        translated,
                        pendingAnimationKeys,
                        refreshPressed
                );
            }
        }
        TRANSLATED = Map.copyOf(translated);
        PENDING_ANIMATION_KEYS = Map.copyOf(pendingAnimationKeys);
        GROUPED_FACES = Set.copyOf(grouped);
    }

    private static List<List<FaceCandidate>> standaloneSigns(List<FaceCandidate> candidates) {
        Map<BlockPos, List<FaceCandidate>> signs = new LinkedHashMap<>();
        for (FaceCandidate candidate : candidates) {
            signs.computeIfAbsent(candidate.key().pos(), ignored -> new ArrayList<>()).add(candidate);
        }
        return List.copyOf(signs.values());
    }

    private static void translateStandaloneSign(
            List<FaceCandidate> sign,
            OtherTranslationsConfig config,
            Map<SignFaceKey, Component[]> translated,
            Map<SignFaceKey, String> pendingAnimationKeys,
            boolean allowForceRefresh
    ) {
        List<Component> lines = new ArrayList<>(sign.size() * SignText.LINES);
        for (FaceCandidate face : sign) {
            lines.addAll(Arrays.asList(face.lines()));
        }
        try {
            ComponentTranslationBundle bundle = ComponentTranslationBundle.create(
                    lines,
                    ComponentTranslationRoute.SIGN_FACE,
                    "sign:standalone; faces=" + sign.size(),
                    STANDALONE_SIGN_POLICY_VERSION,
                    true
            );
            if (allowForceRefresh) {
                ComponentRenderTranslationSupport.maybeForceRefresh(bundle.cacheDocument(), config);
            }
            ComponentTranslationRuntime.Resolution<List<Component>> resolution = ComponentTranslationRuntime.resolve(
                    bundle.cacheDocument(),
                    config.target_language,
                    null,
                    () -> null,
                    bundle::apply,
                    "sign:standalone; pos=" + sign.getFirst().key().pos()
            );
            if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
                String animationKey = "sign:standalone:"
                        + (resolution.cacheKey().isBlank() ? sign.getFirst().key().pos() : resolution.cacheKey());
                for (FaceCandidate face : sign) {
                    pendingAnimationKeys.put(face.key(), animationKey);
                }
                return;
            }
            if (resolution.state() != ComponentTranslationRuntime.State.CACHE_HIT
                    || resolution.value() == null
                    || resolution.value().size() != lines.size()) {
                return;
            }
            for (int faceIndex = 0; faceIndex < sign.size(); faceIndex++) {
                Component[] faceLines = new Component[SignText.LINES];
                for (int lineIndex = 0; lineIndex < SignText.LINES; lineIndex++) {
                    faceLines[lineIndex] = resolution.value().get(faceIndex * SignText.LINES + lineIndex);
                }
                translated.put(sign.get(faceIndex).key(), faceLines);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static void translateFace(
            FaceCandidate candidate,
            OtherTranslationsConfig config,
            Map<SignFaceKey, Component[]> translated,
            Map<SignFaceKey, String> pendingAnimationKeys,
            boolean allowForceRefresh
    ) {
        try {
            ComponentTranslationBundle bundle = ComponentTranslationBundle.create(
                    Arrays.asList(candidate.lines()),
                    ComponentTranslationRoute.SIGN_FACE,
                    "sign:face; side=" + candidate.key().face().name().toLowerCase(),
                    "sign-face-v1"
            );
            if (allowForceRefresh) {
                ComponentRenderTranslationSupport.maybeForceRefresh(bundle.cacheDocument(), config);
            }
            ComponentTranslationRuntime.Resolution<List<Component>> resolution = ComponentTranslationRuntime.resolve(
                    bundle.cacheDocument(),
                    config.target_language,
                    null,
                    () -> null,
                    bundle::apply,
                    "sign:face; pos=" + candidate.key().pos()
                            + "; side=" + candidate.key().face().name().toLowerCase()
            );
            if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
                pendingAnimationKeys.put(
                        candidate.key(),
                        "sign:face:" + candidate.key().pos() + ":" + candidate.key().face().name()
                );
                return;
            }
            if (resolution.state() == ComponentTranslationRuntime.State.CACHE_HIT
                    && resolution.value() != null
                    && resolution.value().size() == SignText.LINES) {
                translated.put(candidate.key(), resolution.value().toArray(Component[]::new));
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static List<FaceCandidate> scanLoadedSigns(
            ClientLevel level,
            Player player,
            int radius,
            boolean filteringEnabled
    ) {
        Vec3 position = player.position();
        int minChunkX = ((int) Math.floor(position.x - radius)) >> 4;
        int maxChunkX = ((int) Math.floor(position.x + radius)) >> 4;
        int minChunkZ = ((int) Math.floor(position.z - radius)) >> 4;
        int maxChunkZ = ((int) Math.floor(position.z + radius)) >> 4;
        List<FaceCandidate> result = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof SignBlockEntity sign)
                            || position.distanceToSqr(Vec3.atCenterOf(sign.getBlockPos())) > (double) radius * radius) {
                        continue;
                    }
                    collectFace(result, sign, Face.FRONT, sign.getFrontText(), filteringEnabled);
                    collectFace(result, sign, Face.BACK, sign.getBackText(), filteringEnabled);
                }
            }
        }
        result.sort(Comparator.comparing((FaceCandidate candidate) -> candidate.key().pos().getY())
                .thenComparing(candidate -> candidate.key().pos().getZ())
                .thenComparing(candidate -> candidate.key().pos().getX())
                .thenComparing(candidate -> candidate.key().face()));
        return result;
    }

    private static void collectFace(
            List<FaceCandidate> result,
            SignBlockEntity sign,
            Face face,
            SignText text,
            boolean filteringEnabled
    ) {
        if (text == null) {
            return;
        }
        Component[] lines = text.getMessages(filteringEnabled);
        if (lines == null || lines.length != SignText.LINES) {
            return;
        }
        result.add(new FaceCandidate(
                new SignFaceKey(sign.getBlockPos(), face),
                Arrays.copyOf(lines, lines.length)
        ));
    }

    private static List<List<FaceCandidate>> adjacentGroups(List<FaceCandidate> candidates) {
        Set<SignFaceKey> visited = new HashSet<>();
        List<List<FaceCandidate>> groups = new ArrayList<>();
        for (FaceCandidate seed : candidates) {
            if (!visited.add(seed.key())) {
                continue;
            }
            List<FaceCandidate> group = new ArrayList<>();
            ArrayDeque<FaceCandidate> pending = new ArrayDeque<>();
            pending.add(seed);
            while (!pending.isEmpty()) {
                FaceCandidate current = pending.removeFirst();
                group.add(current);
                for (FaceCandidate candidate : candidates) {
                    if (visited.contains(candidate.key()) || !areAdjacent(current, candidate)) {
                        continue;
                    }
                    visited.add(candidate.key());
                    pending.addLast(candidate);
                }
            }
            group.sort(Comparator.comparing((FaceCandidate candidate) -> candidate.key().pos().getY())
                    .reversed()
                    .thenComparing(candidate -> candidate.key().pos().getZ())
                    .thenComparing(candidate -> candidate.key().pos().getX()));
            groups.add(group);
        }
        return groups;
    }

    private static boolean areAdjacent(FaceCandidate left, FaceCandidate right) {
        if (left.key().face() != right.key().face()) {
            return false;
        }
        BlockPos a = left.key().pos();
        BlockPos b = right.key().pos();
        return Math.abs(a.getX() - b.getX()) <= 1
                && Math.abs(a.getY() - b.getY()) <= 1
                && Math.abs(a.getZ() - b.getZ()) <= 1;
    }

    private static void translateGroup(
            List<FaceCandidate> group,
            OtherTranslationsConfig config,
            Map<SignFaceKey, Component[]> translated,
            Map<SignFaceKey, String> pendingAnimationKeys,
            boolean allowForceRefresh
    ) {
        List<Component> lines = new ArrayList<>(group.size() * SignText.LINES);
        for (FaceCandidate candidate : group) {
            lines.addAll(Arrays.asList(candidate.lines()));
        }
        try {
            ComponentTranslationBundle bundle = ComponentTranslationBundle.create(
                    lines,
                    ComponentTranslationRoute.SIGN_CONTINUOUS,
                    "sign:continuous; faces=" + group.size(),
                    "sign-continuous-v1"
            );
            if (allowForceRefresh) {
                ComponentRenderTranslationSupport.maybeForceRefresh(bundle.cacheDocument(), config);
            }
            ComponentTranslationRuntime.Resolution<List<Component>> resolution = ComponentTranslationRuntime.resolve(
                    bundle.cacheDocument(),
                    config.target_language,
                    null,
                    () -> null,
                    bundle::apply,
                    "sign:continuous; faces=" + group.size()
            );
            if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
                String animationKey = "sign:continuous:"
                        + (resolution.cacheKey().isBlank() ? group.getFirst().key().pos() : resolution.cacheKey());
                for (FaceCandidate candidate : group) {
                    pendingAnimationKeys.put(candidate.key(), animationKey);
                }
                return;
            }
            if (resolution.state() != ComponentTranslationRuntime.State.CACHE_HIT || resolution.value() == null) {
                return;
            }
            List<Component> responseLines = resolution.value();
            if (responseLines.size() != lines.size()) {
                return;
            }
            for (int faceIndex = 0; faceIndex < group.size(); faceIndex++) {
                Component[] faceLines = new Component[SignText.LINES];
                for (int lineIndex = 0; lineIndex < SignText.LINES; lineIndex++) {
                    faceLines[lineIndex] = responseLines.get(faceIndex * SignText.LINES + lineIndex);
                }
                translated.put(group.get(faceIndex).key(), faceLines);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean isActive(OtherTranslationsConfig config) {
        return ComponentRenderTranslationSupport.isFeatureEnabled(
                config,
                config != null && config.enabled_translate_signs
        );
    }

    public enum Face {
        FRONT,
        BACK
    }

    public record SignFaceKey(BlockPos pos, Face face) {
    }

    private record FaceCandidate(SignFaceKey key, Component[] lines) {
    }
}
