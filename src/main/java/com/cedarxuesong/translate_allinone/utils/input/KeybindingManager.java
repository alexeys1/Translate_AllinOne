package com.cedarxuesong.translate_allinone.utils.input;

import com.cedarxuesong.translate_allinone.utils.config.pojos.InputBindingConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class KeybindingManager {
    private KeybindingManager() {
    }

    public static boolean isBound(InputBindingConfig binding) {
        return binding != null && binding.isBound();
    }

    public static boolean isEscape(KeyInput keyInput) {
        return extractKeyCode(keyInput) == GLFW.GLFW_KEY_ESCAPE;
    }

    public static boolean matchesKeyInput(InputBindingConfig binding, KeyInput keyInput) {
        if (!isBound(binding) || binding.type != InputBindingConfig.InputType.KEYSYM) {
            return false;
        }
        return extractKeyCode(keyInput) == binding.code;
    }

    public static boolean isPressed(InputBindingConfig binding) {
        if (!isBound(binding)) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }

        int code = binding.code;
        if (code < 0) {
            return false;
        }

        try {
            if (binding.type == InputBindingConfig.InputType.MOUSE) {
                return code <= GLFW.GLFW_MOUSE_BUTTON_LAST
                        && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), code) == GLFW.GLFW_PRESS;
            }
            return code <= GLFW.GLFW_KEY_LAST && InputUtil.isKeyPressed(client.getWindow(), code);
        } catch (Exception e) {
            return false;
        }
    }

    public static InputBindingConfig captureKeyboardBinding(KeyInput keyInput) {
        int code = extractKeyCode(keyInput);
        if (code < 0) {
            return null;
        }
        InputBindingConfig binding = new InputBindingConfig();
        binding.type = InputBindingConfig.InputType.KEYSYM;
        binding.code = code;
        return binding;
    }

    public static InputBindingConfig captureMouseBinding(int mouseButton) {
        if (mouseButton < 0 || mouseButton > GLFW.GLFW_MOUSE_BUTTON_LAST) {
            return null;
        }
        InputBindingConfig binding = new InputBindingConfig();
        binding.type = InputBindingConfig.InputType.MOUSE;
        binding.code = mouseButton;
        return binding;
    }

    public static void apply(InputBindingConfig target, InputBindingConfig source) {
        if (target == null || source == null) {
            return;
        }
        target.type = source.type;
        target.code = source.code;
    }

    public static void clear(InputBindingConfig target) {
        if (target == null) {
            return;
        }
        target.type = InputBindingConfig.InputType.KEYSYM;
        target.code = -1;
    }

    public static String displayName(InputBindingConfig binding) {
        if (!isBound(binding)) {
            return "";
        }

        try {
            if (binding.type == InputBindingConfig.InputType.MOUSE) {
                return InputUtil.Type.MOUSE.createFromCode(binding.code).getLocalizedText().getString();
            }
            return InputUtil.Type.KEYSYM.createFromCode(binding.code).getLocalizedText().getString();
        } catch (Exception e) {
            if (binding.type == InputBindingConfig.InputType.MOUSE) {
                return "Mouse " + (binding.code + 1);
            }
            return "Key " + binding.code;
        }
    }

    private static int extractKeyCode(KeyInput keyInput) {
        if (keyInput == null) {
            return -1;
        }
        int code = keyInput.key();
        return code >= 0 ? code : -1;
    }
}
