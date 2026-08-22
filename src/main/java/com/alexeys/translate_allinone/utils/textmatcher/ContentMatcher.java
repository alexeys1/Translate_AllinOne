package com.alexeys.translate_allinone.utils.textmatcher;

import net.minecraft.text.PlainTextContent;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@FunctionalInterface
public interface ContentMatcher {
    boolean matches(TextContent content);

    default ContentMatcher or(ContentMatcher other) {
        Objects.requireNonNull(other, "other");
        return content -> matches(content) || other.matches(content);
    }

    default ContentMatcher and(ContentMatcher other) {
        Objects.requireNonNull(other, "other");
        return content -> matches(content) && other.matches(content);
    }

    default ContentMatcher negate() {
        return content -> !matches(content);
    }

    static ContentMatcher text(String exact) {
        return content -> content instanceof PlainTextContent plainTextContent
                && plainTextContent.string().equals(exact);
    }

    static ContentMatcher regex(String pattern) {
        return regex(Pattern.compile(pattern));
    }

    static ContentMatcher regex(Pattern pattern) {
        return content -> content instanceof PlainTextContent plainTextContent
                && pattern.matcher(plainTextContent.string()).matches();
    }

    static ContentMatcher contains(String substring) {
        return content -> content instanceof PlainTextContent plainTextContent
                && plainTextContent.string().contains(substring);
    }

    static ContentMatcher startsWith(String prefix) {
        return content -> content instanceof PlainTextContent plainTextContent
                && plainTextContent.string().startsWith(prefix);
    }

    static ContentMatcher endsWith(String suffix) {
        return content -> content instanceof PlainTextContent plainTextContent
                && plainTextContent.string().endsWith(suffix);
    }

    static ContentMatcher any() {
        return content -> true;
    }

    static ContentMatcher plainText() {
        return content -> content instanceof PlainTextContent;
    }

    static ContentMatcher translatable(String key) {
        return content -> content instanceof TranslatableTextContent translatableTextContent
                && translatableTextContent.getKey().equals(key);
    }

    static ContentMatcher translatable() {
        return content -> content instanceof TranslatableTextContent;
    }

    static ContentMatcher textMatching(Predicate<String> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return content -> content instanceof PlainTextContent plainTextContent
                && predicate.test(plainTextContent.string());
    }

    static ContentMatcher contentMatching(Predicate<TextContent> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return predicate::test;
    }
}
