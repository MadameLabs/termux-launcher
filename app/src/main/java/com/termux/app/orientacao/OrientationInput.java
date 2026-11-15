package com.termux.app.orientacao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** What the orientation bar knows about the focused pane, as read from its terminal and process. */
public final class OrientationInput {

    /** {@code user@host} of the last OSC 7 from another machine, or null. */
    @Nullable public final String remoteUserHost;
    @Nullable public final String remotePath;
    /** This device's directory for the pane, from OSC 7 or the shell. */
    @Nullable public final String localPath;
    /** The foreground process's argv, empty when unknown. */
    @NonNull public final List<String> foregroundCommand;
    /** The terminal title, which a coding agent sets to its chat's name. */
    @Nullable public final String title;
    /** Whether a coding agent is recognised in the pane. */
    public final boolean agentPresent;
    /** The harness's {@code ml_5h} and {@code ml_semana} user variables, raw. */
    @Nullable public final String fiveHour;
    @Nullable public final String weekly;

    public OrientationInput(@Nullable String remoteUserHost, @Nullable String remotePath,
                            @Nullable String localPath, @Nullable List<String> foregroundCommand,
                            @Nullable String title, boolean agentPresent,
                            @Nullable String fiveHour, @Nullable String weekly) {
        this.remoteUserHost = remoteUserHost;
        this.remotePath = remotePath;
        this.localPath = localPath;
        this.foregroundCommand = foregroundCommand == null ? Collections.emptyList() : foregroundCommand;
        this.title = title;
        this.agentPresent = agentPresent;
        this.fiveHour = fiveHour;
        this.weekly = weekly;
    }
}
