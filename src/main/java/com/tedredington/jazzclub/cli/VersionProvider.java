package com.tedredington.jazzclub.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

/** Reads the version Maven stamped into {@code META-INF/build-info.properties}. */
public final class VersionProvider implements IVersionProvider {

    private static final String UNKNOWN = "development";

    public static String version() {
        try (InputStream in = VersionProvider.class.getResourceAsStream("/META-INF/build-info.properties")) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("build.version", UNKNOWN);
        } catch (IOException e) {
            return UNKNOWN;
        }
    }

    @Override
    public String[] getVersion() {
        return new String[] {"jazzclub " + version()};
    }
}
