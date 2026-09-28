package com.tedredington.jazzclub.lastfm;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Records every call; fails on demand. */
final class FakeLastFmClient implements LastFmClient {

    /** Queued like a failure, to let one call through before the next failure. */
    static final RuntimeException SUCCEED = new RuntimeException("succeed");

    final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    final List<Scrobble> scrobbles = Collections.synchronizedList(new ArrayList<>());
    final Deque<RuntimeException> failSignIn = new ArrayDeque<>();
    final Deque<RuntimeException> failNext = new ArrayDeque<>();
    Duration delay = Duration.ZERO;

    @Override
    public Session signIn(String user, String password) {
        calls.add("signIn " + user + "/" + password);
        throwIfQueued(failSignIn);
        return new Session("Ted", "sk-1");
    }

    @Override
    public void nowPlaying(String sessionKey, Track track) {
        calls.add("nowPlaying " + sessionKey + " " + track.title());
        throwIfQueued(failNext);
    }

    @Override
    public void scrobble(String sessionKey, Scrobble scrobble) {
        sleep();
        calls.add("scrobble " + sessionKey + " " + scrobble.track().title());
        scrobbles.add(scrobble);
        throwIfQueued(failNext);
    }

    private void sleep() {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void throwIfQueued(Deque<RuntimeException> failures) {
        RuntimeException failure = failures.poll();
        if (failure != null && failure != SUCCEED) {
            throw failure;
        }
    }
}
