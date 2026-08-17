package com.cedarxuesong.translate_allinone.mixin.mixinChatHud;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;

@Mixin(ChatComponent.class)
public interface ChatHudAccessor {
    @Accessor("allMessages")
    List<GuiMessage> getMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> getTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int getScrolledLines();

    @Accessor("chatScrollbarPos")
    void setScrolledLines(int scrolledLines);

    @Invoker("refreshTrimmedMessages")
    void invokeRefresh();

    @Invoker("getLineHeight")
    int invokeGetLineHeight();
} 
