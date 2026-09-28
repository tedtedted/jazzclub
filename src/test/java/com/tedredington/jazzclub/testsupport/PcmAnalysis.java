package com.tedredington.jazzclub.testsupport;

import com.tedredington.jazzclub.player.PcmFormat;

/**
 * Measurements on decoded s16le stereo, for checking decoders against the synthetic fixtures in
 * {@code fixtures/audio} (made by {@code scripts/make-audio-fixtures.sh}).
 */
public record PcmAnalysis(double[] left, double[] right, int sampleRate) {

    private static final int FFT_SIZE = 2048;

    public static PcmAnalysis of(byte[] s16le, int sampleRate) {
        int frames = s16le.length / (PcmFormat.CHANNELS * PcmFormat.BYTES_PER_SAMPLE);
        double[] left = new double[frames];
        double[] right = new double[frames];
        for (int i = 0; i < frames; i++) {
            int at = i * 4;
            left[i] = (short) ((s16le[at] & 0xff) | (s16le[at + 1] << 8)) / 32768.0;
            right[i] = (short) ((s16le[at + 2] & 0xff) | (s16le[at + 3] << 8)) / 32768.0;
        }
        return new PcmAnalysis(left, right, sampleRate);
    }

    public double seconds() {
        return (double) left.length / sampleRate;
    }

    /**
     * Amplitude of one sine component in dBFS (a full-scale sine is 0 dB), over the middle two
     * thirds so that encoder priming doesn't count.
     */
    public double toneDb(int channel, double frequency) {
        double[] samples = channel == 0 ? left : right;
        int from = samples.length / 6;
        int n = samples.length - 2 * from;
        if (n <= 0) {
            return Double.NEGATIVE_INFINITY;
        }
        double coefficient = 2 * Math.cos(2 * Math.PI * frequency / sampleRate);
        double s1 = 0;
        double s2 = 0;
        for (int i = 0; i < n; i++) {
            double window = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1));
            double s0 = samples[from + i] * window + coefficient * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        double power = s1 * s1 + s2 * s2 - coefficient * s1 * s2;
        // a Hann window halves a sine's amplitude
        double amplitude = 2 * Math.sqrt(Math.max(power, 0)) / (n * 0.5);
        return 20 * Math.log10(Math.max(amplitude, 1e-12));
    }

    /**
     * Mean power density between two frequencies of the left channel in dB, over the middle three
     * quarters. Only differences between bands mean anything; for white noise all bands match.
     */
    public double bandDb(double lowHz, double highHz) {
        int from = left.length / 8;
        int to = left.length - left.length / 8;
        int lowBin = (int) Math.ceil(lowHz * FFT_SIZE / sampleRate);
        int highBin = (int) Math.floor(highHz * FFT_SIZE / sampleRate);
        double[] re = new double[FFT_SIZE];
        double[] im = new double[FFT_SIZE];
        double sum = 0;
        int count = 0;
        for (int start = from; start + FFT_SIZE <= to; start += FFT_SIZE / 2) {
            for (int i = 0; i < FFT_SIZE; i++) {
                re[i] = left[start + i] * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT_SIZE - 1)));
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
}
