package com.cedarxuesong.translate_allinone.utils.input;

import com.cedarxuesong.translate_allinone.utils.translate.ChatInputTranslateManager;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ChatInputInstructionFieldRegistry {
    private static final Map<TextFieldWidget, Boolean> FIELDS = Collections.synchronizedMap(new WeakHashMap<>());

    private ChatInputInstructionFieldRegistry() {
    }

    public static void register(TextFieldWidget field) {
        if (field != null) {
            FIELDS.put(field, Boolean.TRUE);
        }
    }

    public static boolean typeIntoFocusedField(char chr, int modifiers) {
        if (ChatInputTranslateManager.getPanelAvailability() != ChatInputTranslateManager.PanelAvailability.FULL) {
            return false;
        }

        List<TextFieldWidget> fields;
        synchronized (FIELDS) {
            fields = new ArrayList<>(FIELDS.keySet());
        }
        for (TextFieldWidget field : fields) {
            if (field != null && field.isFocused()) {
                field.charTyped(chr, modifiers);
                return true;
            }
        }
        return false;
    }
}
