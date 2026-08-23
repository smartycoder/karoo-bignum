# awesome-karoo submission

Repo: https://github.com/timklge/awesome-karoo (default branch: `master`)

BigNum is not in Hammerhead's official library, so the entry goes under
**## Other extensions**. Entries there are appended in the order they were contributed, not
alphabetically — add it at the end of that section, and add the matching line to the Table of
Contents.

## Table of Contents line

Insert as the last child under `- [Other extensions](#other-extensions)`:

```markdown
  - [BigNum](#bignum)
```

## Entry

Append at the end of the `## Other extensions` section:

```markdown
### BigNum
- **Extension Name:** [BigNum](https://github.com/smartycoder/karoo-bignum)
  - Description: Large, bold numeric data fields rendered in Oswald Bold so the number fills the whole field, with optional heart rate and power zone coloring that matches the zone colors your Karoo already uses.
  - License: Open Source, Apache 2
  - Features:
    - 39 data fields covering speed, distance, heart rate, cadence, power, climbing, time and temperature
    - Numbers are drawn to fill the available field area instead of using the default field text size
    - Heart rate and power fields are colored by zone, matching the colors on the Karoo's own zone settings screens (five zones for heart rate, seven for power), with boundaries taken from your Karoo profile
    - Zone coloring has three modes: off, color the number, or fill the whole field and set the number in black or white for contrast
    - Elapsed time draws the seconds smaller and raised, so the hours and minutes stay large
    - Power smoothing variants: 3s, 5s, 10s, 30s, 20m, 1hr, lap average, normalized power
    - Derived fields: W/kg, TSS, calories, VAM, % of max HR, % of HRR, distance and elevation to top of climb
    - Respects the metric/imperial preference from your Karoo profile
```

## Before submitting

- [ ] Repo is public at `github.com/smartycoder/karoo-bignum`
- [ ] A release exists with `app-release.apk` attached (the README install links point at
      `releases/latest`)
- [ ] `manifest.json` is attached to the same release — `MANIFEST_URL` in `AndroidManifest.xml`
      points at `releases/latest/download/manifest.json`, and the Karoo "Update" action reads it
- [ ] `docs/icon.png` exists — `manifest.json` `iconUrl` points at it (the app icon is currently a
      vector drawable, so a PNG has to be exported)
- [x] Screenshots in `docs/screenshots/` are up to date (real device, all three zone modes)
