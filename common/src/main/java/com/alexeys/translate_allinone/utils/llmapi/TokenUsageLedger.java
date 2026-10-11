package com.alexeys.translate_allinone.utils.llmapi;

import com.alexeys.translate_allinone.utils.cache.CacheFileSaveSupport;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderType;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

public final class TokenUsageLedger {
    public static final int WINDOW_DAYS = 7;

    private static final int RETAINED_DAYS = 8;
    private static final int FILE_VERSION = 1;
    private static final long SAVE_DEBOUNCE_MILLIS = 30_000L;
    private static final String SAVE_THREAD_NAME = "translate_allinone-token-usage-save";
    private static final Gson GSON = LlmPayloadJsonSupport.gson();
    private static final Logger LOGGER = LoggerFactory.getLogger("translate_allinone");
    private static final ConcurrentMap<String, ModelUsage> MODELS = new ConcurrentHashMap<>();
    private static final AtomicBoolean SAVE_SCHEDULED = new AtomicBoolean();
    private static final ScheduledExecutorService SAVE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, SAVE_THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Path filePath;
    private static volatile Clock clock = Clock.systemDefaultZone();

    private TokenUsageLedger() {
    }

    public record UsageTotals(long inputTokens, long outputTokens, long calls) {
        public long totalTokens() {
            return inputTokens + outputTokens;
        }
    }

    public record ModelUsageSnapshot(
            String key,
            String provider,
            String model,
            UsageTotals today,
            UsageTotals last7Days,
            UsageTotals total
    ) {
    }

    public record Snapshot(
            List<ModelUsageSnapshot> models,
            UsageTotals today,
            UsageTotals last7Days,
            UsageTotals total
    ) {
    }

    public static synchronized void configure(Path path) {
        MODELS.clear();
        filePath = path;
        if (path != null) {
            load(path);
        }
    }

    public static void recordCall(ProviderSettings.OpenAISettings settings, String modelId) {
        resolve(settings, modelId).addCall();
        scheduleSave();
    }

    public static void recordCall(ProviderSettings.OllamaSettings settings, String modelId) {
        resolve(settings, modelId).addCall();
        scheduleSave();
    }

    public static void recordTokens(
            ProviderSettings.OpenAISettings settings,
            String modelId,
            long inputTokens,
            long outputTokens
    ) {
        resolve(settings, modelId).addTokens(inputTokens, outputTokens);
        scheduleSave();
    }

    public static void recordTokens(
            ProviderSettings.OllamaSettings settings,
            String modelId,
            long inputTokens,
            long outputTokens
    ) {
        resolve(settings, modelId).addTokens(inputTokens, outputTokens);
        scheduleSave();
    }

    public static Snapshot snapshot() {
        LocalDate today = today();
        LocalDate windowStart = today.minusDays(WINDOW_DAYS - 1L);
        List<ModelUsageSnapshot> models = new ArrayList<>(MODELS.size());
        long todayInput = 0L;
        long todayOutput = 0L;
        long todayCalls = 0L;
        long windowInput = 0L;
        long windowOutput = 0L;
        long windowCalls = 0L;
        long totalInput = 0L;
        long totalOutput = 0L;
        long totalCalls = 0L;

        for (Map.Entry<String, ModelUsage> entry : MODELS.entrySet()) {
            ModelUsage usage = entry.getValue();
            UsageTotals modelToday = usage.window(today, today);
            UsageTotals modelWindow = usage.window(windowStart, today);
            UsageTotals modelTotal = usage.totals();
            models.add(new ModelUsageSnapshot(
                    entry.getKey(),
                    usage.providerLabel(),
                    usage.modelLabel(),
                    modelToday,
                    modelWindow,
                    modelTotal
            ));
            todayInput += modelToday.inputTokens();
            todayOutput += modelToday.outputTokens();
            todayCalls += modelToday.calls();
            windowInput += modelWindow.inputTokens();
            windowOutput += modelWindow.outputTokens();
            windowCalls += modelWindow.calls();
            totalInput += modelTotal.inputTokens();
            totalOutput += modelTotal.outputTokens();
            totalCalls += modelTotal.calls();
        }

        models.sort(
                Comparator.comparingLong((ModelUsageSnapshot model) -> model.total().totalTokens()).reversed()
                        .thenComparing(Comparator.comparingLong(
                                (ModelUsageSnapshot model) -> model.total().calls()).reversed())
                        .thenComparing(ModelUsageSnapshot::key)
        );

        return new Snapshot(
                List.copyOf(models),
                new UsageTotals(todayInput, todayOutput, todayCalls),
                new UsageTotals(windowInput, windowOutput, windowCalls),
                new UsageTotals(totalInput, totalOutput, totalCalls)
        );
    }

    public static void save() {
        Path path = filePath;
        SAVE_SCHEDULED.set(false);
        if (path == null) {
            return;
        }

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");

            try (Writer writer = Files.newBufferedWriter(tempPath)) {
                GSON.toJson(toFileModel(), writer);
            }

            CacheFileSaveSupport.replaceWithRetry(tempPath, path);
        } catch (Exception e) {
            LOGGER.error("Failed to save token usage to {}", path, e);
        }
    }

    static void overrideClock(Clock replacement) {
        clock = replacement == null ? Clock.systemDefaultZone() : replacement;
    }

    private static void load(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            UsageFile file = GSON.fromJson(reader, UsageFile.class);
            if (file == null || file.models == null) {
                return;
            }

            LocalDate oldestRetainedDay = oldestRetainedDay();
            for (Map.Entry<String, ModelFileEntry> entry : file.models.entrySet()) {
                ModelFileEntry stored = entry.getValue();
                if (entry.getKey() == null || stored == null) {
                    continue;
                }

                ModelUsage usage = new ModelUsage();
                usage.describe(blankToEmpty(stored.provider), blankToEmpty(stored.model));
                usage.seedTotals(stored.input, stored.output, stored.calls);
                if (stored.days != null) {
                    for (Map.Entry<String, DayFileEntry> dayEntry : stored.days.entrySet()) {
                        LocalDate date = parseDay(dayEntry.getKey());
                        DayFileEntry day = dayEntry.getValue();
                        if (date == null || day == null || date.isBefore(oldestRetainedDay)) {
                            continue;
                        }
                        usage.seedDay(date, day.input, day.output, day.calls);
                    }
                }
                MODELS.put(entry.getKey(), usage);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load token usage from {}", path, e);
        }
    }

    private static UsageFile toFileModel() {
        UsageFile file = new UsageFile();
        file.version = FILE_VERSION;
        LocalDate oldestRetainedDay = oldestRetainedDay();

        for (Map.Entry<String, ModelUsage> entry : MODELS.entrySet()) {
            ModelUsage usage = entry.getValue();
            UsageTotals totals = usage.totals();
            ModelFileEntry stored = new ModelFileEntry();
            stored.provider = usage.providerLabel();
            stored.model = usage.modelLabel();
            stored.input = totals.inputTokens();
            stored.output = totals.outputTokens();
            stored.calls = totals.calls();
            stored.days = new LinkedHashMap<>();

            for (Map.Entry<LocalDate, DayUsage> dayEntry : usage.days.entrySet()) {
                LocalDate date = dayEntry.getKey();
                if (date.isBefore(oldestRetainedDay)) {
                    continue;
                }
                DayUsage day = dayEntry.getValue();
                stored.days.put(date.toString(), new DayFileEntry(day.input.sum(), day.output.sum(), day.calls.sum()));
            }

            file.models.put(entry.getKey(), stored);
            usage.pruneDays(oldestRetainedDay);
        }

        return file;
    }

    private static void scheduleSave() {
        if (filePath == null || !SAVE_SCHEDULED.compareAndSet(false, true)) {
            return;
        }

        try {
            SAVE_EXECUTOR.schedule(TokenUsageLedger::save, SAVE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            SAVE_SCHEDULED.set(false);
        }
    }

    private static ModelUsage resolve(ProviderSettings.OpenAISettings settings, String modelId) {
        if (settings == null) {
            return resolve("", "", "", "", modelId);
        }
        String providerType = settings.providerType() == null
                ? ""
                : settings.providerType().name().toLowerCase(Locale.ROOT);
        String resolvedModelId = blankToEmpty(modelId).isEmpty() ? blankToEmpty(settings.modelId()) : modelId;
        return resolve(
                blankToEmpty(settings.providerId()),
                blankToEmpty(settings.providerName()),
                providerType,
                blankToEmpty(settings.baseUrl()),
                resolvedModelId
        );
    }

    private static ModelUsage resolve(ProviderSettings.OllamaSettings settings, String modelId) {
        if (settings == null) {
            return resolve("", "", "", "", modelId);
        }
        String resolvedModelId = blankToEmpty(modelId).isEmpty() ? blankToEmpty(settings.modelId()) : modelId;
        return resolve(
                blankToEmpty(settings.providerId()),
                blankToEmpty(settings.providerName()),
                ApiProviderType.OLLAMA.name().toLowerCase(Locale.ROOT),
                blankToEmpty(settings.baseUrl()),
                resolvedModelId
        );
    }

    private static ModelUsage resolve(
            String providerId,
            String providerName,
            String providerType,
            String baseUrl,
            String modelId
    ) {
        String model = blankToEmpty(modelId);
        String providerKey = providerId.isEmpty()
                ? providerType + '|' + trimTrailingSlashes(baseUrl)
                : providerId;
        ModelUsage usage = MODELS.computeIfAbsent(providerKey + '|' + model, ignored -> new ModelUsage());
        usage.describe(providerLabel(providerName, providerId, providerType, baseUrl), model);
        return usage;
    }

    private static String providerLabel(
            String providerName,
            String providerId,
            String providerType,
            String baseUrl
    ) {
        if (!providerName.isEmpty()) {
            return providerName;
        }
        if (!providerId.isEmpty()) {
            return providerId;
        }
        String normalizedBaseUrl = trimTrailingSlashes(baseUrl);
        return normalizedBaseUrl.isEmpty() ? providerType : normalizedBaseUrl;
    }

    private static String trimTrailingSlashes(String value) {
        String trimmed = blankToEmpty(value);
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static LocalDate parseDay(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate today() {
        return LocalDate.now(clock);
    }

    private static LocalDate oldestRetainedDay() {
        return today().minusDays(RETAINED_DAYS - 1L);
    }

    private static final class ModelUsage {
        private final LongAdder input = new LongAdder();
        private final LongAdder output = new LongAdder();
        private final LongAdder calls = new LongAdder();
        private final ConcurrentMap<LocalDate, DayUsage> days = new ConcurrentHashMap<>();
        private volatile String provider = "";
        private volatile String model = "";

        private void describe(String providerLabel, String modelLabel) {
            if (!providerLabel.isEmpty()) {
                provider = providerLabel;
            }
            if (!modelLabel.isEmpty()) {
                model = modelLabel;
            }
        }

        private void addCall() {
            calls.increment();
            day().calls.increment();
        }

        private void addTokens(long inputTokens, long outputTokens) {
            long safeInput = Math.max(0L, inputTokens);
            long safeOutput = Math.max(0L, outputTokens);
            input.add(safeInput);
            output.add(safeOutput);
            DayUsage day = day();
            day.input.add(safeInput);
            day.output.add(safeOutput);
        }

        private UsageTotals totals() {
            return new UsageTotals(input.sum(), output.sum(), calls.sum());
        }

        private UsageTotals window(LocalDate from, LocalDate to) {
            long windowInput = 0L;
            long windowOutput = 0L;
            long windowCalls = 0L;
            for (Map.Entry<LocalDate, DayUsage> entry : days.entrySet()) {
                LocalDate date = entry.getKey();
                if (date.isBefore(from) || date.isAfter(to)) {
                    continue;
                }
                DayUsage day = entry.getValue();
                windowInput += day.input.sum();
                windowOutput += day.output.sum();
                windowCalls += day.calls.sum();
            }
            return new UsageTotals(windowInput, windowOutput, windowCalls);
        }

        private void seedTotals(long inputTokens, long outputTokens, long callCount) {
            input.add(Math.max(0L, inputTokens));
            output.add(Math.max(0L, outputTokens));
            calls.add(Math.max(0L, callCount));
        }

        private void seedDay(LocalDate date, long inputTokens, long outputTokens, long callCount) {
            DayUsage day = new DayUsage();
            day.input.add(Math.max(0L, inputTokens));
            day.output.add(Math.max(0L, outputTokens));
            day.calls.add(Math.max(0L, callCount));
            days.put(date, day);
        }

        private void pruneDays(LocalDate oldestRetainedDay) {
            days.keySet().removeIf(date -> date.isBefore(oldestRetainedDay));
        }

        private DayUsage day() {
            return days.computeIfAbsent(today(), ignored -> new DayUsage());
        }

        private String providerLabel() {
            return provider;
        }

        private String modelLabel() {
            return model;
        }
    }

    private static final class DayUsage {
        private final LongAdder input = new LongAdder();
        private final LongAdder output = new LongAdder();
        private final LongAdder calls = new LongAdder();
    }

    private static final class UsageFile {
        private int version;
        private Map<String, ModelFileEntry> models = new LinkedHashMap<>();
    }

    private static final class ModelFileEntry {
        private String provider = "";
        private String model = "";
        private long input;
        private long output;
        private long calls;
        private Map<String, DayFileEntry> days = new LinkedHashMap<>();
    }

    private static final class DayFileEntry {
        private long input;
        private long output;
        private long calls;

        private DayFileEntry() {
        }

        private DayFileEntry(long input, long output, long calls) {
            this.input = input;
            this.output = output;
            this.calls = calls;
        }
    }
}
