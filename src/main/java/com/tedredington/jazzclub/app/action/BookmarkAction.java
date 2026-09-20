package com.tedredington.jazzclub.app.action;

import java.util.Set;

import com.tedredington.jazzclub.app.ActionContext;
import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.PandoraCalls;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Prompter;
import org.springframework.stereotype.Component;

@Component
class BookmarkAction implements KeyAction {

    private final PandoraClient client;
    private final PandoraCalls calls;
    private final Prompter prompter;
    private final Console console;

    BookmarkAction(PandoraClient client, PandoraCalls calls, Prompter prompter, Console console) {
        this.client = client;
        this.calls = calls;
        this.prompter = prompter;
        this.console = console;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.BOOKMARK);
    }

    @Override
    public void execute(ActionId id, ActionContext context) {
        Song song = context.selection().song();
        console.print(MessageType.QUESTION, "Bookmark [s]ong or [a]rtist? ");
        prompter.readChar("sa").ifPresent(kind -> {
            if (kind == 's') {
                calls.run("Bookmarking song... ", EventType.SONG_BOOKMARK, context.selection(),
                        () -> client.bookmarkSong(song));
            } else {
                calls.run("Bookmarking artist... ", EventType.ARTIST_BOOKMARK, context.selection(),
                        () -> client.bookmarkArtist(song));
            }
        });
    }
}
