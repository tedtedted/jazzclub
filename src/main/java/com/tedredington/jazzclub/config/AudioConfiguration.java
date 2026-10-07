package com.tedredington.jazzclub.config;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Supplier;

import com.tedredington.jazzclub.config.file.XdgDirectories;
import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;
import com.tedredington.jazzclub.network.HttpClientFactory;
import com.tedredington.jazzclub.player.AudioPlayer;
import com.tedredington.jazzclub.player.AudioSink;
import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.PcmFormat;
import com.tedredington.jazzclub.player.StreamingAudioPlayer;
import com.tedredington.jazzclub.player.ffmpeg.FfmpegDecoder;
import com.tedredington.jazzclub.player.javasound.JavaSoundAudioSink;
import com.tedredington.jazzclub.player.javasound.JavaSoundNativeSupport;
import com.tedredington.jazzclub.player.lavaplayer.DownloadPolicy;
import com.tedredington.jazzclub.player.lavaplayer.LavaplayerDecoder;
import com.tedredington.jazzclub.player.lavaplayer.LavaplayerNatives;
import com.tedredington.jazzclub.player.pipe.PipeAudioSink;
import com.tedredington.jazzclub.ui.Console;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Playback is cheap to assemble; decoder, native, and network resources wait for the first song. */
@Configuration(proxyBeanMethods = false)
class AudioConfiguration {

    @Bean(destroyMethod = "shutdownNow")
    @Lazy
    HttpClient streamHttpClient(PandoraProperties network) {
        // Audio only uses the stream proxy; bind_to and ca_bundle belong to the control connection.
        return HttpClientFactory.create(network.timeout(), network.streamProxy(System.getenv("http_proxy")),
                null, null);
    }

    @Bean(destroyMethod = "shutdownNow")
    @Lazy
    ScheduledExecutorService downloadWatchdog() {
        ScheduledThreadPoolExecutor watchdog = new ScheduledThreadPoolExecutor(1,
                Thread.ofPlatform().name("song-download-watchdog").daemon(true).factory());
        watchdog.setRemoveOnCancelPolicy(true);
        return watchdog;
    }

    @Bean
    @Lazy
    Decoder.Factory decoderFactory(JazzclubProperties properties, PandoraProperties network, Console console,
                                   @Qualifier("streamHttpClient") ObjectProvider<HttpClient> http,
                                   @Qualifier("downloadWatchdog") ObjectProvider<ScheduledExecutorService> watchdog) {
        Path nativeLibraries = XdgDirectories.system().cacheDirectory().resolve("lib");
        PcmFormat format = PcmFormat.of(properties.sampleRate());
        Decoder.Factory decoders = DecoderSelection.choose(properties.decoder(), format,
                () -> {
                    LavaplayerNatives.prepare(nativeLibraries);
                    return LavaplayerDecoder.factory(http.getObject(), format,
                            DownloadPolicy.withTimeout(network.timeout()), watchdog.getObject());
                },
                () -> {
                    var proxy = network.streamProxy(System.getenv("http_proxy"));
                    return FfmpegDecoder.factory(properties.ffmpeg(),
                            proxy == null ? null : proxy.toEnvironmentValue(), format);
                },
                message -> console.info(message + "\n"));
        return decoders.prefetching(properties.bufferSeconds() * format.bytesPerSecond());
    }

    @Bean(destroyMethod = "stop")
    AudioPlayer audioPlayer(JazzclubProperties properties, EventQueue events, ObjectProvider<Decoder.Factory> decoders) {
        PcmFormat format = PcmFormat.of(properties.sampleRate());
        Supplier<AudioSink> sinks = properties.audioPipe() != null
                ? () -> new PipeAudioSink(properties.audioPipe(), format)
                : () -> {
                    JavaSoundNativeSupport.prepare(XdgDirectories.system().cacheDirectory().resolve("lib"));
                    return new JavaSoundAudioSink(format);
                };
        return new StreamingAudioPlayer(uri -> decoders.getObject().open(uri), sinks,
                (id, result) -> events.publish(new Event.TrackFinished(id, result)),
                properties.volume(), properties.gainMul());
    }
}
