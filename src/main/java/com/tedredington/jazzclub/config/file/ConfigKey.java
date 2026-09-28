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
    LASTFM_PASSWORD("lastfm_password", null),
    LASTFM_PASSWORD_COMMAND("lastfm_password_command", null),

    AUDIO_QUALITY("audio_quality", "jazzclub.audio-quality"),
    VOLUME("volume", "jazzclub.volume"),
    GAIN_MUL("gain_mul", "jazzclub.gain-mul"),
    HISTORY("history", "jazzclub.history"),
    MAX_RETRY("max_retry", "jazzclub.max-retry"),
    AUTOSTART_STATION("autostart_station", "jazzclub.autostart-station"),
    SORT("sort", "jazzclub.sort"),
    AUTOSELECT("autoselect", "jazzclub.autoselect"),
    BUFFER_SECONDS("buffer_seconds", "jazzclub.buffer-seconds"),
    SAMPLE_RATE("sample_rate", "jazzclub.sample-rate"),
    AUDIO_PIPE("audio_pipe", "jazzclub.audio-pipe", true),
    DECODER("decoder", "jazzclub.decoder"),
    EVENT_COMMAND("event_command", "jazzclub.event-command", true),
    FIFO("fifo", "jazzclub.fifo", true),
    LASTFM_USER("lastfm_user", "jazzclub.lastfm.user"),

    FORMAT_NOWPLAYING_SONG("format_nowplaying_song", "jazzclub.format.nowplaying-song"),
    FORMAT_NOWPLAYING_STATION("format_nowplaying_station", "jazzclub.format.nowplaying-station"),
    FORMAT_LIST_SONG("format_list_song", "jazzclub.format.list-song"),
    FORMAT_TIME("format_time", "jazzclub.format.time"),
    LOVE_ICON("love_icon", "jazzclub.format.love-icon"),
    BAN_ICON("ban_icon", "jazzclub.format.ban-icon"),
    TIRED_ICON("tired_icon", "jazzclub.format.tired-icon"),
    AT_ICON("at_icon", "jazzclub.format.at-icon"),
    FORMAT_MSG_NONE("format_msg_none", "jazzclub.format.msg[none]"),
    FORMAT_MSG_INFO("format_msg_info", "jazzclub.format.msg[info]"),
    FORMAT_MSG_NOWPLAYING("format_msg_nowplaying", "jazzclub.format.msg[nowplaying]"),
    FORMAT_MSG_TIME("format_msg_time", "jazzclub.format.msg[time]"),
    FORMAT_MSG_ERR("format_msg_err", "jazzclub.format.msg[err]"),
    FORMAT_MSG_QUESTION("format_msg_question", "jazzclub.format.msg[question]"),
    FORMAT_MSG_LIST("format_msg_list", "jazzclub.format.msg[list]"),

    RPC_HOST("rpc_host", "jazzclub.pandora.rpc-host"),
    RPC_TLS_PORT("rpc_tls_port", "jazzclub.pandora.rpc-tls-port"),
    TIMEOUT("timeout", "jazzclub.pandora.timeout"),
    PROXY("proxy", "jazzclub.pandora.proxy"),
    CONTROL_PROXY("control_proxy", "jazzclub.pandora.control-proxy"),
    BIND_TO("bind_to", "jazzclub.pandora.bind-to"),
    CA_BUNDLE("ca_bundle", "jazzclub.pandora.ca-bundle"),
    PARTNER_USER("partner_user", "jazzclub.pandora.partner.user"),
    PARTNER_PASSWORD("partner_password", "jazzclub.pandora.partner.password"),
    DEVICE("device", "jazzclub.pandora.partner.device"),
    ENCRYPT_PASSWORD("encrypt_password", "jazzclub.pandora.partner.encrypt-password"),
    DECRYPT_PASSWORD("decrypt_password", "jazzclub.pandora.partner.decrypt-password");

    private static final Map<String, ConfigKey> BY_FILE_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ConfigKey::fileKey, Function.identity()));

    private final String fileKey;
    private final String property;
    private final boolean path;

    ConfigKey(String fileKey, String property) {
        this(fileKey, property, false);
    }

    /** @param path pianobar expands a leading {@code ~/} in these values, since no shell ever sees them */
    ConfigKey(String fileKey, String property, boolean path) {
        this.fileKey = fileKey;
        this.property = property;
        this.path = path;
    }

    /** The value as it should reach Spring: with {@code ~/} expanded for settings that name a file. */
    public String resolve(String value, String userHome) {
        return path && value.startsWith("~/") ? userHome + value.substring(1) : value;
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
