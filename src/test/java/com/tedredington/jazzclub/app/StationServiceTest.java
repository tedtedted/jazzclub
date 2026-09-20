package com.tedredington.jazzclub.app;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.FORMAT;
import static com.tedredington.jazzclub.testsupport.TestData.HARD_BOP;
import static com.tedredington.jazzclub.testsupport.TestData.QUICKMIX;
import static com.tedredington.jazzclub.testsupport.TestData.song;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.pandora.model.StationSeed;
import com.tedredington.jazzclub.player.PlaybackResult;
import com.tedredington.jazzclub.testsupport.FakeAudioPlayer;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.app.event.PlayerEvents;
import com.tedredington.jazzclub.testsupport.RecordingConsole;
import com.tedredington.jazzclub.testsupport.RecordingEvents;
import com.tedredington.jazzclub.testsupport.StubPandoraClient;
import com.tedredington.jazzclub.ui.Renderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StationServiceTest {

    private final StubPandoraClient client = new StubPandoraClient();
    private final FakeAudioPlayer player = new FakeAudioPlayer();
    private final PlaybackState state = new PlaybackState(5);
    private final RecordingConsole console = new RecordingConsole();
    private final RecordingEvents events = new RecordingEvents();
    private final PlayerEvents playerEvents = events.on(state, player);
    private final Radio radio = new Radio(client, player, state, console, new Renderer(FORMAT), AudioQuality.HIGH, 3,
            playerEvents);
    private final StationService service = new StationService(client, new PandoraCalls(console, playerEvents), state,
            radio);

    @BeforeEach
    void stations() {
        state.stations(List.of(QUICKMIX, EVANS, HARD_BOP));
    }

    @Test
    void aCreatedStationAppearsInTheMenuWithoutRefetching() {
        Station created = service.create(new StationSeed.MusicToken("R123"), "Creating station... ",
                EventType.STATION_CREATE, Selection.NONE);

        assertThat(created).isEqualTo(client.created);
        assertThat(state.stations()).contains(client.created).hasSize(4);
        assertThat(client.calls).containsExactly("create token R123");
        assertThat(console.output()).isEqualTo("(i) Creating station... Ok.\n");
    }

    @Test
    void creatingAStationThatAlreadyExistsDoesNotDuplicateIt() {
        client.created = EVANS.withName("Bill Evans Radio (again)");

        service.create(new StationSeed.MusicToken("R1"), "Creating station... ", EventType.STATION_CREATE,
                Selection.NONE);

        assertThat(state.stations()).hasSize(3).contains(client.created).doesNotContain(EVANS);
    }

    @Test
    void renamingUpdatesTheMenuAndThePlayingStation() {
        state.changeStation(EVANS);

        service.rename(EVANS, "Late Night");

        assertThat(client.calls).containsExactly("rename Bill Evans Radio -> Late Night");
        assertThat(state.findStation("200")).map(Station::name).contains("Late Night");
        assertThat(state.station()).map(Station::name).contains("Late Night");
    }

    @Test
    void sharedStationsAreTransformedBeforeTheyAreEdited() {
        service.rename(HARD_BOP, "Mine Now");

        assertThat(client.calls).containsExactly("transform Hard Bop Radio", "rename Hard Bop Radio -> Mine Now");
        assertThat(state.findStation("300")).contains(new Station("300", "Mine Now", true, false, false));
        assertThat(console.output()).isEqualTo("(i) Transforming station... Ok.\n(i) Renaming station... Ok.\n");
    }

    @Test
    void ownStationsAreNotTransformed() {
        service.addMusic(EVANS, "R123");

        assertThat(client.calls).containsExactly("addMusic Bill Evans Radio R123");
        assertThat(service.transformIfShared(EVANS)).isSameAs(EVANS);
    }

    @Test
    void deletingThePlayingStationStopsTheRadio() {
        client.playlists.add(List.of(song("a", "200")));
        radio.tune(EVANS);

        service.delete(EVANS);

        assertThat(state.stations()).containsExactlyInAnyOrder(QUICKMIX, HARD_BOP);
        assertThat(state.station()).isEmpty();
        assertThat(player.stops()).isEqualTo(1);

        player.finish();
        radio.onTrackFinished(player.lastId(), PlaybackResult.stopped());
        assertThat(player.played()).as("nothing new is started").hasSize(1);
        assertThat(state.song()).isEmpty();
    }

    @Test
    void deletingAnotherStationLeavesTheMusicAlone() {
        client.playlists.add(List.of(song("a", "200")));
        radio.tune(EVANS);

        service.delete(HARD_BOP);

        assertThat(state.station()).contains(EVANS);
        assertThat(player.stops()).isZero();
    }

    @Test
    void stoppingAnIdleRadioJustForgetsTheStation() {
        state.changeStation(EVANS);

        radio.stop();

        assertThat(state.station()).isEmpty();
        assertThat(player.stops()).isZero();
    }

    @Test
    void quickMixSendsTheMembersAndRemembersTheNewFlags() {
        List<Station> selection = List.of(QUICKMIX, EVANS.withInQuickMix(false), HARD_BOP.withInQuickMix(true));

        service.setQuickMix(selection);

        assertThat(client.calls).containsExactly("quickmix [Hard Bop Radio]");
        assertThat(state.findStation("200")).map(Station::inQuickMix).contains(false);
        assertThat(state.findStation("300")).map(Station::inQuickMix).contains(true);
    }

    @Test
    void everyChangeIsAnnouncedToListenersExceptTheSilentTransform() {
        service.create(new StationSeed.MusicToken("R1"), "Adding genre station... ", EventType.STATION_ADD_GENRE,
                Selection.NONE);
        service.rename(HARD_BOP, "Mine");
        service.addMusic(EVANS, "R2");
        service.setQuickMix(List.of(EVANS));
        service.delete(EVANS);

        assertThat(events.types()).containsExactly(EventType.STATION_ADD_GENRE, EventType.STATION_RENAME,
                EventType.STATION_ADD_MUSIC, EventType.STATION_QUICKMIX_TOGGLE, EventType.STATION_DELETE);
        assertThat(events.all().get(1).station().name()).as("listeners see the new name").isEqualTo("Mine");
    }

    @Test
    void failuresAreAnnouncedTooWithTheirErrorCode() {
        client.failure = new PandoraApiException(1005, null);

        assertThatThrownBy(() -> service.rename(EVANS, "x")).isInstanceOf(PandoraApiException.class);

        assertThat(events.last().type()).isEqualTo(EventType.STATION_RENAME);
        assertThat(events.last().result().pandoraMessage()).isEqualTo("Max number of stations reached.");
    }

    @Test
    void aFailureLeavesTheLocalListUntouched() {
        client.failure = new PandoraApiException(1005, null);

        assertThatThrownBy(() -> service.create(new StationSeed.MusicToken("R1"), "Creating station... ",
                EventType.STATION_CREATE, Selection.NONE))
                .isInstanceOf(PandoraApiException.class);
        assertThat(state.stations()).hasSize(3);

        client.failure = new PandoraApiException(1000, null);
        assertThatThrownBy(() -> service.delete(EVANS)).isInstanceOf(PandoraApiException.class);
        assertThat(state.stations()).contains(EVANS);
    }
}
