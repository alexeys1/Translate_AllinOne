package com.cedarxuesong.translate_allinone.mixin.mixinItem;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.cache.CacheStats;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipDecorativeContextSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipInternalLineSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRecentRenderGuardSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRefreshNoticeSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTextDebugCopySupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTextMatcherSupport;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationContext;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationSupport;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.OrderedTextTooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;

@Mixin(DrawContext.class)
public abstract class DrawContextTooltipMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/DrawContextTooltipMixin");

    @Unique
    private static final String ITEM_STATUS_ANIMATION_KEY = "item-tooltip-status-draw-context";

    @Unique
    private static final String DRAW_CONTEXT_DEV_SOURCE = "draw-context";

    @Unique
    private static final String CHAT_HOVER_DEV_SOURCE = "chat-hover";

    @Unique
    private static final ThreadLocal<Boolean> translate_allinone$isProcessing = ThreadLocal.withInitial(() -> false);

    @Unique
    private static int translate_allinone$lastTooltipHash = 0;

    @Unique
    private static ParsedTooltip translate_allinone$lastParsedTooltip = null;

    @Unique
    private static final AtomicLong translate_allinone$parsedTooltipCacheHits = new AtomicLong();

    @Unique
    private static final AtomicLong translate_allinone$parsedTooltipCacheMisses = new AtomicLong();

    @Unique
    private static final String REI_PACKAGE_PREFIX = "me.shedaniel.rei.";

    @Unique
    private record OrderedTooltipLine(OrderedTextTooltipComponentAccessor accessor, Text text) {
    }

    @Unique
    private record ParsedTooltip(List<OrderedTooltipLine> orderedLines, int hash) {
    }

    @Inject(
            method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;)V",
            at = @At("HEAD")
    )
    private void translate_allinone$translateTooltipComponents(
            TextRenderer textRenderer,
            List<TooltipComponent> components,
            int x,
            int y,
            TooltipPositioner positioner,
            CallbackInfo ci
    ) {
        if (translate_allinone$isProcessing.get()) {
            return;
        }

        boolean isWynntilsItemStatTooltip = TooltipTranslationContext.isInWynntilsItemStatTooltipRender();
        boolean isChatHoverTooltip = TooltipTranslationContext.isInChatHoverTooltipRender();
        String devSource = isChatHoverTooltip ? CHAT_HOVER_DEV_SOURCE : DRAW_CONTEXT_DEV_SOURCE;

        if (components == null || components.isEmpty()) {
            return;
        }

        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        if (!translate_allinone$isSupportedExternalTooltip(positioner, isWynntilsItemStatTooltip, isChatHoverTooltip)) {
            return;
        }

        int componentHash = translate_allinone$quickComponentHash(components);
        ParsedTooltip parsedTooltip;
        boolean parsedTooltipCacheHit;
        if (componentHash == translate_allinone$lastTooltipHash && translate_allinone$lastParsedTooltip != null) {
            translate_allinone$parsedTooltipCacheHits.incrementAndGet();
            parsedTooltipCacheHit = true;
            parsedTooltip = translate_allinone$lastParsedTooltip;
        } else {
            translate_allinone$parsedTooltipCacheMisses.incrementAndGet();
            parsedTooltipCacheHit = false;
            parsedTooltip = translate_allinone$parseTooltip(components);
            translate_allinone$lastTooltipHash = componentHash;
            translate_allinone$lastParsedTooltip = parsedTooltip;
        }

        List<Text> tooltipLines = new ArrayList<>(parsedTooltip.orderedLines().size());
        for (OrderedTooltipLine orderedLine : parsedTooltip.orderedLines()) {
            tooltipLines.add(orderedLine.text());
        }
        TooltipTextDebugCopySupport.maybeCopyCurrentTooltip(tooltipLines);
        if (config == null || !config.enabled) {
            return;
        }
        if (TooltipTranslationContext.consumeSkipDrawContextTranslation(tooltipLines)) {
            TooltipTextMatcherSupport.logTooltipGuardIfDev(
                    config,
                    devSource,
                    "skip-consume-shared-guard",
                    tooltipLines,
                    "TooltipTranslationContext.consumeSkipDrawContextTranslation() returned true."
            );
            return;
        }
        TooltipRefreshNoticeSupport.maybeForceRefreshCurrentTooltip(tooltipLines, config);
        boolean showRefreshNotice = TooltipRefreshNoticeSupport.shouldShowRefreshNotice(tooltipLines, config);

        boolean isKeyPressed = KeybindingManager.isPressed(config.keybinding.binding);
        if (TooltipTranslationSupport.shouldShowOriginal(config.keybinding.mode, isKeyPressed)) {
            return;
        }

        if (!TooltipRecentRenderGuardSupport.shouldSkipDuplicateRender(tooltipLines, showRefreshNotice)) {
            TooltipRefreshNoticeSupport.queueRemoteTranslationForCurrentTooltip(tooltipLines, config, devSource);
        }

        boolean emitDevLog = TooltipTextMatcherSupport.beginTooltipDevPass(config, devSource, tooltipLines);
        long tooltipStartedAtNanos = emitDevLog ? System.nanoTime() : 0L;

        boolean locallyStable = false;
        try {
            translate_allinone$isProcessing.set(true);
            locallyStable = translate_allinone$translateComponentsInPlace(
                    parsedTooltip.orderedLines(),
                    components,
                    config,
                    showRefreshNotice,
                    parsedTooltipCacheHit,
                    devSource,
                    emitDevLog,
                    tooltipStartedAtNanos
            );
        } catch (Exception e) {
            LOGGER.error("Failed to translate DrawContext tooltip components", e);
        } finally {
            translate_allinone$isProcessing.set(false);
        }
        if (locallyStable) {
            TooltipRecentRenderGuardSupport.rememberTooltipIfStable(tooltipLines, true);
        }
    }

    @Unique
    private int translate_allinone$quickComponentHash(List<TooltipComponent> components) {
        int hash = 1;
        for (TooltipComponent component : components) {
            hash = 31 * hash + System.identityHashCode(component);
            if (component instanceof OrderedTextTooltipComponent orderedTextComponent) {
                OrderedTextTooltipComponentAccessor accessor = (OrderedTextTooltipComponentAccessor) orderedTextComponent;
                hash = 31 * hash + System.identityHashCode(accessor.getText());
            } else {
                hash = 31 * hash + component.hashCode();
            }
        }
        return hash;
    }

    @Unique
    private ParsedTooltip translate_allinone$parseTooltip(List<TooltipComponent> components) {
        List<OrderedTooltipLine> orderedLines = new ArrayList<>();
        int hash = 1;
        for (TooltipComponent component : components) {
            if (component instanceof OrderedTextTooltipComponent orderedTextComponent) {
                OrderedTextTooltipComponentAccessor accessor = (OrderedTextTooltipComponentAccessor) orderedTextComponent;
                Text line = translate_allinone$orderedTextToText(accessor.getText());
                orderedLines.add(new OrderedTooltipLine(accessor, line));
                hash = 31 * hash + line.getString().hashCode();
            } else {
                hash = 31 * hash + component.hashCode();
            }
        }
        return new ParsedTooltip(orderedLines, hash);
    }

    @Unique
    private boolean translate_allinone$translateComponentsInPlace(
            List<OrderedTooltipLine> orderedLines,
            List<TooltipComponent> components,
            ItemTranslateConfig config,
            boolean showRefreshNotice,
            boolean parsedTooltipCacheHit,
            String devSource,
            boolean emitDevLog,
            long tooltipStartedAtNanos
    ) {
        List<Text> sourceLines = new ArrayList<>(orderedLines.size());
        for (OrderedTooltipLine orderedLine : orderedLines) {
            sourceLines.add(orderedLine.text());
        }

        boolean decorativeTooltipContext = TooltipDecorativeContextSupport.isDecorativeTooltipContext(sourceLines);
        TooltipTranslationSupport.TooltipProcessingResult processedTooltip = TooltipTranslationSupport.processTooltipLines(
                sourceLines,
                config,
                decorativeTooltipContext,
                emitDevLog,
                devSource
        );

        if (processedTooltip.translatedLines().size() == orderedLines.size()) {
            for (int lineIndex = 0; lineIndex < orderedLines.size(); lineIndex++) {
                OrderedTextTooltipComponentAccessor accessor = orderedLines.get(lineIndex).accessor();
                Text translatedLine = processedTooltip.translatedLines().get(lineIndex);
                if (translatedLine != null) {
                    accessor.setText(translatedLine.asOrderedText());
                }
            }
        } else if (orderedLines.size() == components.size()) {
            components.clear();
            for (Text translatedLine : processedTooltip.translatedLines()) {
                if (translatedLine != null) {
                    components.add(TooltipComponent.of(translatedLine.asOrderedText()));
                }
            }
        } else {
            for (int lineIndex = 0; lineIndex < orderedLines.size() && lineIndex < processedTooltip.translatedLines().size(); lineIndex++) {
                OrderedTextTooltipComponentAccessor accessor = orderedLines.get(lineIndex).accessor();
                Text translatedLine = processedTooltip.translatedLines().get(lineIndex);
                if (translatedLine != null) {
                    accessor.setText(translatedLine.asOrderedText());
                }
            }
        }

        if (processedTooltip.translatableLines() > 0) {
            CacheStats stats = TooltipInternalLineSupport.getItemCacheStats();
            if (TooltipInternalLineSupport.shouldShowStatusLine(processedTooltip, stats)) {
                Text statusLine = TooltipInternalLineSupport.createStatusLine(
                        stats,
                        processedTooltip.missingKeyIssue(),
                        ITEM_STATUS_ANIMATION_KEY
                );
                components.add(TooltipComponent.of(statusLine.asOrderedText()));
            }
            if (TooltipInternalLineSupport.shouldShowErrorStatusLine(processedTooltip)) {
                Text errorStatusLine = TooltipInternalLineSupport.createErrorStatusLine(processedTooltip.errorMessage());
                components.add(TooltipComponent.of(errorStatusLine.asOrderedText()));
            }
        }

        if (showRefreshNotice && !TooltipRefreshNoticeSupport.containsRefreshNoticeLine(sourceLines)) {
            components.add(TooltipComponent.of(TooltipRefreshNoticeSupport.createRefreshNoticeLine().asOrderedText()));
        }

        TooltipTextMatcherSupport.logTooltipPassIfDev(
                config,
                emitDevLog,
                devSource,
                orderedLines.size(),
                processedTooltip.translatableLines(),
                tooltipStartedAtNanos
        );
        TooltipTextMatcherSupport.logTooltipCacheStatsIfDev(
                config,
                emitDevLog,
                devSource,
                "parsedTooltipLast=" + (parsedTooltipCacheHit ? "hit" : "miss")
                        + " parsedTooltipHits=" + translate_allinone$parsedTooltipCacheHits.get()
                        + " parsedTooltipMisses=" + translate_allinone$parsedTooltipCacheMisses.get()
        );
        return !processedTooltip.pending() && !processedTooltip.missingKeyIssue();
    }

    @Unique
    private Text translate_allinone$orderedTextToText(OrderedText orderedText) {
        MutableText result = Text.empty();
        StringBuilder currentSegment = new StringBuilder();
        Style[] currentStyle = {Style.EMPTY};

        orderedText.accept((index, style, codePoint) -> {
            if (!style.equals(currentStyle[0]) && currentSegment.length() > 0) {
                result.append(Text.literal(currentSegment.toString()).setStyle(currentStyle[0]));
                currentSegment.setLength(0);
            }
            currentStyle[0] = style;
            currentSegment.appendCodePoint(codePoint);
            return true;
        });

        if (currentSegment.length() > 0) {
            result.append(Text.literal(currentSegment.toString()).setStyle(currentStyle[0]));
        }

        return result;
    }

    @Unique
    private boolean translate_allinone$isSupportedExternalTooltip(
            TooltipPositioner positioner,
            boolean isWynntilsItemStatTooltip,
            boolean isChatHoverTooltip
    ) {
        if (isChatHoverTooltip) {
            return true;
        }

        if (isWynntilsItemStatTooltip || TooltipTranslationContext.isInWynntilsQuestTooltipRender()) {
            return true;
        }

        if (TooltipTranslationContext.isInReiTooltipRender()) {
            return true;
        }

        return translate_allinone$isReiClass(positioner);
    }

    @Inject(
            method = "drawHoverEvent(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Style;II)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$pushChatHoverTooltipContext(
            TextRenderer textRenderer,
            Style style,
            int x,
            int y,
            CallbackInfo ci
    ) {
        if (translate_allinone$isChatScreenHoverStyle(style)) {
            TooltipTranslationContext.pushChatHoverTooltipRender();
        }
    }

    @Inject(
            method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void translate_allinone$popChatHoverTooltipContext(
            CallbackInfo ci
    ) {
        if (TooltipTranslationContext.isInChatHoverTooltipRender()) {
            TooltipTranslationContext.popChatHoverTooltipRender();
        }
    }

    @Unique
    private boolean translate_allinone$isChatScreenHoverStyle(Style style) {
        if (style == null || style.getHoverEvent() == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.currentScreen instanceof ChatScreen;
    }

    @Unique
    private boolean translate_allinone$isReiClass(Object instance) {
        return instance != null && translate_allinone$isReiClassName(instance.getClass().getName());
    }

    @Unique
    private boolean translate_allinone$isReiClassName(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        return className.startsWith(REI_PACKAGE_PREFIX);
    }
}
