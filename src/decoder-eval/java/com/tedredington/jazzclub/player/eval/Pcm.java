package com.tedredington.jazzclub.player.eval;

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

    /**
     * Mean power density between two frequencies, in dB, over the middle of the audio (Welch's
     * method: Hann-windowed 2048-point FFTs, half overlapping). Only differences between bands mean
     * anything; for white noise every band should come out the same.
     */
    double bandDb(int channel, double lowHz, double highHz) {
        int size = 2048;
        double[] samples = channel(channel);
        int from = frames() / 8;
        int to = frames() - frames() / 8;
        int lowBin = (int) Math.ceil(lowHz * size / sampleRate);
        int highBin = (int) Math.floor(highHz * size / sampleRate);
        double sum = 0;
        int count = 0;
        double[] re = new double[size];
        double[] im = new double[size];
        for (int start = from; start + size <= to; start += size / 2) {
            for (int i = 0; i < size; i++) {
                re[i] = samples[start + i] * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (size - 1)));
                im[i] = 0;
            }
            fft(re, im);
            for (int bin = lowBin; bin <= highBin; bin++) {
                sum += re[bin] * re[bin] + im[bin] * im[bin];
                count++;
            }
        }
        return count == 0 ? Double.NEGATIVE_INFINITY : 10 * Math.log10(Math.max(sum / count, 1e-30));
    }

    /** In-place radix-2 FFT; the length must be a power of two. */
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
            double angle = -2 * Math.PI / length;
            for (int i = 0; i < n; i += length) {
                for (int k = 0; k < length / 2; k++) {
                    double wr = Math.cos(angle * k);
                    double wi = Math.sin(angle * k);
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
}
