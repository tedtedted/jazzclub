package com.tedredington.jazzclub.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ActionIdTest {

    @Test
    void defaultKeysAreUniqueSoNoActionShadowsAnother() {
        assertThat(Arrays.stream(ActionId.values()).map(ActionId::defaultKey)).doesNotHaveDuplicates();
    }

    @Test
    void configKeysAreUniqueAndFollowPianobarsNaming() {
        assertThat(Arrays.stream(ActionId.values()).map(ActionId::configKey))
                .doesNotHaveDuplicates()
                .allMatch(key -> key.startsWith("act_"));
    }

    @Test
    void defaultsMatchPianobar() {
        assertThat(ActionId.SONG_LOVE.defaultKey()).isEqualTo('+');
        assertThat(ActionId.SONG_BAN.defaultKey()).isEqualTo('-');
        assertThat(ActionId.SONG_NEXT.defaultKey()).isEqualTo('n');
        assertThat(ActionId.SONG_PAUSE_TOGGLE_2.defaultKey()).isEqualTo(' ');
        assertThat(ActionId.STATION_CHANGE.defaultKey()).isEqualTo('s');
        assertThat(ActionId.QUIT.defaultKey()).isEqualTo('q');
        assertThat(ActionId.fromConfigKey("act_songtired")).contains(ActionId.SONG_TIRED);
        assertThat(ActionId.fromConfigKey("act_nonsense")).isEmpty();
    }
}
