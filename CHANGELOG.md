# Changelog

Notable changes per release. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each entry here should match the `releaseNotes` field in `app/manifest.json`, which is what the
Karoo shows in its own update flow.

## [Unreleased]

## [1.1.0] - 2026-08-25

### Added

- **Font setting.** Numbers are now set in **Saira** by default, with a **Width** (50-124%) and
  **Weight** (100-900) to go with it; **Oswald Bold** is still there for anyone who preferred it.
  Saira's digits are all the same width, so a value no longer shifts sideways as its digits
  change; measured against Oswald over same-length values, that shift reaches 35-45px in the
  widest field. Narrow digits also make the number *taller*: most fields run out of width before
  they run out of height, so at the default width the digits gain roughly 14% on a half-width
  field and a third on **Time - Elapsed** and **Climb - VAM**. Field labels stay in Oswald
  whatever the number is set to, since at 11dp a condensed face loses the space between a label's
  words.
- **Climb - Grade** draws the slope as a coloured wedge behind the number. Its direction follows
  the sign -- rising for a climb, falling for a descent -- and its height and colour follow the
  size, over the same seven bands as the Karoo's own Climber scale, with thresholds at
  2 / 5 / 8 / 11 / 14 / 20%. At 20% the wedge runs corner to corner. Because the wedge cuts
  diagonally across the number, the number and label take a thin contrasting outline rather than
  flipping colour wholesale, which no single threshold could get right.
- Turning zone colouring off now turns the grade wedge off too, on the reading that a rider who
  wants no colour means everywhere.

### Notes

- Saira is bundled as the variable `Saira[wdth,wght].ttf` from Google Fonts, subset to Latin so
  the two axes survive without carrying glyphs no field draws. Both it and Oswald are licensed
  under the SIL Open Font License 1.1; see [OFL.txt](OFL.txt).
- A non-finite grade takes the lowest band and the floor wedge height rather than the loudest
  colour and an undefined path.
- Fields other than Grade render exactly as before when no wedge is present.

## [1.0.1] - 2026-08-24

### Added

- **Power - W/kg 3s** and **Power - W/kg 5s**. karoo-ext has no smoothed power-to-weight type, so
  these divide the smoothed power stream by the rider weight from the Karoo profile. Without a
  usable weight the field shows `--` rather than raw watts labelled as W/kg.

### Fixed

- A field's fallback value is now rendered through the same conversion as a live one, so a derived
  field cannot print watts where it promises W/kg.
- A non-finite rider weight no longer reaches the screen as `NaN`.

## [1.0.0] - 2026-08-24

First public release.

### Added

- 39 data fields under **BigNum** in the field picker, covering speed, distance, heart rate,
  cadence, power, climbing, time and temperature.
- Numbers rendered in bundled Oswald Bold, sized to fill the field rather than sit at the
  default text size. Each field keeps a constant text size regardless of how many digits its
  current value has; wider values shrink to fit.
- Zone coloring for heart rate and power fields, using the colors the Karoo shows on its own
  Heart Rate Zones and Power Zones settings screens — five zones for heart rate, seven for
  power — with the boundaries taken from the rider's Karoo profile. Three modes:
  - **Off** — every value in the normal text color.
  - **Number** — the number takes the zone color.
  - **Field background** — the field is filled with the zone color and the number, label and
    icon switch to black or white, whichever contrasts better with that fill.
- Elapsed time draws the seconds at half size and raised, so the hours and minutes stay large.
- Metric/imperial follows the Karoo profile for speed, distance, elevation and temperature;
  power-to-weight and TSS use the rider weight and FTP from the same profile.
- Power smoothing offered as separate fields — 3s, 5s, 10s, 30s, 20m, 1hr, lap average and
  normalized power — because karoo-ext gives an extension no per-field settings of its own.
- Light and dark themes, picked up from the Karoo's own setting on every redraw.

### Notes

- A field with no zone, a value of zero, or a profile with no zones configured is drawn in the
  normal text color and never filled.
- The rounded card behind each field is drawn by Karoo. On a ride page it does not clip the
  extension's view to that card, so the fill rounds its own corners to match.

[Unreleased]: https://github.com/smartycoder/karoo-bignum/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/smartycoder/karoo-bignum/releases/tag/v1.1.0
[1.0.1]: https://github.com/smartycoder/karoo-bignum/releases/tag/v1.0.1
[1.0.0]: https://github.com/smartycoder/karoo-bignum/releases/tag/v1.0.0
