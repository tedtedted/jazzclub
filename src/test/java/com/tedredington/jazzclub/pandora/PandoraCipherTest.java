package com.tedredington.jazzclub.pandora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import com.tedredington.jazzclub.pandora.error.PandoraProtocolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The expected values were produced by pianobar's own {@code crypt.c}, compiled against libgcrypt
 * with pianobar's default keys. Matching them means matching pianobar on the wire.
 */
class PandoraCipherTest {

    private final PandoraCipher cipher = PandoraCipher.forPartner(PartnerCredentials.ANDROID);

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            a|1c853f258cbb42e0
            12345678|c0987ab91d507e5d
            {"syncTime": 1789855649}|dc197ba6264f6957c421a99bf43ab710098245e32b3f997d
            { "userAuthToken": "XXX+/=", "returnAllStations": true }|1ea1d4a04472b0bcea957420092815f1e98ebf4a0ba050976aa48c370383c2d4a74ca9418358145a5400ca674cbd2b072a4c613763b24c1d
            héllo wörld|e9ecda2a41eb08de6dbcdd2163a245e2
            """)
    void encryptsExactlyLikePianobar(String plain, String expectedHex) {
        assertThat(cipher.encrypt(plain)).isEqualTo(expectedHex);
    }

    @Test
    void inputThatFillsABlockExactlyIsNotPaddedFurther() {
        assertThat(cipher.encrypt("12345678")).hasSize(16);
        assertThat(cipher.encrypt("123456789")).hasSize(32);
    }

    @Test
    void decryptsTheServersSyncTimeSkippingTheNoisePrefix() {
        assertThat(cipher.decryptSyncTime("43269bad8ab5c0f2da2b4339dd203985")).isEqualTo(1_789_855_649L);
    }

    @Test
    void decryptReversesEncryptWhenKeysAreSwapped() {
        PartnerCredentials partner = PartnerCredentials.ANDROID;
        PandoraCipher serverSide = new PandoraCipher(partner.decryptKey(), partner.encryptKey());

        byte[] plain = serverSide.decrypt(cipher.encrypt("{\"a\":1}"));

        assertThat(new String(plain, StandardCharsets.UTF_8)).startsWith("{\"a\":1}").hasSize(8);
    }

    @Test
    void rejectsValuesThatAreNotHex() {
        assertThatThrownBy(() -> cipher.decrypt("not hex at all!!"))
                .isInstanceOf(PandoraProtocolException.class)
                .hasMessageContaining("not valid hex");
    }

    @Test
    void rejectsValuesThatAreNotWholeBlocks() {
        assertThatThrownBy(() -> cipher.decrypt("abcd")).isInstanceOf(PandoraProtocolException.class);
        assertThatThrownBy(() -> cipher.decrypt("")).isInstanceOf(PandoraProtocolException.class);
    }

    @Test
    void rejectsASyncTimeWithoutDigits() {
        PartnerCredentials partner = PartnerCredentials.ANDROID;
        PandoraCipher serverSide = new PandoraCipher(partner.decryptKey(), partner.encryptKey());

        assertThatThrownBy(() -> cipher.decryptSyncTime(serverSide.encrypt("\1\2\3\4nope")))
                .isInstanceOf(PandoraProtocolException.class)
                .hasMessageContaining("timestamp");
    }
}
