package com.tedredington.jazzclub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyBindings;
import com.tedredington.jazzclub.app.StationSort;
import com.tedredington.jazzclub.credentials.CredentialsProvider;
import com.tedredington.jazzclub.pandora.UserCredentials;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** The whole path: file on disk, post-processor, property binding, credentials bean. */
@SpringBootTest(properties = "jazzclub.config-file=src/test/resources/config/sample-config")
class ConfigFileBindingTest {

    @Autowired
    private JazzclubProperties jazzclubProperties;

    @Autowired
    private PandoraProperties pandoraProperties;

    @Autowired
    private CredentialsProvider credentialsProvider;

    @Autowired
    private Environment environment;

    @Autowired
    private KeyBindings keyBindings;

    @Test
    void fileSettingsReachTheTypedProperties() {
        assertThat(jazzclubProperties.audioQuality()).isEqualTo(AudioQuality.LOW);
        assertThat(pandoraProperties.rpcHost()).isEqualTo("internal-tuner.pandora.com");
        // pianobar's bare number means seconds, not Spring's default of milliseconds
        assertThat(pandoraProperties.timeout()).isEqualTo(Duration.ofSeconds(12));
    }

    @Test
    void playerSettingsKeyBindingsAndIconsSurviveTheTrip() {
        assertThat(jazzclubProperties.volume()).isEqualTo(-4);
        assertThat(jazzclubProperties.keys()).containsEntry("act_songlove", "l");
        // two spaces after '=' in the file: pianobar drops one, the icon keeps the other
        assertThat(jazzclubProperties.format().loveIcon()).isEqualTo(" [loved]");
        assertThat(keyBindings.actionFor('l')).contains(ActionId.SONG_LOVE);
    }

    @Test
    void pianobarsSpellingsBindToTypedSettings() {
        assertThat(jazzclubProperties.sort()).isEqualTo(StationSort.QUICKMIX_01_NAME_ZA);
        assertThat(jazzclubProperties.autoselect()).as("pianobar writes booleans as 0 and 1").isFalse();
        assertThat(jazzclubProperties.format().msg()).containsEntry("err", "!! %s");
    }

    @Test
    void theRunnerStaysIdleUnlessLaunchedFromMain() {
        assertThat(jazzclubProperties.interactive()).isFalse();
    }

    @Test
    void settingsAbsentFromTheFileKeepTheirDefaults() {
        assertThat(pandoraProperties.rpcTlsPort()).isEqualTo(443);
        assertThat(pandoraProperties.partner().user()).isEqualTo("android");
    }

    @Test
    void credentialsComeFromTheSameFileVerbatim() {
        // "${a}" would have been mangled had the password travelled through Spring's Environment
        assertThat(credentialsProvider.credentials())
                .isEqualTo(new UserCredentials("listener@example.com", "not${a}placeholder"));
        assertThat(environment.getProperty("password")).isNull();
    }
}
