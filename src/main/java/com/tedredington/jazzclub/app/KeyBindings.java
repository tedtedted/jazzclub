package com.tedredington.jazzclub.app;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Which key triggers which action: the defaults, changed by {@code act_*} settings. */
public final class KeyBindings {

    /** pianobar's special value that unbinds an action. */
    private static final String DISABLED = "disabled";

    private final Map<ActionId, Character> keys = new EnumMap<>(ActionId.class);

    /**
     * @param overrides config key to value, e.g. {@code act_songlove -> l}
     * @throws IllegalArgumentException for a value that is not exactly one character or {@code disabled}
     */
    public KeyBindings(Map<String, String> overrides) {
        for (ActionId action : ActionId.values()) {
            keys.put(action, action.defaultKey());
        }
        overrides.forEach((configKey, value) -> {
            ActionId action = ActionId.fromConfigKey(configKey)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown key binding: " + configKey));
            if (DISABLED.equals(value)) {
                keys.remove(action);
            } else if (value.length() == 1) {
                keys.put(action, value.charAt(0));
            } else {
                throw new IllegalArgumentException(
                        configKey + " must be a single character or 'disabled', was '" + value + "'");
            }
        });
    }

    /** The first action bound to the key, in {@link ActionId} order, like pianobar's dispatch table. */
    public Optional<ActionId> actionFor(char key) {
        return keys.entrySet().stream().filter(e -> e.getValue() == key).map(Map.Entry::getKey).findFirst();
    }

    public Optional<Character> keyFor(ActionId action) {
        return Optional.ofNullable(keys.get(action));
    }
}
