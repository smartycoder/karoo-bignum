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

All verified 2026-08-24:

- [x] Repo is public at `github.com/smartycoder/karoo-bignum`
- [x] Release `v1.0.0` has `app-release.apk` attached; `releases/latest/download/app-release.apk`
      returns 302
- [x] `manifest.json` attached to the same release; `releases/latest/download/manifest.json`
      returns 302, which is where `MANIFEST_URL` points
- [x] `docs/icon.png` exists and the `iconUrl` in the published manifest resolves
- [x] Screenshots in `docs/screenshots/` are up to date (real device, all three zone modes)

## How to submit

Per the list's own Contributing section:

1. Fork `timklge/awesome-karoo`.
2. Branch, e.g. `feature/bignum`.
3. Add the Table of Contents line and the entry above, both at the end of their sections.
4. Commit, push, open a pull request.

The last entry under **Other extensions** is currently `karoo-hass-companion`, so BigNum goes
after it.
