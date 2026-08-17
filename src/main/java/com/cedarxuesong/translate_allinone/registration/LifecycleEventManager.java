package com.cedarxuesong.translate_allinone.registration;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.cache.ChatOutputTranslationCache;
import com.cedarxuesong.translate_allinone.utils.cache.ScoreboardTextCache;
import com.cedarxuesong.translate_allinone.utils.cache.SkyblockNpcTranslationCache;
import com.cedarxuesong.translate_allinone.utils.cache.component.ComponentCacheMigrationManager;
import com.cedarxuesong.translate_allinone.utils.cache.component.ComponentTranslationStoreRegistry;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.cedarxuesong.translate_allinone.utils.cache.WynnDialogueTextCache;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.cache.WynntilsTaskTrackerTextCache;
import com.cedarxuesong.translate_allinone.utils.translate.ItemTranslateManager;
import com.cedarxuesong.translate_allinone.utils.translate.ScoreboardTranslateManager;
import com.cedarxuesong.translate_allinone.utils.translate.BookTranslationSupport;
import com.cedarxuesong.translate_allinone.utils.translate.ChatOutputTranslateManager;
import com.cedarxuesong.translate_allinone.utils.translate.ComponentRenderTranslationSupport;
import com.cedarxuesong.translate_allinone.utils.translate.ContinuousSignTranslationCoordinator;
import com.cedarxuesong.translate_allinone.utils.translate.ScoreboardTranslationInputSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTextDebugCopySupport;
import com.cedarxuesong.translate_allinone.utils.translate.TextDisplayTranslationSupport;
import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueTranslateManager;
import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueTranslationSupport;
import com.cedarxuesong.translate_allinone.utils.translate.WynntilsTaskTrackerTranslateManager;
import com.cedarxuesong.translate_allinone.utils.update.UpdateCheckManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LifecycleEventManager {

    public static final Logger LOGGER = LoggerFactory.getLogger(Translate_AllinOne.MOD_ID);

    public static volatile boolean isReadyForTranslation = false;
    private static boolean awaitingReadinessCheck = false;
    private static int readinessGracePeriodTicks = -1;
    private static final int GRACE_PERIOD_DURATION_TICKS = 20; // 1 second (20 ticks/sec)

    public static void register() {
        registerShutdownHook();
        registerJoinHandler();
        registerReadinessTickHandler();
        registerDisconnectHandler();
    }

    public static synchronized void globalTranslationFeatureChanged(boolean enabled) {
        ComponentTranslationRuntime.providerConfigurationChanged();
        BookTranslationSupport.resetSession();
        ContinuousSignTranslationCoordinator.reset();
        TextDisplayTranslationSupport.resetSession();
        ScoreboardTranslationInputSupport.reset();
        WynnDialogueTranslationSupport.resetSession();
        ChatOutputTranslateManager.cancelPendingTranslations();

        if (!enabled) {
            WynnDialogueTranslateManager.getInstance().cancelPendingTranslations();
            WynntilsTaskTrackerTranslateManager.getInstance().cancelPendingTranslations();
            return;
        }

        if (isReadyForTranslation) {
            WynnDialogueTranslateManager.getInstance().start();
            WynntilsTaskTrackerTranslateManager.getInstance().start();
        }
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Game is shutting down, performing final cache save...");
            saveCaches();
            ComponentTranslationStoreRegistry.getInstance().endSession();
        }));
    }

    private static void registerJoinHandler() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            resetReadinessState();
            ComponentTranslationRuntime.beginSession();
            BookTranslationSupport.resetSession();
            ContinuousSignTranslationCoordinator.reset();
            TextDisplayTranslationSupport.resetSession();
            awaitingReadinessCheck = true;
            LOGGER.info("Player joining world, awaiting client readiness for translation...");

            stopTranslationManagers();
            loadCachesAndStartTranslationManagers();
        });
    }

    private static void registerReadinessTickHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (awaitingReadinessCheck && client.player != null && client.world != null && client.currentScreen == null) {
                awaitingReadinessCheck = false;
                readinessGracePeriodTicks = GRACE_PERIOD_DURATION_TICKS;
                LOGGER.info("Client is ready. Starting grace period for {} ticks before enabling translations.", readinessGracePeriodTicks);
            }

            if (readinessGracePeriodTicks > 0) {
                readinessGracePeriodTicks--;
                if (readinessGracePeriodTicks == 0) {
                    isReadyForTranslation = true;
                    LOGGER.info("Grace period over. Translations are now active.");
                }
            }

            if (isReadyForTranslation) {
                UpdateCheckManager.tryNotifyInChat(client);
                WynnDialogueTranslationSupport.tick();
                ContinuousSignTranslationCoordinator.tick();
            }
            ScoreboardTranslationInputSupport.tick(
                    Translate_AllinOne.getConfig() == null
                            ? null
                            : Translate_AllinOne.getConfig().scoreboardTranslate
            );
            ComponentRenderTranslationSupport.tickRefreshState(
                    Translate_AllinOne.getConfig() == null
                            ? null
                            : Translate_AllinOne.getConfig().otherTranslations
            );
            TooltipTextDebugCopySupport.tick(client);
        });
    }

    private static void registerDisconnectHandler() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            resetReadinessState();
            ComponentTranslationRuntime.endSession();
            BookTranslationSupport.resetSession();
            ContinuousSignTranslationCoordinator.reset();
            TextDisplayTranslationSupport.resetSession();
            LOGGER.info("Player has disconnected. Translation readiness reset.");
            stopTranslationManagers();
            saveCaches();
            ComponentTranslationStoreRegistry.getInstance().endSession();
        });
    }

    private static void stopTranslationManagers() {
        ItemTranslateManager.getInstance().stop();
        ScoreboardTranslateManager.getInstance().stop();
        WynnDialogueTranslateManager.getInstance().stop();
        WynntilsTaskTrackerTranslateManager.getInstance().stop();
        WynnDialogueTranslationSupport.resetSession();
    }

    private static void loadCachesAndStartTranslationManagers() {
        ComponentCacheMigrationManager.migrateLegacyCaches(ComponentTranslationStoreRegistry.getInstance());
        ComponentTranslationStoreRegistry.getInstance().load();
        ChatOutputTranslationCache.getInstance().load();
        SkyblockNpcTranslationCache.getInstance().load();
        ItemTemplateCache.getInstance().load();
        ItemTranslateManager.getInstance().start();
        ScoreboardTextCache.getInstance().load();
        ScoreboardTranslateManager.getInstance().start();
        WynnDialogueTextCache.getInstance().load();
        WynnDialogueTranslateManager.getInstance().start();
        WynntilsTaskTrackerTextCache.getInstance().load();
        WynntilsTaskTrackerTranslateManager.getInstance().start();
    }

    private static void saveCaches() {
        ComponentTranslationStoreRegistry.getInstance().save();
        ChatOutputTranslationCache.getInstance().save();
        SkyblockNpcTranslationCache.getInstance().save();
        ItemTemplateCache.getInstance().save();
        ScoreboardTextCache.getInstance().save();
        WynnDialogueTextCache.getInstance().save();
        WynntilsTaskTrackerTextCache.getInstance().save();
    }

    private static void resetReadinessState() {
        isReadyForTranslation = false;
        awaitingReadinessCheck = false;
        readinessGracePeriodTicks = -1;
    }
}
