package com.termux.app.tour;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TourEditionTest {

    @Test
    public void aNixBuildInstalledAsComTermuxIsTheNixEdition() {
        assertEquals(TourEdition.NIX, TourEdition.of("com.termux", "nix"));
    }

    @Test
    public void comTermuxOnAptIsTheTermuxEdition() {
        assertEquals(TourEdition.TERMUX, TourEdition.of("com.termux", "apt-android-7"));
    }

    @Test
    public void thePackageNameStillIdentifiesTheOtherEditions() {
        assertEquals(TourEdition.NIX, TourEdition.of(TourEdition.NIX_PACKAGE_NAME, "apt-android-7"));
        assertEquals(TourEdition.VAJ, TourEdition.of(TourEdition.VAJ_PACKAGE_NAME, "apt-android-7"));
    }
}
