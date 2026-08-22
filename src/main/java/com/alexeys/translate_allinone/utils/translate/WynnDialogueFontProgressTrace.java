package com.alexeys.translate_allinone.utils.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class WynnDialogueFontProgressTrace {
    private long nextSessionId = 1L;
    private Session activeSession;

    synchronized List<Event> observe(String npcName, String dialogue) {
        String normalizedNpcName = WynnDialogueTextTemplate.normalize(npcName);
        String normalizedDialogue = WynnDialogueTextTemplate.normalize(dialogue);
        if (normalizedDialogue.isBlank()) {
            return List.of();
        }

        if (activeSession == null) {
            Event start = start(normalizedNpcName, normalizedDialogue);
            return List.of(start);
        }

        if (!compatible(activeSession, normalizedNpcName, normalizedDialogue)) {
            List<Event> events = new ArrayList<>();
            events.add(summary(activeSession, "source_changed"));
            events.add(start(normalizedNpcName, normalizedDialogue));
            return List.copyOf(events);
        }

        if (Objects.equals(activeSession.currentDialogue, normalizedDialogue)) {
            return List.of();
        }

        String relation = normalizedDialogue.startsWith(activeSession.currentDialogue)
                ? "extends_previous"
                : "contracts_previous";
        activeSession.frame++;
        activeSession.currentDialogue = normalizedDialogue;
        if (normalizedDialogue.length() > activeSession.longestDialogue.length()) {
            activeSession.longestDialogue = normalizedDialogue;
        }
        if (normalizedDialogue.length() > activeSession.firstDialogue.length()
                && normalizedDialogue.startsWith(activeSession.firstDialogue)) {
            activeSession.firstFrameExtended = true;
        }
        return List.of(event(EventKind.PROGRESS, activeSession, relation));
    }

    synchronized Event finish(String reason) {
        if (activeSession == null) {
            return null;
        }
        Event summary = summary(activeSession, reason == null ? "finished" : reason);
        activeSession = null;
        return summary;
    }

    synchronized void reset() {
        activeSession = null;
    }

    private Event start(String npcName, String dialogue) {
        activeSession = new Session(nextSessionId++, npcName, dialogue);
        return event(EventKind.START, activeSession, "new_source");
    }

    private static boolean compatible(Session session, String npcName, String dialogue) {
        return Objects.equals(session.npcName, npcName)
                && (dialogue.startsWith(session.currentDialogue)
                || session.currentDialogue.startsWith(dialogue));
    }

    private static Event summary(Session session, String reason) {
        return event(EventKind.SUMMARY, session, reason);
    }

    private static Event event(EventKind kind, Session session, String relation) {
        return new Event(
                kind,
                session.id,
                session.frame,
                relation,
                session.npcName,
                session.firstDialogue,
                session.currentDialogue,
                session.longestDialogue,
                session.firstFrameExtended
        );
    }

    enum EventKind {
        START,
        PROGRESS,
        SUMMARY
    }

    record Event(
            EventKind kind,
            long sessionId,
            int frame,
            String relation,
            String npcName,
            String firstDialogue,
            String currentDialogue,
            String longestDialogue,
            boolean firstFrameExtended
    ) {
    }

    private static final class Session {
        private final long id;
        private final String npcName;
        private final String firstDialogue;
        private String currentDialogue;
        private String longestDialogue;
        private int frame;
        private boolean firstFrameExtended;

        private Session(long id, String npcName, String dialogue) {
            this.id = id;
            this.npcName = npcName;
            this.firstDialogue = dialogue;
            this.currentDialogue = dialogue;
            this.longestDialogue = dialogue;
        }
    }
}
