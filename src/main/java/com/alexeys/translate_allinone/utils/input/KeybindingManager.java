package com.alexeys.translate_allinone.utils.input;

import com.alexeys.translate_allinone.utils.config.pojos.InputBindingConfig;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.system.MemoryStack;

public final class KeybindingManager {
    private static final int MOUSE_BUTTON_FIRST = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int MOUSE_BUTTON_LAST = InputConstants.MOUSE_BUTTON_8;

    private KeybindingManager() {
    }

    public static boolean isBound(InputBindingConfig binding) {
        return binding != null && binding.isBound();
    }

    public static boolean isEscape(KeyEvent keyInput) {
        return extractKeyCode(keyInput) == InputConstants.KEY_ESCAPE;
    }

    public static boolean matchesKeyInput(InputBindingConfig binding, KeyEvent keyInput) {
        if (!isBound(binding) || binding.type != InputBindingConfig.InputType.KEYSYM || keyInput == null) {
            return false;
        }
        InputConstants.Key bound = resolveKey(binding);
        return bound != null && bound.equals(InputConstants.getKey(keyInput));
    }

    public static boolean isPressed(InputBindingConfig binding) {
        if (!isBound(binding)) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }

        InputConstants.Key key = resolveKey(binding);
        if (key == null) {
            return false;
        }

        try {
            if (key.getType() == InputConstants.Type.MOUSE) {
                return isMouseButtonDown(key.getValue());
            }
            return InputConstants.isKeyDown(key.getValue());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isMouseButtonDown(int button) {
        if (button < MOUSE_BUTTON_FIRST || button > MOUSE_BUTTON_LAST) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer x = stack.callocFloat(1);
            FloatBuffer y = stack.callocFloat(1);
            return (SDLMouse.SDL_GetMouseState(x, y) & 1 << (button - 1)) != 0;
        }
    }

    public static InputBindingConfig captureKeyboardBinding(KeyEvent keyInput) {
        if (keyInput == null) {
            return null;
        }
        InputConstants.Key key = InputConstants.getKey(keyInput);
        if (key == null || key == InputConstants.UNKNOWN) {
            return null;
        }
        InputBindingConfig binding = new InputBindingConfig();
        binding.type = InputBindingConfig.InputType.KEYSYM;
        binding.keyName = key.getName();
        binding.code = key.getValue();
        return binding;
    }

    public static InputBindingConfig captureMouseBinding(int mouseButton) {
        if (mouseButton < MOUSE_BUTTON_FIRST || mouseButton > MOUSE_BUTTON_LAST) {
            return null;
        }
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(mouseButton);
        InputBindingConfig binding = new InputBindingConfig();
        binding.type = InputBindingConfig.InputType.MOUSE;
        binding.keyName = key.getName();
        binding.code = key.getValue();
        return binding;
    }

    public static void apply(InputBindingConfig target, InputBindingConfig source) {
        if (target == null || source == null) {
            return;
        }
        target.type = source.type;
        target.code = source.code;
        target.keyName = source.keyName;
    }

    public static void clear(InputBindingConfig target) {
        if (target == null) {
            return;
        }
        target.type = InputBindingConfig.InputType.KEYSYM;
        target.code = -1;
        target.keyName = null;
    }

    public static String displayName(InputBindingConfig binding) {
        if (!isBound(binding)) {
            return "";
        }

        try {
            InputConstants.Key key = resolveKey(binding);
            if (key != null) {
                return key.getDisplayName().getString();
            }
        } catch (Exception ignored) {
        }
        return fallbackDisplayName(binding);
    }

    static InputConstants.Key resolveKey(InputBindingConfig binding) {
        if (binding == null || binding.keyName == null || binding.keyName.isBlank()) {
            return null;
        }
        InputConstants.Key named = InputConstants.getKey(binding.keyName);
        return named == InputConstants.UNKNOWN ? null : named;
    }

    private static String fallbackDisplayName(InputBindingConfig binding) {
        if (binding.type == InputBindingConfig.InputType.MOUSE) {
            return "Mouse " + (binding.code + 1);
        }
        return "Key " + binding.code;
    }

    private static int extractKeyCode(KeyEvent keyInput) {
        if (keyInput == null) {
            return -1;
        }
        int code = keyInput.key();
        return code >= 0 ? code : -1;
    }
}
