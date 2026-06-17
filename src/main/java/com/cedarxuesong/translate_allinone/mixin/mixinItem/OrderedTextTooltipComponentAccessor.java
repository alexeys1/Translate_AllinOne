package com.cedarxuesong.translate_allinone.mixin.mixinItem;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientTextTooltip.class)
public interface OrderedTextTooltipComponentAccessor {
    @Accessor("text")
    FormattedCharSequence getText();

    @Accessor("text")
    @Mutable
    void setText(FormattedCharSequence text);
}
