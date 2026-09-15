package com.alexeys.translate_allinone.utils.input;

import com.mojang.blaze3d.platform.InputConstants;

public final class LegacyKeybindingNumberingSupport {
    private static final int[] LEGACY_KEYBOARD_CODES = {
            32, 39, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53,
            54, 55, 56, 57, 59, 61, 65, 66, 67, 68, 69, 70,
            71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82,
            83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 96,
            161, 162, 256, 257, 258, 259, 260, 261, 262, 263, 264, 265,
            266, 267, 268, 269, 280, 281, 282, 283, 284, 290, 291, 292,
            293, 294, 295, 296, 297, 298, 299, 300, 301, 302, 303, 304,
            305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 320, 321,
            322, 323, 324, 325, 326, 327, 328, 329, 330, 331, 332, 333,
            334, 335, 336, 340, 341, 342, 343, 344, 345, 346, 347, 348
    };

    private static final String[] LEGACY_KEYBOARD_NAMES = {
            "key.keyboard.space", "key.keyboard.apostrophe", "key.keyboard.comma",
            "key.keyboard.minus", "key.keyboard.period", "key.keyboard.slash",
            "key.keyboard.0", "key.keyboard.1", "key.keyboard.2",
            "key.keyboard.3", "key.keyboard.4", "key.keyboard.5",
            "key.keyboard.6", "key.keyboard.7", "key.keyboard.8",
            "key.keyboard.9", "key.keyboard.semicolon", "key.keyboard.equal",
            "key.keyboard.a", "key.keyboard.b", "key.keyboard.c",
            "key.keyboard.d", "key.keyboard.e", "key.keyboard.f",
            "key.keyboard.g", "key.keyboard.h", "key.keyboard.i",
            "key.keyboard.j", "key.keyboard.k", "key.keyboard.l",
            "key.keyboard.m", "key.keyboard.n", "key.keyboard.o",
            "key.keyboard.p", "key.keyboard.q", "key.keyboard.r",
            "key.keyboard.s", "key.keyboard.t", "key.keyboard.u",
            "key.keyboard.v", "key.keyboard.w", "key.keyboard.x",
            "key.keyboard.y", "key.keyboard.z", "key.keyboard.left.bracket",
            "key.keyboard.backslash", "key.keyboard.right.bracket", "key.keyboard.grave.accent",
            "key.keyboard.world.1", "key.keyboard.world.2", "key.keyboard.escape",
            "key.keyboard.enter", "key.keyboard.tab", "key.keyboard.backspace",
            "key.keyboard.insert", "key.keyboard.delete", "key.keyboard.right",
            "key.keyboard.left", "key.keyboard.down", "key.keyboard.up",
            "key.keyboard.page.up", "key.keyboard.page.down", "key.keyboard.home",
            "key.keyboard.end", "key.keyboard.caps.lock", "key.keyboard.scroll.lock",
            "key.keyboard.num.lock", "key.keyboard.print.screen", "key.keyboard.pause",
            "key.keyboard.f1", "key.keyboard.f2", "key.keyboard.f3",
            "key.keyboard.f4", "key.keyboard.f5", "key.keyboard.f6",
            "key.keyboard.f7", "key.keyboard.f8", "key.keyboard.f9",
            "key.keyboard.f10", "key.keyboard.f11", "key.keyboard.f12",
            "key.keyboard.f13", "key.keyboard.f14", "key.keyboard.f15",
            "key.keyboard.f16", "key.keyboard.f17", "key.keyboard.f18",
            "key.keyboard.f19", "key.keyboard.f20", "key.keyboard.f21",
            "key.keyboard.f22", "key.keyboard.f23", "key.keyboard.f24",
            "key.keyboard.f25", "key.keyboard.keypad.0", "key.keyboard.keypad.1",
            "key.keyboard.keypad.2", "key.keyboard.keypad.3", "key.keyboard.keypad.4",
            "key.keyboard.keypad.5", "key.keyboard.keypad.6", "key.keyboard.keypad.7",
            "key.keyboard.keypad.8", "key.keyboard.keypad.9", "key.keyboard.keypad.decimal",
            "key.keyboard.keypad.divide", "key.keyboard.keypad.multiply", "key.keyboard.keypad.subtract",
            "key.keyboard.keypad.add", "key.keyboard.keypad.enter", "key.keyboard.keypad.equal",
            "key.keyboard.left.shift", "key.keyboard.left.control", "key.keyboard.left.alt",
            "key.keyboard.left.win", "key.keyboard.right.shift", "key.keyboard.right.control",
            "key.keyboard.right.alt", "key.keyboard.right.win", "key.keyboard.menu"
    };

    private static final int[] LEGACY_MOUSE_CODES = {
            0, 1, 2, 3, 4, 5, 6, 7
    };

    private static final String[] LEGACY_MOUSE_NAMES = {
            "key.mouse.left", "key.mouse.right", "key.mouse.middle", "key.mouse.4",
            "key.mouse.5", "key.mouse.6", "key.mouse.7", "key.mouse.8"
    };

    private LegacyKeybindingNumberingSupport() {
    }

    public static InputConstants.Key resolveLegacyKeybinding(int code, boolean mouse) {
        String name = mouse ? legacyMouseName(code) : legacyKeyboardName(code);
        if (name == null) {
            return null;
        }
        InputConstants.Key key = InputConstants.getKey(name);
        return key == null || key == InputConstants.UNKNOWN ? null : key;
    }

    private static String legacyKeyboardName(int code) {
        return nameAt(LEGACY_KEYBOARD_CODES, LEGACY_KEYBOARD_NAMES, code);
    }

    private static String legacyMouseName(int code) {
        return nameAt(LEGACY_MOUSE_CODES, LEGACY_MOUSE_NAMES, code);
    }

    private static String nameAt(int[] codes, String[] names, int code) {
        for (int index = 0; index < codes.length; index++) {
            if (codes[index] == code) {
                return names[index];
            }
        }
        return null;
    }
}
