package com.tedredington.jazzclub;

import static org.assertj.core.api.Assertions.assertThat;

import com.tedredington.jazzclub.pandora.DefaultPandoraClient;
import com.tedredington.jazzclub.pandora.PandoraClient;
import com.tedredington.jazzclub.pandora.PandoraTransport;
import com.tedredington.jazzclub.pandora.http.RestClientPandoraTransport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JazzclubApplicationTests {

    @Autowired
    private PandoraClient pandoraClient;

    @Autowired
    private PandoraTransport pandoraTransport;

    @Test
    void contextWiresThePandoraClientOntoTheRestClientTransport() {
        assertThat(pandoraClient).isInstanceOf(DefaultPandoraClient.class);
        assertThat(pandoraTransport).isInstanceOf(RestClientPandoraTransport.class);
    }
}
