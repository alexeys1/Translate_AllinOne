package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.AnimationManager;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Optional;

public final class WynnDialogueInlinePendingAnimation {
    private static final String INSERTION = "translate_allinone:wynn_dialogue_inline_pending_animation";
    private static final String ANIMATION_KEY = "wynn_dialogue:inline_pending";

    private WynnDialogueInlinePendingAnimation() {
    }

    static Style mark(Style style) {
        return (style == null ? Style.EMPTY : style).withInsertion(INSERTION);
    }

    static boolean isMarked(Style style) {
        return style != null && INSERTION.equals(style.getInsertion());
    }

    public static Text animate(Text text) {
        if (text == null) {
            return Text.empty();
        }
        MutableText animated = Text.empty();
        boolean[] changed = {false};
        text.visit((style, part) -> {
            if (part == null || part.isEmpty()) {
                return Optional.empty();
            }
            if (!isMarked(style)) {
                animated.append(Text.literal(part).setStyle(style));
                return Optional.empty();
            }
            changed[0] = true;
            Style sourceStyle = style.withInsertion(null);
            animated.append(AnimationManager.getAnimatedStyledText(
                    Text.literal(part).setStyle(sourceStyle),
                    ANIMATION_KEY,
                    false
            ));
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? animated : text;
    }
}
