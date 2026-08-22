package com.alexeys.translate_allinone.utils.textmatcher;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

/**
 * Minimal 1.21.10-compatible TextMatcher-style flat text node.
 */
public record FlatNode(ComponentContents content, Style style) {
    public MutableComponent toText() {
        return MutableComponent.create(content).setStyle(style);
    }

    public String extractString() {
        return extractString(content);
    }

    public static String extractString(ComponentContents content) {
        if (content instanceof PlainTextContents plainTextContent) {
            return plainTextContent.text();
        }
        return content == null ? "" : content.toString();
    }

    public static List<FlatNode> flatten(Component text) {
        List<FlatNode> result = new ArrayList<>();
        visit(text, Style.EMPTY, result);
        return result;
    }

    private static void visit(Component text, Style parentStyle, List<FlatNode> result) {
        if (text == null) {
            return;
        }

        Style resolvedStyle = text.getStyle().applyTo(parentStyle);
        ComponentContents content = text.getContents();
        if (content != PlainTextContents.EMPTY) {
            result.add(new FlatNode(content, resolvedStyle));
        }

        for (Component sibling : text.getSiblings()) {
            visit(sibling, resolvedStyle, result);
        }
    }

    public static List<FlatNode> compact(List<FlatNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        List<FlatNode> result = new ArrayList<>();
        StringBuilder accumulator = null;
        Style currentStyle = Style.EMPTY;

        for (FlatNode node : nodes) {
            ComponentContents content = node.content();
            if (content instanceof PlainTextContents plainTextContent
                    && accumulator != null
                    && node.style().equals(currentStyle)) {
                accumulator.append(plainTextContent.text());
                continue;
            }

            if (accumulator != null) {
                result.add(new FlatNode(PlainTextContents.create(accumulator.toString()), currentStyle));
                accumulator = null;
            }

            if (content instanceof PlainTextContents plainTextContent) {
                accumulator = new StringBuilder(plainTextContent.text());
                currentStyle = node.style();
            } else {
                result.add(node);
            }
        }

        if (accumulator != null) {
            result.add(new FlatNode(PlainTextContents.create(accumulator.toString()), currentStyle));
        }

        return result;
    }
}
