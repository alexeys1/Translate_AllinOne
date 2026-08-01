package com.cedarxuesong.translate_allinone.mixin.mixinBook;

import com.cedarxuesong.translate_allinone.utils.translate.BookPageTranslationSnapshot;
import com.cedarxuesong.translate_allinone.utils.translate.BookTranslationSupport;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BookViewScreen.class)
public class BookViewScreenMixin {
    @Shadow
    private BookViewScreen.BookAccess bookAccess;

    @Shadow
    private int currentPage;

    @Shadow
    private int cachedPage;

    @Unique
    private BookPageTranslationSnapshot translate_allinone$pageSnapshot;

    @Unique
    private Component translate_allinone$lastDisplayedPage;

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void translate_allinone$resolveBookPage(
            GuiGraphicsExtractor extractor,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        translate_allinone$updateSnapshot(bookAccess, currentPage);
    }

    @Redirect(
            method = "visitText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/BookViewScreen$BookAccess;getPage(I)Lnet/minecraft/network/chat/Component;"
            ),
            require = 0
    )
    private Component translate_allinone$renderSnapshotPage(BookViewScreen.BookAccess access, int pageIndex) {
        return translate_allinone$pageFor(access, pageIndex);
    }

    @Redirect(
            method = "getNarrationMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/BookViewScreen$BookAccess;getPage(I)Lnet/minecraft/network/chat/Component;"
            ),
            require = 0
    )
    private Component translate_allinone$narrateSnapshotPage(BookViewScreen.BookAccess access, int pageIndex) {
        return translate_allinone$pageFor(access, pageIndex);
    }

    @Unique
    private Component translate_allinone$pageFor(BookViewScreen.BookAccess access, int pageIndex) {
        translate_allinone$updateSnapshot(access, pageIndex);
        BookPageTranslationSnapshot snapshot = translate_allinone$pageSnapshot;
        return snapshot != null && snapshot.pageIndex() == pageIndex
                ? snapshot.displayedPage()
                : access.getPage(pageIndex);
    }

    @Unique
    private void translate_allinone$updateSnapshot(BookViewScreen.BookAccess access, int pageIndex) {
        BookPageTranslationSnapshot snapshot = BookTranslationSupport.resolveCurrentPage(access, pageIndex);
        if (!Objects.equals(translate_allinone$lastDisplayedPage, snapshot.displayedPage())) {
            cachedPage = -1;
            translate_allinone$lastDisplayedPage = snapshot.displayedPage();
        }
        translate_allinone$pageSnapshot = snapshot;
    }
}
