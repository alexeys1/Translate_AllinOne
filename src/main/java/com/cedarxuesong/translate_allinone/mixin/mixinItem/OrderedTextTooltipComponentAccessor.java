package com.cedarxuesong.translate_allinone.mixin.mixinItem;

import net.minecraft.client.gui.tooltip.OrderedTextTooltipComponent;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OrderedTextTooltipComponent.class)
public interface OrderedTextTooltipComponentAccessor {
    @Accessor("text")
    OrderedText getText();

    @Accessor("text")
    @Mutable
    void setText(OrderedText text);
}
