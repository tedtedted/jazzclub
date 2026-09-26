package com.tedredington.jazzclub.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Last.fm is an add-on: it listens to the player, and nothing else knows it exists. Deleting the
 * package must leave a working player.
 */
class LastFmIsolationTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.tedredington.jazzclub");

    @Test
    void nothingOutsideTheLastFmPackageDependsOnIt() {
        noClasses().that().resideOutsideOfPackage("..lastfm..")
                .should().dependOnClassesThat().resideInAPackage("..lastfm..")
                .check(PRODUCTION);
    }

    @Test
    void lastFmUsesNoPandoraClientCodeOnlyItsModel() {
        noClasses().that().resideInAPackage("..lastfm..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.tedredington.jazzclub.pandora", "..pandora.http..", "..pandora.error..")
                .check(PRODUCTION);
    }
}
