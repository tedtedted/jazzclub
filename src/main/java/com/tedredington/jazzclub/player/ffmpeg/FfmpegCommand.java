package com.tedredington.jazzclub.player.ffmpeg;

import java.net.URI;
import java.util.List;

import com.tedredington.jazzclub.player.PcmFormat;

/** Builds the ffmpeg invocation. Separate from process handling so it can be tested. */
public final class FfmpegCommand {

    private FfmpegCommand() {
    }

    /**
     * @throws IllegalArgumentException for anything but http(s): a URL from the network must never make
     *                                  ffmpeg open a local file or another protocol
     */
    public static List<String> decodeToPcm(String ffmpegExecutable, URI audioUrl, PcmFormat format) {
        String scheme = audioUrl.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Invalid song url.");
        }
        return List.of(ffmpegExecutable,
                "-nostdin", "-hide_banner", "-loglevel", "error",
                "-protocol_whitelist", "http,https,tcp,tls,crypto",
                // retry a dropped connection after 0, 1, 2 and 4 s, then give up: ffmpeg's default of
                // 120 s (still in 6.x) stalls a song for two minutes when the server is gone
                "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5",
                "-i", audioUrl.toString(),
                "-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ar", String.valueOf(format.sampleRate()),
                "-ac", String.valueOf(PcmFormat.CHANNELS),
                "pipe:1");
    }
}
