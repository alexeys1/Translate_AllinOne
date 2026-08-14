package com.cedarxuesong.translate_allinone.utils.update;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateCheckManager {
    private static final String MODRINTH_PROJECT_ID = "WEuWEFmQ";
    private static final String MODRINTH_VERSION_URL_PREFIX = "https://modrinth.com/mod/translate_allinone(fork)/version/";
    private static final String MODRINTH_VERSIONS_API_PREFIX = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_ID + "/version";

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private static final AtomicBoolean STARTUP_CHECK_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean CHAT_NOTIFICATION_SENT = new AtomicBoolean(false);

    private static volatile boolean checkCompleted = false;
    private static volatile boolean updateAvailable = false;
    private static volatile String currentVersion = "unknown";
    private static volatile String latestVersion = "";
    private static volatile String latestReleaseUrl = "";
    private static volatile String lastError = "";

    private UpdateCheckManager() {
    }

    public static void startStartupCheck() {
        if (!STARTUP_CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }

        currentVersion = resolveCurrentVersion();
        NumericVersion current = NumericVersion.parse(currentVersion);
        if (current == null) {
            lastError = "Current mod version is not numeric: " + currentVersion;
            checkCompleted = true;
            Translate_AllinOne.LOGGER.warn("Skip update check: {}", lastError);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildModrinthVersionsApiUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "Translate_AllinOne/" + currentVersion)
                .GET()
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        lastError = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
                        checkCompleted = true;
                        Translate_AllinOne.LOGGER.warn("Update check failed: {}", lastError);
                        return;
                    }

                    try {
                        handleModrinthVersionsResponse(response, current);
                    } catch (Exception e) {
                        lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        Translate_AllinOne.LOGGER.warn("Update check parse failed: {}", lastError);
                    } finally {
                        checkCompleted = true;
                    }
                });
    }

    public static boolean isCheckCompleted() {
        return checkCompleted;
    }

    public static boolean hasUpdateAvailable() {
        return checkCompleted && updateAvailable;
    }

    public static boolean shouldShowConfigNotice() {
        return hasUpdateAvailable();
    }

    public static String currentVersion() {
        return currentVersion;
    }

    public static String latestVersion() {
        return latestVersion;
    }

    public static String latestReleaseUrl() {
        return latestReleaseUrl;
    }

    public static String lastError() {
        return lastError;
    }

    public static void tryNotifyInChat(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }
        if (!hasUpdateAvailable()) {
            return;
        }
        if (!CHAT_NOTIFICATION_SENT.compareAndSet(false, true)) {
            return;
        }

        MutableText tip = Text.translatable("text.translate_allinone.update.chat.available", latestVersion, currentVersion)
                .formatted(Formatting.GOLD);
        MutableText link = Text.translatable("text.translate_allinone.update.chat.open")
                .setStyle(Style.EMPTY
                        .withColor(Formatting.AQUA)
                        .withUnderline(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, latestReleaseUrl))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Text.translatable("text.translate_allinone.update.chat.hover", latestReleaseUrl)
                        )));

        client.player.sendMessage(tip.append(Text.literal(" ")).append(link), false);
    }

    public static void openLatestReleasePage() {
        if (latestReleaseUrl == null || latestReleaseUrl.isBlank()) {
            return;
        }
        Util.getOperatingSystem().open(latestReleaseUrl);
    }

    private static void handleModrinthVersionsResponse(HttpResponse<String> response, NumericVersion current) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Modrinth API status=" + response.statusCode());
        }

        LatestModrinthVersion latestModrinthVersion = extractLatestModrinthVersion(response.body());
        if (latestModrinthVersion == null) {
            throw new IllegalStateException("No numeric Modrinth versions found");
        }

        latestVersion = latestModrinthVersion.versionNumber();
        latestReleaseUrl = MODRINTH_VERSION_URL_PREFIX + latestModrinthVersion.id();
        updateAvailable = latestModrinthVersion.version().compareTo(current) > 0;
        lastError = "";

        if (updateAvailable) {
            Translate_AllinOne.LOGGER.info("New version detected: latest={}, current={}", latestVersion, currentVersion);
        } else {
            Translate_AllinOne.LOGGER.info("Version is up to date: current={}, latest={}", currentVersion, latestVersion);
        }
    }

    private static LatestModrinthVersion extractLatestModrinthVersion(String body) {
        JsonArray versions = GSON.fromJson(body, JsonArray.class);
        if (versions == null || versions.isEmpty()) {
            return null;
        }

        LatestModrinthVersion best = null;
        for (JsonElement element : versions) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }

            JsonObject versionObj = element.getAsJsonObject();
            if (!versionObj.has("id") || !versionObj.get("id").isJsonPrimitive()
                    || !versionObj.has("version_number") || !versionObj.get("version_number").isJsonPrimitive()) {
                continue;
            }

            String id = versionObj.get("id").getAsString();
            String versionNumber = versionObj.get("version_number").getAsString();
            NumericVersion parsed = NumericVersion.parse(versionNumber);
            if (parsed == null) {
                continue;
            }

            LatestModrinthVersion candidate = new LatestModrinthVersion(id, versionNumber, parsed);
            if (best == null || candidate.version().compareTo(best.version()) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static String resolveCurrentVersion() {
        return FabricLoader.getInstance()
                .getModContainer(Translate_AllinOne.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String buildModrinthVersionsApiUrl() {
        String gameVersion = SharedConstants.getGameVersion().getId();
        String loaderFilter = URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8);
        String gameVersionFilter = URLEncoder.encode("[\"" + gameVersion + "\"]", StandardCharsets.UTF_8);
        return MODRINTH_VERSIONS_API_PREFIX
                + "?loaders=" + loaderFilter
                + "&game_versions=" + gameVersionFilter;
    }

    private record LatestModrinthVersion(String id, String versionNumber, NumericVersion version) {
    }
}
