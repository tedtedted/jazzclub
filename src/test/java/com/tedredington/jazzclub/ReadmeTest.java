package com.tedredington.jazzclub;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.cli.CommandLineParser;
import com.tedredington.jazzclub.config.file.ConfigKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The README is written by hand; these checks fail when it drifts from the code it describes. */
class ReadmeTest {

    private static String readme;

    @BeforeAll
    static void read() throws IOException {
        readme = Files.readString(Path.of("README.md"));
    }

    @ParameterizedTest
    @EnumSource(ActionId.class)
    void everyKeyActionIsDocumentedWithItsDefaultKeyAndHelpText(ActionId action) {
        String key = action.defaultKey() == ' ' ? "Space" : String.valueOf(action.defaultKey());
        String description = action.helpText() != null ? action.helpText() + " " : "";

        assertThat(readme).containsPattern(
                "\\| `" + java.util.regex.Pattern.quote(key) + "` \\| .*"
                        + java.util.regex.Pattern.quote(description) + ".*`" + action.configKey() + "`");
    }

    @ParameterizedTest
    @EnumSource(ConfigKey.class)
    void everySupportedSettingIsMentioned(ConfigKey key) {
        assertThat(readme).contains("`" + key.fileKey() + "`");
    }

    @Test
    void theUsageBlockIsWhatHelpReallyPrints() {
        StringWriter out = new StringWriter();
        new CommandLineParser(new PrintWriter(out), new PrintWriter(new StringWriter())).parse("--help");

        out.toString().lines()
                .filter(line -> line.startsWith("Usage:") || line.startsWith("  -"))
                .forEach(line -> assertThat(readme).contains(line.stripTrailing()));
    }
}
