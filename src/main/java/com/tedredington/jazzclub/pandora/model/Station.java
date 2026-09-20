package com.tedredington.jazzclub.pandora.model;

import java.util.Objects;

/**
 * @param token      Pandora's {@code stationToken}; what pianobar calls the station id
 * @param creator    {@code false} for stations shared by somebody else
 * @param quickMix   this is the QuickMix/Shuffle pseudo station itself
 * @param inQuickMix this station contributes to the QuickMix
 */
public record Station(String token, String name, boolean creator, boolean quickMix, boolean inQuickMix) {

    public Station {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(name, "name");
    }

    public Station withName(String newName) {
        return new Station(token, newName, creator, quickMix, inQuickMix);
    }

    public Station withInQuickMix(boolean member) {
        return new Station(token, name, creator, quickMix, member);
    }

    /** A shared station after {@code transformSharedStation}: now the listener's own. */
    public Station asOwned() {
        return new Station(token, name, true, quickMix, inQuickMix);
    }
}
