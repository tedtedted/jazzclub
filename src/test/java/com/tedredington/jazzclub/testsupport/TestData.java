package com.tedredington.jazzclub.testsupport;

import java.net.URI;
import java.time.Duration;

import com.tedredington.jazzclub.config.JazzclubProperties;
import com.tedredington.jazzclub.pandora.model.AudioEncoding;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;

public final class TestData {

    public static final Station QUICKMIX = new Station("100", "QuickMix", true, true, false);
    public static final Station EVANS = new Station("200", "Bill Evans Radio", true, false, true);
    public static final Station HARD_BOP = new Station("300", "Hard Bop Radio", false, false, false);

    /** pianobar's default formats. */
    public static final JazzclubProperties.Format FORMAT = new JazzclubProperties.Format(
            "\"%t\" by \"%a\" on \"%l\"%r%@%s", "Station \"%n\" (%i)", "%i) %a - %t%r", "%s%r/%t",
            " <3", " </3", " zZ", " @ ");

    private TestData() {
    }

    public static Song song(String title, String stationId) {
        return new Song(title, "Artist of " + title, "Album of " + title, "token-" + title, stationId,
                URI.create("https://audio.example/" + title.replace(' ', '-') + ".m4a"), AudioEncoding.AAC_PLUS,
                null, null, -2.5, Duration.ofSeconds(185), Rating.NONE);
    }
}
