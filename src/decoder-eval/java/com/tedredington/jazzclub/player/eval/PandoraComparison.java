package com.tedredington.jazzclub.player.eval;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * F1 in issue #9: decodes real Pandora tracks with every candidate and compares each with ffmpeg.
 * Reads the file jazzclub writes when {@code JAZZCLUB_SPIKE_URL_LOG} is set ({@code <time> <encoding>
 * <url>} per line) and writes a Markdown report that names tracks by number only: the URLs grant
 * access to the audio and must not end up in the report, the PR or a log.
 *
 * <p>Run it with {@code scripts/decoder-eval-pandora.sh <url-file>}.
 */
public final class PandoraComparison {

    private static final int[][] BANDS = {{4000, 8000}, {8000, 11000}, {11000, 13000}, {13000, 15000},
            {15000, 16000}};

    private PandoraComparison() {
    }

    public static void main(String[] args) throws Exception {
        Path urlFile = Path.of(args[0]);
        Path report = Path.of(args.length > 1 ? args[1] : "target/decoder-eval/pandora/report.md");
        List<String> lines = Files.readAllLines(urlFile).stream().filter(l -> !l.isBlank()).toList();

        StringBuilder probes = new StringBuilder("| Track | Pandora says | ffprobe: profile, rate, channels, bit rate | MP4 index |\n|---|---|---|---|\n");
        StringBuilder decodes = new StringBuilder("| Track | Candidate | Result | Length | First PCM | Bands vs 1-4 kHz (4-8k, 8-11k, 11-13k, 13-15k, 15-16k) | L/R correlation | Largest third-octave difference from ffmpeg |\n|---|---|---|---|---|---|---|---|\n");
        int track = 0;
        for (String line : lines) {
            track++;
            String[] parts = line.split(" ", 3);
            if (parts.length < 3) {
                continue;
            }
            String encoding = parts[1];
            URI url = URI.create(parts[2].strip());
            System.out.println("track " + track + " (" + encoding + ")");
            probes.append("| ").append(track).append(" | ").append(encoding).append(" | ").append(probe(url))
                    .append(" | ").append(indexPosition(url)).append(" |\n");

            Map<Candidate, DecodeRun> runs = new EnumMap<>(Candidate.class);
            for (Candidate candidate : Candidate.values()) {
                runs.put(candidate, DecodeRun.of(candidate.factory(), url, 180));
            }
            Pcm reference = runs.get(Candidate.FFMPEG).succeeded() ? runs.get(Candidate.FFMPEG).pcm() : null;
            for (Candidate candidate : Candidate.values()) {
                decodes.append(row(track, candidate, runs.get(candidate), reference)).append('\n');
            }
        }
        // error messages from ffmpeg, ffprobe and LavaPlayer can quote the URL
        String markdown = withoutUrls("## Pandora streams\n\n" + probes + "\n## Decodes\n\n" + decodes);
        Files.createDirectories(report.getParent());
        Files.writeString(report, markdown);
        System.out.println(markdown);
        System.exit(0);
    }

    private static String row(int track, Candidate candidate, DecodeRun run, Pcm reference) {
        List<String> cells = new ArrayList<>(List.of(String.valueOf(track), candidate.name().toLowerCase()));
        if (!run.succeeded()) {
            cells.add(run.hung() ? "hung" : "error: " + run.reportedError().replace("|", "\\|"));
            cells.addAll(List.of("", "", "", "", ""));
        } else {
            Pcm pcm = run.pcm();
            double ref = pcm.bandDb(0, 1000, 4000);
            StringBuilder bands = new StringBuilder();
            for (int[] band : BANDS) {
                bands.append(bands.isEmpty() ? "" : ", ").append(f("%+.1f", pcm.bandDb(0, band[0], band[1]) - ref));
            }
            cells.add("ok");
            cells.add(f("%.2f s", pcm.seconds()));
            cells.add(run.firstByteMs() + " ms");
            cells.add(bands.toString());
            cells.add(f("%.3f", pcm.stereoCorrelation()));
            if (reference == null) {
                cells.add("no reference");
            } else if (candidate == Candidate.FFMPEG) {
                cells.add("(reference)");
            } else {
                double[] worst = pcm.worstBandDifference(reference);
                cells.add(f("%+.1f dB at %.0f Hz", worst[1], worst[0]));
            }
        }
        return "| " + String.join(" | ", cells) + " |";
    }

    private static String probe(URI url) throws IOException, InterruptedException {
        String out = run("ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries",
                "stream=profile,sample_rate,channels,bit_rate", "-of", "csv=p=0", url.toString());
        return out.isBlank() ? "(ffprobe failed)" : out.strip().replace(",", ", ");
    }

    /** Whether the MP4 index (moov) comes before the audio (mdat), which streaming needs. */
    private static String indexPosition(URI url) throws IOException, InterruptedException {
        String trace = run("ffprobe", "-v", "trace", url.toString());
        int moov = trace.indexOf("type:'moov' parent:'root'");
        int mdat = trace.indexOf("type:'mdat' parent:'root'");
        if (moov < 0 && mdat < 0) {
            return "not MP4";
        }
        return mdat < 0 || (moov >= 0 && moov < mdat) ? "before audio" : "after audio";
    }

    private static String run(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        process.waitFor(60, TimeUnit.SECONDS);
        return new String(output, StandardCharsets.UTF_8);
    }

    static String withoutUrls(String text) {
        return text.replaceAll("(?i)https?://[^\\s|)\\]]+", "<url>");
    }

    private static String f(String format, Object... values) {
        return String.format(Locale.ROOT, format, values);
    }
}
