package com.tedredington.jazzclub.config;

import java.net.http.HttpClient;
import java.time.InstantSource;

import com.tedredington.jazzclub.network.HttpClientFactory;
import com.tedredington.jazzclub.pandora.DefaultPandoraClient;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.PandoraTransport;
import com.tedredington.jazzclub.pandora.http.RestClientPandoraTransport;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The only place where the framework-free Pandora client meets Spring. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PandoraProperties.class)
class PandoraConfiguration {

    @Bean
    InstantSource instantSource() {
        return InstantSource.system();
    }

    @Bean(destroyMethod = "shutdownNow")
    HttpClient pandoraHttpClient(PandoraProperties properties) {
        return HttpClientFactory.create(properties.timeout(),
                properties.apiProxy(System.getenv("http_proxy")), properties.bindTo(), properties.caBundle());
    }

    @Bean
    PandoraTransport pandoraTransport(RestClient.Builder builder, PandoraProperties properties,
                                     @Qualifier("pandoraHttpClient") HttpClient httpClient) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return new RestClientPandoraTransport(builder.requestFactory(requestFactory).build());
    }

    @Bean
    PandoraClient pandoraClient(PandoraTransport transport, PandoraProperties properties, InstantSource clock) {
        return new DefaultPandoraClient(transport, properties.partner().toCredentials(), properties.baseUri(), clock);
    }
}
