package com.cedarxuesong.translate_allinone.utils.translate;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;

public final class UiTranslationLazySplitList extends AbstractList<OrderedText> {
    private static final long serialVersionUID = 1L;

    private final TextRenderer font;
    private final StringVisitable source;
    private final int width;
    private final UiTextRole role;
    private List<OrderedText> cached;
    private int cachedFrame = -1;

    public UiTranslationLazySplitList(TextRenderer font, StringVisitable source, int width, UiTextRole role) {
        this.font = Objects.requireNonNull(font, "font");
        this.source = source;
        this.width = width;
        this.role = role;
    }

    @Override
    public OrderedText get(int index) {
        return fresh().get(index);
    }

    @Override
    public int size() {
        return fresh().size();
    }

    @Override
    public java.util.Iterator<OrderedText> iterator() {
        return fresh().iterator();
    }

    private List<OrderedText> fresh() {
        int frame = UiTranslationRuntime.currentFrameId();
        if (cached != null && cachedFrame == frame) {
            return cached;
        }

        StringVisitable visible = UiTranslationRuntime.translateFormattedTextInCurrentScreen(source, role);
        List<OrderedText> result = UiTranslationRuntime.withoutNestedTranslation(
                () -> font.wrapLines(visible, width)
        );
        result.forEach(UiTranslationRuntime::markFormattedSequenceHandled);
        cached = result;
        cachedFrame = frame;
        return result;
    }
}
