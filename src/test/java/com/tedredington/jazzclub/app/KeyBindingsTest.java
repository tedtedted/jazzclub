package com.tedredington.jazzclub.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class KeyBindingsTest {

    @Test
    void withoutOverridesTheDefaultsApply() {
        KeyBindings bindings = new KeyBindings(Map.of());

        assertThat(bindings.actionFor('n')).contains(ActionId.SONG_NEXT);
        assertThat(bindings.actionFor(' ')).contains(ActionId.SONG_PAUSE_TOGGLE_2);
        assertThat(bindings.actionFor('#')).isEmpty();
        assertThat(bindings.keyFor(ActionId.QUIT)).contains('q');
    }

    @Test
    void anOverrideMovesTheActionAndFreesTheOldKey() {
        KeyBindings bindings = new KeyBindings(Map.of("act_songlove", "l"));

        assertThat(bindings.actionFor('l')).contains(ActionId.SONG_LOVE);
        assertThat(bindings.actionFor('+')).isEmpty();
    }

    @Test
    void disabledUnbindsAnAction() {
        KeyBindings bindings = new KeyBindings(Map.of("act_songban", "disabled"));

        assertThat(bindings.actionFor('-')).isEmpty();
        assertThat(bindings.keyFor(ActionId.SONG_BAN)).isEmpty();
    }

    @Test
    void whenTwoActionsShareAKeyTheFirstInTableOrderWins() {
        KeyBindings bindings = new KeyBindings(Map.of("act_quit", "n"));

        assertThat(bindings.actionFor('n')).contains(ActionId.SONG_NEXT);
    }

    @Test
    void valuesMustBeOneCharacter() {
        assertThatThrownBy(() -> new KeyBindings(Map.of("act_quit", "quit")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("act_quit").hasMessageContaining("single character");
        assertThatThrownBy(() -> new KeyBindings(Map.of("act_quit", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownActionsAreRejected() {
        assertThatThrownBy(() -> new KeyBindings(Map.of("act_dance", "d")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("act_dance");
    }
}
