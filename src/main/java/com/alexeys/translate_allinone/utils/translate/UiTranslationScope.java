package com.alexeys.translate_allinone.utils.translate;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class UiTranslationScope {
    private static final Logger LOGGER = LoggerFactory.getLogger("translate_allinone.ui-scope");
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> INTERNAL_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final long SCREEN_SESSION_INACTIVITY_NANOS = 750_000_000L;
    private static volatile Object activeScreenSession;
    private static volatile String activeClassNameSession;
    private static volatile long activeSessionLastActivityNanos;
    private static final Set<Screen> SCREEN_REMOVAL_HOOKED = Collections.newSetFromMap(
            new WeakHashMap<>()
    );

    private UiTranslationScope() {
    }

    public static Scope enter(Screen screen) {
        return enterObject(screen);
    }

    public static Scope enter(Object screenObject) {
        return enterObject(screenObject);
    }

    public static Scope enter(String className) {
        Frame parent = currentFrame();
        UiScreenAdapter adapter = className == null ? null : UiScreenAdapterRegistry.resolve(className);
        if (adapter == null && parent != null) {
            adapter = parent.adapter;
        }
        UiTranslationDiagnostics.recordScreen(className, adapter);
        if (adapter == null) {
            return Scope.inactive();
        }
        if (parent == null) {
            trackClassNameSession(className);
        }
        Frame frame = new Frame(
                adapter,
                parent == null ? new HashMap<>() : parent.cache,
                UiTextRole.OPTION,
                false,
                false
        );
        FRAMES.get().push(frame);
        return new Scope(frame);
    }

    private static Scope enterObject(Object screenObject) {
        Frame parent = currentFrame();
        UiScreenAdapter adapter = screenObject == null ? null : UiScreenAdapterRegistry.resolve(screenObject.getClass());
        if (adapter == null && parent != null) {
            adapter = parent.adapter;
        }
        UiTranslationDiagnostics.recordScreen(
                screenObject == null ? null : screenObject.getClass().getName(),
                adapter
        );
        if (adapter == null) {
            return Scope.inactive();
        }
        if (parent == null) {
            trackObjectSession(screenObject);
        }
        Frame frame = new Frame(
                adapter,
                parent == null ? new HashMap<>() : parent.cache,
                UiTextRole.OPTION,
                false,
                false
        );
        FRAMES.get().push(frame);
        return new Scope(frame);
    }

    private static void trackObjectSession(Object screenObject) {
        long now = System.nanoTime();
        if (activeScreenSession == screenObject) {
            if (now - activeSessionLastActivityNanos >= SCREEN_SESSION_INACTIVITY_NANOS) {
                UiTranslationRuntime.onScreenClosed();
                UiTranslationRuntime.onScreenOpened();
            }
            activeClassNameSession = null;
            activeSessionLastActivityNanos = now;
            return;
        }
        endActiveSession();
        activeScreenSession = screenObject;
        activeClassNameSession = null;
        activeSessionLastActivityNanos = now;
        if (screenObject instanceof Screen screen && SCREEN_REMOVAL_HOOKED.add(screen)) {
            ScreenEvents.remove(screen).register(removed -> {
                if (activeScreenSession == removed) {
                    endActiveSession();
                }
            });
        }
        UiTranslationRuntime.onScreenOpened();
    }

    private static void trackClassNameSession(String className) {
        long now = System.nanoTime();
        if (activeScreenSession != null
                && now - activeSessionLastActivityNanos < SCREEN_SESSION_INACTIVITY_NANOS) {
            return;
        }
        if (activeClassNameSession != null && className.equals(activeClassNameSession)) {
            if (now - activeSessionLastActivityNanos >= SCREEN_SESSION_INACTIVITY_NANOS) {
                UiTranslationRuntime.onScreenClosed();
                UiTranslationRuntime.onScreenOpened();
            }
            activeSessionLastActivityNanos = now;
            return;
        }
        endActiveSession();
        activeClassNameSession = className;
        activeScreenSession = null;
        activeSessionLastActivityNanos = now;
        UiTranslationRuntime.onScreenOpened();
    }

    private static boolean hasActiveSession() {
        return activeScreenSession != null || activeClassNameSession != null;
    }

    private static boolean isSessionStale() {
        return hasActiveSession()
                && System.nanoTime() - activeSessionLastActivityNanos
                >= SCREEN_SESSION_INACTIVITY_NANOS;
    }

    private static void endActiveSession() {
        if (!hasActiveSession()) {
            return;
        }
        activeScreenSession = null;
        activeClassNameSession = null;
        activeSessionLastActivityNanos = 0L;
        UiTranslationRuntime.onScreenClosed();
    }

    public static void expireIdleScreenSessions() {
        if (isSessionStale()) {
            endActiveSession();
        }
    }

    public static Scope enterInput() {
        Frame parent = currentFrame();
        if (parent == null) {
            return Scope.inactive();
        }
        Frame frame = parent.child(parent.role, true, parent.tooltip);
        FRAMES.get().push(frame);
        return new Scope(frame);
    }

    public static Scope enterTooltip() {
        Frame parent = currentFrame();
        if (parent == null) {
            return Scope.inactive();
        }
        Frame frame = parent.child(UiTextRole.TOOLTIP, parent.input, true);
        FRAMES.get().push(frame);
        return new Scope(frame);
    }

    public static Scope enterRole(UiTextRole role) {
        Frame parent = currentFrame();
        if (parent == null) {
            return Scope.inactive();
        }
        Frame frame = parent.child(role == null ? parent.role : role, parent.input, parent.tooltip);
        FRAMES.get().push(frame);
        return new Scope(frame);
    }

    public static boolean isActive() {
        return currentFrame() != null;
    }

    public static boolean isUserInput() {
        Frame frame = currentFrame();
        return frame != null && frame.input;
    }

    public static UiTextRole role() {
        Frame frame = currentFrame();
        if (frame == null) {
            return UiTextRole.OPTION;
        }
        return frame.tooltip ? UiTextRole.TOOLTIP : frame.role;
    }

    public static UiScreenAdapter adapter() {
        Frame frame = currentFrame();
        return frame == null ? null : frame.adapter;
    }

    static UiTranslationResult lookup(String source, UiTextRole role, String targetLanguage) {
        Frame frame = currentFrame();
        return frame == null
                ? null
                : frame.cache.get(new CacheKey(source, role, targetLanguage));
    }

    static void remember(String source, UiTextRole role, String targetLanguage, UiTranslationResult result) {
        Frame frame = currentFrame();
        if (frame != null && result != null && result.translated()) {
            frame.cache.put(new CacheKey(source, role, targetLanguage), result);
            UiTranslationRuntime.notifyScreenTranslationAvailable(source, role, targetLanguage);
        }
    }

    public static boolean isInternal() {
        return INTERNAL_DEPTH.get() > 0;
    }

    public static Scope enterInternal() {
        INTERNAL_DEPTH.set(INTERNAL_DEPTH.get() + 1);
        return new Scope(null, true);
    }

    public static void withInternal(Runnable action) {
        if (action == null) {
            return;
        }
        INTERNAL_DEPTH.set(INTERNAL_DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            decrementInternalDepth();
        }
    }

    public static <T> T withInternal(Supplier<T> action) {
        if (action == null) {
            return null;
        }
        INTERNAL_DEPTH.set(INTERNAL_DEPTH.get() + 1);
        try {
            return action.get();
        } finally {
            decrementInternalDepth();
        }
    }

    private static void decrementInternalDepth() {
        int depth = INTERNAL_DEPTH.get() - 1;
        if (depth <= 0) {
            INTERNAL_DEPTH.remove();
        } else {
            INTERNAL_DEPTH.set(depth);
        }
    }

    private static Frame currentFrame() {
        Deque<Frame> frames = FRAMES.get();
        return frames.peek();
    }

    public static final class Scope implements AutoCloseable {
        private final Frame frame;
        private final boolean internal;
        private boolean closed;

        private Scope(Frame frame) {
            this(frame, false);
        }

        private Scope(Frame frame, boolean internal) {
            this.frame = frame;
            this.internal = internal;
        }

        private static Scope inactive() {
            return new Scope(null, false);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (internal) {
                decrementInternalDepth();
                return;
            }
            if (frame == null) {
                return;
            }
            Deque<Frame> frames = FRAMES.get();
            if (frames.peek() == frame) {
                frames.pop();
            } else {
                Frame popped;
                do {
                    popped = frames.poll();
                } while (popped != null && popped != frame);
            }
            if (frames.isEmpty()) {
                FRAMES.remove();
            }
        }
    }

    private record CacheKey(String source, UiTextRole role, String targetLanguage) {
    }

    static void discardStaleFrames(int currentFrameId) {
        Deque<Frame> frames = FRAMES.get();
        int discarded = 0;
        while (!frames.isEmpty() && frames.peek().frameId() < currentFrameId) {
            frames.pop();
            discarded++;
        }
        if (discarded > 0) {
            LOGGER.warn(
                    "Discarded {} stale UI translation frame(s) from a previous render (leak recovery); remaining depth={}",
                    discarded,
                    frames.size()
            );
            if (frames.isEmpty()) {
                FRAMES.remove();
            }
        }
    }

    private static final class Frame {
        private final UiScreenAdapter adapter;
        private final Map<CacheKey, UiTranslationResult> cache;
        private final UiTextRole role;
        private final boolean input;
        private final boolean tooltip;
        private final int frameId;

        private Frame(
                UiScreenAdapter adapter,
                Map<CacheKey, UiTranslationResult> cache,
                UiTextRole role,
                boolean input,
                boolean tooltip
        ) {
            this.adapter = adapter;
            this.cache = cache;
            this.role = role;
            this.input = input;
            this.tooltip = tooltip;
            this.frameId = UiTranslationRuntime.currentFrameId();
        }

        private int frameId() {
            return frameId;
        }

        private Frame child(UiTextRole nextRole, boolean nextInput, boolean nextTooltip) {
            return new Frame(adapter, cache, nextRole, nextInput, nextTooltip);
        }
    }
}