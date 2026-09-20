package com.tedredington.jazzclub.network;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * pianobar's {@code ca_bundle}: a PEM file of certificate authorities to trust <em>instead of</em> the
 * system's. For networks that intercept TLS with a private authority.
 */
public final class CaBundle {

    private CaBundle() {
    }

    /** @throws IllegalArgumentException with a message fit for the user */
    public static SSLContext sslContext(Path pemFile) {
        try (InputStream in = Files.newInputStream(pemFile)) {
            Collection<? extends Certificate> certificates =
                    CertificateFactory.getInstance("X.509").generateCertificates(in);
            if (certificates.isEmpty()) {
                throw new IllegalArgumentException("ca_bundle is invalid: " + pemFile + " contains no certificates");
            }
            KeyStore trusted = KeyStore.getInstance(KeyStore.getDefaultType());
            trusted.load(null, null);
            int index = 0;
            for (Certificate certificate : certificates) {
                trusted.setCertificateEntry("ca-" + index++, certificate);
            }
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trusted);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers.getTrustManagers(), null);
            return context;
        } catch (IOException e) {
            throw new IllegalArgumentException("ca_bundle is invalid: cannot read " + pemFile, e);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("ca_bundle is invalid: " + pemFile + " is not a PEM certificate file", e);
        }
    }
}
