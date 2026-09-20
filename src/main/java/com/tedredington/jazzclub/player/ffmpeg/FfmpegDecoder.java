package com.tedredington.jazzclub.player.ffmpeg;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.PcmFormat;

/** Decodes with an {@code ffmpeg} child process writing raw PCM to its standard output. */
public final class FfmpegDecoder implements Decoder {

    private final Process process;

    private FfmpegDecoder(Process process) {
        this.process = process;
    }

    /** @param httpProxy value for ffmpeg's {@code http_proxy} environment variable, or {@code null} */
    public static Decoder.Factory factory(String ffmpegExecutable, String httpProxy, PcmFormat format) {
        return audioUrl -> open(ffmpegExecutable, audioUrl, httpProxy, format);
    }

    private static Decoder open(String ffmpegExecutable, URI audioUrl, String httpProxy, PcmFormat format)
            throws IOException {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    FfmpegCommand.decodeToPcm(ffmpegExecutable, audioUrl, format));
            if (httpProxy != null) {
                // ffmpeg has no option for this; the environment variable is what it reads
                builder.environment().put("http_proxy", httpProxy);
            }
            return new FfmpegDecoder(builder.start());
        } catch (IOException e) {
            throw new IOException("Could not start '" + ffmpegExecutable + "'. jazzclub needs ffmpeg to decode audio: "
                    + "install it with your package manager (brew install ffmpeg, pacman -S ffmpeg, "
                    + "apt install ffmpeg).", e);
        }
    }

    @Override
    public InputStream pcm() {
        return process.getInputStream();
    }

    @Override
    public String failure() throws InterruptedException {
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            return "ffmpeg did not exit.";
        }
        if (process.exitValue() == 0) {
            return null;
        }
        String stderr;
        try {
            stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            stderr = "";
        }
        String lastLine = stderr.isEmpty() ? "exit status " + process.exitValue()
                : stderr.substring(stderr.lastIndexOf('\n') + 1);
        return "Decoding failed: " + lastLine;
    }

    @Override
    public void close() {
        process.destroyForcibly();
    }
}
