package com.tedredington.jazzclub.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import com.tedredington.jazzclub.testsupport.RecordingConsole;
import com.tedredington.jazzclub.testsupport.ScriptedPrompter;
import org.junit.jupiter.api.Test;

class ListPickerTest {

    private static final List<String> ARTISTS = List.of("Miles Davis", "Miles Davis Quintet", "Bill Evans");

    private final RecordingConsole console = new RecordingConsole();
    private final ScriptedPrompter prompter = new ScriptedPrompter();
    private final ListPicker picker = new ListPicker(console, prompter);

    @Test
    void listsInGivenOrderAndSelectsByNumber() {
        prompter.answer("2");

        assertThat(picker.pick(ARTISTS, Function.identity(), "Select artist: ")).contains("Bill Evans");
        assertThat(console.output()).isEqualTo(
                "\t 0) Miles Davis\n\t 1) Miles Davis Quintet\n\t 2) Bill Evans\n[?] Select artist: ");
    }

    @Test
    void textNarrowsTheListButNumbersStayTheSame() {
        prompter.answer("EVANS", "2");

        assertThat(picker.pick(ARTISTS, Function.identity(), "? ")).contains("Bill Evans");
        assertThat(console.output()).endsWith("[?] ? \t 2) Bill Evans\n[?] ? ");
    }

    @Test
    void unlikeTheStationMenuASingleMatchIsNotTakenAutomatically() {
        prompter.answer("evans", "");

        assertThat(picker.pick(ARTISTS, Function.identity(), "? ")).isEmpty();
        assertThat(prompter.prompts()).isEqualTo(2);
    }

    @Test
    void anEmptyLineBacksOutAndBadNumbersAskAgain() {
        prompter.answer("7", "99999999999", "");

        assertThat(picker.pick(ARTISTS, Function.identity(), "? ")).isEmpty();
        assertThat(prompter.prompts()).isEqualTo(3);
    }

    @Test
    void recognisesIndexes() {
        assertThat(ListPicker.isIndex("0")).isTrue();
        assertThat(ListPicker.isIndex("123456")).isTrue();
        assertThat(ListPicker.isIndex("1234567")).isFalse();
        assertThat(ListPicker.isIndex("")).isFalse();
        assertThat(ListPicker.isIndex("1a")).isFalse();
        assertThat(ListPicker.isIndex("-1")).isFalse();
    }
}
