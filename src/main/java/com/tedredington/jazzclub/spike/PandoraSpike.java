package com.tedredington.jazzclub.spike;

import java.net.URI;
import java.time.Instant;

import com.tedredington.jazzclub.config.PandoraProperties;
import com.tedredington.jazzclub.pandora.PandoraCipher;
import com.tedredington.jazzclub.pandora.PandoraTransport;
import com.tedredington.jazzclub.pandora.PartnerCredentials;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Live check of the partner handshake only: public app credentials, no listener account involved.
 * Proves HTTPS, Jackson and Blowfish all work against the real server, also as a native image.
 */
@Component
class PandoraSpike {

    private final PandoraTransport transport;
    private final PandoraProperties properties;

    PandoraSpike(PandoraTransport transport, PandoraProperties properties) {
        this.transport = transport;
        this.properties = properties;
    }

    void run() {
        PartnerCredentials partner = properties.partner().toCredentials();
        JsonMapper json = JsonMapper.shared();
        String body = json.writeValueAsString(json.createObjectNode()
                .put("username", partner.user())
                .put("password", partner.password())
                .put("deviceModel", partner.deviceModel())
                .put("version", "5")
                .put("includeUrls", true));

        JsonNode response = json.readTree(transport.post(
                URI.create(properties.baseUri() + "/services/json/?method=auth.partnerLogin"), body));
        System.out.println("stat=" + response.path("stat").asString("?")
                + (response.has("code") ? " code=" + response.path("code").asInt() : ""));

        JsonNode result = response.path("result");
        if (!result.isMissingNode()) {
            long serverTime = PandoraCipher.forPartner(partner).decryptSyncTime(result.path("syncTime").asString());
            System.out.println("partnerId=" + result.path("partnerId").asString()
                    + " tokenLength=" + result.path("partnerAuthToken").asString("").length());
            System.out.println("server time " + Instant.ofEpochSecond(serverTime)
                    + ", local clock offset " + (Instant.now().getEpochSecond() - serverTime) + "s");
        }
    }
}
