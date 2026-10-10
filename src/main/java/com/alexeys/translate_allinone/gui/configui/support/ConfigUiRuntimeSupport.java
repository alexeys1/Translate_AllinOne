package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.registration.ConfigManager;
import com.alexeys.translate_allinone.utils.cache.CacheBackupManager;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderType;
import com.alexeys.translate_allinone.utils.llmapi.ProviderConnectionTester;
import com.alexeys.translate_allinone.utils.translate.WynnSharedDictionaryService;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class ConfigUiRuntimeSupport {
    private ConfigUiRuntimeSupport() {
    }

    public static boolean saveConfig(
            Translator translator,
            StatusSetter statusSetter,
            int okColor,
            int errorColor,
            ErrorLogger errorLogger
    ) {
        try {
            ConfigManager.save();
            CompletableFuture.runAsync(CacheBackupManager::enforceBackupLimit);
            WynnSharedDictionaryService.getInstance().loadAll();
            statusSetter.set(translator.t("status.config_saved", ConfigManager.getConfigPath().getFileName()), okColor);
            return true;
        } catch (Exception e) {
            errorLogger.error("Failed to save config", e);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            statusSetter.set(translator.t("error.save_failed", reason), errorColor);
            return false;
        }
    }

    public static void testProviderConnection(
            ApiProviderProfile profile,
            Translator translator,
            StatusSetter statusSetter,
            int okColor,
            int errorColor,
            Consumer<Runnable> uiThreadExecutor
    ) {
        statusSetter.set(translator.t("status.testing_provider", profile.name), okColor);
        ProviderConnectionTester.test(profile).whenComplete((result, throwable) -> {
            Runnable updateStatus = () -> {
                if (throwable != null) {
                    String reason = throwable.getMessage() == null ? "request failed" : throwable.getMessage();
                    statusSetter.set(translator.t("status.test_failed", profile.name, reason), errorColor);
                    return;
                }

                if (result == null) {
                    statusSetter.set(translator.t("status.test_failed", profile.name, "null result"), errorColor);
                    return;
                }

                if (result.success()) {
                    statusSetter.set(translator.t("status.test_success", profile.name, result.detail()), okColor);
                } else {
                    statusSetter.set(translator.t("status.test_failed", profile.name, result.detail()), errorColor);
                }
            };

            uiThreadExecutor.accept(updateStatus);
        });
    }

    @FunctionalInterface
    public interface Translator {
        Component t(String key, Object... args);
    }

    @FunctionalInterface
    public interface StatusSetter {
        void set(Component message, int color);
    }

    @FunctionalInterface
    public interface ErrorLogger {
        void error(String message, Throwable throwable);
    }

    @FunctionalInterface
    public interface ActionBlockAdder {
        void add(
                int x,
                int y,
                int width,
                int height,
                Supplier<Component> labelSupplier,
                Runnable action,
                int color,
                int hoverColor,
                int textColor,
                boolean centered,
                Component tooltip
        );

        default void add(
                int x,
                int y,
                int width,
                int height,
                Supplier<Component> labelSupplier,
                Runnable action,
                int color,
                int hoverColor,
                int textColor,
                boolean centered
        ) {
            add(x, y, width, height, labelSupplier, action, color, hoverColor, textColor, centered, null);
        }
    }

    @FunctionalInterface
    public interface TextFieldAdder {
        EditBox add(
                int x,
                int y,
                int width,
                int maxLength,
                String initialValue,
                Component placeholder,
                Consumer<String> changed,
                boolean editable
        );
    }

    @FunctionalInterface
    public interface ToggleAdder {
        void add(
                int x,
                int y,
                int width,
                Component label,
                BooleanSupplier getter,
                Consumer<Boolean> setter,
                Component tooltip,
                boolean defaultValue
        );
    }

    @FunctionalInterface
    public interface GroupBoxAdder {
        void add(int x, int y, int width, int height, Component title);
    }

    @FunctionalInterface
    public interface ProviderTypeLabelProvider {
        Component label(ApiProviderType providerType);
    }
}
