package com.tedredington.jazzclub.app.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.tedredington.jazzclub.app.ActionContext;
import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.MusicSearch;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.StationPicker;
import com.tedredington.jazzclub.app.StationService;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Prompter;
import org.springframework.stereotype.Component;

/** Changes to the station that is playing: more music, a new name, deletion, QuickMix membership. */
@Component
class EditStationAction implements KeyAction {

    private final PlaybackState state;
    private final StationService stations;
    private final MusicSearch search;
    private final StationPicker stationPicker;
    private final Prompter prompter;
    private final Console console;

    EditStationAction(PlaybackState state, StationService stations, MusicSearch search, StationPicker stationPicker,
                      Prompter prompter, Console console) {
        this.state = state;
        this.stations = stations;
        this.search = search;
        this.stationPicker = stationPicker;
        this.prompter = prompter;
        this.console = console;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.STATION_ADD_MUSIC, ActionId.STATION_RENAME, ActionId.STATION_DELETE,
                ActionId.STATION_QUICKMIX);
    }

    @Override
    public void execute(ActionId id, ActionContext context) {
        Station station = context.selection().station();
        switch (id) {
            case STATION_ADD_MUSIC -> search.selectMusicToken("Add artist or title to station: ")
                    .ifPresent(token -> stations.addMusic(station, token));
            case STATION_RENAME -> {
                console.print(MessageType.QUESTION, "New name: ");
                prompter.readLine().ifPresent(name -> stations.rename(station, name));
            }
            case STATION_DELETE -> {
                console.print(MessageType.QUESTION, "Really delete \"" + station.name() + "\"? [yN] ");
                if (prompter.confirm(false)) {
                    stations.delete(station);
                }
            }
            case STATION_QUICKMIX -> selectQuickMix(station);
            default -> throw new IllegalArgumentException("Not an edit action: " + id);
        }
    }

    /** Toggle stations one by one; {@code t} flips all, {@code a} selects all, {@code n} none; Enter saves. */
    private void selectQuickMix(Station playing) {
        if (!playing.quickMix()) {
            console.error("Please select a QuickMix station first.\n");
            return;
        }
        List<Station> selection = new ArrayList<>(state.stations());
        while (true) {
            Optional<Station> toggled = stationPicker.pick(() -> selection, "Toggle QuickMix for station: ", false,
                    input -> applyToAll(selection, input));
            if (toggled.isEmpty()) {
                break;
            }
            selection.replaceAll(s -> s.token().equals(toggled.get().token()) ? s.withInQuickMix(!s.inQuickMix()) : s);
        }
        stations.setQuickMix(selection);
    }

    private static boolean applyToAll(List<Station> selection, String input) {
        UnaryOperator<Station> change = switch (input) {
            case "t" -> s -> s.withInQuickMix(!s.inQuickMix());
            case "a" -> s -> s.withInQuickMix(true);
            case "n" -> s -> s.withInQuickMix(false);
            default -> null;
        };
        if (change == null) {
            return false;
        }
        selection.replaceAll(change);
        return true;
    }
}
