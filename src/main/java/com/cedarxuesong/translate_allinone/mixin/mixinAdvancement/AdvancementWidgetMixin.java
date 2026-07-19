package com.cedarxuesong.translate_allinone.mixin.mixinAdvancement;

import com.cedarxuesong.translate_allinone.utils.translate.VanillaAdvancementTranslationSupport;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
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
    private AdvancementNode advancementNode;

    @Shadow
    @Final
    private DisplayInfo display;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    @Mutable
    private List<FormattedCharSequence> titleLines;

    @Shadow
    @Final
    @Mutable
    private int width;

    @Shadow
    @Final
    @Mutable
    private List<FormattedCharSequence> description;

    @Inject(
            method = "extractHover(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIFII)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$refreshTextLayoutBeforeHover(
            GuiGraphicsExtractor context,
            int x,
            int y,
            float fade,
            int width,
            int height,
            CallbackInfo ci
    ) {
        translate_allinone$refreshHoverTextLayout();
    }

    @Unique
    private void translate_allinone$refreshHoverTextLayout() {
        if (display == null || minecraft == null || minecraft.font == null) {
            return;
        }

        AdvancementHolder holder = advancementNode == null ? null : advancementNode.holder();
        Component translatedTitle = VanillaAdvancementTranslationSupport.translateHoveredTitle(holder, display.getTitle());
        Component translatedDescription = VanillaAdvancementTranslationSupport.translateHoveredDescription(
                holder,
                display,
                display.getDescription()
        );

        List<FormattedCharSequence> refreshedTitleLines = minecraft.font.split(translatedTitle, 163);
        int titleWidth = refreshedTitleLines.stream()
                .mapToInt(minecraft.font::width)
                .max()
                .orElse(0);
        int contentWidth = 29 + Math.max(80, titleWidth) + getMaxProgressWidth();
        List<FormattedText> optimalDescriptionLines = findOptimalLines(translatedDescription, contentWidth);
        List<FormattedCharSequence> refreshedDescription = optimalDescriptionLines == null
                ? List.of()
                : Language.getInstance().getVisualOrder(optimalDescriptionLines);

        for (FormattedCharSequence line : refreshedDescription) {
            contentWidth = Math.max(contentWidth, minecraft.font.width(line));
        }

        titleLines = refreshedTitleLines;
        description = refreshedDescription;
        width = contentWidth + 3 + 5;
    }

    @Shadow
    private int getMaxProgressWidth() {
        throw new AssertionError();
    }

    @Shadow
    private List<FormattedText> findOptimalLines(Component component, int width) {
        throw new AssertionError();
    }
}
