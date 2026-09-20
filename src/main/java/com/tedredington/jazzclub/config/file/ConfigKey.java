package com.tedredington.jazzclub.config.file;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every config file key jazzclub understands, and the Spring property it feeds. This is the single
 * place that ties pianobar's names to {@code @ConfigurationProperties}.
 *
 * <p>Keys with {@code property == null} are secrets. They are read straight from the file and are
 * never copied into Spring's {@code Environment}.
 */
public enum ConfigKey {

    USER("user", null),
    PASSWORD("password", null),
    PASSWORD_COMMAND("password_command", null),

    AUDIO_QUALITY("audio_quality", "jazzclub.audio-quality"),
    VOLUME("volume", "jazzclub.volume"),
    GAIN_MUL("gain_mul", "jazzclub.gain-mul"),
    HISTORY("history", "jazzclub.history"),
    MAX_RETRY("max_retry", "jazzclub.max-retry"),
    AUTOSTART_STATION("autostart_station", "jazzclub.autostart-station"),
    EVENT_COMMAND("event_command", "jazzclub.event-command"),
    FIFO("fifo", "jazzclub.fifo"),

    FORMAT_NOWPLAYING_SONG("format_nowplaying_song", "jazzclub.format.nowplaying-song"),
    FORMAT_NOWPLAYING_STATION("format_nowplaying_station", "jazzclub.format.nowplaying-station"),
    FORMAT_LIST_SONG("format_list_song", "jazzclub.format.list-song"),
    FORMAT_TIME("format_time", "jazzclub.format.time"),
    LOVE_ICON("love_icon", "jazzclub.format.love-icon"),
    BAN_ICON("ban_icon", "jazzclub.format.ban-icon"),
    TIRED_ICON("tired_icon", "jazzclub.format.tired-icon"),
    AT_ICON("at_icon", "jazzclub.format.at-icon"),

    RPC_HOST("rpc_host", "jazzclub.pandora.rpc-host"),
    RPC_TLS_PORT("rpc_tls_port", "jazzclub.pandora.rpc-tls-port"),
    TIMEOUT("timeout", "jazzclub.pandora.timeout"),
    PARTNER_USER("partner_user", "jazzclub.pandora.partner.user"),
    PARTNER_PASSWORD("partner_password", "jazzclub.pandora.partner.password"),
    DEVICE("device", "jazzclub.pandora.partner.device"),
    ENCRYPT_PASSWORD("encrypt_password", "jazzclub.pandora.partner.encrypt-password"),
    DECRYPT_PASSWORD("decrypt_password", "jazzclub.pandora.partner.decrypt-password");

    private static final Map<String, ConfigKey> BY_FILE_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ConfigKey::fileKey, Function.identity()));

    private final String fileKey;
    private final String property;

    ConfigKey(String fileKey, String property) {
        this.fileKey = fileKey;
        this.property = property;
    }

    public static ConfigKey fromFileKey(String fileKey) {
        return BY_FILE_KEY.get(fileKey);
    }

    public String fileKey() {
        return fileKey;
    }

    /** @return the Spring property name, or {@code null} for secrets */
    public String property() {
        return property;
    }

    public boolean isSecret() {
        return property == null;
    }
}
