package com.tedredington.jazzclub.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.tedredington.jazzclub.pandora.model.Station;
import org.junit.jupiter.api.Test;

class StationSortTest {

    private static final Station QUICKMIX = new Station("1", "QuickMix", true, true, false);
    private static final Station ABBA = new Station("2", "abba Radio", true, false, false);
    private static final Station BACH = new Station("3", "Bach Radio", true, false, false);
    private static final Station ZAPPA = new Station("4", "Zappa Radio", true, false, false);
    private static final List<Station> STATIONS = List.of(ZAPPA, QUICKMIX, BACH, ABBA);

    private static List<String> sorted(StationSort sort) {
        return STATIONS.stream().sorted(sort.comparator()).map(Station::name).toList();
    }

    @Test
    void byNameIgnoresCaseAndTreatsQuickMixLikeAnyOtherName() {
        assertThat(sorted(StationSort.NAME_AZ)).containsExactly("abba Radio", "Bach Radio", "QuickMix", "Zappa Radio");
        assertThat(sorted(StationSort.NAME_ZA)).containsExactly("Zappa Radio", "QuickMix", "Bach Radio", "abba Radio");
    }

    @Test
    void quickmix01PutsQuickMixLast() {
        assertThat(sorted(StationSort.QUICKMIX_01_NAME_AZ))
                .containsExactly("abba Radio", "Bach Radio", "Zappa Radio", "QuickMix");
        assertThat(sorted(StationSort.QUICKMIX_01_NAME_ZA))
                .containsExactly("Zappa Radio", "Bach Radio", "abba Radio", "QuickMix");
    }

    @Test
    void quickmix10PutsQuickMixFirst() {
        assertThat(sorted(StationSort.QUICKMIX_10_NAME_AZ))
                .containsExactly("QuickMix", "abba Radio", "Bach Radio", "Zappa Radio");
        assertThat(sorted(StationSort.QUICKMIX_10_NAME_ZA))
                .containsExactly("QuickMix", "Zappa Radio", "Bach Radio", "abba Radio");
    }
}
