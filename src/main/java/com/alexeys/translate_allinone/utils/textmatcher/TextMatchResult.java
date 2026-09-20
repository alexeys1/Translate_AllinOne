package com.alexeys.translate_allinone.utils.textmatcher;

import java.util.Map;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

public final class TextMatchResult {
    public static final TextMatchResult FAILURE = new TextMatchResult(false, List.of(), List.of(), Map.of(), -1, -1);

    private final boolean success;
    private final List<FlatNode> matchedNodes;
    private final List<String> matchedBranches;
    private final Map<String, List<FlatNode>> captures;
    private final int startIndex;
    private final int endIndex;

    public TextMatchResult(boolean success, List<FlatNode> matchedNodes, int startIndex, int endIndex) {
        this(success, matchedNodes, List.of(), Map.of(), startIndex, endIndex);
    }

    public TextMatchResult(
            boolean success,
            List<FlatNode> matchedNodes,
            List<String> matchedBranches,
            int startIndex,
            int endIndex
    ) {
        this(success, matchedNodes, matchedBranches, Map.of(), startIndex, endIndex);
    }

    public TextMatchResult(
            boolean success,
            List<FlatNode> matchedNodes,
            List<String> matchedBranches,
            Map<String, List<FlatNode>> captures,
            int startIndex,
            int endIndex
    ) {
        this.success = success;
        this.matchedNodes = matchedNodes;
        this.matchedBranches = matchedBranches;
        this.captures = captures;
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

    public Set<String> groupNames() {
        return captures.keySet();
    }

    public String groupText(String name) {
        List<FlatNode> nodes = captures.get(name);
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (FlatNode node : nodes) {
            builder.append(node.extractString());
        }
        return builder.toString();
    }

    public List<FlatNode> groupNodes(String name) {
        return captures.get(name);
    }

    public Style groupStyle(String name) {
        List<FlatNode> nodes = captures.get(name);
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        return nodes.get(0).style();
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

    public MutableComponent toText() {
        MutableComponent base = MutableComponent.create(PlainTextContents.EMPTY);
        for (FlatNode node : matchedNodes) {
            base.append(node.toText());
        }
        return base;
    }
}
