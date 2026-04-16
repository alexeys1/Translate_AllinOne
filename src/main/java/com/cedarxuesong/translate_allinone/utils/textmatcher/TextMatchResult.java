package com.cedarxuesong.translate_allinone.utils.textmatcher;

import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;

import java.util.List;

public final class TextMatchResult {
    public static final TextMatchResult FAILURE = new TextMatchResult(false, List.of(), List.of(), -1, -1);

    private final boolean success;
    private final List<FlatNode> matchedNodes;
    private final List<String> matchedBranches;
    private final int startIndex;
    private final int endIndex;

    public TextMatchResult(boolean success, List<FlatNode> matchedNodes, int startIndex, int endIndex) {
        this(success, matchedNodes, List.of(), startIndex, endIndex);
    }

    public TextMatchResult(
            boolean success,
            List<FlatNode> matchedNodes,
            List<String> matchedBranches,
            int startIndex,
            int endIndex
    ) {
        this.success = success;
        this.matchedNodes = matchedNodes;
        this.matchedBranches = matchedBranches;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    public boolean success() {
        return success;
    }

    public List<FlatNode> matchedNodes() {
        return matchedNodes;
    }

    public List<String> matchedBranches() {
        return matchedBranches;
    }

    public int startIndex() {
        return startIndex;
    }

    public int endIndex() {
        return endIndex;
    }

    public String fullText() {
        StringBuilder builder = new StringBuilder();
        for (FlatNode node : matchedNodes) {
            builder.append(node.extractString());
        }
        return builder.toString();
    }

    public MutableText toText() {
        MutableText base = MutableText.of(PlainTextContent.EMPTY);
        for (FlatNode node : matchedNodes) {
            base.append(node.toText());
        }
        return base;
    }
}
