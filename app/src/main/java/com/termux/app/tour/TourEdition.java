package com.termux.app.tour;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Which of the three shipped editions is running, as far as the closing card has to care.
 *
 * <p>The package name identifies the shipped editions. A Nix build installed as com.termux also
 * needs its package variant, since its application ID matches the regular Termux edition.
 */
public enum TourEdition {

    /** {@code com.termux}: the Termux repositories and {@code pkg}. */
    TERMUX,
    /** {@code com.termux.launcher.nix}: nixpkgs and the nix profile. */
    NIX,
    /** {@code io.vaj.tl}: the demo edition, on its own apt repository but still {@code pkg}. */
    VAJ;

    public static final String NIX_PACKAGE_NAME = "com.termux.launcher.nix";
    public static final String VAJ_PACKAGE_NAME = "io.vaj.tl";

    /** The edition that ships under {@code packageName}; anything unknown is treated as Termux. */
    @NonNull
    public static TourEdition of(@Nullable String packageName) {
        return of(packageName, null);
    }

    @NonNull
    public static TourEdition of(@Nullable String packageName, @Nullable String packageVariant) {
        if ("nix".equals(packageVariant)) return NIX;
        if (NIX_PACKAGE_NAME.equals(packageName)) return NIX;
        if (VAJ_PACKAGE_NAME.equals(packageName)) return VAJ;
        return TERMUX;
    }

    /** Whether this edition installs with nix rather than {@code pkg}. */
    public boolean usesNixPackages() {
        return this == NIX;
    }
}
