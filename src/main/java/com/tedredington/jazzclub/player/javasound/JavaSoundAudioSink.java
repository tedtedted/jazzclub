package com.tedredington.jazzclub.player.javasound;

import java.io.IOException;
import java.time.Duration;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import com.tedredington.jazzclub.player.AudioSink;
import com.tedredington.jazzclub.player.PcmFormat;

/** Plays through the default output device using {@code javax.sound.sampled}. */
public final class JavaSoundAudioSink implements AudioSink {

    private final AudioFormat pcm;

    private volatile SourceDataLine line;
    private volatile FloatControl gainControl;

    public JavaSoundAudioSink(PcmFormat format) {
        this.pcm = new AudioFormat(format.sampleRate(), PcmFormat.BYTES_PER_SAMPLE * 8, PcmFormat.CHANNELS, true,
                false);
    }

    @Override
    public void open() throws IOException {
        try {
            SourceDataLine newLine = AudioSystem.getSourceDataLine(pcm);
            newLine.open(pcm);
            if (newLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                gainControl = (FloatControl) newLine.getControl(FloatControl.Type.MASTER_GAIN);
            }
            newLine.start();
            line = newLine;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            throw new IOException("No audio output device is available: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(byte[] buffer, int offset, int length) {
        line.write(buffer, offset, length);
    }

    @Override
    public void setPaused(boolean paused) {
        SourceDataLine l = line;
        if (l == null) {
            return;
        }
        if (paused) {
            l.stop();
        } else {
            l.start();
        }
    }

    @Override
    public void setGain(double gainDb) {
        FloatControl control = gainControl;
        if (control != null) {
            control.setValue((float) Math.clamp(gainDb, control.getMinimum(), control.getMaximum()));
        }
    }

    @Override
    public void drain() {
        line.drain();
    }

    @Override
    public Duration position() {
        SourceDataLine l = line;
        return l == null ? Duration.ZERO : Duration.ofNanos(l.getMicrosecondPosition() * 1_000);
    }

    @Override
    public void close() {
        SourceDataLine l = line;
        if (l != null) {
            l.stop();
            l.flush();
            l.close();
        }
    }
}
