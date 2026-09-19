package com.tedredington.jazzclub.testsupport;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.tedredington.jazzclub.player.AudioPlayer;

/** A player without sound or threads. Tests end tracks themselves by calling the code under test. */
public final class FakeAudioPlayer implements AudioPlayer {

    public record Played(long id, URI url, double gainDb) {
    }

    private final List<Played> played = new ArrayList<>();
    private boolean active;
    private boolean paused;
    private int volume;
    private int stops;
    private Duration elapsed = Duration.ZERO;

    @Override
    public long play(URI audioUrl, double trackGainDb) {
        long id = played.size() + 1L;
        played.add(new Played(id, audioUrl, trackGainDb));
        active = true;
        paused = false;
        return id;
    }

    @Override
    public void stop() {
        stops++;
    }

    /** What the real player does once its thread has wound down. */
    public void finish() {
        active = false;
        paused = false;
    }

    @Override
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public Duration elapsed() {
        return elapsed;
    }

    public void elapsed(Duration elapsed) {
        this.elapsed = elapsed;
    }

    @Override
    public void setVolume(int volumeDb) {
        this.volume = volumeDb;
    }

    @Override
    public int volume() {
        return volume;
    }

    public List<Played> played() {
        return played;
    }

    public long lastId() {
        return played.getLast().id();
    }

    public int stops() {
        return stops;
    }
}
