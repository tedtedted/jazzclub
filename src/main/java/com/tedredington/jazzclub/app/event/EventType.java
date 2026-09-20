package com.tedredington.jazzclub.app.event;

/** The events of pianobar's {@code event_command} interface that jazzclub emits, under the same names. */
public enum EventType {

    USER_LOGIN("userlogin"),
    USER_GET_STATIONS("usergetstations"),
    STATION_FETCH_PLAYLIST("stationfetchplaylist"),
    STATION_FETCH_GENRE("stationfetchgenre"),
    STATION_CREATE("stationcreate"),
    STATION_ADD_GENRE("stationaddgenre"),
    STATION_ADD_SHARED("stationaddshared"),
    STATION_ADD_MUSIC("stationaddmusic"),
    STATION_RENAME("stationrename"),
    STATION_DELETE("stationdelete"),
    STATION_QUICKMIX_TOGGLE("stationquickmixtoggle"),
    SONG_START("songstart"),
    SONG_FINISH("songfinish"),
    SONG_LOVE("songlove"),
    SONG_BAN("songban"),
    SONG_SHELF("songshelf"),
    SONG_EXPLAIN("songexplain"),
    SONG_BOOKMARK("songbookmark"),
    ARTIST_BOOKMARK("artistbookmark");

    private final String pianobarName;

    EventType(String pianobarName) {
        this.pianobarName = pianobarName;
    }

    /** The first argument an event script is started with. */
    public String pianobarName() {
        return pianobarName;
    }
}
