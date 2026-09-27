package com.tedredington.jazzclub.player.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects one cell per (check, candidate) and writes them as a Markdown table, which is what gets
 * posted on issue #9. A cell records the measurement, not just pass/fail, so the thresholds can be
 * argued about afterwards.
 */
final class EvalReport {

    private static final Map<String, Map<Candidate, String>> CELLS = new LinkedHashMap<>();
    private static final List<String> NOTES = new ArrayList<>();

    private EvalReport() {
    }

    static synchronized void record(String check, Candidate candidate, boolean passed, String measurement) {
        CELLS.computeIfAbsent(check, k -> new EnumMap<>(Candidate.class))
                .put(candidate, (passed ? "✅ " : "❌ ") + measurement.replace("|", "\\|").replace("\n", " "));
    }

    static synchronized void note(String note) {
        NOTES.add(note);
    }

    static synchronized void write(Path file) {
        StringBuilder md = new StringBuilder("| Check |");
        for (Candidate c : Candidate.values()) {
            md.append(' ').append(c.name().toLowerCase()).append(" |");
        }
        md.append("\n|---|");
        md.append("---|".repeat(Candidate.values().length)).append('\n');
        CELLS.forEach((check, row) -> {
            md.append("| ").append(check).append(" |");
            for (Candidate c : Candidate.values()) {
                md.append(' ').append(row.getOrDefault(c, "not run")).append(" |");
            }
            md.append('\n');
        });
        if (!NOTES.isEmpty()) {
            md.append("\n");
            NOTES.forEach(n -> md.append("- ").append(n).append('\n'));
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, md);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
