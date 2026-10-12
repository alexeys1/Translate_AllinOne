package com.alexeys.translate_allinone.utils.config;

import com.alexeys.translate_allinone.utils.cache.CacheFileSaveSupport;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PromptOverrideStore {
    public static final int CURRENT_RESET_GENERATION = 1;
    public static final String FILE_NAME = "prompts.json";

    private static final Logger LOGGER = LoggerFactory.getLogger("translate_allinone");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static volatile PromptOverrideStore instance = new PromptOverrideStore(null);

    private final Map<String, Map<String, String>> prompts = new LinkedHashMap<>();
    private final Path filePath;

    private int resetGeneration;
    private boolean resetNoticePending;
    private Runnable changeListener;

    private PromptOverrideStore(Path filePath) {
        this.filePath = filePath;
        load();
    }

    public static PromptOverrideStore getInstance() {
        return instance;
    }

    public static synchronized void initialize(Path filePath) {
        instance = new PromptOverrideStore(filePath);
    }

    public synchronized void setChangeListener(Runnable listener) {
        changeListener = listener;
    }

    public synchronized Map<String, String> overridesFor(String providerId) {
        Map<String, String> stored = providerId == null ? null : prompts.get(providerId);
        return stored == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stored);
    }

    public void replaceProviderOverrides(String providerId, Map<String, String> overrides) {
        if (providerId == null || providerId.isBlank()) {
            return;
        }

        boolean changed;
        synchronized (this) {
            Map<String, Map<String, String>> next = new LinkedHashMap<>(prompts);
            Map<String, String> sanitized = sanitize(overrides);
            if (sanitized.isEmpty()) {
                next.remove(providerId);
            } else {
                next.put(providerId, sanitized);
            }
            changed = apply(next);
        }
        notifyContentChanged(changed);
    }

    public void clear() {
        boolean changed;
        synchronized (this) {
            changed = apply(new LinkedHashMap<>());
        }
        notifyContentChanged(changed);
    }

    public void retainProviderIds(Collection<String> providerIds) {
        if (providerIds == null) {
            return;
        }

        boolean changed;
        synchronized (this) {
            Set<String> retainedIds = new LinkedHashSet<>(providerIds);
            Map<String, Map<String, String>> retained = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, String>> entry : prompts.entrySet()) {
                if (retainedIds.contains(entry.getKey())) {
                    retained.put(entry.getKey(), entry.getValue());
                }
            }
            changed = apply(retained);
        }
        notifyContentChanged(changed);
    }

    private boolean apply(Map<String, Map<String, String>> next) {
        Map<String, Map<String, String>> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : next.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            Map<String, String> overrides = sanitize(entry.getValue());
            if (!overrides.isEmpty()) {
                sanitized.put(entry.getKey(), overrides);
            }
        }

        if (sanitized.equals(prompts)) {
            return false;
        }

        prompts.clear();
        prompts.putAll(sanitized);
        persist();
        return true;
    }

    private void notifyContentChanged(boolean changed) {
        if (!changed) {
            return;
        }

        Runnable listener;
        synchronized (this) {
            listener = changeListener;
        }
        if (listener != null) {
            listener.run();
        }
    }

    private static Map<String, String> sanitize(Map<String, String> source) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (source == null) {
            return sanitized;
        }

        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            sanitized.put(entry.getKey(), value);
        }
        return sanitized;
    }

    private void load() {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            return;
        }

        PromptOverridesData data = null;
        try (Reader reader = Files.newBufferedReader(filePath)) {
            data = GSON.fromJson(reader, PromptOverridesData.class);
        } catch (Exception error) {
            quarantineCorruptFile(error);
        }

        if (data == null) {
            return;
        }

        prompts.clear();
        if (data.prompts != null) {
            for (Map.Entry<String, Map<String, String>> entry : data.prompts.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                Map<String, String> overrides = sanitize(entry.getValue());
                if (!overrides.isEmpty()) {
                    prompts.put(entry.getKey(), overrides);
                }
            }
        }
        resetGeneration = Math.max(0, data.reset_generation);
        resetNoticePending = data.reset_notice_pending;
    }

    private void quarantineCorruptFile(Exception error) {
        Path backupPath = filePath.resolveSibling(FILE_NAME + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.error(
                    "Prompt override file is unreadable, using built-in defaults and keeping a copy at {}",
                    backupPath,
                    error
            );
        } catch (Exception copyError) {
            LOGGER.error("Prompt override file is unreadable, using built-in defaults: {}", filePath, copyError);
        }
    }

    private void persist() {
        if (filePath == null) {
            return;
        }

        PromptOverridesData data = new PromptOverridesData();
        data.prompts = new LinkedHashMap<>(prompts);
        data.reset_generation = resetGeneration;
        data.reset_notice_pending = resetNoticePending;

        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempPath = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath)) {
                GSON.toJson(data, writer);
            }
            CacheFileSaveSupport.replaceWithRetry(tempPath, filePath);
        } catch (Exception error) {
            LOGGER.error("Failed to persist prompt overrides: {}", filePath, error);
        }
    }

    private static final class PromptOverridesData {
        @SerializedName("prompts")
        private Map<String, Map<String, String>> prompts = new LinkedHashMap<>();

        @SerializedName("reset_generation")
        private int reset_generation;

        @SerializedName("reset_notice_pending")
        private boolean reset_notice_pending;
    }
}
