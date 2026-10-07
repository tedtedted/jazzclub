package com.tedredington.jazzclub.config;

import java.nio.file.Path;
import java.util.Map;

import com.tedredington.jazzclub.app.StationSort;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.player.DecoderType;
import com.tedredington.jazzclub.player.PcmFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Player-level settings. Defaults equal pianobar's.
 *
 * @param configFile       the config file in effect; set by {@link ConfigFileEnvironmentPostProcessor}
 * @param stateFile        where volume and last station are remembered; set the same way
 * @param interactive      run the player; {@code false} in tests, which only want the wiring
 * @param audioQuality     {@code audio_quality}: low, medium or high
 * @param volume           {@code volume}: initial volume correction in dB
 * @param gainMul          {@code gain_mul}: how much of Pandora's ReplayGain to apply, 0.0 to 1.0
 * @param history          {@code history}: how many played songs to remember
 * @param maxRetry         {@code max_retry}: consecutive playback failures before the station is stopped
 * @param autostartStation {@code autostart_station}: station id to play without asking
 * @param sort             {@code sort}: order of the station list
 * @param autoselect       {@code autoselect}: take a single remaining match in the station menu without asking
 * @param bufferSeconds    {@code buffer_seconds}: how much decoded audio to keep ahead of playback
 * @param sampleRate       {@code sample_rate}: output rate in Hz; 0 for the stream's own, 44100
 * @param audioPipe        {@code audio_pipe}: named pipe to write raw audio to instead of the sound card
 * @param decoder          {@code decoder}: lavaplayer (built in) or ffmpeg (external, needs ffmpeg installed)
 * @param eventCommand     {@code event_command}: executable run for every player event
 * @param fifo             {@code fifo}: named pipe for remote control; default {@code ctl} next to the config
 * @param ffmpeg           name or path of the ffmpeg executable
 * @param keys             {@code act_*} overrides, keyed by pianobar's config key
 */
@ConfigurationProperties("jazzclub")
@Validated
public record JazzclubProperties(
        Path configFile,
        Path stateFile,
        @DefaultValue("false") boolean interactive,
        @DefaultValue("high") AudioQuality audioQuality,
        @DefaultValue("0") int volume,
        @DefaultValue("1.0") @DecimalMin(value = "0", message = "gain_mul must not be negative") double gainMul,
        @DefaultValue("5") @PositiveOrZero(message = "history must not be negative") int history,
        @DefaultValue("3") @Positive(message = "max_retry must be at least 1") int maxRetry,
        String autostartStation,
        @DefaultValue("name_az") StationSort sort,
        @DefaultValue("true") boolean autoselect,
        @DefaultValue("5") @Min(value = 0, message = "buffer_seconds must be between 0 and 600")
        @Max(value = 600, message = "buffer_seconds must be between 0 and 600") int bufferSeconds,
        @DefaultValue("0") int sampleRate,
        Path audioPipe,
        @DefaultValue("lavaplayer") DecoderType decoder,
        String eventCommand,
        Path fifo,
        @DefaultValue("ffmpeg") String ffmpeg,
        @DefaultValue Format format,
        @DefaultValue Map<String, String> keys) {

    public JazzclubProperties {
        if (!Double.isFinite(gainMul)) {
            throw new IllegalArgumentException("gain_mul must be finite, was " + gainMul);
        }
        PcmFormat.of(sampleRate); // validates
    }

    /** pianobar's {@code format_*} strings and icons. */
    public record Format(
            @DefaultValue("\"%t\" by \"%a\" on \"%l\"%r%@%s") String nowplayingSong,
            @DefaultValue("Station \"%n\" (%i)") String nowplayingStation,
            @DefaultValue("%i) %a - %t%r") String listSong,
            @DefaultValue("%s%r/%t") String time,
            @DefaultValue(" <3") String loveIcon,
            @DefaultValue(" </3") String banIcon,
            @DefaultValue(" zZ") String tiredIcon,
            @DefaultValue(" @ ") String atIcon,
            @DefaultValue Map<String, String> msg) {
    }
}
