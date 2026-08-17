package com.cedarxuesong.translate_allinone.utils.translate;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;

public final class UiTranslationLazySplitList extends AbstractList<FormattedCharSequence> {
    private static final long serialVersionUID = 1L;

    private final Font font;
    private final FormattedText source;
    private final int width;
    private final UiTextRole role;
    private List<FormattedCharSequence> cached;
    private int cachedFrame = -1;

    public UiTranslationLazySplitList(Font font, FormattedText source, int width, UiTextRole role) {
        this.font = Objects.requireNonNull(font, "font");
        this.source = source;
        this.width = width;
        this.role = role;
    }

    @Override
    public FormattedCharSequence get(int index) {
        return fresh().get(index);
    }

    @Override
    public int size() {
        return fresh().size();
    }

    @Override
    public java.util.Iterator<FormattedCharSequence> iterator() {
        return fresh().iterator();
    }

    private List<FormattedCharSequence> fresh() {
        int frame = UiTranslationRuntime.currentFrameId();
        if (cached != null && cachedFrame == frame) {
            return cached;
        }

        FormattedText visible = UiTranslationRuntime.translateFormattedTextInCurrentScreen(source, role);
        List<FormattedCharSequence> result = UiTranslationRuntime.withoutNestedTranslation(
                () -> font.split(visible, width)
        );
        result.forEach(UiTranslationRuntime::markFormattedSequenceHandled);
        cached = result;
        cachedFrame = frame;
        return result;
    }
}
