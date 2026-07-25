package com.cedarxuesong.translate_allinone.utils.cache.component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ComponentTranslationJobRegistry {
    private final ConcurrentMap<String, RegisteredJob> jobs = new ConcurrentHashMap<>();

    public boolean registerQueued(String cacheKey, ComponentTranslationJob job) {
        if (cacheKey == null || cacheKey.isBlank() || job == null) {
            return false;
        }
        return jobs.putIfAbsent(cacheKey, new RegisteredJob(job, State.QUEUED, false)) == null;
    }

    public boolean markInFlight(String cacheKey, long sessionEpoch) {
        boolean[] changed = {false};
        jobs.computeIfPresent(cacheKey, (key, current) -> {
            if (current.job().sessionEpoch() != sessionEpoch || current.state() != State.QUEUED) {
                return current;
            }
            changed[0] = true;
            return new RegisteredJob(current.job(), State.IN_FLIGHT, current.refreshAfterCompletion());
        });
        return changed[0];
    }

    public Optional<ComponentTranslationJob> complete(String cacheKey, long sessionEpoch) {
        RegisteredJob[] completed = {null};
        jobs.computeIfPresent(cacheKey, (key, current) -> {
            if (current.job().sessionEpoch() == sessionEpoch) {
                completed[0] = current;
                return null;
            }
            return current;
        });
        if (completed[0] == null || !completed[0].refreshAfterCompletion()) {
            return Optional.empty();
        }
        return Optional.of(completed[0].job());
    }

    public void fail(String cacheKey, long sessionEpoch) {
        jobs.computeIfPresent(cacheKey, (key, current) ->
                current.job().sessionEpoch() == sessionEpoch ? null : current
        );
    }

    public boolean requestRefresh(String cacheKey) {
        boolean[] found = {false};
        jobs.computeIfPresent(cacheKey, (key, current) -> {
            found[0] = true;
            if (current.state() == State.QUEUED) {
                // A queued refresh has not reached the provider yet; remove it so
                // a refresh-only action cannot start a new request by itself.
                return null;
            }
            return new RegisteredJob(current.job(), current.state(), true);
        });
        return found[0];
    }

    public boolean contains(String cacheKey) {
        return jobs.containsKey(cacheKey);
    }

    public int size() {
        return jobs.size();
    }

    public void clear() {
        jobs.clear();
    }

    private record RegisteredJob(ComponentTranslationJob job, State state, boolean refreshAfterCompletion) {
    }

    private enum State {
        QUEUED,
        IN_FLIGHT
    }
}
