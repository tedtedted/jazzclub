package com.tedredington.jazzclub.app.action;

import java.util.Set;

import com.tedredington.jazzclub.app.ActionContext;
import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.Radio;
import com.tedredington.jazzclub.player.AudioPlayer;
import org.springframework.stereotype.Component;

/** The buttons of a tape deck: next, pause, play, volume, and the power switch. */
@Component
class TransportAction implements KeyAction {

    private final AudioPlayer player;
    private final Radio radio;
    private final PlaybackState state;

    TransportAction(AudioPlayer player, Radio radio, PlaybackState state) {
        this.player = player;
        this.radio = radio;
        this.state = state;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.SONG_NEXT, ActionId.SONG_PAUSE_TOGGLE, ActionId.SONG_PAUSE_TOGGLE_2,
                ActionId.SONG_PLAY, ActionId.SONG_PAUSE, ActionId.VOLUME_DOWN, ActionId.VOLUME_UP,
                ActionId.VOLUME_RESET, ActionId.QUIT);
    }

    @Override
    public void execute(ActionId id, ActionContext context) {
        switch (id) {
            case SONG_NEXT -> radio.skip();
            case SONG_PAUSE_TOGGLE, SONG_PAUSE_TOGGLE_2 -> player.setPaused(!player.isPaused());
            case SONG_PLAY -> player.setPaused(false);
            case SONG_PAUSE -> player.setPaused(true);
            case VOLUME_DOWN -> player.setVolume(player.volume() - 1);
            case VOLUME_UP -> player.setVolume(player.volume() + 1);
            case VOLUME_RESET -> player.setVolume(0);
            // Only ask the loop to end. Its shutdown stops the player, after reading how much of the
            // song was heard; stopping here would lose that for the final "songfinish".
            case QUIT -> state.requestQuit();
            default -> throw new IllegalArgumentException("Not a transport action: " + id);
        }
    }
}
