package com.termux.app.orientacao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** What the orientation bar draws: one chip per field, a null field draws no chip. */
public final class OrientationLine {

    /** {@code user@host} in lower case, or {@code tlnix} for this device. Never cut. */
    @NonNull public final String machine;
    @Nullable public final String path;
    @Nullable public final String chat;
    /** Percent of the harness's limits already spent, 0–100. */
    @Nullable public final Integer fiveHour;
    @Nullable public final Integer weekly;
    public final boolean fiveHourHot;
    /** MadameLabs fork: when the 5 h limit renews, {@code HH:MM}, or null. */
    @Nullable public final String fiveHourReset;
    public final boolean weeklyHot;

    public OrientationLine(@NonNull String machine, @Nullable String path, @Nullable String chat,
                           @Nullable Integer fiveHour, @Nullable Integer weekly) {
        this(machine, path, chat, fiveHour, weekly, null);
    }

    public OrientationLine(@NonNull String machine, @Nullable String path, @Nullable String chat,
                           @Nullable Integer fiveHour, @Nullable Integer weekly,
                           @Nullable String fiveHourReset) {
        this.fiveHourReset = fiveHour == null ? null : fiveHourReset;
        this.machine = machine;
        this.path = path;
        this.chat = chat;
        this.fiveHour = fiveHour;
        this.weekly = weekly;
        this.fiveHourHot = fiveHour != null && fiveHour > OrientationModel.HOT_ABOVE;
        this.weeklyHot = weekly != null && weekly > OrientationModel.HOT_ABOVE;
    }

    @NonNull
    OrientationLine with(@Nullable String newPath, @Nullable String newChat) {
        return new OrientationLine(machine, newPath, newChat, fiveHour, weekly, fiveHourReset);
    }
}
