package com.tedredington.jazzclub.player.ffmpeg;

import java.net.URI;
import java.util.List;

/** Builds the ffmpeg invocation. Separate from process handling so it can be tested. */
public final class FfmpegCommand {

    private FfmpegCommand() {
    }

    /**
     * @throws IllegalArgumentException for anything but http(s): a URL from the network must never make
     *                                  ffmpeg open a local file or another protocol
     */
    public static List<String> decodeToPcm(String ffmpegExecutable, URI audioUrl) {
        String scheme = audioUrl.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Invalid song url.");
        }
        return List.of(ffmpegExecutable,
                "-nostdin", "-hide_banner", "-loglevel", "error",
                "-protocol_whitelist", "http,https,tcp,tls,crypto",
                "-reconnect", "1", "-reconnect_streamed", "1",
                "-i", audioUrl.toString(),
                "-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ar", "44100", "-ac", "2",
                "pipe:1");
    }
}
