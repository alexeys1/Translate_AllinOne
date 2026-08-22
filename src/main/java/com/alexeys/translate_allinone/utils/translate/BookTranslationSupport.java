package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

public final class BookTranslationSupport {
    private static final String POLICY_VERSION = "book-page-v1";
    private static final String CACHE_CONTEXT = "book:page";

    private BookTranslationSupport() {
    }

    public static BookPageTranslationSnapshot resolveCurrentPage(BookScreen.Contents contents, int pageIndex) {
        Text original = page(contents, pageIndex);
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

        ComponentRenderTranslationSupport.TranslationResult result = resolvePage(original, config, true);
        if (config.book_prefetch_adjacent_pages) {
            prefetch(contents, pageIndex - 1, config);
            prefetch(contents, pageIndex + 1, config);
        }
        Text displayed = ComponentRenderTranslationSupport.displayWithPendingAnimation(
                result,
                "book:page:" + pageIndex
        );
        return new BookPageTranslationSnapshot(pageIndex, original, displayed, result.state());
    }

    public static void resetSession() {
        ComponentRenderTranslationSupport.resetRefreshState();
    }

    private static void prefetch(BookScreen.Contents contents, int pageIndex, OtherTranslationsConfig config) {
        resolvePage(page(contents, pageIndex), config, false);
    }

    private static ComponentRenderTranslationSupport.TranslationResult resolvePage(
            Text original,
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

    private static Text page(BookScreen.Contents contents, int pageIndex) {
        if (contents == null || pageIndex < 0 || pageIndex >= contents.getPageCount()) {
            return Text.empty();
        }
        StringVisitable page = contents.getPage(pageIndex);
        if (page == null) {
            return Text.empty();
        }
        return page instanceof Text text ? text : Text.literal(page.getString());
    }
}
