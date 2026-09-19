package com.tedredington.jazzclub.config.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Each case mirrors a rule of pianobar's settings.c, so a pianobar config behaves the same here. */
class ConfigFileParserTest {

    private final ConfigFileParser parser = new ConfigFileParser();

    private ParsedConfig parse(String... lines) {
        return parser.parse(List.of(lines), "test-config");
    }

    @Test
    void readsKeyValuePairs() {
        ParsedConfig config = parse("user = me@example.com", "audio_quality = high");

        assertThat(config.entries()).containsEntry("user", "me@example.com").containsEntry("audio_quality", "high");
        assertThat(config.warnings()).isEmpty();
    }

    @Test
    void spacesAroundTheDelimiterAreOptional() {
        assertThat(parse("volume=5", "  history   =  7").entries())
                .containsEntry("volume", "5")
                .containsEntry("history", " 7"); // only ONE leading space of the value is dropped
    }

    @Test
    void valuesKeepTrailingAndExtraLeadingWhitespace() {
        ParsedConfig config = parse("love_icon =  <3 ", "act_songpausetoggle2 =  ");

        assertThat(config.get("love_icon")).contains(" <3 ");
        assertThat(config.get("act_songpausetoggle2")).contains(" "); // bound to the space bar
    }

    @Test
    void onlyTheFirstEqualsSignSplits() {
        assertThat(parse("password = a=b==c").get("password")).contains("a=b==c");
    }

    @Test
    void valuesAreTakenLiterallyWithoutEscapesOrQuotes() {
        assertThat(parse("password = \"p\\tw#d\" ").get("password")).contains("\"p\\tw#d\" ");
    }

    @Test
    void commentsAndBlankLinesAreSkippedSilently() {
        ParsedConfig config = parse("# a comment", "   # indented comment", "", "   ", "user = x");

        assertThat(config.entries()).containsOnlyKeys("user");
        assertThat(config.warnings()).isEmpty();
    }

    @Test
    void aHashInsideAValueIsNotAComment() {
        assertThat(parse("encrypt_password = 6#26FRL$ZWD").get("encrypt_password")).contains("6#26FRL$ZWD");
    }

    @Test
    void windowsLineEndingsAreStripped() {
        assertThat(parse("user = me\r\n", "volume = 3\r").entries())
                .containsEntry("user", "me").containsEntry("volume", "3");
    }

    @Test
    void laterDuplicatesWin() {
        assertThat(parse("volume = 1", "volume = 2").get("volume")).contains("2");
    }

    @Test
    void emptyValuesAreKept() {
        assertThat(parse("password =").get("password")).contains("");
    }

    @Test
    void linesWithoutDelimiterAreReportedWithTheirLineNumber() {
        ParsedConfig config = parse("user = x", "this is not valid", "volume = 1");

        assertThat(config.entries()).containsOnlyKeys("user", "volume");
        assertThat(config.warnings()).containsExactly("Invalid line at test-config:2");
    }

    @Test
    void linesWithoutKeyAreReported() {
        assertThat(parse(" = value").warnings()).containsExactly("Missing key at test-config:1");
    }

    @Test
    void absentKeysAreEmpty() {
        assertThat(ParsedConfig.EMPTY.get("user")).isEmpty();
    }
}
