package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationBundle;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.cedarxuesong.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
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
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

public final class ContinuousSignTranslationCoordinator {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int MAX_FACES_PER_GROUP = 16;
    private static final String STANDALONE_SIGN_POLICY_VERSION = "sign-standalone-v2";
    private static volatile Map<SignFaceKey, Text[]> translated = Map.of();
    private static volatile Map<SignFaceKey, String> pendingAnimationKeys = Map.of();
    private static volatile Set<SignFaceKey> groupedFaces = Set.of();
    private static int ticksUntilScan;
    private static ClientWorld lastWorld;
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
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client == null ? null : client.world;
        PlayerEntity player = client == null ? null : client.player;
        if (world == null || player == null) {
            reset();
            return;
        }
        int radius = MathHelper.clamp(config.sign_translation_radius, 1, 16);
        boolean refreshStarted = refreshPressed && !refreshWasPressed;
        refreshWasPressed = refreshPressed;
        if (world == lastWorld
                && radius == lastRadius
                && config.continuous_sign_translation == lastContinuousTranslation
                && !refreshStarted
                && ticksUntilScan-- > 0) {
            return;
        }
        ticksUntilScan = SCAN_INTERVAL_TICKS;
        lastWorld = world;
        lastRadius = radius;
        lastContinuousTranslation = config.continuous_sign_translation;
        rebuild(world, player, radius, client.shouldFilterText(), config, renderTranslated, refreshPressed);
    }

    public static Text[] translatedLines(SignFaceKey key) {
        Text[] lines = translated.get(key);
        return lines == null ? null : Arrays.copyOf(lines, lines.length);
    }

    public static boolean isGroupedFace(SignFaceKey key) {
        return groupedFaces.contains(key);
    }

    public static String pendingAnimationKey(SignFaceKey key) {
        return pendingAnimationKeys.get(key);
    }

    public static void reset() {
        translated = Map.of();
        pendingAnimationKeys = Map.of();
        groupedFaces = Set.of();
        ticksUntilScan = 0;
        lastWorld = null;
        lastRadius = -1;
        lastContinuousTranslation = false;
        refreshWasPressed = false;
    }

    private static void rebuild(
            ClientWorld world,
            PlayerEntity player,
            int radius,
            boolean filteringEnabled,
            OtherTranslationsConfig config,
            boolean renderTranslated,
            boolean refreshPressed
    ) {
        List<FaceCandidate> candidates = scanLoadedSigns(world, player, radius, filteringEnabled);
        if (!config.continuous_sign_translation) {
            Map<SignFaceKey, Text[]> translatedFaces = new HashMap<>();
            Map<SignFaceKey, String> pendingKeys = new HashMap<>();
            for (List<FaceCandidate> sign : standaloneSigns(candidates)) {
                translateStandaloneSign(sign, config, translatedFaces, pendingKeys, refreshPressed);
            }
            translated = Map.copyOf(translatedFaces);
            pendingAnimationKeys = Map.copyOf(pendingKeys);
            groupedFaces = Set.of();
            return;
        }
        List<List<FaceCandidate>> groups = adjacentGroups(candidates);
        Set<SignFaceKey> grouped = new HashSet<>();
        Map<SignFaceKey, Text[]> translatedFaces = new HashMap<>();
        Map<SignFaceKey, String> pendingKeys = new HashMap<>();
        for (List<FaceCandidate> group : groups) {
            if (group.size() < 2) {
                for (FaceCandidate candidate : group) {
                    translateFace(candidate, config, translatedFaces, pendingKeys, refreshPressed);
                }
                continue;
            }
            grouped.addAll(group.stream().map(FaceCandidate::key).toList());
            for (int start = 0; start < group.size(); start += MAX_FACES_PER_GROUP) {
                List<FaceCandidate> partition = group.subList(start, Math.min(start + MAX_FACES_PER_GROUP, group.size()));
                if (!renderTranslated && !refreshPressed) {
                    continue;
                }
                translateGroup(partition, config, translatedFaces, pendingKeys, refreshPressed);
            }
        }
        translated = Map.copyOf(translatedFaces);
        pendingAnimationKeys = Map.copyOf(pendingKeys);
        groupedFaces = Set.copyOf(grouped);
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
            Map<SignFaceKey, Text[]> translatedFaces,
            Map<SignFaceKey, String> pendingKeys,
            boolean allowForceRefresh
    ) {
        List<Text> lines = new ArrayList<>(sign.size() * SignText.field_43299);
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
            ComponentTranslationRuntime.Resolution<List<Text>> resolution = ComponentTranslationRuntime.resolve(
                    bundle.cacheDocument(),
                    config.target_language,
                    null,
                    () -> null,
                    bundle::apply,
                    "sign:standalone; pos=" + sign.get(0).key().pos()
            );
            if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
                String animationKey = "sign:standalone:"
                        + (resolution.cacheKey().isBlank() ? sign.get(0).key().pos() : resolution.cacheKey());
                for (FaceCandidate face : sign) {
                    pendingKeys.put(face.key(), animationKey);
                }
                return;
            }
            if (resolution.state() != ComponentTranslationRuntime.State.CACHE_HIT
                    || resolution.value() == null
                    || resolution.value().size() != lines.size()) {
                return;
            }
            for (int faceIndex = 0; faceIndex < sign.size(); faceIndex++) {
                Text[] faceLines = new Text[SignText.field_43299];
                for (int lineIndex = 0; lineIndex < SignText.field_43299; lineIndex++) {
                    faceLines[lineIndex] = resolution.value().get(faceIndex * SignText.field_43299 + lineIndex);
                }
                translatedFaces.put(sign.get(faceIndex).key(), faceLines);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static void translateFace(
            FaceCandidate candidate,
            OtherTranslationsConfig config,
            Map<SignFaceKey, Text[]> translatedFaces,
            Map<SignFaceKey, String> pendingKeys,
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
            ComponentTranslationRuntime.Resolution<List<Text>> resolution = ComponentTranslationRuntime.resolve(
                    bundle.cacheDocument(),
                    config.target_language,
                    null,
                    () -> null,
                    bundle::apply,
                    "sign:face; pos=" + candidate.key().pos()
                            + "; side=" + candidate.key().face().name().toLowerCase()
            );
            if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
                pendingKeys.put(
                        candidate.key(),
                        "sign:face:" + candidate.key().pos() + ":" + candidate.key().face().name()
                );
                return;
            }
            if (resolution.state() == ComponentTranslationRuntime.State.CACHE_HIT
                    && resolution.value() != null
                    && resolution.value().size() == SignText.field_43299) {
                translatedFaces.put(candidate.key(), resolution.value().toArray(Text[]::new));
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static List<FaceCandidate> scanLoadedSigns(
            ClientWorld world,
            PlayerEntity player,
            int radius,
            boolean filteringEnabled
    ) {
        Vec3d position = player.getPos();
        int minChunkX = ((int) Math.floor(position.x - radius)) >> 4;
        int maxChunkX = ((int) Math.floor(position.x + radius)) >> 4;
        int minChunkZ = ((int) Math.floor(position.z - radius)) >> 4;
        int maxChunkZ = ((int) Math.floor(position.z + radius)) >> 4;
        List<FaceCandidate> result = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof SignBlockEntity sign)
                            || position.squaredDistanceTo(Vec3d.ofCenter(sign.getPos())) > (double) radius * radius) {
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
        Text[] lines = text.getMessages(filteringEnabled);
        if (lines == null || lines.length != SignText.field_43299) {
            return;
        }
        result.add(new FaceCandidate(
                new SignFaceKey(sign.getPos(), face),
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
            Map<SignFaceKey, Text[]> translatedFaces,
            Map<SignFaceKey, String> pendingKeys,
            boolean allowForceRefresh
    ) {
        List<Text> lines = new ArrayList<>(group.size() * SignText.field_43299);
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
            ComponentTranslationRuntime.Resolution<List<Text>> resolution = ComponentTranslationRuntime.resolve(
                    bundle.cacheDocument(),
                    config.target_language,
                    null,
                    () -> null,
                    bundle::apply,
                    "sign:continuous; faces=" + group.size()
            );
            if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
                String animationKey = "sign:continuous:"
                        + (resolution.cacheKey().isBlank() ? group.get(0).key().pos() : resolution.cacheKey());
                for (FaceCandidate candidate : group) {
                    pendingKeys.put(candidate.key(), animationKey);
                }
                return;
            }
            if (resolution.state() != ComponentTranslationRuntime.State.CACHE_HIT || resolution.value() == null) {
                return;
            }
            List<Text> responseLines = resolution.value();
            if (responseLines.size() != lines.size()) {
                return;
            }
            for (int faceIndex = 0; faceIndex < group.size(); faceIndex++) {
                Text[] faceLines = new Text[SignText.field_43299];
                for (int lineIndex = 0; lineIndex < SignText.field_43299; lineIndex++) {
                    faceLines[lineIndex] = responseLines.get(faceIndex * SignText.field_43299 + lineIndex);
                }
                translatedFaces.put(group.get(faceIndex).key(), faceLines);
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

    private record FaceCandidate(SignFaceKey key, Text[] lines) {
    }
}
