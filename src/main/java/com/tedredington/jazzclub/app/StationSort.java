package com.tedredington.jazzclub.app;

import java.util.Comparator;

import com.tedredington.jazzclub.pandora.model.Station;

/**
 * pianobar's {@code sort} setting: by name, optionally with the QuickMix station pinned to the end
 * ({@code quickmix_01_*}) or the start ({@code quickmix_10_*}) of the list. The order decides the
 * numbers in the station menu, so it is shared by everything that lists stations.
 */
public enum StationSort {

    NAME_AZ(false, 0),
    NAME_ZA(true, 0),
    QUICKMIX_01_NAME_AZ(false, 1),
    QUICKMIX_01_NAME_ZA(true, 1),
    QUICKMIX_10_NAME_AZ(false, -1),
    QUICKMIX_10_NAME_ZA(true, -1);

    private final Comparator<Station> comparator;

    /** @param quickMixWeight where a QuickMix station goes relative to the others: after, nowhere special, before */
    StationSort(boolean descending, int quickMixWeight) {
        Comparator<Station> byName = Comparator.comparing(Station::name, String.CASE_INSENSITIVE_ORDER);
        Comparator<Station> byQuickMix = Comparator.comparingInt(s -> s.quickMix() ? quickMixWeight : 0);
        this.comparator = byQuickMix.thenComparing(descending ? byName.reversed() : byName);
    }

    public Comparator<Station> comparator() {
        return comparator;
    }
}
