# Security policy

This is the Madame Labs fork of [PickleHik3/termux-launcher](https://github.com/PickleHik3/termux-launcher).
It has no releases: only the current `main` branch is maintained, and builds are made from it
locally.

## Reporting a vulnerability

Report it privately through GitHub: open this repository's **Security** tab and choose
**Report a vulnerability**. Please do not open a public issue for it.

Include:
- the commit you built (Settings → About shows the version),
- the Android version and device model,
- the steps to reproduce, and what an attacker gains.

## Scope

In scope: what this fork adds or changes — the Groq speech engine (it sends dictation audio to
Groq with the remote model's key), the terminal command input row, the `com.termux` + Nix build identity and
`scripts/nix/com-termux-profile-compat.nix`.

Report elsewhere:
- Anything that also exists in upstream Termux Launcher (the launcher, `launcherctl`, the
  privileged lane, the companion apps): [PickleHik3/termux-launcher](https://github.com/PickleHik3/termux-launcher/security).
- Termux packages and the apt repository: [termux/termux-packages](https://github.com/termux/termux-packages).
- Bugs that also exist in upstream Termux: [termux.dev/security](https://termux.dev/security).

Known and intended: APKs built from this repository are signed with the shared debug key
(`testkey_untrusted.jks`), the same key upstream publishes. Anyone can sign an APK with it, so
treat these builds as unverified and install only what you built yourself.
