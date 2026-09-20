package com.tedredington.jazzclub.testsupport;

import java.util.ArrayList;
import java.util.List;

import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.app.event.PlayerEvent;
import com.tedredington.jazzclub.app.event.PlayerEvents;
import com.tedredington.jazzclub.player.AudioPlayer;

/** Collects what {@link PlayerEvents} emits. */
public final class RecordingEvents {

    private final List<PlayerEvent> events = new ArrayList<>();

    public PlayerEvents on(PlaybackState state, AudioPlayer player) {
        return new PlayerEvents(state, player, events::add);
    }

    public List<PlayerEvent> all() {
        return events;
    }

    public List<EventType> types() {
        return events.stream().map(PlayerEvent::type).toList();
    }

    public PlayerEvent last() {
        return events.getLast();
    }

    public void clear() {
        events.clear();
    }
}
