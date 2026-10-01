package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatOutputRetryBudgetTest {
    private static final String KEY = "target=chinese\u001f<s0>hello</s0>";
    private static final long ROUND_ONE_START = 1_000_000L;

    private final ChatOutputRetryBudget budget = new ChatOutputRetryBudget();

    @Test
    void keepsFailureVisibleDuringCooldown() {
        budget.recordProviderCall(KEY);
        budget.recordFailedRound(KEY, ROUND_ONE_START);

        assertFalse(budget.claimAutomaticRetry(KEY, ROUND_ONE_START));
        assertFalse(budget.claimAutomaticRetry(KEY, ROUND_ONE_START + ChatOutputRetryBudget.ROUND_COOLDOWN_MILLIS - 1));
        assertTrue(budget.claimAutomaticRetry(KEY, ROUND_ONE_START + ChatOutputRetryBudget.ROUND_COOLDOWN_MILLIS));
    }

    @Test
    void claimingDoesNotConsumeTheBudget() {
        budget.recordProviderCall(KEY);
        budget.recordFailedRound(KEY, ROUND_ONE_START);
        long retryAt = ROUND_ONE_START + ChatOutputRetryBudget.ROUND_COOLDOWN_MILLIS;

        assertTrue(budget.claimAutomaticRetry(KEY, retryAt));
        assertTrue(budget.claimAutomaticRetry(KEY, retryAt));
    }

    @Test
    void allowsSecondProviderCallInTheSameRound() {
        budget.recordProviderCall(KEY);

        assertTrue(budget.canRetryInRound(KEY, 1));
        assertFalse(budget.canRetryInRound(KEY, ChatOutputRetryBudget.PROVIDER_CALLS_PER_ROUND));
    }

    @Test
    void stopsAfterSecondFailedRound() {
        budget.recordProviderCall(KEY);
        budget.recordProviderCall(KEY);
        budget.recordFailedRound(KEY, ROUND_ONE_START);
        long secondRoundAt = ROUND_ONE_START + ChatOutputRetryBudget.ROUND_COOLDOWN_MILLIS;
        assertTrue(budget.claimAutomaticRetry(KEY, secondRoundAt));

        budget.recordProviderCall(KEY);
        budget.recordProviderCall(KEY);
        budget.recordFailedRound(KEY, secondRoundAt);

        long farFuture = secondRoundAt + ChatOutputRetryBudget.ROUND_COOLDOWN_MILLIS * 100;
        assertFalse(budget.claimAutomaticRetry(KEY, farFuture));
        assertFalse(budget.canRetryInRound(KEY, 1));
    }

    @Test
    void explicitRefreshResetsExhaustedBudget() {
        budget.recordProviderCall(KEY);
        budget.recordProviderCall(KEY);
        budget.recordFailedRound(KEY, ROUND_ONE_START);
        long secondRoundAt = ROUND_ONE_START + ChatOutputRetryBudget.ROUND_COOLDOWN_MILLIS;
        budget.claimAutomaticRetry(KEY, secondRoundAt);
        budget.recordProviderCall(KEY);
        budget.recordProviderCall(KEY);
        budget.recordFailedRound(KEY, secondRoundAt);
        assertFalse(budget.claimAutomaticRetry(KEY, secondRoundAt + 1_000_000L));

        budget.reset(KEY);

        assertTrue(budget.claimAutomaticRetry(KEY, secondRoundAt + 1_000_000L));
    }

    @Test
    void successfulTranslationForgetsHistory() {
        budget.recordProviderCall(KEY);
        budget.reset(KEY);

        assertTrue(budget.claimAutomaticRetry(KEY, ROUND_ONE_START));
    }

    @Test
    void claimsUntrackedFailureWithoutCooldown() {
        assertTrue(budget.claimAutomaticRetry(KEY, ROUND_ONE_START));
    }

    @Test
    void ignoresNullKey() {
        assertFalse(budget.claimAutomaticRetry(null, ROUND_ONE_START));
        assertTrue(budget.canRetryInRound(null, 1));

        budget.recordProviderCall(null);
        budget.recordFailedRound(null, ROUND_ONE_START);
        budget.reset(null);
    }

    @Test
    void queueResetDropsEveryKey() {
        budget.recordProviderCall(KEY);
        budget.recordProviderCall(KEY);
        budget.recordFailedRound(KEY, ROUND_ONE_START);
        budget.recordFailedRound(KEY, ROUND_ONE_START);

        budget.clear();

        assertTrue(budget.claimAutomaticRetry(KEY, ROUND_ONE_START));
    }
}
