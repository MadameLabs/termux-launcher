{ config, pkgs, ... }:

{
  # Import this module from nix-on-droid.nix when using launcher-nix as com.termux.
  # It adapts package activation only; the fork's read-only app paths stay intact.
  # Back up login, /etc and profiles first: rebuilding login still uses old paths.
  build.activationBefore.comTermuxSkipNixEnvInstall = ''
    nix-env() {
      if [ "''${1-}" = --install ] && [ "''${2-}" = "${config.environment.path}" ]; then
        echo "com.termux: installing the environment through nix profile after activation"
        return 0
      fi
      command nix-env "$@"
    }
  '';

  build.activationAfter.installPackagesComTermux = ''
    newPath=${config.environment.path}
    if ! ${pkgs.jq}/bin/jq -e --arg path "$newPath" \
      '.elements["nix-on-droid-path"] | .active == true and .storePaths == [$path]' \
      "$HOME/.nix-profile/manifest.json" > /dev/null; then
      $DRY_RUN_CMD "$newPath/bin/nix" profile remove nix-on-droid-path
      if ! $DRY_RUN_CMD "$newPath/bin/nix" profile add "$newPath"; then
        "$newPath/bin/nix" profile rollback
        exit 1
      fi
    fi
  '';
}
