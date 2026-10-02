"""Exercise the evaluated activation hook with isolated Nix profiles.

Run inside the configured Nix-on-Droid environment, passing a previous
nix-on-droid-path store path as --old-path. The production profile is untouched.
"""

import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--flake", default=str(Path.home() / ".config/nix-on-droid"))
    parser.add_argument("--old-path", required=True)
    args = parser.parse_args()

    def evaluate(attribute):
        return subprocess.check_output(
            ["nix", "eval", "--impure", "--raw",
             args.flake + "#nixOnDroidConfigurations.default.config." + attribute],
            text=True,
        )

    hook = evaluate("build.activationAfter.installPackagesComTermux")
    new_path = evaluate("environment.path")
    assert args.old_path != new_path, "The test needs a different previous environment"
    real_nix = new_path + "/bin/nix"
    production = Path.home() / ".nix-profile"
    production_before = production.resolve()
    manifest = json.loads((production / "manifest.json").read_text())
    unrelated = next((name, element) for name, element in manifest["elements"].items()
                     if name != "nix-on-droid-path" and element["active"])

    root = Path(tempfile.mkdtemp(prefix="tlnix-profile-tests-"))
    profile = root / "profile"
    for store_path in [args.old_path, unrelated[1]["storePaths"][0]]:
        subprocess.run([real_nix, "profile", "add", "--profile", str(profile),
                        store_path], check=True)
    initial_manifest = json.loads((profile / "manifest.json").read_text())
    preserved = {name: entry for name, entry in initial_manifest["elements"].items()
                 if name != "nix-on-droid-path"}

    # Redirect only the hook's profile and Nix invocation. Actual profile operations
    # use the real Nix binary; only the failed-add scenario injects a failure.
    assert '"$HOME/.nix-profile/manifest.json"' in hook
    assert '"$newPath/bin/nix" profile' in hook
    redirected = hook.replace('"$HOME/.nix-profile/manifest.json"',
                              '"$testProfile/manifest.json"')
    redirected = redirected.replace('"$newPath/bin/nix" profile', "test_nix")
    wrapper = """test_nix() {
      if [ "${FAIL_ADD:-0}" = 1 ] && [ "$1" = add ]; then return 23; fi
      "$realNix" profile "$@" --profile "$testProfile"
    }
    """
    env = os.environ | {"testProfile": str(profile), "realNix": real_nix,
                        "DRY_RUN_CMD": "", "FAIL_ADD": "0"}

    def activate(**extra):
        return subprocess.run(["bash", "-c", "set -euo pipefail\n" + wrapper + redirected],
                              env=env | extra, text=True, capture_output=True)

    initial = profile.resolve()
    result = activate(DRY_RUN_CMD="echo")
    assert result.returncode == 0 and profile.resolve() == initial, result
    print("PASS dry-run preserves profile", flush=True)
    result = activate(FAIL_ADD="1")
    assert result.returncode == 1 and profile.resolve() == initial, result
    print("PASS failed add restores exact previous profile", flush=True)
    result = activate()
    assert result.returncode == 0, (result.stdout, result.stderr)
    updated_manifest = json.loads((profile / "manifest.json").read_text())
    assert updated_manifest["elements"]["nix-on-droid-path"]["storePaths"] == [new_path]
    assert {name: entry for name, entry in updated_manifest["elements"].items()
            if name != "nix-on-droid-path"} == preserved
    print("PASS replacement preserves unrelated package", flush=True)
    updated = profile.resolve()
    result = activate()
    assert result.returncode == 0 and profile.resolve() == updated, result
    print("PASS repeated activation creates no profile generation", flush=True)
    assert production.resolve() == production_before
    print("PASS production profile untouched by isolated tests", flush=True)
    print("TEST_PROFILES=" + str(root), flush=True)


if __name__ == "__main__":
    main()
