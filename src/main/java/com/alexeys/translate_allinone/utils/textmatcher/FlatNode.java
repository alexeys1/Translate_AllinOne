package com.alexeys.translate_allinone.utils.textmatcher;

import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal 1.21.10-compatible TextMatcher-style flat text node.
 */
public record FlatNode(TextContent content, Style style) {
    public MutableText toText() {
        return MutableText.of(content).setStyle(style);
    }

    public String extractString() {
        return extractString(content);
    }

    public static String extractString(TextContent content) {
        if (content instanceof PlainTextContent plainTextContent) {
            return plainTextContent.string();
        }
        return content == null ? "" : content.toString();
    }

    public static List<FlatNode> flatten(Text text) {
        List<FlatNode> result = new ArrayList<>();
        visit(text, Style.EMPTY, result);
        return result;
    }

    private static void visit(Text text, Style parentStyle, List<FlatNode> result) {
        if (text == null) {
            return;
        }

        Style resolvedStyle = text.getStyle().withParent(parentStyle);
        TextContent content = text.getContent();
        if (content != PlainTextContent.EMPTY) {
            result.add(new FlatNode(content, resolvedStyle));
        }

        for (Text sibling : text.getSiblings()) {
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
            TextContent content = node.content();
            if (content instanceof PlainTextContent plainTextContent
                    && accumulator != null
                    && node.style().equals(currentStyle)) {
                accumulator.append(plainTextContent.string());
                continue;
            }

            if (accumulator != null) {
                result.add(new FlatNode(PlainTextContent.of(accumulator.toString()), currentStyle));
                accumulator = null;
            }

            if (content instanceof PlainTextContent plainTextContent) {
                accumulator = new StringBuilder(plainTextContent.string());
                currentStyle = node.style();
            } else {
                result.add(node);
            }
        }

        if (accumulator != null) {
            result.add(new FlatNode(PlainTextContent.of(accumulator.toString()), currentStyle));
        }

        return result;
    }
}
