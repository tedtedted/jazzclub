package com.tedredington.jazzclub.spike;

import java.io.InputStream;
import java.util.ServiceLoader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.spi.MixerProvider;

import org.springframework.stereotype.Component;

/**
 * Proves the audio path: ffmpeg decodes AAC to raw PCM on a pipe, Java owns the
 * output line (so pause, volume and gain are ours). The open question is whether
 * javax.sound works inside a native image.
 */
@Component
class AudioSpike {

    private static final AudioFormat PCM = new AudioFormat(44_100f, 16, 2, true, false);

    void run(String source) throws Exception {
        // A native image has no java.home, but Java Sound insists on one while looking
        // for an optional conf/sound.properties. Any directory satisfies it.
        if (System.getProperty("java.home") == null) {
            System.setProperty("java.home", System.getProperty("java.io.tmpdir"));
        }
        // The JDK finds audio devices via ServiceLoader with a class computed at runtime,
        // which native-image cannot see. This constant-class lookup makes it register them.
        ServiceLoader.load(MixerProvider.class)
                .forEach(p -> System.out.println("mixer provider: " + p.getClass().getName()));
        System.out.println("mixers: " + AudioSystem.getMixerInfo().length);

        Process ffmpeg = new ProcessBuilder("ffmpeg", "-loglevel", "error", "-i", source,
                "-f", "s16le", "-ar", "44100", "-ac", "2", "-")
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

        try (SourceDataLine line = AudioSystem.getSourceDataLine(PCM);
             InputStream pcm = ffmpeg.getInputStream()) {
            line.open(PCM);
            System.out.println("line opened: " + line.getLineInfo());
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                gain.setValue(-20f); // keep the test tone quiet
                System.out.println("gain control ok, set to " + gain.getValue() + " dB");
            }
            line.start();

            byte[] buffer = new byte[8192];
            long total = 0;
            boolean pausedOnce = false;
            int n;
            while ((n = pcm.read(buffer)) > 0) {
                line.write(buffer, 0, n);
                total += n;
                if (!pausedOnce && total > 44_100 * 4) { // after ~1s: pause/resume round trip
                    line.stop();
                    Thread.sleep(300);
                    line.start();
                    pausedOnce = true;
                    System.out.println("pause/resume ok");
                }
            }
            line.drain();
            System.out.println("played " + total + " PCM bytes, ffmpeg exit=" + ffmpeg.waitFor());
        }
    }
}
