package com.cedarxuesong.translate_allinone.utils.translate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

final class TooltipGuardDevLogSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipGuardDevLogSupport");
    private static final long FLUSH_INTERVAL_MILLIS = 1500L;
    private static final ScheduledExecutorService FLUSH_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "translate_allinone-tooltip-guard-devlog");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, GuardEventState> STATE_BY_KEY = new ConcurrentHashMap<>();

    static {
        FLUSH_EXECUTOR.scheduleAtFixedRate(
                TooltipGuardDevLogSupport::flushPendingEvents,
                FLUSH_INTERVAL_MILLIS,
                FLUSH_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
        );
    }

    private TooltipGuardDevLogSupport() {
    }

    static void recordGuardEvent(String source, String phase, int lineCount, String detail) {
        if (source == null || source.isBlank() || phase == null || phase.isBlank()) {
            return;
        }

        String key = source + ":" + phase;
        GuardEventState state = STATE_BY_KEY.computeIfAbsent(key, ignored -> new GuardEventState(source, phase));
        state.hitCount.increment();
        state.lastLineCount = Math.max(0, lineCount);
        state.lastDetail = truncate(detail, 220);
        state.lastUpdatedAtMillis = System.currentTimeMillis();
    }

    private static void flushPendingEvents() {
        for (GuardEventState state : STATE_BY_KEY.values()) {
            long totalHits = state.hitCount.sum();
            long deltaHits = totalHits - state.lastLoggedHitCount;
            if (deltaHits <= 0) {
                continue;
            }

            state.lastLoggedHitCount = totalHits;
            LOGGER.info(
                    "[TooltipDev:{}:guard] phase={} hits={} lineCount={} detail=\"{}\"",
                    state.source,
                    state.phase,
                    deltaHits,
                    state.lastLineCount,
                    state.lastDetail
            );
        }
    }

    static List<String> drainPendingEventsForTest() {
        List<String> summaries = new ArrayList<>();
        List<GuardEventState> states = new ArrayList<>(STATE_BY_KEY.values());
        states.sort(Comparator.comparing(state -> state.source + ":" + state.phase));
        for (GuardEventState state : states) {
            long totalHits = state.hitCount.sum();
            long deltaHits = totalHits - state.lastLoggedHitCount;
            if (deltaHits <= 0) {
                continue;
            }
            state.lastLoggedHitCount = totalHits;
            summaries.add(
                    state.source + "|" + state.phase + "|" + deltaHits + "|" + state.lastLineCount + "|" + state.lastDetail
            );
        }
        return summaries;
    }

    static void resetForTest() {
        STATE_BY_KEY.clear();
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private static final class GuardEventState {
        private final String source;
        private final String phase;
        private final LongAdder hitCount = new LongAdder();
        private volatile long lastLoggedHitCount = 0L;
        private volatile long lastUpdatedAtMillis = 0L;
        private volatile int lastLineCount = 0;
        private volatile String lastDetail = "";

        private GuardEventState(String source, String phase) {
            this.source = source;
            this.phase = phase;
        }
    }
}
