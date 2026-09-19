package com.tedredington.jazzclub.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CustomFormatTest {

    private static final String ESC = String.valueOf((char) 27);

    @Test
    void replacesKnownPlaceholders() {
        assertThat(CustomFormat.render("\"%t\" by %a", Map.of('t', "So What", 'a', "Miles Davis")))
                .isEqualTo("\"So What\" by Miles Davis");
    }

    @Test
    void unknownPlaceholdersArePrintedUnchangedLikePianobar() {
        assertThat(CustomFormat.render("%t %x", Map.of('t', "T"))).isEqualTo("T %x");
    }

    @Test
    void doublePercentIsNotAnEscape() {
        // pianobar has no escape: "%%" is an unknown placeholder named '%'
        assertThat(CustomFormat.render("100%% %t", Map.of('t', "T"))).isEqualTo("100%% T");
    }

    @Test
    void aTrailingPercentIsDropped() {
        assertThat(CustomFormat.render("50%", Map.of())).isEqualTo("50");
    }

    @Test
    void aKnownPlaceholderWithoutValuePrintsNothing() {
        Map<Character, String> values = new HashMap<>();
        values.put('u', null);
        assertThat(CustomFormat.render("[%u]", values)).isEqualTo("[]");
    }

    @Test
    void ansiEscapesPassThrough() {
        assertThat(CustomFormat.render(ESC + "[32m%t" + ESC + "[0m", Map.of('t', "T")))
                .isEqualTo(ESC + "[32mT" + ESC + "[0m");
    }
}
