package com.tedredington.jazzclub.player.ffmpeg;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.PcmFormat;

/** Decodes with an {@code ffmpeg} child process writing raw PCM to its standard output. */
public final class FfmpegDecoder implements Decoder {

    /** ffmpeg's own message when the file ended before the index said it would. */
    static final String TRUNCATED = "partial file";
    private static final int STDERR_KEPT = 16 * 1024;

    private final Process process;
    private final Thread stderrReader;
    private final StringBuilder stderr = new StringBuilder();

    /**
     * stderr is drained while ffmpeg runs, keeping the tail: read only at the end, a chatty ffmpeg
     * would fill the pipe and block.
     */
    private FfmpegDecoder(Process process) {
        this.process = process;
        this.stderrReader = Thread.ofVirtual().start(this::drainStderr);
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
        stderrReader.join(Duration.ofSeconds(1));
        String log;
        synchronized (stderr) {
            log = stderr.toString().strip();
        }
        return failure(process.exitValue(), log);
    }

    /**
     * ffmpeg exits 0 when a file it was decoding stopped short, because the server went away or the
     * file itself is cut off: it plays what it had. Its MP4 reader says so as "partial file", which
     * a connection that drops and resumes never produces.
     *
     * @return {@code null} if the whole song was decoded
     */
    static String failure(int exitValue, String log) {
        if (exitValue == 0) {
            return log.contains(TRUNCATED)
                    ? "Decoding failed: the stream ended early (" + lineWith(log, TRUNCATED) + ")"
                    : null;
        }
        String lastLine = log.isEmpty() ? "exit status " + exitValue : log.substring(log.lastIndexOf('\n') + 1);
        return "Decoding failed: " + lastLine;
    }

    private static String lineWith(String log, String text) {
        return log.lines().filter(line -> line.contains(text)).findFirst().orElse(text).strip();
    }

    private void drainStderr() {
        try (var in = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            for (int n; (n = in.read(buffer)) >= 0; ) {
                synchronized (stderr) {
                    stderr.append(buffer, 0, n);
                    if (stderr.length() > STDERR_KEPT) {
                        stderr.delete(0, stderr.length() - STDERR_KEPT);
                    }
                }
            }
        } catch (IOException e) {
            // ffmpeg is gone; what was read is all there is
        }
    }

    @Override
    public void close() {
        process.destroyForcibly();
    }
}
