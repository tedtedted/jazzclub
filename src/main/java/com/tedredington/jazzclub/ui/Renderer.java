package com.tedredington.jazzclub.ui;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.tedredington.jazzclub.config.JazzclubProperties;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;

/** Turns domain objects into the lines pianobar prints, honouring the user's {@code format_*} settings. */
public final class Renderer {

    private final JazzclubProperties.Format format;

    public Renderer(JazzclubProperties.Format format) {
        this.format = format;
    }

    /** @param realStation where the song came from when playing QuickMix, otherwise {@code null} */
    public String nowPlayingSong(Song song, Station realStation) {
        Map<Character, String> values = new HashMap<>();
        values.put('t', song.title());
        values.put('a', song.artist());
        values.put('l', song.album());
        values.put('r', ratingIcon(song));
        values.put('@', realStation != null ? format.atIcon() : "");
        values.put('s', realStation != null ? realStation.name() : "");
        values.put('u', song.detailUrl() != null ? song.detailUrl().toString() : "");
        return CustomFormat.render(format.nowplayingSong(), values);
    }

    public String nowPlayingStation(Station station) {
        return CustomFormat.render(format.nowplayingStation(), Map.of('n', station.name(), 'i', station.token()));
    }

    /** @param otherStation name of the song's station if it is not the one playing, otherwise {@code null} */
    public String listSong(int index, Song song, String otherStation) {
        Map<Character, String> values = new HashMap<>();
        values.put('i', "%2d".formatted(index));
        values.put('a', song.artist());
        values.put('t', song.title());
        values.put('r', ratingIcon(song));
        values.put('d', song.length().isZero() ? "??:??" : minutesSeconds(song.length()));
        values.put('@', otherStation != null ? format.atIcon() : "");
        values.put('s', otherStation != null ? otherStation : "");
        return CustomFormat.render(format.listSong(), values);
    }

    public String time(Duration elapsed, Duration total) {
        boolean overrun = elapsed.compareTo(total) > 0;
        Duration remaining = overrun ? elapsed.minus(total) : total.minus(elapsed);
        return CustomFormat.render(format.time(), Map.of(
                't', minutesSeconds(total),
                'r', minutesSeconds(remaining),
                'e', minutesSeconds(elapsed),
                's', overrun ? "+" : "-"));
    }

    /** One line of the station picker: index, QuickMix member, QuickMix itself, shared. */
    public String stationListEntry(int index, Station station) {
        return "%2d) %c%c%c %s".formatted(index,
                station.inQuickMix() ? 'q' : ' ',
                station.quickMix() ? 'Q' : ' ',
                station.creator() ? ' ' : 'S',
                station.name());
    }

    private String ratingIcon(Song song) {
        return ratingIcon(song.rating());
    }

    /** The user's {@code love_icon}, {@code ban_icon} or {@code tired_icon}; empty for unrated. */
    public String ratingIcon(Rating rating) {
        return switch (rating) {
            case LOVE -> format.loveIcon();
            case BAN -> format.banIcon();
            case TIRED -> format.tiredIcon();
            case NONE -> "";
        };
    }

    private static String minutesSeconds(Duration duration) {
        long seconds = duration.toSeconds();
        return "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }
}
