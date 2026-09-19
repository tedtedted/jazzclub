package com.tedredington.jazzclub.app;

import java.util.Arrays;
import java.util.Optional;

/**
 * Every key-driven action: pianobar's config key, default key and help text in one place.
 * Dispatch, the {@code ?} help and the {@code act_*} config overrides are all derived from this table.
 */
public enum ActionId {

    HELP("act_help", '?', null, Requires.NOTHING),
    SONG_LOVE("act_songlove", '+', "love song", Requires.SONG),
    SONG_BAN("act_songban", '-', "ban song", Requires.SONG),
    SONG_EXPLAIN("act_songexplain", 'e', "explain why this song is played", Requires.SONG),
    SONG_INFO("act_songinfo", 'i', "print information about song/station", Requires.SONG),
    SONG_NEXT("act_songnext", 'n', "next song", Requires.STATION),
    SONG_PAUSE_TOGGLE("act_songpausetoggle", 'p', "pause/resume playback", Requires.STATION),
    SONG_PAUSE_TOGGLE_2("act_songpausetoggle2", ' ', null, Requires.STATION),
    SONG_PLAY("act_songplay", 'P', "resume playback", Requires.STATION),
    SONG_PAUSE("act_songpause", 'S', "pause playback", Requires.STATION),
    QUIT("act_quit", 'q', "quit", Requires.NOTHING),
    STATION_CHANGE("act_stationchange", 's', "change station", Requires.NOTHING),
    SONG_TIRED("act_songtired", 't', "tired (ban song for 1 month)", Requires.SONG),
    UPCOMING("act_upcoming", 'u', "upcoming songs", Requires.STATION),
    VOLUME_DOWN("act_voldown", '(', "decrease volume", Requires.NOTHING),
    VOLUME_UP("act_volup", ')', "increase volume", Requires.NOTHING),
    VOLUME_RESET("act_volreset", '^', "reset volume", Requires.NOTHING);

    /** What must be selected for the action to make sense; otherwise the key press is ignored. */
    public enum Requires {
        NOTHING, STATION, SONG
    }

    private final String configKey;
    private final char defaultKey;
    private final String helpText;
    private final Requires requires;

    ActionId(String configKey, char defaultKey, String helpText, Requires requires) {
        this.configKey = configKey;
        this.defaultKey = defaultKey;
        this.helpText = helpText;
        this.requires = requires;
    }

    public static Optional<ActionId> fromConfigKey(String configKey) {
        return Arrays.stream(values()).filter(a -> a.configKey.equals(configKey)).findFirst();
    }

    public String configKey() {
        return configKey;
    }

    public char defaultKey() {
        return defaultKey;
    }

    /** @return the line for the help screen, or {@code null} for actions pianobar does not list */
    public String helpText() {
        return helpText;
    }

    public Requires requires() {
        return requires;
    }
}
