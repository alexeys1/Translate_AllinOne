package com.cedarxuesong.translate_allinone.utils.textmatcher;

import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class NodePredicateBuilder {
    private final List<Predicate<FlatNode>> predicates = new ArrayList<>();

    public NodePredicateBuilder color(int rgb) {
        int normalizedRgb = rgb & 0xFFFFFF;
        predicates.add(node -> {
            TextColor color = resolveStyle(node).getColor();
            return color != null && (color.getRgb() & 0xFFFFFF) == normalizedRgb;
        });
        return this;
    }

    public NodePredicateBuilder color(TextColor color) {
        predicates.add(node -> Objects.equals(resolveStyle(node).getColor(), color));
        return this;
    }

    public NodePredicateBuilder font(String namespace, String path) {
        return font(Identifier.of(namespace, path));
    }

    public NodePredicateBuilder font(Identifier font) {
        predicates.add(node -> Objects.equals(resolveStyle(node).getFont(), font));
        return this;
    }

    public NodePredicateBuilder defaultFont() {
        predicates.add(node -> Objects.equals(resolveStyle(node).getFont(), Style.EMPTY.getFont()));
        return this;
    }

    public NodePredicateBuilder bold() {
        return bold(true);
    }

    public NodePredicateBuilder bold(boolean value) {
        predicates.add(node -> resolveStyle(node).isBold() == value);
        return this;
    }

    public NodePredicateBuilder italic() {
        return italic(true);
    }

    public NodePredicateBuilder italic(boolean value) {
        predicates.add(node -> resolveStyle(node).isItalic() == value);
        return this;
    }

    public NodePredicateBuilder underlined() {
        return underlined(true);
    }

    public NodePredicateBuilder underlined(boolean value) {
        predicates.add(node -> resolveStyle(node).isUnderlined() == value);
        return this;
    }

    public NodePredicateBuilder strikethrough() {
        return strikethrough(true);
    }

    public NodePredicateBuilder strikethrough(boolean value) {
        predicates.add(node -> resolveStyle(node).isStrikethrough() == value);
        return this;
    }

    public NodePredicateBuilder obfuscated() {
        return obfuscated(true);
    }

    public NodePredicateBuilder obfuscated(boolean value) {
        predicates.add(node -> resolveStyle(node).isObfuscated() == value);
        return this;
    }

    public NodePredicateBuilder style(Predicate<Style> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        predicates.add(node -> predicate.test(resolveStyle(node)));
        return this;
    }

    public NodePredicateBuilder node(Predicate<FlatNode> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        predicates.add(predicate);
        return this;
    }

    public NodePredicateBuilder not(Consumer<NodePredicateBuilder> block) {
        Objects.requireNonNull(block, "block");
        Predicate<FlatNode> inner = buildPredicate(block);
        predicates.add(node -> !inner.test(node));
        return this;
    }

    public NodePredicateBuilder anyOf(Consumer<AnyOfBuilder> block) {
        Objects.requireNonNull(block, "block");
        AnyOfBuilder builder = new AnyOfBuilder();
        block.accept(builder);
        List<Predicate<FlatNode>> branches = builder.build();
        if (branches.isEmpty()) {
            throw new IllegalArgumentException("anyOf must contain at least one branch");
        }
        predicates.add(node -> {
            for (Predicate<FlatNode> branch : branches) {
                if (branch.test(node)) {
                    return true;
                }
            }
            return false;
        });
        return this;
    }

    Predicate<FlatNode> compilePredicate() {
        if (predicates.isEmpty()) {
            return node -> true;
        }
        if (predicates.size() == 1) {
            return predicates.get(0);
        }

        List<Predicate<FlatNode>> snapshot = List.copyOf(predicates);
        return node -> {
            for (Predicate<FlatNode> predicate : snapshot) {
                if (!predicate.test(node)) {
                    return false;
                }
            }
            return true;
        };
    }

    public static Predicate<FlatNode> buildPredicate(Consumer<NodePredicateBuilder> block) {
        Objects.requireNonNull(block, "block");
        NodePredicateBuilder builder = new NodePredicateBuilder();
        block.accept(builder);
        return builder.compilePredicate();
    }

    private static Style resolveStyle(FlatNode node) {
        return node == null || node.style() == null ? Style.EMPTY : node.style();
    }

    public static final class AnyOfBuilder {
        private final List<Predicate<FlatNode>> branches = new ArrayList<>();

        public AnyOfBuilder match(Predicate<FlatNode> predicate) {
            Objects.requireNonNull(predicate, "predicate");
            branches.add(predicate);
            return this;
        }

        public AnyOfBuilder match(Consumer<NodePredicateBuilder> block) {
            branches.add(buildPredicate(block));
            return this;
        }

        private List<Predicate<FlatNode>> build() {
            return List.copyOf(branches);
        }
    }
}
