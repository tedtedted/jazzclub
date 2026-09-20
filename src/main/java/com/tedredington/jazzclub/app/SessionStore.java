package com.tedredington.jazzclub.app;

import java.util.Optional;

import com.tedredington.jazzclub.pandora.model.Station;

/** Remembers, from one run to the next, how the listener left the player. */
@FunctionalInterface
public interface SessionStore {

    /** @param station what was playing; empty if the radio was stopped, which the next start should respect */
    void save(int volumeDb, Optional<Station> station);
}
