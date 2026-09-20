package com.tedredington.jazzclub.app;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.FORMAT;
import static com.tedredington.jazzclub.testsupport.TestData.HARD_BOP;
import static com.tedredington.jazzclub.testsupport.TestData.QUICKMIX;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.testsupport.RecordingConsole;
import com.tedredington.jazzclub.testsupport.ScriptedPrompter;
import com.tedredington.jazzclub.ui.Renderer;
import org.junit.jupiter.api.Test;

class StationPickerTest {

    private static final Station BEBOP = new Station("400", "bebop Radio", true, false, false);
    private static final List<Station> STATIONS = List.of(QUICKMIX, HARD_BOP, EVANS, BEBOP);

    private final RecordingConsole console = new RecordingConsole();
    private final ScriptedPrompter prompter = new ScriptedPrompter();
    private final StationPicker picker = new StationPicker(console, prompter, new Renderer(FORMAT));

    @Test
    void listsStationsSortedByNameIgnoringCase() {
        prompter.answer("0");

        picker.pick(STATIONS, "Select station: ");

        assertThat(console.output()).isEqualTo("""
                \t 0)     bebop Radio
                \t 1) q   Bill Evans Radio
                \t 2)   S Hard Bop Radio
                \t 3)  Q  QuickMix
                [?] Select station:\s""");
    }

    @Test
    void aNumberSelectsFromTheSortedList() {
        prompter.answer("2");

        assertThat(picker.pick(STATIONS, "? ")).contains(HARD_BOP);
    }

    @Test
    void textNarrowsTheListAndASingleMatchIsTakenAutomatically() {
        prompter.answer("evans");

        assertThat(picker.pick(STATIONS, "? ")).contains(EVANS);
        assertThat(prompter.prompts()).isEqualTo(1);
        assertThat(console.output()).endsWith("\t 1) q   Bill Evans Radio\n[?] ? 1\n");
    }

    @Test
    void aFilterWithSeveralMatchesKeepsOriginalNumbersAndAsksAgain() {
        prompter.answer("BOP", "2");

        assertThat(picker.pick(STATIONS, "? ")).contains(HARD_BOP);
        assertThat(console.output()).contains("[?] ? \t 0)     bebop Radio\n\t 2)   S Hard Bop Radio\n[?] ? ");
    }

    @Test
    void aFilterWithoutMatchesShowsNothingAndAsksAgain() {
        prompter.answer("zzz", "3");

        assertThat(picker.pick(STATIONS, "? ")).contains(QUICKMIX);
        assertThat(prompter.prompts()).isEqualTo(2);
    }

    @Test
    void anOutOfRangeNumberIsTreatedAsAFilter() {
        prompter.answer("99", "");

        assertThat(picker.pick(STATIONS, "? ")).isEmpty();
    }

    @Test
    void anEmptyLineBacksOut() {
        prompter.answer("");

        assertThat(picker.pick(STATIONS, "? ")).isEmpty();
    }

    @Test
    void aSingleStationIsStillOfferedNotForced() {
        prompter.answer("0");

        assertThat(picker.pick(List.of(EVANS), "? ")).contains(EVANS);
        assertThat(prompter.prompts()).isEqualTo(1);
    }

    @Test
    void absurdlyLongNumbersDoNotCrash() {
        prompter.answer("99999999999999999999", "");

        assertThat(picker.pick(STATIONS, "? ")).isEmpty();
    }

    @Test
    void withAutoselectOffASingleMatchStillAsks() {
        prompter.answer("evans", "1");

        assertThat(picker.pick(() -> STATIONS, "? ", false, input -> false)).contains(EVANS);
        assertThat(prompter.prompts()).isEqualTo(2);
    }

    @Test
    void aCommandCanChangeTheListAndIsNotUsedAsFilter() {
        java.util.List<Station> shown = new java.util.ArrayList<>(List.of(EVANS, HARD_BOP));
        prompter.answer("a", "");

        picker.pick(() -> shown, "? ", false, input -> {
            if (input.equals("a")) {
                shown.replaceAll(s -> s.withInQuickMix(true));
                return true;
            }
            return false;
        });

        // second listing shows both stations (no filter "a") with the q flag set by the command
        assertThat(console.output()).endsWith("\t 0) q   Bill Evans Radio\n\t 1) q S Hard Bop Radio\n[?] ? ");
    }

    @Test
    void noStationsIsAnErrorNotAPrompt() {
        assertThat(picker.pick(List.of(), "? ")).isEmpty();
        assertThat(console.output()).isEqualTo("/!\\ No station available.\n");
        assertThat(prompter.prompts()).isZero();
    }
}
