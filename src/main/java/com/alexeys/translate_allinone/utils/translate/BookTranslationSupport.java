package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
public final class BookTranslationSupport {
    private static final String POLICY_VERSION = "book-page-v1";
    private static final String CACHE_CONTEXT = "book:page";

    private BookTranslationSupport() {
    }

    public static BookPageTranslationSnapshot resolveCurrentPage(BookViewScreen.BookAccess access, int pageIndex) {
        Component original = page(access, pageIndex);
        OtherTranslationsConfig config = ComponentRenderTranslationSupport.config();
        if (!isFeatureEnabled(config)) {
            return new BookPageTranslationSnapshot(
                    pageIndex,
                    original,
                    original,
                    ComponentTranslationRuntime.State.INELIGIBLE
            );
        }
        if (!ComponentRenderTranslationSupport.shouldRenderTranslated(config)) {
            ComponentRenderTranslationSupport.forceRefreshAndQueue(
                    original,
                    ComponentTranslationRoute.BOOK_PAGE,
                    CACHE_CONTEXT,
                    POLICY_VERSION,
                    config
            );
            return new BookPageTranslationSnapshot(
                    pageIndex,
                    original,
                    original,
                    ComponentTranslationRuntime.State.INELIGIBLE
            );
        }

        ComponentRenderTranslationSupport.TranslationResult result = resolvePage(original, pageIndex, config, true);
        if (config.book_prefetch_adjacent_pages) {
            prefetch(access, pageIndex - 1, config);
            prefetch(access, pageIndex + 1, config);
        }
        Component displayed = ComponentRenderTranslationSupport.displayWithPendingAnimation(
                result,
                "book:page:" + pageIndex
        );
        return new BookPageTranslationSnapshot(pageIndex, original, displayed, result.state());
    }

    public static void resetSession() {
        ComponentRenderTranslationSupport.resetRefreshState();
    }

    private static void prefetch(BookViewScreen.BookAccess access, int pageIndex, OtherTranslationsConfig config) {
        Component page = page(access, pageIndex);
        resolvePage(page, pageIndex, config, false);
    }

    private static ComponentRenderTranslationSupport.TranslationResult resolvePage(
            Component original,
            int pageIndex,
            OtherTranslationsConfig config,
            boolean allowForceRefresh
    ) {
        if (!ComponentRenderTranslationSupport.isEligible(original, config.book_max_page_characters)) {
            return ComponentRenderTranslationSupport.TranslationResult.original(original, null);
        }
        return ComponentRenderTranslationSupport.translate(
                original,
                ComponentTranslationRoute.BOOK_PAGE,
                CACHE_CONTEXT,
                POLICY_VERSION,
                config,
                allowForceRefresh
        );
    }

    private static boolean isFeatureEnabled(OtherTranslationsConfig config) {
        return ComponentRenderTranslationSupport.isFeatureEnabled(
                config,
                config != null && config.enabled_translate_written_books
        );
    }

    private static Component page(BookViewScreen.BookAccess access, int pageIndex) {
        if (access == null || pageIndex < 0 || pageIndex >= access.getPageCount()) {
            return Component.empty();
        }
        Component page = access.getPage(pageIndex);
        return page == null ? Component.empty() : page;
    }
}
