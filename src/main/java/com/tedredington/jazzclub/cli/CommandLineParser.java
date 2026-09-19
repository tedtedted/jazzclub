package com.tedredington.jazzclub.cli;

import java.io.PrintWriter;
import java.util.Optional;

import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

/** Turns {@code argv} into {@link LaunchOptions}, or prints help, version or a usage error. */
public final class CommandLineParser {

    /** Conventional exit status for a malformed command line. */
    public static final int EXIT_USAGE = 2;

    private final PrintWriter out;
    private final PrintWriter err;
    private int exitCode;

    public CommandLineParser(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
    }

    /** @return the options to launch with; empty if everything was handled here, see {@link #exitCode()} */
    public Optional<LaunchOptions> parse(String... args) {
        JazzclubCommand command = new JazzclubCommand();
        CommandLine commandLine = new CommandLine(command).setOut(out).setErr(err);
        try {
            commandLine.parseArgs(args);
        } catch (ParameterException e) {
            err.println(e.getMessage());
            err.println("Try 'jazzclub --help' for more information.");
            err.flush();
            exitCode = EXIT_USAGE;
            return Optional.empty();
        }
        if (commandLine.isUsageHelpRequested()) {
            commandLine.usage(out);
            out.flush();
            return Optional.empty();
        }
        if (commandLine.isVersionHelpRequested()) {
            commandLine.printVersionHelp(out);
            out.flush();
            return Optional.empty();
        }
        return Optional.of(command.toLaunchOptions());
    }

    public int exitCode() {
        return exitCode;
    }
}
