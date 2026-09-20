package com.tedredington.jazzclub.app;

import java.util.Arrays;
import java.util.Optional;

/**
 * Every key-driven action: pianobar's config key, default key and help text in one place.
 * Dispatch, the {@code ?} help and the {@code act_*} config overrides are all derived from this table.
 */
public enum ActionId {

    // In the order of pianobar's dispatch table, which is also the order of the help screen and
    // decides which action wins when two are bound to the same key.
    HELP("act_help", '?', null, Requires.NOTHING),
    SONG_LOVE("act_songlove", '+', "love song", Requires.SONG),
    SONG_BAN("act_songban", '-', "ban song", Requires.SONG),
    STATION_ADD_MUSIC("act_stationaddmusic", 'a', "add music to station", Requires.STATION),
    STATION_CREATE("act_stationcreate", 'c', "create new station", Requires.NOTHING),
    STATION_DELETE("act_stationdelete", 'd', "delete station", Requires.STATION),
    SONG_EXPLAIN("act_songexplain", 'e', "explain why this song is played", Requires.SONG),
    STATION_ADD_GENRE("act_stationaddbygenre", 'g', "add genre station", Requires.NOTHING),
    SONG_INFO("act_songinfo", 'i', "print information about song/station", Requires.SONG),
    STATION_ADD_SHARED("act_addshared", 'j', "add shared station", Requires.NOTHING),
    SONG_NEXT("act_songnext", 'n', "next song", Requires.STATION),
    SONG_PAUSE_TOGGLE("act_songpausetoggle", 'p', "pause/resume playback", Requires.STATION),
    QUIT("act_quit", 'q', "quit", Requires.NOTHING),
    STATION_RENAME("act_stationrename", 'r', "rename station", Requires.STATION),
    STATION_CHANGE("act_stationchange", 's', "change station", Requires.NOTHING),
    SONG_TIRED("act_songtired", 't', "tired (ban song for 1 month)", Requires.SONG),
    UPCOMING("act_upcoming", 'u', "upcoming songs", Requires.STATION),
    STATION_QUICKMIX("act_stationselectquickmix", 'x', "select quickmix stations", Requires.STATION),
    BOOKMARK("act_bookmark", 'b', "bookmark song/artist", Requires.SONG),
    VOLUME_DOWN("act_voldown", '(', "decrease volume", Requires.NOTHING),
    VOLUME_UP("act_volup", ')', "increase volume", Requires.NOTHING),
    SONG_PAUSE_TOGGLE_2("act_songpausetoggle2", ' ', null, Requires.STATION),
    STATION_CREATE_FROM_SONG("act_stationcreatefromsong", 'v', "create new station from song or artist",
            Requires.SONG),
    SONG_PLAY("act_songplay", 'P', "resume playback", Requires.STATION),
    SONG_PAUSE("act_songpause", 'S', "pause playback", Requires.STATION),
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
