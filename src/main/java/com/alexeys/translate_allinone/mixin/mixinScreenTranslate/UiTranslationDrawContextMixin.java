package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Mixin(DrawContext.class)
public abstract class UiTranslationDrawContextMixin {
    @Unique
    private static final ThreadLocal<Deque<UiTranslationScope.Scope>> translate_allinone$tooltipPaintScopes =
            ThreadLocal.withInitial(() -> new ArrayDeque<UiTranslationScope.Scope>());

    @Inject(
            method = "drawTooltipImmediately(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;Lnet/minecraft/util/Identifier;)V",
            at = @At("HEAD")
    )
    private void translate_allinone$enterTooltipPaintScope(
            TextRenderer textRenderer,
            List<TooltipComponent> components,
            int x,
            int y,
            TooltipPositioner positioner,
            Identifier texture,
            CallbackInfo callbackInfo
    ) {
        translate_allinone$tooltipPaintScopes.get().push(UiTranslationScope.enterInternal());
    }

    @Inject(
            method = "drawTooltipImmediately(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;Lnet/minecraft/util/Identifier;)V",
            at = @At("RETURN")
    )
    private void translate_allinone$leaveTooltipPaintScope(
            TextRenderer textRenderer,
            List<TooltipComponent> components,
            int x,
            int y,
            TooltipPositioner positioner,
            Identifier texture,
            CallbackInfo callbackInfo
    ) {
        Deque<UiTranslationScope.Scope> scopes = translate_allinone$tooltipPaintScopes.get();
        UiTranslationScope.Scope scope = scopes.poll();
        if (scope != null) {
            scope.close();
        }
        if (scopes.isEmpty()) {
            translate_allinone$tooltipPaintScopes.remove();
        }
    }

    @ModifyVariable(
            method = {
                    "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V",
                    "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V",
                    "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private OrderedText translate_allinone$translateFormattedSequence(OrderedText source) {
        return UiTranslationRuntime.translateFormattedCharSequence(source, UiTextRole.OPTION);
    }

    @ModifyVariable(
            method = {
                    "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",
                    "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V",
                    "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private Text translate_allinone$translateComponent(Text source) {
        Text visible = UiTranslationRuntime.translateComponent(source, UiTextRole.OPTION);
        if (visible != source) {
            UiTranslationRuntime.markFormattedSequenceHandled(visible.asOrderedText());
        }
        return visible;
    }

    @ModifyVariable(
            method = {
                    "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V",
                    "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V",
                    "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private String translate_allinone$translateString(String source) {
        return UiTranslationRuntime.translateStringAnimated(source, UiTextRole.OPTION);
    }

    @ModifyVariable(
            method = {
                    "drawWrappedText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/StringVisitable;IIIIZ)V",
                    "drawWrappedTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/StringVisitable;IIII)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private StringVisitable translate_allinone$translateWrapped(StringVisitable source) {
        return UiTranslationRuntime.translateFormattedText(source, UiTextRole.DESCRIPTION);
    }

    @ModifyVariable(
            method = {
                    "drawOrderedTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;II)V",
                    "drawOrderedTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/util/Identifier;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private List<OrderedText> translate_allinone$translateTooltipSequences(List<OrderedText> source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        List<OrderedText> translated = new java.util.ArrayList<>(source.size());
        for (OrderedText sequence : source) {
            translated.add(UiTranslationRuntime.translateFormattedCharSequence(sequence, UiTextRole.TOOLTIP));
        }
        return java.util.Collections.unmodifiableList(translated);
    }
}
