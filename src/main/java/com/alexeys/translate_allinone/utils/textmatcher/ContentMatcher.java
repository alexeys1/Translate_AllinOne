package com.alexeys.translate_allinone.utils.textmatcher;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

@FunctionalInterface
public interface ContentMatcher {
    boolean matches(ComponentContents content);

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
        return content -> content instanceof PlainTextContents plainTextContent
                && plainTextContent.text().equals(exact);
    }

    static ContentMatcher regex(String pattern) {
        return regex(Pattern.compile(pattern));
    }

    static ContentMatcher regex(Pattern pattern) {
        return content -> content instanceof PlainTextContents plainTextContent
                && pattern.matcher(plainTextContent.text()).matches();
    }

    static ContentMatcher contains(String substring) {
        return content -> content instanceof PlainTextContents plainTextContent
                && plainTextContent.text().contains(substring);
    }

    static ContentMatcher startsWith(String prefix) {
        return content -> content instanceof PlainTextContents plainTextContent
                && plainTextContent.text().startsWith(prefix);
    }

    static ContentMatcher endsWith(String suffix) {
        return content -> content instanceof PlainTextContents plainTextContent
                && plainTextContent.text().endsWith(suffix);
    }

    static ContentMatcher any() {
        return content -> true;
    }

    static ContentMatcher plainText() {
        return content -> content instanceof PlainTextContents;
    }

    static ContentMatcher translatable(String key) {
        return content -> content instanceof TranslatableContents translatableTextContent
                && translatableTextContent.getKey().equals(key);
    }

    static ContentMatcher translatable() {
        return content -> content instanceof TranslatableContents;
    }

    static ContentMatcher textMatching(Predicate<String> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return content -> content instanceof PlainTextContents plainTextContent
                && predicate.test(plainTextContent.text());
    }

    static ContentMatcher contentMatching(Predicate<ComponentContents> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return predicate::test;
    }
}
