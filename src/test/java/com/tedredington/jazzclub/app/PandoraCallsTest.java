package com.tedredington.jazzclub.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.testsupport.FakeAudioPlayer;
import com.tedredington.jazzclub.testsupport.RecordingConsole;
import com.tedredington.jazzclub.testsupport.RecordingEvents;
import org.junit.jupiter.api.Test;

class PandoraCallsTest {

    private final RecordingConsole console = new RecordingConsole();
    private final RecordingEvents events = new RecordingEvents();
    private final PandoraCalls calls = new PandoraCalls(console,
            events.on(new PlaybackState(5), new FakeAudioPlayer()));

    @Test
    void announcesCallsConfirmsAndReports() {
        String value = calls.call("Doing it... ", EventType.SONG_LOVE, Selection.NONE, () -> "done");

        assertThat(value).isEqualTo("done");
        assertThat(console.output()).isEqualTo("(i) Doing it... Ok.\n");
        assertThat(events.types()).containsExactly(EventType.SONG_LOVE);
        assertThat(events.last().result().isOk()).isTrue();
    }

    @Test
    void onFailureTheLineStaysOpenForTheErrorAndTheEventCarriesIt() {
        assertThatThrownBy(() -> calls.run("Doing it... ", EventType.SONG_BAN, Selection.NONE, () -> {
            throw new PandoraApiException(1000, null);
        })).isInstanceOf(PandoraApiException.class);

        assertThat(console.output()).isEqualTo("(i) Doing it... ");
        assertThat(events.last().result().pandoraMessage()).isEqualTo("Read only mode. Try again later.");
    }

    @Test
    void callsWithoutAnEventStaySilentTowardsListeners() {
        calls.run("Transforming station... ", null, Selection.NONE, () -> { });

        assertThat(console.output()).isEqualTo("(i) Transforming station... Ok.\n");
        assertThat(events.all()).isEmpty();
    }

    @Test
    void otherExceptionsAreNotSwallowedOrReported() {
        assertThatThrownBy(() -> calls.run("x", EventType.SONG_BAN, Selection.NONE, () -> {
            throw new IllegalStateException("bug");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(events.all()).isEmpty();
    }
}
