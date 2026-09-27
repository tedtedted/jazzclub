package com.tedredington.jazzclub.player.eval;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.tedredington.jazzclub.player.PcmFormat;

/** Decoded stereo s16le audio, with the few measurements the evaluation needs. */
record Pcm(double[] left, double[] right, int sampleRate) {

    static Pcm fromS16le(byte[] bytes, int sampleRate) {
        int frames = bytes.length / (PcmFormat.CHANNELS * PcmFormat.BYTES_PER_SAMPLE);
        double[] left = new double[frames];
        double[] right = new double[frames];
        for (int i = 0; i < frames; i++) {
            int at = i * 4;
            left[i] = (short) ((bytes[at] & 0xff) | (bytes[at + 1] << 8)) / 32768.0;
            right[i] = (short) ((bytes[at + 2] & 0xff) | (bytes[at + 3] << 8)) / 32768.0;
        }
        return new Pcm(left, right, sampleRate);
    }

    int frames() {
        return left.length;
    }

    double seconds() {
        return (double) frames() / sampleRate;
    }

    double[] channel(int index) {
        return index == 0 ? left : right;
    }

    /**
     * Amplitude of one sine component, in dBFS, measured over the middle of the audio so that
     * encoder priming and fade-out don't count. A full-scale sine is 0 dB; the fixtures use -12 dB.
     */
    double toneDb(int channel, double frequency) {
        double[] samples = channel(channel);
        int from = frames() / 6;
        int to = frames() - frames() / 6;
        return goertzelDb(samples, from, to, frequency);
    }

    private double goertzelDb(double[] samples, int from, int to, double frequency) {
        int n = to - from;
        if (n <= 0) {
            return Double.NEGATIVE_INFINITY;
        }
        double coefficient = 2 * Math.cos(2 * Math.PI * frequency / sampleRate);
        double s1 = 0;
        double s2 = 0;
        for (int i = from; i < to; i++) {
            // Hann window, so a strong neighbouring tone doesn't leak into this one
            double window = 0.5 - 0.5 * Math.cos(2 * Math.PI * (i - from) / (n - 1));
            double s0 = samples[i] * window + coefficient * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        double power = s1 * s1 + s2 * s2 - coefficient * s1 * s2;
        // the Hann window halves a sine's amplitude
        double amplitude = 2 * Math.sqrt(Math.max(power, 0)) / (n * 0.5);
        return 20 * Math.log10(Math.max(amplitude, 1e-12));
    }

    private static final int FFT_SIZE = 2048;
    private static final int MAX_WINDOWS = Integer.getInteger("eval.maxWindows", 400);
    private static final double[] HANN = new double[FFT_SIZE];
    private static final double[] COS = new double[FFT_SIZE / 2];
    private static final double[] SIN = new double[FFT_SIZE / 2];
    // weak, so a finished track's samples can be collected (arrays hash by identity)
    private static final Map<double[], double[]> SPECTRA = Collections.synchronizedMap(new WeakHashMap<>());

    static {
        for (int i = 0; i < FFT_SIZE; i++) {
            HANN[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT_SIZE - 1));
        }
        for (int i = 0; i < FFT_SIZE / 2; i++) {
            COS[i] = Math.cos(-2 * Math.PI * i / FFT_SIZE);
            SIN[i] = Math.sin(-2 * Math.PI * i / FFT_SIZE);
        }
    }

    /**
     * Mean power density between two frequencies, in dB, over the middle three quarters of the
     * audio (Welch's method with Hann-windowed 2048-point FFTs; at most 400 windows, spread evenly,
     * so a whole song stays quick). Only differences between bands mean anything; for white noise
     * every band should come out the same.
     */
    double bandDb(int channel, double lowHz, double highHz) {
        double[] spectrum = SPECTRA.computeIfAbsent(channel(channel), this::spectrum);
        int lowBin = (int) Math.ceil(lowHz * FFT_SIZE / sampleRate);
        int highBin = Math.min((int) Math.floor(highHz * FFT_SIZE / sampleRate), spectrum.length - 1);
        double sum = 0;
        int count = 0;
        for (int bin = lowBin; bin <= highBin; bin++) {
            sum += spectrum[bin];
            count++;
        }
        return count == 0 ? Double.NEGATIVE_INFINITY : 10 * Math.log10(Math.max(sum / count, 1e-30));
    }

    /** Average power per FFT bin, the part of {@link #bandDb} worth computing only once. */
    private double[] spectrum(double[] samples) {
        double[] power = new double[FFT_SIZE / 2 + 1];
        int from = samples.length / 8;
        int to = samples.length - samples.length / 8 - FFT_SIZE;
        if (to <= from) {
            return power;
        }
        int windows = Math.min(MAX_WINDOWS, (to - from) / (FFT_SIZE / 2) + 1);
        double step = windows == 1 ? 0 : (double) (to - from) / (windows - 1);
        double[] re = new double[FFT_SIZE];
        double[] im = new double[FFT_SIZE];
        for (int w = 0; w < windows; w++) {
            int start = from + (int) (w * step);
            for (int i = 0; i < FFT_SIZE; i++) {
                re[i] = samples[start + i] * HANN[i];
                im[i] = 0;
            }
            fft(re, im);
            for (int bin = 0; bin < power.length; bin++) {
                power[bin] += (re[bin] * re[bin] + im[bin] * im[bin]) / windows;
            }
        }
        return power;
    }

    /** Correlation of left and right over the whole track: 1 is mono, near 0 is wide stereo. */
    double stereoCorrelation() {
        double lr = 0;
        double ll = 0;
        double rr = 0;
        for (int i = 0; i < frames(); i++) {
            lr += left[i] * right[i];
            ll += left[i] * left[i];
            rr += right[i] * right[i];
        }
        return ll == 0 || rr == 0 ? 0 : lr / Math.sqrt(ll * rr);
    }

    /** Third-octave band centres from 125 Hz to 16 kHz. */
    static final double[] THIRD_OCTAVES = java.util.stream.IntStream.rangeClosed(0, 21)
            .mapToDouble(i -> 125 * Math.pow(2, i / 3.0)).toArray();

    /**
     * Largest difference between this decode's third-octave spectrum and a reference decode's, left
     * channel, as {@code {band centre, difference in dB}}, over bands within 60 dB of the loudest. Used instead of a sample-by-sample null
     * test: fdk-aac and FFmpeg synthesise HE-AAC's SBR band differently (different delay and phase),
     * so their samples don't line up even when both decode correctly, but their spectra do.
     */
    double[] worstBandDifference(Pcm reference) {
        double[] levels = new double[THIRD_OCTAVES.length];
        double loudest = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < levels.length; i++) {
            levels[i] = reference.bandDb(0, lowEdge(THIRD_OCTAVES[i]), highEdge(THIRD_OCTAVES[i]));
            loudest = Math.max(loudest, levels[i]);
        }
        double worst = 0;
        double at = 0;
        for (int i = 0; i < levels.length; i++) {
            // near-silent bands give large dB differences that nobody could hear
            if (levels[i] < loudest - 60) {
                continue;
            }
            double difference = bandDb(0, lowEdge(THIRD_OCTAVES[i]), highEdge(THIRD_OCTAVES[i])) - levels[i];
            if (Math.abs(difference) > Math.abs(worst)) {
                worst = difference;
                at = THIRD_OCTAVES[i];
            }
        }
        return new double[] {at, worst};
    }

    private static double lowEdge(double centre) {
        return centre / Math.pow(2, 1 / 6.0);
    }

    private static double highEdge(double centre) {
        return centre * Math.pow(2, 1 / 6.0);
    }

    /** In-place radix-2 FFT of {@link #FFT_SIZE} points. */
    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int length = 2; length <= n; length <<= 1) {
            int stride = n / length;
            for (int i = 0; i < n; i += length) {
                for (int k = 0; k < length / 2; k++) {
                    double wr = COS[k * stride];
                    double wi = SIN[k * stride];
                    int a = i + k;
                    int b = a + length / 2;
                    double xr = re[b] * wr - im[b] * wi;
                    double xi = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                }
            }
        }
    }
}
