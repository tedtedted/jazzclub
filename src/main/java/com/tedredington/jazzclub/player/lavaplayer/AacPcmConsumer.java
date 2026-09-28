package com.tedredington.jazzclub.player.lavaplayer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.ReadableByteChannel;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegTrackConsumer;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegTrackInfo;
import com.sedmelluq.discord.lavaplayer.natives.aac.AacDecoder;
import com.tedredington.jazzclub.player.PcmFormat;

/**
 * Receives one MP4 track's AAC packets from LavaPlayer's parser, decodes them with fdk-aac and
 * writes jazzclub's PCM (s16le stereo) to a stream. Stands in for LavaPlayer's own consumer, which
 * would pull in its whole playback pipeline.
 */
final class AacPcmConsumer implements MpegTrackConsumer {

    private static final int FLUSH_FRAMES = 2;

    private final MpegTrackInfo track;
    private final PcmFormat format;
    private final OutputStream out;
    private final ByteBuffer input = ByteBuffer.allocateDirect(4096);
    private AacDecoder decoder;
    private ShortBuffer decoded;
    private byte[] pcm;
    private int channels;
    private int frameSize;

    AacPcmConsumer(MpegTrackInfo track, PcmFormat format, OutputStream out) {
        this.track = track;
        this.format = format;
        this.out = out;
    }

    /** Whether LavaPlayer's parser found a track this consumer can decode. */
    static boolean canDecode(MpegTrackInfo track) {
        return "soun".equals(track.handler) && "mp4a".equals(track.codecName);
    }

    @Override
    public MpegTrackInfo getTrack() {
        return track;
    }

    @Override
    public void initialise() {
        decoder = new AacDecoder();
        int error = track.decoderConfig != null
                ? decoder.configure(track.decoderConfig)
                : decoder.configure(AacDecoder.AAC_LC, track.sampleRate, track.channelCount);
        if (error != 0) {
            throw new IllegalStateException("The AAC decoder rejected the track's configuration (error " + error
                    + ")");
        }
    }

    @Override
    public void seekPerformed(long requestedTimecode, long providedTimecode) {
        // jazzclub never seeks within a song
    }

    @Override
    public void consume(ReadableByteChannel channel, int length) throws InterruptedException {
        int remaining = length;
        while (remaining > 0) {
            input.clear();
            input.limit(Math.min(remaining, input.capacity()));
            try {
                while (input.hasRemaining()) {
                    if (channel.read(input) < 0) {
                        throw new IOException("The file ended inside an audio frame");
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            remaining -= input.position();
            input.flip();
            // fill() may take only part of the buffer; decoding frees room for the rest
            while (input.hasRemaining()) {
                int taken = decoder.fill(input);
                if (!drain() && taken == 0) {
                    throw new IllegalStateException("The AAC decoder accepts no more input and produces no audio");
                }
            }
        }
    }

    /**
     * Collects the frames the decoder still holds back (SBR delays its output by about a frame).
     * Bounded, because fdk-aac keeps producing concealment frames for as long as it is asked to.
     */
    @Override
    public void flush() {
        if (decoded == null && !prepareOutput()) {
            return;
        }
        for (int i = 0; i < FLUSH_FRAMES && decoder.decode(decoded, true); i++) {
            write();
        }
    }

    @Override
    public void close() {
        if (decoder != null) {
            decoder.close();
        }
    }

    /** @return whether any audio came out */
    private boolean drain() {
        if (decoded == null && !prepareOutput()) {
            return false;
        }
        boolean produced = false;
        while (decoder.decode(decoded, false)) {
            write();
            produced = true;
        }
        return produced;
    }

    /** The decoder knows the real output (SBR doubles the rate) only after the first frames. */
    private boolean prepareOutput() {
        AacDecoder.StreamInfo info = decoder.resolveStreamInfo();
        if (info == null) {
            return false;
        }
        if (info.sampleRate != format.sampleRate()) {
            throw new IllegalStateException("The stream is " + info.sampleRate + " Hz but sample_rate is "
                    + format.sampleRate() + "; set decoder = ffmpeg to resample");
        }
        if (info.channels < 1 || info.channels > PcmFormat.CHANNELS) {
            throw new IllegalStateException("Unsupported channel count " + info.channels);
        }
        channels = info.channels;
        frameSize = info.frameSize;
        decoded = ByteBuffer.allocateDirect(2 * info.frameSize * info.channels).order(ByteOrder.nativeOrder())
                .asShortBuffer();
        pcm = new byte[info.frameSize * PcmFormat.CHANNELS * PcmFormat.BYTES_PER_SAMPLE];
        return true;
    }

    /**
     * One decoded frame, interleaved native-order shorts from index 0 (the decoder ignores the
     * buffer's position), to s16le stereo; mono is duplicated to both channels.
     */
    private void write() {
        int at = 0;
        for (int i = 0; i < frameSize; i++) {
            short left = decoded.get(i * channels);
            short right = channels == 2 ? decoded.get(i * channels + 1) : left;
            pcm[at++] = (byte) left;
            pcm[at++] = (byte) (left >> 8);
            pcm[at++] = (byte) right;
            pcm[at++] = (byte) (right >> 8);
        }
        try {
            out.write(pcm, 0, at);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
