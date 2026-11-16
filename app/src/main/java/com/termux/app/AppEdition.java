package com.termux.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.BuildConfig;

/**
 * Which of the three shipped editions is running, as far as the screens that print install
 * commands have to care.
 *
 * <p>The package name identifies the shipped editions. A Nix build installed as com.termux (the
 * MadameLabs fork) also needs its bootstrap variant, since its application ID matches the regular
 * Termux edition.
 */
public enum AppEdition {

    /** {@code com.termux}: the Termux repositories and {@code pkg}. */
    TERMUX,
    /** {@code com.termux.launcher.nix}: nixpkgs and the nix profile. */
    NIX,
    /** {@code io.vaj.tl}: the demo edition, on its own apt repository but still {@code pkg}. */
    VAJ;

    public static final String NIX_PACKAGE_NAME = "com.termux.launcher.nix";
    public static final String VAJ_PACKAGE_NAME = "io.vaj.tl";

    /** The edition this build is: its own package name and the bootstrap variant it was built with. */
    @NonNull
    public static AppEdition running(@Nullable Context context) {
        return of(context == null ? null : context.getPackageName(), BuildConfig.TERMUX_PACKAGE_VARIANT);
    }

    /** The edition for {@code packageName} built with {@code packageVariant}; anything unknown is Termux. */
    @NonNull
    public static AppEdition of(@Nullable String packageName, @Nullable String packageVariant) {
        if ("nix".equals(packageVariant)) return NIX;
        return of(packageName);
    }

    /** The edition that ships under {@code packageName}; anything unknown is treated as Termux. */
    @NonNull
    public static AppEdition of(@Nullable String packageName) {
        if (NIX_PACKAGE_NAME.equals(packageName)) return NIX;
        if (VAJ_PACKAGE_NAME.equals(packageName)) return VAJ;
        return TERMUX;
    }

    /** Whether this edition installs with nix rather than {@code pkg}. */
    public boolean usesNixPackages() {
        return this == NIX;
    }
}
