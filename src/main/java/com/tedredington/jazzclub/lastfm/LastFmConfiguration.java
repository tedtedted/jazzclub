package com.tedredington.jazzclub.lastfm;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;

import com.tedredington.jazzclub.config.JazzclubProperties;
import com.tedredington.jazzclub.config.PandoraProperties;
import com.tedredington.jazzclub.config.file.ConfigKey;
import com.tedredington.jazzclub.config.file.UserConfigFile;
import com.tedredington.jazzclub.config.file.XdgDirectories;
import com.tedredington.jazzclub.credentials.CommandRunner;
import com.tedredington.jazzclub.credentials.ConfigFilePassword;
import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;
import com.tedredington.jazzclub.network.HttpClientFactory;
import com.tedredington.jazzclub.ui.MessageType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Where Last.fm meets Spring. Everything Last.fm lives in this package, and nothing outside it refers
 * to it: deleting the package leaves a working player.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LastFmProperties.class)
class LastFmConfiguration {

    private static final Duration DRAIN_ON_EXIT = Duration.ofSeconds(5);

    @Bean(destroyMethod = "shutdownNow")
    @Lazy
    HttpClient lastFmHttpClient(PandoraProperties network) {
        return HttpClientFactory.create(network.timeout(),
                network.streamProxy(System.getenv("http_proxy")), null, network.caBundle());
    }

    @Bean
    LastFmListener lastFmListener(LastFmProperties lastfm, JazzclubProperties app, PandoraProperties network,
                                  CommandRunner commandRunner, EventQueue events, InstantSource clock,
                                  RestClient.Builder restClient,
                                  @Qualifier("lastFmHttpClient") ObjectProvider<HttpClient> http) {
        if (!lastfm.isEnabled()) {
            return new LastFmListener(null, clock, false);
        }
        if (!lastfm.hasApiAccount()) {
            events.publish(new Event.Notice(MessageType.ERROR,
                    "Last.fm: this build of jazzclub has no Last.fm API key, not scrobbling."));
            return new LastFmListener(null, clock, false);
        }
        Path configFile = app.configFile();
        UserConfigFile file = new UserConfigFile();
        ConfigFilePassword password =
                new ConfigFilePassword(ConfigKey.LASTFM_PASSWORD, ConfigKey.LASTFM_PASSWORD_COMMAND, commandRunner);
        Path stateFile = app.stateFile() != null ? app.stateFile() : XdgDirectories.system().stateFile();
        Scrobbler scrobbler = new Scrobbler(
                client(lastfm, network, restClient, http.getObject()),
                lastfm.user(),
                () -> password.find(file.load(configFile)),
                new SessionFile(stateFile.resolveSibling("lastfm-session")),
                events::publish,
                configFile,
                DRAIN_ON_EXIT);
        return new LastFmListener(scrobbler, clock, lastfm.love());
    }

    /**
     * {@code proxy}, {@code timeout} and {@code ca_bundle} are about the network, so they apply here too.
     * {@code control_proxy} and {@code bind_to} exist to get past Pandora's country check, which Last.fm
     * does not have.
     */
    private static LastFmClient client(LastFmProperties lastfm, PandoraProperties network, RestClient.Builder builder,
                                       HttpClient httpClient) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(network.timeout());
        return new HttpLastFmClient(builder.requestFactory(requestFactory).build(), lastfm.endpoint(),
                lastfm.apiKey(), lastfm.apiSecret());
    }
}
