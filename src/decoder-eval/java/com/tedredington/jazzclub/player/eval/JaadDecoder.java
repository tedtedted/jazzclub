package com.tedredington.jazzclub.player.eval;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.PcmFormat;

/**
 * JAAD candidate, the "Java Sound + AAC" route: we fetch the stream with the JDK's HttpClient (as
 * jazzclub would, so proxy and TLS settings stay ours) and hand it to {@link AudioSystem}, where
 * vavi-sound-aac's Java Sound plugin recognises and decodes it.
 */
final class JaadDecoder implements Decoder {

    /**
     * vavi-sound-aac parses the MP4 by marking the stream and reading ahead up to this much
     * ({@code net.sourceforge.jaad.bufferSize}, 20 MiB by default); a smaller mark limit makes
     * detection fail. Pandora tracks are 2-8 MB, so the whole file may end up buffered.
     */
    static final int MARK_LIMIT = 20 * 1024 * 1024;

    private final InputStream body;
    private final CapturingStream pcm;

    private JaadDecoder(InputStream body, AudioInputStream pcm) {
        this.body = body;
        this.pcm = new CapturingStream(pcm);
    }

    static Decoder.Factory factory(PcmFormat format) {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        return audioUrl -> open(http, audioUrl, format);
    }

    private static Decoder open(HttpClient http, URI audioUrl, PcmFormat format) throws IOException {
        HttpResponse<InputStream> response;
        try {
            response = http.send(HttpRequest.newBuilder(audioUrl).build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException();
        }
        InputStream body = response.body();
        if (response.statusCode() != 200) {
            body.close();
            throw new IOException("HTTP " + response.statusCode());
        }
        try {
            InputStream buffered = new BufferedInputStream(new FullReads(body), MARK_LIMIT);
            AudioInputStream aac = AudioSystem.getAudioInputStream(buffered);
            // Asking for 44.1 kHz little-endian directly fails: for HE-AAC the plugin reports the
            // core's 22.05 kHz (SBR doubles it only while decoding) and it only writes big-endian.
            AudioInputStream pcm = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, aac);
            if (pcm.getFormat().getSampleRate() != format.sampleRate()
                    || pcm.getFormat().getChannels() != PcmFormat.CHANNELS) {
                body.close();
                throw new IOException("JAAD decodes to " + pcm.getFormat() + ", not " + format.sampleRate()
                        + " Hz stereo");
            }
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.sampleRate(),
                    PcmFormat.BYTES_PER_SAMPLE * 8, PcmFormat.CHANNELS,
                    PcmFormat.CHANNELS * PcmFormat.BYTES_PER_SAMPLE, format.sampleRate(), false);
            return new JaadDecoder(body, AudioSystem.getAudioInputStream(target, pcm));
        } catch (UnsupportedAudioFileException | IllegalArgumentException e) {
            body.close();
            throw new IOException("Java Sound cannot decode the stream: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream pcm() {
        return pcm;
    }

    @Override
    public String failure() {
        return pcm.failure;
    }

    @Override
    public void close() {
        try {
            body.close();
        } catch (IOException e) {
            // closing anyway
        }
    }

    /**
     * Makes every read wait for at least 32 KiB (or the end), roughly as a file read does. The
     * plugin detects the format by parsing only {@code in.available()} bytes, i.e. whatever the
     * network happened to deliver so far, and reports "Stream of unsupported format" if the MP4
     * header isn't all there yet (first spike run: every throttled stream failed). 32 KiB is a
     * guess that covers the header of a few-minute track; it is inherently racy. Waiting for the
     * whole request instead would download the entire file before the first sound, because
     * BufferedInputStream asks for all 20 MiB of its buffer at once.
     */
    private static final class FullReads extends FilterInputStream {

        FullReads(InputStream in) {
            super(in);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int want = Math.min(length, 32 * 1024);
            int n = in.readNBytes(buffer, offset, want);
            if (n == want && length > want) {
                // whatever else has already arrived, without waiting for it
                int more = in.read(buffer, offset + n, Math.min(length - n, Math.max(in.available(), 0)));
                n += Math.max(more, 0);
            }
            return n == 0 && length > 0 ? -1 : n;
        }
    }

    /** Decoding happens inside read(), so that is where decode errors show up. */
    private static final class CapturingStream extends FilterInputStream {
        volatile String failure;

        CapturingStream(InputStream in) {
            super(in);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            try {
                return super.read(buffer, offset, length);
            } catch (IOException | RuntimeException e) {
                failure = "Decoding failed: " + e;
                return -1;
            }
        }
    }
}
