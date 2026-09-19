package com.tedredington.jazzclub.pandora;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import com.tedredington.jazzclub.pandora.error.PandoraProtocolException;

/**
 * Pandora's payload "encryption": Blowfish in ECB mode, zero-padded to the 8 byte block size,
 * hex encoded. Byte-for-byte compatible with pianobar's {@code crypt.c}.
 */
public final class PandoraCipher {

    private static final String ALGORITHM = "Blowfish";
    private static final String TRANSFORMATION = "Blowfish/ECB/NoPadding";
    private static final int BLOCK_SIZE = 8;
    /** The server prefixes the sync time with four bytes of noise. */
    private static final int SYNC_TIME_PREFIX = 4;
    private static final HexFormat HEX = HexFormat.of();

    private final SecretKeySpec encryptKey;
    private final SecretKeySpec decryptKey;

    public PandoraCipher(String encryptKey, String decryptKey) {
        this.encryptKey = new SecretKeySpec(encryptKey.getBytes(StandardCharsets.US_ASCII), ALGORITHM);
        this.decryptKey = new SecretKeySpec(decryptKey.getBytes(StandardCharsets.US_ASCII), ALGORITHM);
    }

    public static PandoraCipher forPartner(PartnerCredentials partner) {
        return new PandoraCipher(partner.encryptKey(), partner.decryptKey());
    }

    /** @return lower-case hex of the encrypted, zero-padded UTF-8 bytes of {@code plain} */
    public String encrypt(String plain) {
        byte[] input = plain.getBytes(StandardCharsets.UTF_8);
        int paddedLength = Math.ceilDiv(input.length, BLOCK_SIZE) * BLOCK_SIZE;
        byte[] padded = new byte[paddedLength];
        System.arraycopy(input, 0, padded, 0, input.length);
        return HEX.formatHex(run(Cipher.ENCRYPT_MODE, encryptKey, padded));
    }

    /** @return the decrypted bytes, still including any zero padding */
    public byte[] decrypt(String hex) {
        byte[] input;
        try {
            input = HEX.parseHex(hex);
        } catch (IllegalArgumentException e) {
            throw new PandoraProtocolException("Encrypted value is not valid hex", e);
        }
        if (input.length == 0 || input.length % BLOCK_SIZE != 0) {
            throw new PandoraProtocolException("Encrypted value is not a multiple of the cipher block size");
        }
        return run(Cipher.DECRYPT_MODE, decryptKey, input);
    }

    /** Decrypts the {@code syncTime} of a partner login: four noise bytes, then epoch seconds in ASCII. */
    public long decryptSyncTime(String hex) {
        byte[] plain = decrypt(hex);
        int end = SYNC_TIME_PREFIX;
        while (end < plain.length && plain[end] >= '0' && plain[end] <= '9') {
            end++;
        }
        if (end == SYNC_TIME_PREFIX) {
            throw new PandoraProtocolException("Sync time does not contain a timestamp");
        }
        return Long.parseLong(new String(plain, SYNC_TIME_PREFIX, end - SYNC_TIME_PREFIX, StandardCharsets.US_ASCII));
    }

    private static byte[] run(int mode, SecretKeySpec key, byte[] input) {
        try {
            // Cipher instances are not thread safe and cheap to create, so none is cached.
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, key);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Blowfish is unavailable in this Java runtime", e);
        }
    }
}
