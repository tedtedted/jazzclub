package com.tedredington.jazzclub.app.action;

import java.util.Optional;
import java.util.Set;

import com.tedredington.jazzclub.app.ActionContext;
import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.PandoraCalls;
import com.tedredington.jazzclub.app.Selection;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.model.AccountChange;
import com.tedredington.jazzclub.pandora.model.AccountSettings;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Prompter;
import org.springframework.stereotype.Component;

/** View and change the Pandora account: user name, password, explicit content filter. */
@Component
class SettingsAction implements KeyAction {

    private final PandoraClient client;
    private final PandoraCalls calls;
    private final Prompter prompter;
    private final Console console;

    SettingsAction(PandoraClient client, PandoraCalls calls, Prompter prompter, Console console) {
        this.client = client;
        this.calls = calls;
        this.prompter = prompter;
        this.console = console;
    }

    @Override
    public Set<ActionId> ids() {
        return Set.of(ActionId.SETTINGS);
    }

    @Override
    public void execute(ActionId id, ActionContext context) {
        AccountSettings settings = calls.call("Retrieving settings... ", EventType.SETTINGS_GET, Selection.NONE,
                client::accountSettings);
        console.list(" 0) Username (" + settings.username() + ")\n");
        console.list(" 1) Password (*****)\n");
        console.list(" 2) Explicit content filter (" + (settings.explicitContentFilter() ? "yes" : "no") + ")\n");

        AccountChange change = AccountChange.NONE;
        while (true) {
            console.print(MessageType.QUESTION, "Change setting: ");
            Optional<Integer> setting = prompter.readNumber();
            if (setting.isEmpty()) {
                break;
            }
            switch (setting.get()) {
                case 0 -> {
                    console.print(MessageType.QUESTION, "New username: ");
                    change = prompter.readLine().map(change::withUsername).orElse(change);
                }
                case 1 -> {
                    console.print(MessageType.QUESTION, "New password: ");
                    change = prompter.readSecret().map(change::withPassword).orElse(change);
                }
                case 2 -> {
                    console.print(MessageType.QUESTION, "Enable explicit content filter? [yn] ");
                    change = change.withExplicitContentFilter(prompter.confirm(settings.explicitContentFilter()));
                }
                default -> {
                    // not a setting; ask again
                }
            }
        }
        if (change.isEmpty()) {
            return;
        }
        AccountChange requested = change;
        calls.run("Changing settings... ", EventType.SETTINGS_CHANGE, Selection.NONE,
                () -> client.changeAccount(requested));
        if (requested.newUsername() != null || requested.newPassword() != null) {
            // this session carries on with the new login; the next start reads the file again
            console.info("Remember to update your config file, or the next start cannot log in.\n");
        }
    }
}
