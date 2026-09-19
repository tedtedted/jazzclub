package com.tedredington.jazzclub.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CommandLineParserTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final CommandLineParser parser = new CommandLineParser(new PrintWriter(out), new PrintWriter(err));

    @Test
    void noArgumentsMeansDefaults() {
        assertThat(parser.parse()).contains(new LaunchOptions(0, null));
        assertThat(out.toString()).isEmpty();
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void helpIsPrintedAndNothingIsLaunched() {
        assertThat(parser.parse("--help")).isEmpty();

        assertThat(parser.exitCode()).isZero();
        assertThat(out.toString())
                .contains("Usage: jazzclub")
                .contains("-v, --verbose")
                .contains("-c, --config=FILE")
                .contains("-V, --version")
                .contains("~/.config/jazzclub/config")
                .contains("ffmpeg");
    }

    @Test
    void shortHelpWorksToo() {
        assertThat(parser.parse("-h")).isEmpty();
        assertThat(out.toString()).contains("Usage: jazzclub");
    }

    @Test
    void versionIsPrintedAndNothingIsLaunched() {
        assertThat(parser.parse("--version")).isEmpty();

        assertThat(parser.exitCode()).isZero();
        assertThat(out.toString()).startsWith("jazzclub ");
    }

    @Test
    void lowerCaseVIsVerboseNotVersion() {
        assertThat(parser.parse("-v")).map(LaunchOptions::verbosity).contains(1);
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void verbosityStacks() {
        assertThat(parser.parse("-vv")).map(LaunchOptions::verbosity).contains(2);
        assertThat(parser.parse("-v", "--verbose", "-v")).map(LaunchOptions::verbosity).contains(3);
    }

    @Test
    void configFileCanBeGivenInEveryUsualSpelling() {
        Optional<LaunchOptions> expected = Optional.of(new LaunchOptions(0, Path.of("/tmp/c")));

        assertThat(parser.parse("-c", "/tmp/c")).isEqualTo(expected);
        assertThat(parser.parse("--config", "/tmp/c")).isEqualTo(expected);
        assertThat(parser.parse("--config=/tmp/c")).isEqualTo(expected);
    }

    @Test
    void unknownOptionsAreAUsageErrorOnStderrWithExitStatusTwo() {
        assertThat(parser.parse("--frobnicate")).isEmpty();

        assertThat(parser.exitCode()).isEqualTo(CommandLineParser.EXIT_USAGE);
        assertThat(err.toString()).contains("--frobnicate").contains("jazzclub --help");
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void aMissingOptionValueIsAUsageError() {
        assertThat(parser.parse("--config")).isEmpty();
        assertThat(parser.exitCode()).isEqualTo(CommandLineParser.EXIT_USAGE);
    }

    @Test
    void positionalArgumentsAreRejected() {
        assertThat(parser.parse("play")).isEmpty();
        assertThat(parser.exitCode()).isEqualTo(CommandLineParser.EXIT_USAGE);
    }
}
