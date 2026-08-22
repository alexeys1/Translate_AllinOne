package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.AnimationManager;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

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

    public static Component animate(Component text) {
        if (text == null) {
            return Component.empty();
        }
        MutableComponent animated = Component.empty();
        boolean[] changed = {false};
        text.visit((style, part) -> {
            if (part == null || part.isEmpty()) {
                return Optional.empty();
            }
            if (!isMarked(style)) {
                animated.append(Component.literal(part).setStyle(style));
                return Optional.empty();
            }
            changed[0] = true;
            Style sourceStyle = style.withInsertion(null);
            animated.append(AnimationManager.getAnimatedStyledText(
                    Component.literal(part).setStyle(sourceStyle),
                    ANIMATION_KEY,
                    false
            ));
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? animated : text;
    }
}
