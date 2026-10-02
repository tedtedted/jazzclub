package com.tedredington.jazzclub.lastfm;

/** The parts of the Last.fm API jazzclub uses. Every method may throw a {@link LastFmException}. */
interface LastFmClient {

    /** {@code auth.getMobileSession}: trades a password for a session key that does not expire. */
    Session signIn(String user, String password);

    /** {@code track.updateNowPlaying}. */
    void nowPlaying(String sessionKey, Track track);

    /** {@code track.scrobble}. */
    void scrobble(String sessionKey, Scrobble scrobble);

    /** {@code track.love}. Loving a loved track again is harmless. */
    void love(String sessionKey, Track track);

    /** {@code track.unlove}. Harmless for a track that was never loved. */
    void unlove(String sessionKey, Track track);
}
