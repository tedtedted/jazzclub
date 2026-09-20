package com.tedredington.jazzclub.eventcmd;

import com.tedredington.jazzclub.app.event.PlayerEvent;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;

/**
 * Renders an event the way pianobar writes it to an event script's standard input: {@code key=value}
 * lines in a fixed order. Scripts written for pianobar, such as its scrobbler example, parse exactly this.
 */
public final class EventCommandFormatter {

    private EventCommandFormatter() {
    }

    public static String format(PlayerEvent event) {
        StringBuilder out = new StringBuilder(512);
        line(out, "stationName", event.station() == null ? "" : event.station().name());
        line(out, "songStationName", event.songStation() == null ? "" : event.songStation().name());
        line(out, "pRet", event.result().pandoraCode());
        line(out, "pRetStr", event.result().pandoraMessage());
        line(out, "wRet", event.result().networkCode());
        line(out, "wRetStr", event.result().networkMessage());
        line(out, "songPlayed", event.played().toSeconds());

        if (event.song() != null) {
            song(out, event.song(), "");
        }
        int next = 0;
        for (Song upcoming : event.upcoming()) {
            song(out, upcoming, "Next" + next++);
        }

        line(out, "stationCount", event.stations().size());
        int index = 0;
        for (Station station : event.stations()) {
            line(out, "station" + index++, station.name());
        }
        return out.toString();
    }

    private static void song(StringBuilder out, Song song, String postfix) {
        line(out, "artist" + postfix, song.artist());
        line(out, "title" + postfix, song.title());
        line(out, "album" + postfix, song.album());
        line(out, "coverArt" + postfix, song.coverArtUrl());
        // pianobar's PianoSongRating_t: none, love, ban, tired
        line(out, "rating" + postfix, song.rating().ordinal());
        line(out, "detailUrl" + postfix, song.detailUrl());
        line(out, "songDuration" + postfix, song.length().toSeconds());
    }

    private static void line(StringBuilder out, String key, Object value) {
        // one line per value, or a script's "split on newline" parser would go wrong
        String text = value == null ? "" : value.toString().replace('\n', ' ').replace('\r', ' ');
        out.append(key).append('=').append(text).append('\n');
    }
}
