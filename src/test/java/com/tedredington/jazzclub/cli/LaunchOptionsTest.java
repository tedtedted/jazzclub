package com.tedredington.jazzclub.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LaunchOptionsTest {

    @Test
    void alwaysSwitchesThePlayerOn() {
        assertThat(new LaunchOptions(0, null).toProperties())
                .containsEntry("jazzclub.interactive", "true")
                .doesNotContainKey("jazzclub.config-file")
                .doesNotContainKey("logging.level.com.tedredington.jazzclub");
    }

    @Test
    void oneVMeansInfoForJazzclubOnly() {
        assertThat(new LaunchOptions(1, null).toProperties())
                .containsEntry("logging.level.com.tedredington.jazzclub", "INFO")
                .doesNotContainKey("logging.level.root");
    }

    @Test
    void twoOrMoreMeanDebugPlusInfoFromLibraries() {
        assertThat(new LaunchOptions(2, null).toProperties())
                .containsEntry("logging.level.com.tedredington.jazzclub", "DEBUG")
                .containsEntry("logging.level.root", "INFO");
        assertThat(new LaunchOptions(5, null).toProperties())
                .containsEntry("logging.level.com.tedredington.jazzclub", "DEBUG");
    }

    @Test
    void aRelativeConfigPathIsResolvedBeforeTheWorkingDirectoryCanChange() {
        assertThat(new LaunchOptions(0, Path.of("my-config")).toProperties().get("jazzclub.config-file"))
                .asString().startsWith("/").endsWith("/my-config");
    }

    @Test
    void versionFallsBackOutsideAPackagedBuild() {
        assertThat(VersionProvider.version()).isNotBlank();
        assertThat(new VersionProvider().getVersion()).singleElement().asString().startsWith("jazzclub ");
    }
}
