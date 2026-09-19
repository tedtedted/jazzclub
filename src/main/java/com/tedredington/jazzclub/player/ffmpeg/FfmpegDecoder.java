package com.tedredington.jazzclub.player.ffmpeg;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import com.tedredington.jazzclub.player.Decoder;

/** Decodes with an {@code ffmpeg} child process writing raw PCM to its standard output. */
public final class FfmpegDecoder implements Decoder {

    private final Process process;

    private FfmpegDecoder(Process process) {
        this.process = process;
    }

    public static Decoder.Factory factory(String ffmpegExecutable) {
        return audioUrl -> open(ffmpegExecutable, audioUrl);
    }

    private static Decoder open(String ffmpegExecutable, URI audioUrl) throws IOException {
        try {
            return new FfmpegDecoder(new ProcessBuilder(FfmpegCommand.decodeToPcm(ffmpegExecutable, audioUrl)).start());
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
