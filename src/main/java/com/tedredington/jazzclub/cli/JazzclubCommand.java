package com.tedredington.jazzclub.cli;

import java.nio.file.Path;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The command line. Parsed <em>before</em> Spring starts, so {@code --help} and {@code --version}
 * answer instantly and never touch the network.
 */
@Command(
        name = "jazzclub",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        sortOptions = false,
        usageHelpAutoWidth = true,
        optionListHeading = "%nOptions:%n",
        description = {
                "",
                "Console client for Pandora internet radio, a port of pianobar.",
                "",
                "jazzclub is driven by single key presses while it runs: press ? for the list,",
                "s to change station, n for the next song, p or space to pause, q to quit."},
        footerHeading = "%nFiles:%n",
        footer = {
                "  ~/.config/jazzclub/config   settings, in pianobar's \"key = value\" format",
                "                              ($XDG_CONFIG_HOME is honoured)",
                "",
                "Minimal config:",
                "  user = you@example.com",
                "  password_command = security find-generic-password -s jazzclub -w",
                "",
                "Audio decoding is built in. ffmpeg is optional for resampling or fallback."})
public final class JazzclubCommand {

    @Option(names = {"-c", "--config"}, paramLabel = "FILE",
            description = "Use FILE instead of the default config file.")
    Path configFile;

    @Option(names = {"-v", "--verbose"},
            description = "Log what jazzclub is doing. Repeat (-vv) for debug detail including stack traces.")
    boolean[] verbose = new boolean[0];

    public LaunchOptions toLaunchOptions() {
        return new LaunchOptions(verbose.length, configFile);
    }
}
