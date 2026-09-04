#!/usr/bin/env bash
#
# Refuse to publish a release APK that would break existing installs.
#
# Two things here are irreversible if they go wrong, which is why they are a script that exits
# non-zero rather than a line in a checklist someone reads:
#
#   1. THE SIGNING KEY. Android refuses an update signed with a different key than the installed
#      version. Ship one release under the wrong key and no existing install can EVER take an
#      update again -- there is no recovery, only asking every rider to uninstall and lose their
#      layout. The key is compared against the APK riders actually have, not against a constant
#      committed here, because the installed base is the only thing that matters.
#
#   2. THE VERSION CODE. Android will not install an update whose versionCode is not higher.
#      Publishing an equal or lower one produces a release the library offers and every device
#      then refuses, which looks like a broken download rather than a numbering mistake.
#
# Usage:  scripts/verify-release-apk.sh [path-to-apk]
# Default path is what `./gradlew assembleRelease` produces.
set -euo pipefail

apk="${1:-app/build/outputs/apk/release/app-release.apk}"
manifest="app/manifest.json"

die() { echo "FAIL: $*" >&2; exit 1; }

[ -f "$apk" ] || die "no APK at $apk -- run ./gradlew assembleRelease first"
[ -f "$manifest" ] || die "no $manifest (run this from the repository root)"

# Android build tools are not on PATH by default. Take the highest version installed.
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
tools=$(ls -d "$sdk"/build-tools/* 2>/dev/null | sort -V | tail -1) \
  || die "no Android build-tools under $sdk"
apksigner="$tools/apksigner"
aapt2="$tools/aapt2"
[ -x "$apksigner" ] || die "apksigner not found at $apksigner"
[ -x "$aapt2" ] || die "aapt2 not found at $aapt2"
command -v jq >/dev/null || die "jq is required"

# Captured whole and sliced with parameter expansion, NEVER piped into `head`. Under
# `set -o pipefail` a large producer feeding an early-exiting reader is a guaranteed failure:
# head closes after line one, aapt2 takes SIGPIPE, and the pipeline reports 141. This script
# died that way on its first run.
badging_all=$("$aapt2" dump badging "$apk")
badging=${badging_all%%$'\n'*}

# Reads key='value' out of a badging line. ONE copy of this arithmetic, used for the candidate
# and for the published APK alike: the second copy had its offsets off by one and silently
# produced an empty version code, which then compared as "not greater" and looked like a real
# refusal rather than a broken script.
# awk and not sed, because sed's `.*` is GREEDY and so matches the LAST key that ends in the
# name being looked for: the badging line carries platformBuildVersionName='14' after
# name='io.smartycoder.bignum', and a greedy pattern returned 14 as the package name. awk's
# match() is leftmost, which is the real key every time.
badging_value() {
    awk -v key="$2" '
        { if (match($0, key "=\047[^\047]*\047")) {
              v = substr($0, RSTART + length(key) + 2, RLENGTH - length(key) - 3)
              print v } }
    ' <<< "$1"
}
extract() { badging_value "$badging" "$1"; }
apk_pkg=$(extract "name")
apk_code=$(extract "versionCode")
apk_name=$(extract "versionName")

man_pkg=$(jq -r '.packageName' "$manifest")
man_code=$(jq -r '.latestVersionCode' "$manifest")
man_name=$(jq -r '.latestVersion' "$manifest")
apk_url=$(jq -r '.latestApkUrl' "$manifest")

echo "candidate  $apk_pkg $apk_name (code $apk_code)"
echo "manifest   $man_pkg $man_name (code $man_code)"

[ "$apk_pkg" = "$man_pkg" ] || die "package $apk_pkg != manifest $man_pkg"
[ "$apk_name" = "$man_name" ] || die "versionName $apk_name != latestVersion $man_name"
[ "$apk_code" = "$man_code" ] || die "versionCode $apk_code != latestVersionCode $man_code"

cert() {
    local out digests
    out=$("$apksigner" verify --print-certs "$1")
    digests=$(sed -n 's/.*certificate SHA-256 digest: //p' <<< "$out")
    printf '%s' "${digests%%$'\n'*}"
}
new_cert=$(cert "$apk")
[ -n "$new_cert" ] || die "$apk is not signed -- keystore.properties missing when it was built?"

# The APK riders have installed right now. Before publishing, releases/latest still points at the
# previous release, which is exactly what the new one has to remain compatible with.
published=$(mktemp -t published-apk).apk
if curl -fsSL -o "$published" "$apk_url"; then
    old_cert=$(cert "$published")
    old_badging=$("$aapt2" dump badging "$published")
    old_code=$(badging_value "${old_badging%%$'\n'*}" versionCode)
    echo "published  code $old_code"

    [ "$new_cert" = "$old_cert" ] || die "SIGNING KEY CHANGED
  published $old_cert
  candidate $new_cert
  Publishing this would permanently block updates for every existing install."

    [ "$apk_code" -gt "$old_code" ] || die "versionCode $apk_code is not greater than the published $old_code"
    echo "OK: same signing key, version code goes up"
else
    # A failed download must never read as a pass. This is the one check that cannot be skipped
    # by accident, so skipping it has to be deliberate and typed out.
    [ "${ALLOW_FIRST_RELEASE:-}" = "1" ] || die "could not fetch the published APK from $apk_url
  If this is genuinely the FIRST release, re-run with ALLOW_FIRST_RELEASE=1.
  Otherwise fix the network or the URL -- do not publish without this check."
    echo "OK: first release, nothing published to compare against"
fi
rm -f "$published"

echo "OK: $apk is safe to publish as $man_name"
