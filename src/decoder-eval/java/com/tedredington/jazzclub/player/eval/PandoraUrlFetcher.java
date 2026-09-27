package com.tedredington.jazzclub.player.eval;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.tedredington.jazzclub.JazzclubApplication;
import com.tedredington.jazzclub.credentials.CredentialsProvider;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;

/**
 * Collects real Pandora stream URLs for F1 without playing anything. It starts jazzclub's Spring
 * context without {@code jazzclub.interactive}, so the config file and credentials are handled by
 * jazzclub's own code and neither the player nor the terminal is touched. It then fetches one
 * playlist per station for a few stations, at every audio quality, and appends
 * {@code <time> <encoding>/<quality> <url>} lines to an owner-only file.
 *
 * <p>No feedback is sent and no event command runs. Station and song names are not printed.
 */
public final class PandoraUrlFetcher {

    private PandoraUrlFetcher() {
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args.length > 0 ? args[0] : "target/decoder-eval/pandora/urls.txt");
        int stationCount = args.length > 1 ? Integer.parseInt(args[1]) : 3;

        SpringApplication application = new SpringApplication(JazzclubApplication.class);
        application.setAddCommandLineProperties(false);
        try (ConfigurableApplicationContext context = application.run()) {
            PandoraClient client = context.getBean(PandoraClient.class);
            client.login(context.getBean(CredentialsProvider.class).credentials());
            List<Station> stations = client.stations().stream().filter(s -> !s.quickMix()).limit(stationCount)
                    .toList();
            System.out.println("Logged in; using " + stations.size() + " stations");

            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                Files.createFile(file);
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            }
            int count = 0;
            for (int i = 0; i < stations.size(); i++) {
                for (AudioQuality quality : AudioQuality.values()) {
                    StringBuilder lines = new StringBuilder();
                    for (Song song : client.playlist(stations.get(i), quality)) {
                        lines.append(Instant.now()).append(' ').append(song.encoding()).append('/')
                                .append(quality.configValue()).append(' ').append(song.audioUrl()).append('\n');
                        count++;
                    }
                    Files.writeString(file, lines, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                    System.out.println("station " + (i + 1) + ", " + quality.configValue() + ": playlist fetched");
                }
            }
            System.out.println(count + " stream URLs written to " + file);
        }
        System.exit(0);
    }
}
