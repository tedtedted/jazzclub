package com.tedredington.jazzclub.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AnsiConsoleTest {

    private static final String CLEAR_LINE = (char) 27 + "[2K";

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final Console console = new AnsiConsole(new PrintStream(bytes, true, StandardCharsets.UTF_8));

    private String written() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void prefixedMessagesClearTheLineFirstSoTheyOverwriteTheTimeDisplay() {
        console.info("Login... ");

        assertThat(written()).isEqualTo(CLEAR_LINE + "(i) Login... ");
    }

    @Test
    void appendContinuesTheLineUntouched() {
        console.info("Login... ");
        console.append("Ok.\n");

        assertThat(written()).isEqualTo(CLEAR_LINE + "(i) Login... Ok.\n");
    }

    @Test
    void formatMsgSettingsWrapTheMessage() {
        Console custom = new AnsiConsole(new PrintStream(bytes, true, StandardCharsets.UTF_8),
                java.util.Map.of("info", "[info] %s", "err", "<<%s>>", "nowplaying", "%s"));

        custom.info("hello\n");
        custom.error("oops");
        custom.print(MessageType.PLAYING, "song\n");
        custom.list("untouched\n");

        assertThat(written().replace(CLEAR_LINE, "")).isEqualTo("[info] hello\n<<oops>>song\n\tuntouched\n");
    }

    @Test
    void aFormatWithoutPlaceholderIsIgnoredSoATypoCannotSwallowEveryMessage() {
        Console custom = new AnsiConsole(new PrintStream(bytes, true, StandardCharsets.UTF_8),
                java.util.Map.of("info", "no placeholder here", "bogus", "%s"));

        custom.info("still visible\n");

        assertThat(written()).isEqualTo(CLEAR_LINE + "(i) still visible\n");
    }

    @Test
    void everyTypeHasPianobarsPrefix() {
        console.error("e\n");
        console.list("l\n");
        console.print(MessageType.PLAYING, "p\n");
        console.print(MessageType.TIME, "t\r");
        console.print(MessageType.QUESTION, "q");

        assertThat(written().replace(CLEAR_LINE, ""))
                .isEqualTo("/!\\ e\n\tl\n|>  p\n#   t\r[?] q");
    }
}
