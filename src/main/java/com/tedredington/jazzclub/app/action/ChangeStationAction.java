package com.tedredington.jazzclub.app.action;

import java.util.Set;

import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.PlaybackState;
import com.tedredington.jazzclub.app.Radio;
import com.tedredington.jazzclub.app.StationPicker;
import org.springframework.stereotype.Component;

@Component
class ChangeStationAction implements KeyAction {

    private final StationPicker picker;
    private final PlaybackState state;
    private final Radio radio;

    ChangeStationAction(StationPicker picker, PlaybackState state, Radio radio) {
        this.picker = picker;
        this.state = state;
        this.radio = radio;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.STATION_CHANGE);
    }

    @Override
    public void execute(ActionId id) {
        picker.pick(state.stations(), "Select station: ").ifPresent(radio::tune);
    }
}
