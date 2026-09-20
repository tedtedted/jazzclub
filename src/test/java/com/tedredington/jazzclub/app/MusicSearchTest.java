package com.tedredington.jazzclub.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.tedredington.jazzclub.pandora.model.SearchResult;
import com.tedredington.jazzclub.pandora.model.SearchResult.ArtistMatch;
import com.tedredington.jazzclub.pandora.model.SearchResult.SongMatch;
import com.tedredington.jazzclub.testsupport.RecordingConsole;
import com.tedredington.jazzclub.testsupport.ScriptedPrompter;
import com.tedredington.jazzclub.testsupport.StubPandoraClient;
import org.junit.jupiter.api.Test;

class MusicSearchTest {

    private static final ArtistMatch MILES = new ArtistMatch("Miles Davis", "R123");
    private static final SongMatch SO_WHAT = new SongMatch("So What", "Miles Davis", "S789");

    private final StubPandoraClient client = new StubPandoraClient();
    private final RecordingConsole console = new RecordingConsole();
    private final ScriptedPrompter prompter = new ScriptedPrompter();
    private final MusicSearch search = new MusicSearch(client, console, prompter, new ListPicker(console, prompter));

    @Test
    void whenBothKindsMatchTheUserSaysWhichWasMeant() {
        client.searchResult = new SearchResult(List.of(MILES), List.of(SO_WHAT));
        prompter.answer("miles", "a", "0");

        assertThat(search.selectMusicToken("Create station from artist or title: ")).contains("R123");
        assertThat(client.calls).containsExactly("search miles");
        assertThat(console.output()).isEqualTo("""
                [?] Create station from artist or title: (i) Searching... \r\
                [?] Is this an [a]rtist or [t]rack name? \t 0) Miles Davis
                [?] Select artist:\s""");
    }

    @Test
    void tracksAreListedWithTheirArtist() {
        client.searchResult = new SearchResult(List.of(MILES), List.of(SO_WHAT));
        prompter.answer("so what", "t", "0");

        assertThat(search.selectMusicToken("? ")).contains("S789");
        assertThat(console.output()).contains("\t 0) Miles Davis - So What\n[?] Select song: ");
    }

    @Test
    void onlyArtistsOrOnlySongsSkipTheQuestion() {
        client.searchResult = new SearchResult(List.of(MILES), List.of());
        prompter.answer("miles", "0");
        assertThat(search.selectMusicToken("? ")).contains("R123");

        client.searchResult = new SearchResult(List.of(), List.of(SO_WHAT));
        prompter.answer("so what", "0");
        assertThat(search.selectMusicToken("? ")).contains("S789");

        assertThat(console.output()).doesNotContain("[a]rtist or [t]rack");
    }

    @Test
    void nothingFoundSaysSo() {
        prompter.answer("zzzz");

        assertThat(search.selectMusicToken("? ")).isEmpty();
        assertThat(console.output()).endsWith("(i) Nothing found...\n");
    }

    @Test
    void anEmptySearchTextNeverReachesPandora() {
        prompter.answer("");

        assertThat(search.selectMusicToken("? ")).isEmpty();
        assertThat(client.calls).isEmpty();
    }

    @Test
    void backingOutAtAnyLaterStepSelectsNothing() {
        client.searchResult = new SearchResult(List.of(MILES), List.of(SO_WHAT));

        prompter.answer("miles", "");
        assertThat(search.selectMusicToken("? ")).isEmpty();

        prompter.answer("miles", "a", "");
        assertThat(search.selectMusicToken("? ")).isEmpty();
    }
}
