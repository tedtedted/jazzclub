package com.tedredington.jazzclub.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;

import com.tedredington.jazzclub.player.ffmpeg.FfmpegCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FfmpegCommandTest {

    @Test
    void decodesTheUrlToRawPcmOnStandardOutput() {
        List<String> command = FfmpegCommand.decodeToPcm("/opt/bin/ffmpeg", URI.create("https://a.example/x.m4a?t=1"));

        assertThat(command).startsWith("/opt/bin/ffmpeg").endsWith("pipe:1")
                .containsSequence("-i", "https://a.example/x.m4a?t=1")
                .containsSequence("-f", "s16le")
                .containsSequence("-ar", "44100")
                .containsSequence("-ac", "2")
                .contains("-nostdin");
    }

    @Test
    void ffmpegMayOnlySpeakHttp() {
        assertThat(FfmpegCommand.decodeToPcm("ffmpeg", URI.create("http://a.example/x")))
                .containsSequence("-protocol_whitelist", "http,https,tcp,tls,crypto");
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "/etc/passwd", "concat:a%7Cb", "ftp://a.example/x", "pipe:0"})
    void aUrlFromTheNetworkCanNeverPointFfmpegAtLocalFilesOrOtherProtocols(String url) {
        assertThatThrownBy(() -> FfmpegCommand.decodeToPcm("ffmpeg", URI.create(url)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid song url.");
    }
}
