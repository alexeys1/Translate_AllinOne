package com.cedarxuesong.translate_allinone.mixin.mixinBook;

import com.cedarxuesong.translate_allinone.utils.translate.BookPageTranslationSnapshot;
import com.cedarxuesong.translate_allinone.utils.translate.BookTranslationSupport;
import java.util.Objects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BookScreen.class)
public class BookScreenMixin {
    @Shadow
    private BookScreen.Contents contents;

    @Shadow
    private int pageIndex;

    @Shadow
    private int cachedPageIndex;

    @Unique
    private BookPageTranslationSnapshot translate_allinone$pageSnapshot;

    @Unique
    private Text translate_allinone$lastDisplayedPage;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"))
    private void translate_allinone$resolveBookPage(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        translate_allinone$updateSnapshot(contents, pageIndex);
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/ingame/BookScreen$Contents;getPage(I)Lnet/minecraft/text/StringVisitable;"
            )
    )
    private StringVisitable translate_allinone$renderSnapshotPage(BookScreen.Contents contents, int pageIndex) {
        return translate_allinone$pageFor(contents, pageIndex);
    }

    @Unique
    private StringVisitable translate_allinone$pageFor(BookScreen.Contents contents, int pageIndex) {
        translate_allinone$updateSnapshot(contents, pageIndex);
        BookPageTranslationSnapshot snapshot = translate_allinone$pageSnapshot;
        return snapshot != null && snapshot.pageIndex() == pageIndex
                ? snapshot.displayedPage()
                : contents.getPage(pageIndex);
    }

    @Unique
    private void translate_allinone$updateSnapshot(BookScreen.Contents contents, int pageIndex) {
        BookPageTranslationSnapshot snapshot = BookTranslationSupport.resolveCurrentPage(contents, pageIndex);
        if (!Objects.equals(translate_allinone$lastDisplayedPage, snapshot.displayedPage())) {
            cachedPageIndex = -1;
            translate_allinone$lastDisplayedPage = snapshot.displayedPage();
        }
        translate_allinone$pageSnapshot = snapshot;
    }
}
