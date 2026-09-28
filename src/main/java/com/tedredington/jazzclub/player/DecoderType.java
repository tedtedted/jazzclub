package com.tedredington.jazzclub.player;

/** The {@code decoder} setting: what turns Pandora's AAC streams into PCM. */
public enum DecoderType {

    /** In-process: LavaPlayer's MP4 parser and fdk-aac. Needs nothing installed. */
    LAVAPLAYER,

    /** An {@code ffmpeg} child process, as pianobar-style setups had it. Needs ffmpeg on the PATH. */
    FFMPEG
}
