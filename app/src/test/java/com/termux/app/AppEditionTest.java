package com.termux.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppEditionTest {

    @Test
    public void aNixBuildInstalledAsComTermuxIsTheNixEdition() {
        assertEquals(AppEdition.NIX, AppEdition.of("com.termux", "nix"));
    }

    @Test
    public void comTermuxOnAptIsTheTermuxEdition() {
        assertEquals(AppEdition.TERMUX, AppEdition.of("com.termux", "apt-android-7"));
    }

    @Test
    public void thePackageNameStillIdentifiesTheOtherEditions() {
        assertEquals(AppEdition.NIX, AppEdition.of(AppEdition.NIX_PACKAGE_NAME, "apt-android-7"));
        assertEquals(AppEdition.VAJ, AppEdition.of(AppEdition.VAJ_PACKAGE_NAME, "apt-android-7"));
    }
}
