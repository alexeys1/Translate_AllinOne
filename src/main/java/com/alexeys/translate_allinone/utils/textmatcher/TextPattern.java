package com.alexeys.translate_allinone.utils.textmatcher;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class TextPattern {
    private final List<PatternStep> steps;
    
    private record MatchState(int endIndex, List<String> matchedBranches, Map<String, List<FlatNode>> captures) {
    }

    private TextPattern(List<PatternStep> steps) {
        this.steps = steps;
    }

    public static Builder builder() {
        return new Builder();
    }

    public TextMatchResult match(Text text) {
        return match(text, true);
    }

    public TextMatchResult match(Text text, boolean compact) {
        return match(normalize(text, compact));
    }

    public TextMatchResult find(Text text) {
        return find(text, true);
    }

    public TextMatchResult find(Text text, boolean compact) {
        return find(normalize(text, compact));
    }

    public TextMatchResult match(List<FlatNode> nodes) {
        MatchState matchState = tryMatch(nodes, steps, 0, 0, List.of(), Map.of());
        if (matchState == null || matchState.endIndex() != nodes.size()) {
            return TextMatchResult.FAILURE;
        }
        return new TextMatchResult(
                true,
                List.copyOf(nodes),
                matchState.matchedBranches(),
                matchState.captures(),
                0,
                nodes.size()
        );
    }

    public TextMatchResult find(List<FlatNode> nodes) {
        for (int start = 0; start <= nodes.size(); start++) {
            MatchState matchState = tryMatch(nodes, steps, 0, start, List.of(), Map.of());
            if (matchState != null) {
                return new TextMatchResult(
                        true,
                        List.copyOf(nodes.subList(start, matchState.endIndex())),
                        matchState.matchedBranches(),
                        matchState.captures(),
                        start,
                        matchState.endIndex()
                );
            }
        }
        return TextMatchResult.FAILURE;
    }

    private static List<FlatNode> normalize(Text text, boolean compact) {
        List<FlatNode> nodes = FlatNode.flatten(text);
        return compact ? FlatNode.compact(nodes) : nodes;
    }

    private MatchState tryMatch(
            List<FlatNode> nodes,
            List<PatternStep> activeSteps,
            int stepIndex,
            int nodeIndex,
            List<String> matchedBranches,
            Map<String, List<FlatNode>> captures
    ) {
        if (stepIndex >= activeSteps.size()) {
            return new MatchState(nodeIndex, matchedBranches, Map.copyOf(captures));
        }

        PatternStep step = activeSteps.get(stepIndex);
        if (step instanceof QuantifiedStep quantifiedStep) {
            return tryMatchQuantified(nodes, activeSteps, stepIndex, nodeIndex, matchedBranches, captures, quantifiedStep);
        }
        if (step instanceof EitherStep eitherStep) {
            return tryMatchEither(nodes, activeSteps, stepIndex, nodeIndex, matchedBranches, captures, eitherStep);
        }
        return null;
    }

    private MatchState tryMatchQuantified(
            List<FlatNode> nodes,
            List<PatternStep> activeSteps,
            int stepIndex,
            int nodeIndex,
            List<String> matchedBranches,
            Map<String, List<FlatNode>> captures,
            QuantifiedStep step
    ) {
        int maxPossible = 0;
        int limit = Math.min(step.maxCount(), nodes.size() - nodeIndex);
        while (maxPossible < limit && step.predicate().test(nodes.get(nodeIndex + maxPossible))) {
            maxPossible++;
        }

        if (maxPossible < step.minCount()) {
            return null;
        }

        if (step.greedy()) {
            for (int count = maxPossible; count >= step.minCount(); count--) {
                MatchState result = tryMatch(
                        nodes,
                        activeSteps,
                        stepIndex + 1,
                        nodeIndex + count,
                        matchedBranches,
                        capturesWithCapture(captures, step.captureName(), nodes, nodeIndex, count)
                );
                if (result != null) {
                    return result;
                }
            }
        } else {
            for (int count = step.minCount(); count <= maxPossible; count++) {
                MatchState result = tryMatch(
                        nodes,
                        activeSteps,
                        stepIndex + 1,
                        nodeIndex + count,
                        matchedBranches,
                        capturesWithCapture(captures, step.captureName(), nodes, nodeIndex, count)
                );
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private MatchState tryMatchEither(
            List<FlatNode> nodes,
            List<PatternStep> activeSteps,
            int stepIndex,
            int nodeIndex,
            List<String> matchedBranches,
            Map<String, List<FlatNode>> captures,
            EitherStep step
    ) {
        List<PatternStep> remainingSteps = activeSteps.subList(stepIndex + 1, activeSteps.size());
        for (EitherBranch branch : step.branches()) {
            List<PatternStep> combinedSteps = new ArrayList<>(branch.steps().size() + remainingSteps.size());
            combinedSteps.addAll(branch.steps());
            combinedSteps.addAll(remainingSteps);
            List<String> nextMatchedBranches = matchedBranches;
            if (branch.label() != null && !branch.label().isBlank()) {
                nextMatchedBranches = new ArrayList<>(matchedBranches.size() + 1);
                nextMatchedBranches.addAll(matchedBranches);
                nextMatchedBranches.add(branch.label());
                nextMatchedBranches = List.copyOf(nextMatchedBranches);
            }
            MatchState result = tryMatch(nodes, combinedSteps, 0, nodeIndex, nextMatchedBranches, captures);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private sealed interface PatternStep permits QuantifiedStep, EitherStep {
    }

    private record QuantifiedStep(
            Predicate<FlatNode> predicate,
            int minCount,
            int maxCount,
            boolean greedy,
            String captureName
    )
            implements PatternStep {
    }

    private record EitherBranch(String label, List<PatternStep> steps) {
    }

    private record EitherStep(List<EitherBranch> branches) implements PatternStep {
    }

    public static final class Builder {
        private final List<PatternStep> steps = new ArrayList<>();

        public Builder one(ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate) {
            return quantified(contentMatcher, nodePredicate, 1, 1, true, null);
        }

        public Builder one(ContentMatcher contentMatcher, String capture, Predicate<FlatNode> nodePredicate) {
            return quantified(contentMatcher, nodePredicate, 1, 1, true, capture);
        }

        public Builder one(ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock) {
            return quantified(contentMatcher, styleBlock, 1, 1, true, null);
        }

        public Builder one(ContentMatcher contentMatcher, String capture, Consumer<NodePredicateBuilder> styleBlock) {
            return quantified(contentMatcher, styleBlock, 1, 1, true, capture);
        }

        public Builder optional(ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate, boolean greedy) {
            return quantified(contentMatcher, nodePredicate, 0, 1, greedy, null);
        }

        public Builder optional(ContentMatcher contentMatcher, String capture, Predicate<FlatNode> nodePredicate, boolean greedy) {
            return quantified(contentMatcher, nodePredicate, 0, 1, greedy, capture);
        }

        public Builder optional(ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock, boolean greedy) {
            return quantified(contentMatcher, styleBlock, 0, 1, greedy, null);
        }

        public Builder optional(
                ContentMatcher contentMatcher,
                String capture,
                Consumer<NodePredicateBuilder> styleBlock,
                boolean greedy
        ) {
            return quantified(contentMatcher, styleBlock, 0, 1, greedy, capture);
        }

        public Builder oneOrMore(ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate) {
            return quantified(contentMatcher, nodePredicate, 1, Integer.MAX_VALUE, true, null);
        }

        public Builder oneOrMore(ContentMatcher contentMatcher, String capture, Predicate<FlatNode> nodePredicate) {
            return quantified(contentMatcher, nodePredicate, 1, Integer.MAX_VALUE, true, capture);
        }

        public Builder oneOrMore(ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock) {
            return quantified(contentMatcher, styleBlock, 1, Integer.MAX_VALUE, true, null);
        }

        public Builder oneOrMore(ContentMatcher contentMatcher, String capture, Consumer<NodePredicateBuilder> styleBlock) {
            return quantified(contentMatcher, styleBlock, 1, Integer.MAX_VALUE, true, capture);
        }

        public Builder zeroOrMore(ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate, boolean greedy) {
            return quantified(contentMatcher, nodePredicate, 0, Integer.MAX_VALUE, greedy, null);
        }

        public Builder zeroOrMore(ContentMatcher contentMatcher, String capture, Predicate<FlatNode> nodePredicate, boolean greedy) {
            return quantified(contentMatcher, nodePredicate, 0, Integer.MAX_VALUE, greedy, capture);
        }

        public Builder zeroOrMore(ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock, boolean greedy) {
            return quantified(contentMatcher, styleBlock, 0, Integer.MAX_VALUE, greedy, null);
        }

        public Builder zeroOrMore(
                ContentMatcher contentMatcher,
                String capture,
                Consumer<NodePredicateBuilder> styleBlock,
                boolean greedy
        ) {
            return quantified(contentMatcher, styleBlock, 0, Integer.MAX_VALUE, greedy, capture);
        }

        public Builder exactly(int count, ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate) {
            return quantified(contentMatcher, nodePredicate, count, count, true, null);
        }

        public Builder exactly(int count, ContentMatcher contentMatcher, String capture, Predicate<FlatNode> nodePredicate) {
            return quantified(contentMatcher, nodePredicate, count, count, true, capture);
        }

        public Builder exactly(int count, ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock) {
            return quantified(contentMatcher, styleBlock, count, count, true, null);
        }

        public Builder exactly(int count, ContentMatcher contentMatcher, String capture, Consumer<NodePredicateBuilder> styleBlock) {
            return quantified(contentMatcher, styleBlock, count, count, true, capture);
        }

        public Builder between(int minCount, int maxCount, ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate, boolean greedy) {
            return quantified(contentMatcher, nodePredicate, minCount, maxCount, greedy, null);
        }

        public Builder between(
                int minCount,
                int maxCount,
                ContentMatcher contentMatcher,
                String capture,
                Predicate<FlatNode> nodePredicate,
                boolean greedy
        ) {
            return quantified(contentMatcher, nodePredicate, minCount, maxCount, greedy, capture);
        }

        public Builder between(
                int minCount,
                int maxCount,
                ContentMatcher contentMatcher,
                Consumer<NodePredicateBuilder> styleBlock,
                boolean greedy
        ) {
            return quantified(contentMatcher, styleBlock, minCount, maxCount, greedy, null);
        }

        public Builder between(
                int minCount,
                int maxCount,
                ContentMatcher contentMatcher,
                String capture,
                Consumer<NodePredicateBuilder> styleBlock,
                boolean greedy
        ) {
            return quantified(contentMatcher, styleBlock, minCount, maxCount, greedy, capture);
        }

        public Builder either(Consumer<EitherBuilder> eitherBlock) {
            Objects.requireNonNull(eitherBlock, "eitherBlock");
            EitherBuilder builder = new EitherBuilder();
            eitherBlock.accept(builder);
            List<EitherBranch> branches = builder.build();
            if (branches.isEmpty()) {
                throw new IllegalArgumentException("either must contain at least one branch");
            }
            steps.add(new EitherStep(branches));
            return this;
        }

        public TextPattern build() {
            return new TextPattern(List.copyOf(steps));
        }

        private Builder quantified(
                ContentMatcher contentMatcher,
                Predicate<FlatNode> nodePredicate,
                int minCount,
                int maxCount,
                boolean greedy,
                String capture
        ) {
            Objects.requireNonNull(contentMatcher, "contentMatcher");
            if (minCount < 0) {
                throw new IllegalArgumentException("minCount must be non-negative");
            }
            if (maxCount < minCount) {
                throw new IllegalArgumentException("maxCount must be >= minCount");
            }

            Predicate<FlatNode> predicate = node -> contentMatcher.matches(node.content())
                    && (nodePredicate == null || nodePredicate.test(node));
            steps.add(new QuantifiedStep(predicate, minCount, maxCount, greedy, capture));
            return this;
        }

        private Builder quantified(
                ContentMatcher contentMatcher,
                Consumer<NodePredicateBuilder> styleBlock,
                int minCount,
                int maxCount,
                boolean greedy,
                String capture
        ) {
            Predicate<FlatNode> nodePredicate = styleBlock == null ? null : NodePredicateBuilder.buildPredicate(styleBlock);
            return quantified(contentMatcher, nodePredicate, minCount, maxCount, greedy, capture);
        }

        private List<PatternStep> snapshot() {
            return List.copyOf(steps);
        }
    }

    public static final class EitherBuilder {
        private final List<EitherBranch> branches = new ArrayList<>();

        public EitherBuilder branch(Consumer<Builder> branchBlock) {
            return branch(null, branchBlock);
        }

        public EitherBuilder branch(String label, Consumer<Builder> branchBlock) {
            Objects.requireNonNull(branchBlock, "branchBlock");
            Builder builder = new Builder();
            branchBlock.accept(builder);
            branches.add(new EitherBranch(label, builder.snapshot()));
            return this;
        }

        public EitherBuilder one(String label, ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate) {
            return branch(label, branchBuilder -> branchBuilder.one(contentMatcher, nodePredicate));
        }

        public EitherBuilder one(ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate) {
            return branch(branchBuilder -> branchBuilder.one(contentMatcher, nodePredicate));
        }

        public EitherBuilder one(String label, ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock) {
            return branch(label, branchBuilder -> branchBuilder.one(contentMatcher, styleBlock));
        }

        public EitherBuilder one(ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock) {
            return branch(branchBuilder -> branchBuilder.one(contentMatcher, styleBlock));
        }

        public EitherBuilder optional(String label, ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate, boolean greedy) {
            return branch(label, branchBuilder -> branchBuilder.optional(contentMatcher, nodePredicate, greedy));
        }

        public EitherBuilder optional(ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate, boolean greedy) {
            return branch(branchBuilder -> branchBuilder.optional(contentMatcher, nodePredicate, greedy));
        }

        public EitherBuilder optional(String label, ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock, boolean greedy) {
            return branch(label, branchBuilder -> branchBuilder.optional(contentMatcher, styleBlock, greedy));
        }

        public EitherBuilder optional(ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock, boolean greedy) {
            return branch(branchBuilder -> branchBuilder.optional(contentMatcher, styleBlock, greedy));
        }

        public EitherBuilder oneOrMore(String label, ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate) {
            return branch(label, branchBuilder -> branchBuilder.oneOrMore(contentMatcher, nodePredicate));
        }

        public EitherBuilder oneOrMore(ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate) {
            return branch(branchBuilder -> branchBuilder.oneOrMore(contentMatcher, nodePredicate));
        }

        public EitherBuilder oneOrMore(String label, ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock) {
            return branch(label, branchBuilder -> branchBuilder.oneOrMore(contentMatcher, styleBlock));
        }

        public EitherBuilder oneOrMore(ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock) {
            return branch(branchBuilder -> branchBuilder.oneOrMore(contentMatcher, styleBlock));
        }

        public EitherBuilder zeroOrMore(String label, ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate, boolean greedy) {
            return branch(label, branchBuilder -> branchBuilder.zeroOrMore(contentMatcher, nodePredicate, greedy));
        }

        public EitherBuilder zeroOrMore(ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate, boolean greedy) {
            return branch(branchBuilder -> branchBuilder.zeroOrMore(contentMatcher, nodePredicate, greedy));
        }

        public EitherBuilder zeroOrMore(String label, ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock, boolean greedy) {
            return branch(label, branchBuilder -> branchBuilder.zeroOrMore(contentMatcher, styleBlock, greedy));
        }

        public EitherBuilder zeroOrMore(ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock, boolean greedy) {
            return branch(branchBuilder -> branchBuilder.zeroOrMore(contentMatcher, styleBlock, greedy));
        }

        public EitherBuilder exactly(String label, int count, ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate) {
            return branch(label, branchBuilder -> branchBuilder.exactly(count, contentMatcher, nodePredicate));
        }

        public EitherBuilder exactly(int count, ContentMatcher contentMatcher, Predicate<FlatNode> nodePredicate) {
            return branch(branchBuilder -> branchBuilder.exactly(count, contentMatcher, nodePredicate));
        }

        public EitherBuilder exactly(String label, int count, ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock) {
            return branch(label, branchBuilder -> branchBuilder.exactly(count, contentMatcher, styleBlock));
        }

        public EitherBuilder exactly(int count, ContentMatcher contentMatcher, Consumer<NodePredicateBuilder> styleBlock) {
            return branch(branchBuilder -> branchBuilder.exactly(count, contentMatcher, styleBlock));
        }

        public EitherBuilder between(
                String label,
                int minCount,
                int maxCount,
                ContentMatcher contentMatcher,
                Predicate<FlatNode> nodePredicate,
                boolean greedy
        ) {
            return branch(label, branchBuilder -> branchBuilder.between(minCount, maxCount, contentMatcher, nodePredicate, greedy));
        }

        public EitherBuilder between(
                int minCount,
                int maxCount,
                ContentMatcher contentMatcher,
                Predicate<FlatNode> nodePredicate,
                boolean greedy
        ) {
            return branch(branchBuilder -> branchBuilder.between(minCount, maxCount, contentMatcher, nodePredicate, greedy));
        }

        public EitherBuilder between(
                String label,
                int minCount,
                int maxCount,
                ContentMatcher contentMatcher,
                Consumer<NodePredicateBuilder> styleBlock,
                boolean greedy
        ) {
            return branch(label, branchBuilder -> branchBuilder.between(minCount, maxCount, contentMatcher, styleBlock, greedy));
        }

        public EitherBuilder between(
                int minCount,
                int maxCount,
                ContentMatcher contentMatcher,
                Consumer<NodePredicateBuilder> styleBlock,
                boolean greedy
        ) {
            return branch(branchBuilder -> branchBuilder.between(minCount, maxCount, contentMatcher, styleBlock, greedy));
        }

        private List<EitherBranch> build() {
            return List.copyOf(branches);
        }
    }

    private static Map<String, List<FlatNode>> capturesWithCapture(
            Map<String, List<FlatNode>> captures,
            String captureName,
            List<FlatNode> nodes,
            int nodeIndex,
            int count
    ) {
        if (captureName == null || captureName.isBlank()) {
            return captures;
        }

        Map<String, List<FlatNode>> next = new HashMap<>(captures);
        next.put(captureName, List.copyOf(nodes.subList(nodeIndex, nodeIndex + count)));
        return Map.copyOf(next);
    }
}
