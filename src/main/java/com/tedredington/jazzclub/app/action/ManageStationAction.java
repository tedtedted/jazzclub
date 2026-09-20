package com.tedredington.jazzclub.app.action;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.tedredington.jazzclub.app.ActionContext;
import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.ListPicker;
import com.tedredington.jazzclub.app.PandoraCalls;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.Radio;
import com.tedredington.jazzclub.app.Selection;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.pandora.model.StationInfo;
import com.tedredington.jazzclub.pandora.model.StationMode;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Prompter;
import com.tedredington.jazzclub.ui.Renderer;
import org.springframework.stereotype.Component;

/** Undo what shaped a station: remove seeds, take back ratings, or switch its mode. */
@Component
class ManageStationAction implements KeyAction {

    private final PandoraClient client;
    private final PandoraCalls calls;
    private final PlaybackState state;
    private final Radio radio;
    private final ListPicker picker;
    private final Prompter prompter;
    private final Console console;
    private final Renderer renderer;

    ManageStationAction(PandoraClient client, PandoraCalls calls, PlaybackState state, Radio radio, ListPicker picker,
                        Prompter prompter, Console console, Renderer renderer) {
        this.client = client;
        this.calls = calls;
        this.state = state;
        this.radio = radio;
        this.picker = picker;
        this.prompter = prompter;
        this.console = console;
        this.renderer = renderer;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.STATION_MANAGE);
    }

    @Override
    public void execute(ActionId id, ActionContext context) {
        Selection selection = context.selection();
        Station station = selection.station();
        StationInfo info = calls.call("Fetching station info... ", EventType.STATION_FETCH_INFO, selection,
                () -> client.stationInfo(station));

        Menu menu = Menu.of(info, station);
        if (menu.allowed().isEmpty()) {
            console.info("No actions available.\n");
            return;
        }
        console.print(MessageType.QUESTION, menu.question());
        prompter.readChar(menu.allowed()).ifPresent(choice -> {
            switch (choice) {
                case 'a' -> picker.pick(info.artistSeeds(), StationInfo.ArtistSeed::name, "Select artist: ")
                        .ifPresent(seed -> calls.run("Deleting artist seed... ",
                                EventType.STATION_DELETE_ARTIST_SEED, selection, () -> client.deleteSeed(seed.seedId())));
                case 's' -> picker.pick(info.songSeeds(), seed -> seed.artist() + " - " + seed.title(), "Select song: ")
                        .ifPresent(seed -> calls.run("Deleting song seed... ",
                                EventType.STATION_DELETE_SONG_SEED, selection, () -> client.deleteSeed(seed.seedId())));
                case 'f' -> picker.pick(info.feedback(), this::describe, "Select song: ")
                        .ifPresent(entry -> calls.run("Deleting feedback... ",
                                EventType.STATION_DELETE_FEEDBACK, selection,
                                () -> client.deleteFeedback(entry.feedbackId())));
                case 'm' -> changeMode(selection);
                default -> throw new IllegalStateException("Unexpected choice: " + choice);
            }
        });
    }

    private String describe(StationInfo.Feedback entry) {
        return entry.artist() + " - " + entry.title()
                + renderer.ratingIcon(entry.positive() ? Rating.LOVE : Rating.BAN);
    }

    private void changeMode(Selection selection) {
        Station station = selection.station();
        List<StationMode> modes = calls.call("Fetching modes... ", EventType.STATION_GET_MODES, selection,
                () -> client.stationModes(station));
        for (int i = 0; i < modes.size(); i++) {
            StationMode mode = modes.get(i);
            console.list("%2d) %s: %s%s\n".formatted(i, mode.name(), mode.description(),
                    mode.active() ? " (active)" : ""));
        }
        console.print(MessageType.QUESTION, "Pick a new mode: ");
        while (true) {
            Optional<Integer> number = prompter.readNumber();
            if (number.isEmpty()) {
                return;
            }
            if (number.get() < modes.size()) {
                StationMode mode = modes.get(number.get());
                calls.run("Selecting mode \"" + mode.name() + "\"... ", EventType.STATION_SET_MODE, selection,
                        () -> client.setStationMode(station, mode));
                if (state.station().filter(s -> s.token().equals(station.token())).isPresent()) {
                    // what is queued was chosen under the old mode
                    radio.restartStation();
                }
                return;
            }
        }
    }

    /** pianobar's question, which only offers what the station actually has. */
    record Menu(String question, String allowed) {

        static Menu of(StationInfo info, Station station) {
            StringBuilder question = new StringBuilder();
            StringBuilder allowed = new StringBuilder();
            boolean anySeeds = !info.artistSeeds().isEmpty() || !info.songSeeds().isEmpty();
            if (anySeeds || !info.feedback().isEmpty()) {
                question.append("Delete ");
            }
            if (!info.artistSeeds().isEmpty()) {
                question.append("[a]rtist");
                allowed.append('a');
            }
            if (!info.songSeeds().isEmpty()) {
                question.append(allowed.isEmpty() ? "" : "/").append("[s]ong");
                allowed.append('s');
            }
            if (anySeeds) {
                question.append(" seeds");
            }
            if (!info.feedback().isEmpty()) {
                question.append(anySeeds ? " or " : "").append("[f]eedback");
                allowed.append('f');
            }
            if (!allowed.isEmpty()) {
                question.append("? ");
            }
            // station modes do not exist for QuickMix
            if (!station.quickMix()) {
                question.append("Manage [m]ode? ");
                allowed.append('m');
            }
            return new Menu(question.toString(), allowed.toString());
        }
    }
}
