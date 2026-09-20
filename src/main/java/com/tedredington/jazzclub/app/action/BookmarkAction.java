package com.tedredington.jazzclub.app.action;

import java.util.Set;

import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Prompter;
import org.springframework.stereotype.Component;

@Component
class BookmarkAction implements KeyAction {

    private final PandoraClient client;
    private final PlaybackState state;
    private final Prompter prompter;
    private final Console console;

    BookmarkAction(PandoraClient client, PlaybackState state, Prompter prompter, Console console) {
        this.client = client;
        this.state = state;
        this.prompter = prompter;
        this.console = console;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.BOOKMARK);
    }

    @Override
    public void execute(ActionId id) {
        Song song = state.song().orElseThrow();
        console.print(MessageType.QUESTION, "Bookmark [s]ong or [a]rtist? ");
        prompter.readChar("sa").ifPresent(kind -> {
            if (kind == 's') {
                console.info("Bookmarking song... ");
                client.bookmarkSong(song);
            } else {
                console.info("Bookmarking artist... ");
                client.bookmarkArtist(song);
            }
            console.append("Ok.\n");
        });
    }
}
