package com.tedredington.jazzclub.app;

import java.util.Optional;

import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.model.SearchResult;
import com.tedredington.jazzclub.pandora.model.SearchResult.ArtistMatch;
import com.tedredington.jazzclub.pandora.model.SearchResult.SongMatch;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Prompter;

/** pianobar's "artist or title" dialogue: ask for text, search, let the user pick one match. */
public final class MusicSearch {

    private final PandoraClient client;
    private final Console console;
    private final Prompter prompter;
    private final ListPicker picker;

    public MusicSearch(PandoraClient client, Console console, Prompter prompter, ListPicker picker) {
        this.client = client;
        this.console = console;
        this.prompter = prompter;
        this.picker = picker;
    }

    /** @return the chosen match's music token; empty if the user backed out or nothing was found */
    public Optional<String> selectMusicToken(String prompt) {
        console.print(MessageType.QUESTION, prompt);
        Optional<String> text = prompter.readLine();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        console.info("Searching... ");
        SearchResult result = client.search(text.get());
        console.append("\r");

        if (result.isEmpty()) {
            console.info("Nothing found...\n");
            return Optional.empty();
        }
        if (result.songs().isEmpty()) {
            return pickArtist(result);
        }
        if (result.artists().isEmpty()) {
            return pickSong(result);
        }
        console.print(MessageType.QUESTION, "Is this an [a]rtist or [t]rack name? ");
        return prompter.readChar("at").flatMap(kind -> kind == 'a' ? pickArtist(result) : pickSong(result));
    }

    private Optional<String> pickArtist(SearchResult result) {
        return picker.pick(result.artists(), ArtistMatch::name, "Select artist: ").map(ArtistMatch::musicToken);
    }

    private Optional<String> pickSong(SearchResult result) {
        return picker.pick(result.songs(), song -> song.artist() + " - " + song.title(), "Select song: ")
                .map(SongMatch::musicToken);
    }
}
