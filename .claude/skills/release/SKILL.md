---
name: release
description: "Publish a BigNum release to GitHub so the Karoo extension library picks it up. Use when cutting a release, tagging a version, publishing an update, or when asked why an update is not reaching riders. Covers the build, the signing check, the manifest, and verifying the published URLs. Triggers on: release, izdaja, objavi verzijo, nova verzija, tag, deploy, publish."
---

# Releasing BigNum

Publishing is **distribution, not a push**. The moment the GitHub Release appears, the Karoo
extension library offers the update to every rider who has BigNum installed. There is no staged
rollout and no undo. Confirm with the user before step 5, always.

The build stays local: `keystore.properties` is gitignored and must never leave this machine, so
CI cannot sign anything. What CI does is catch a release published wrongly *after the fact*
(`.github/workflows/release-assets.yml`). This procedure is what stops it happening.

## What has actually gone wrong here

Both real failures are worth knowing, because they look nothing alike:

- **v1.3.0 and v1.3.1 shipped without `manifest.json` attached.** The library reads
  `releases/latest/download/manifest.json`; bumping the file in the repo does nothing on its own.
  That URL returned 404 for two days. Riders could not open BigNum's settings and were offered no
  update — while the APK sat there perfectly healthy, which is why nobody suspected the release.
  A user reported it by email. **Step 6 exists to catch this.**
- **v1.3.0's release notes described a demo value that had changed days earlier.** No script can
  catch that. **Step 4 exists to catch this**, and it is the reason this is a skill rather than
  one big script.

## Steps

### 1. Preconditions

- On `main`, working tree clean, in sync with `origin`.
- `keystore.properties` exists (without it `assembleRelease` silently produces an *unsigned* APK).
- `gh auth status` is good.

Stop and tell the user if any of these fail. Do not fix them silently.

### 2. Versions agree

Read, do not assume:

- `app/build.gradle.kts` — `versionName`, `versionCode`
- `app/manifest.json` — `latestVersion`, `latestVersionCode`
- `CHANGELOG.md` — a `## [x.y.z] - YYYY-MM-DD` heading for this version

All three must name the same version, and `versionCode` must be exactly one higher than the last
release's. The tag `vX.Y.Z` must not exist yet, locally or on `origin`.

This skill does **not** bump versions or write the changelog. If they disagree, say so and let the
user decide what the version is.

### 3. Build

```
./gradlew assembleRelease
./gradlew testDebugUnitTest
scripts/verify-release-apk.sh
```

`verify-release-apk.sh` is not optional and its result is not advisory. It compares the new APK's
signing certificate against the APK riders currently have installed, and refuses a version code
that does not go up. Both mistakes are irreversible: a different key means **no existing install
can ever be updated again**, with no recovery but asking every rider to uninstall and lose their
layout. If it fails, stop. Never pass `ALLOW_FIRST_RELEASE=1` unless this is genuinely the first
release ever.

### 4. Read the release notes against the diff

The judgement step. Put these three side by side and actually read them:

- the `CHANGELOG.md` entry for this version
- `releaseNotes` in `app/manifest.json` — condensed prose, deliberately not a copy, so it cannot
  be diffed mechanically
- `git log` / `git diff` since the previous tag

`releaseNotes` is what the Karoo shows riders in its update flow. Check that it still describes
what the code now does — not what it did when the note was written — and that nothing in the
changelog claims behaviour that later commits changed. Report anything stale to the user rather
than fixing the prose yourself.

### 5. Publish — confirm first

Show the user the version, the release notes as riders will see them, and that this reaches every
existing install. Wait for an explicit yes.

```
git tag -a vX.Y.Z -m "vX.Y.Z"
git push origin vX.Y.Z
gh release create vX.Y.Z \
    app/build/outputs/apk/release/app-release.apk \
    app/manifest.json \
    --title "vX.Y.Z" --notes-file <(...)
```

**Both files.** The APK alone is the failure described above.

### 6. Verify what riders will actually fetch

Not "the release looks right" — fetch the URLs:

```
gh release view vX.Y.Z --json assets --jq '.assets[].name'     # both assets present
curl -sL .../releases/latest/download/manifest.json            # 200, and byte-identical to app/manifest.json
curl -sIL .../releases/latest/download/app-release.apk         # 200
```

Also check the `iconUrl` from the manifest resolves. Then confirm the workflow run on the release
went green.

If anything here fails, the release is broken **for everyone**, and the fix is usually to attach
the missing asset — `gh release upload vX.Y.Z app/manifest.json` — not to cut a new version.

### 7. After

Update the CHANGELOG's `[Unreleased]` section for the next cycle if the user wants it.
