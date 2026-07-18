package com.cedarxuesong.translate_allinone.mixin.mixinAdvancement;

import com.cedarxuesong.translate_allinone.utils.translate.VanillaAdvancementTranslationSupport;
import java.util.List;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.advancement.AdvancementWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementWidget.class)
public class AdvancementWidgetMixin {
    @Shadow
    @Final
    private PlacedAdvancement advancement;

    @Shadow
    @Final
    private AdvancementDisplay display;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    @Mutable
    private List<OrderedText> title;

    @Shadow
    @Final
    @Mutable
    private int width;

    @Shadow
    @Final
    @Mutable
    private List<OrderedText> description;

    @Inject(
            method = "drawTooltip(Lnet/minecraft/client/gui/DrawContext;IIFII)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$refreshTextLayoutBeforeHover(
            DrawContext context,
            int originX,
            int originY,
            float alpha,
            int x,
            int y,
            CallbackInfo ci
    ) {
        translate_allinone$refreshHoverTextLayout();
    }

    @Unique
    private void translate_allinone$refreshHoverTextLayout() {
        if (display == null || client == null || client.textRenderer == null) {
            return;
        }

        AdvancementEntry holder = advancement == null ? null : advancement.getAdvancementEntry();
        Text translatedTitle = VanillaAdvancementTranslationSupport.translateHoveredTitle(holder, display.getTitle());
        Text translatedDescription = VanillaAdvancementTranslationSupport.translateHoveredDescription(
                holder,
                display,
                display.getDescription()
        );

        List<OrderedText> refreshedTitleLines = client.textRenderer.wrapLines(translatedTitle, 163);
        int titleWidth = refreshedTitleLines.stream()
                .mapToInt(client.textRenderer::getWidth)
                .max()
                .orElse(0);
        int contentWidth = 29 + Math.max(80, titleWidth) + getProgressWidth();
        List<StringVisitable> optimalDescriptionLines = wrapDescription(translatedDescription, contentWidth);
        List<OrderedText> refreshedDescription = optimalDescriptionLines == null
                ? List.of()
                : Language.getInstance().reorder(optimalDescriptionLines);

        for (OrderedText line : refreshedDescription) {
            contentWidth = Math.max(contentWidth, client.textRenderer.getWidth(line));
        }

        title = refreshedTitleLines;
        description = refreshedDescription;
        width = contentWidth + 3 + 5;
    }

    @Shadow
    private int getProgressWidth() {
        throw new AssertionError();
    }

    @Shadow
    private List<StringVisitable> wrapDescription(Text component, int width) {
        throw new AssertionError();
    }
}
